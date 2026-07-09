package com.mylosis.roster;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.storage.ImportExportService;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.ui.RosterPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
    name = "Roster",
    description = "Enhanced account management for OSRS with groups, import/export, and drag-and-drop",
    tags = {"account", "roster", "login", "management", "utility"}
)
public class RosterPlugin extends Plugin
{
    private static final String ICON_PATH = "/com/mylosis/roster/icon.png";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    @Getter
    private ColorPickerManager colorPickerManager;

    @Inject
    @Getter
    private RosterConfig config;

    @Inject
    @Getter
    private AccountStorage accountStorage;

    @Inject
    @Getter
    private ImportExportService importExportService;

    @Inject
    @Getter
    private ConfigManager configManager;

    @Inject
    @Getter
    private Gson gson;

    private RosterPanel panel;
    private NavigationButton navButton;

    @Getter
    private volatile String loggedInDisplayName;

    @Getter
    private volatile String selectedAccountId;

    /**
     * One-shot link between a login-screen card selection and the next completed
     * login. Set by {@link #selectAccount}, consumed (and cleared) by the first
     * LOGGED_IN that follows, so a later, unrelated manual login can never be
     * mis-attributed to a stale selection. Lets us learn the account's real
     * character name even when it matches neither alias nor login email.
     */
    private volatile String pendingLinkAccountId;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Roster started");

        panel = new RosterPanel(this);

        BufferedImage icon = loadIcon();

        navButton = NavigationButton.builder()
            .tooltip("Roster")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Roster stopped");
        // Flush any coalesced disk write before tearing down, so the on-disk
        // mirror matches what's in ConfigManager on next launch.
        if (accountStorage != null)
        {
            accountStorage.shutdown();
        }
        clientToolbar.removeNavigation(navButton);
        panel = null;
        navButton = null;
    }

    private BufferedImage loadIcon()
    {
        try
        {
            return ImageUtil.loadImageResource(getClass(), ICON_PATH);
        }
        catch (Exception e)
        {
            log.warn("Failed to load plugin icon, using default");
            BufferedImage defaultIcon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = defaultIcon.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            java.awt.Color iconColor = new java.awt.Color(200, 200, 200);
            g.setColor(iconColor);

            g.fillOval(1, 3, 5, 5);
            g.fillArc(-1, 8, 8, 8, 0, 180);
            g.fillOval(7, 2, 6, 6);
            g.fillArc(5, 8, 10, 10, 0, 180);

            g.dispose();
            return defaultIcon;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            // Player object may not be available immediately — retries on next game tick
            clientThread.invokeLater(() -> {
                if (client.getGameState() != GameState.LOGGED_IN)
                {
                    // Logged out again before the player resolved — abandon the
                    // retry, or this closure re-runs every frame for the rest
                    // of the session (and each LOGGED_IN stacks another one).
                    return true;
                }
                if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
                {
                    return false;
                }
                String previousOnline = loggedInDisplayName;
                String characterName = client.getLocalPlayer().getName();
                loggedInDisplayName = characterName;
                log.debug("Detected logged-in player: {}", characterName);
                // AccountStorage is EDT-confined: every other reader/writer is
                // Swing UI code. Doing the stamp here on the client thread (as
                // this used to) raced UI-driven saves on the shared AccountData.
                SwingUtilities.invokeLater(() -> {
                    String nowOnlineId = stampLastOnline(characterName);
                    String prevOnlineId = previousOnline != null && !previousOnline.equalsIgnoreCase(characterName)
                        ? findAccountIdByName(previousOnline)
                        : null;
                    if (panel == null) return;
                    // Refresh just the cards whose visible state can have changed —
                    // the now-online card, and the previously-online card if any.
                    // No-op if the logged-in character isn't in the roster.
                    if (prevOnlineId != null && !prevOnlineId.equals(nowOnlineId))
                    {
                        panel.refreshAccount(prevOnlineId);
                    }
                    if (nowOnlineId != null)
                    {
                        panel.refreshAccount(nowOnlineId);
                    }
                });
                return true;
            });
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.CONNECTION_LOST)
        {
            String previousOnline = loggedInDisplayName;
            loggedInDisplayName = null;
            SwingUtilities.invokeLater(() -> {
                String prevOnlineId = findAccountIdByName(previousOnline);
                if (panel != null && prevOnlineId != null)
                {
                    panel.refreshAccount(prevOnlineId);
                }
            });
        }
    }

    /**
     * Returns the roster account matching an in-game character name: an exact
     * {@code characterName} match wins, else the first display-name match
     * (alias if set, otherwise login name). Case-insensitive. EDT only.
     */
    private Account findAccountByCharacterOrDisplayName(String name)
    {
        if (name == null || accountStorage == null) return null;
        Account displayMatch = null;
        for (Account a : accountStorage.getAccounts())
        {
            if (name.equalsIgnoreCase(a.getCharacterName()))
            {
                return a;
            }
            if (displayMatch == null && name.equalsIgnoreCase(a.getDisplayName()))
            {
                displayMatch = a;
            }
        }
        return displayMatch;
    }

    /** Id-returning convenience over {@link #findAccountByCharacterOrDisplayName}. */
    private String findAccountIdByName(String name)
    {
        Account a = findAccountByCharacterOrDisplayName(name);
        return a != null ? a.getId() : null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!RosterConfig.CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }

        // The "data" key is written by every saveAccount/saveGroup. Those code paths
        // already trigger their own UI refresh where appropriate (or are followed by
        // an explicit rebuild). Listening for our own data writes here would create a
        // feedback loop: every badge click, every drag, every 60s last-online stamp
        // would bounce the entire panel. So skip "data" and only rebuild for
        // user-visible setting changes (privacy toggles, grid view, sort key, etc.).
        String key = event.getKey();
        if (key == null || AccountStorage.CONFIG_KEY_DATA.equals(key))
        {
            return;
        }

        if (panel != null)
        {
            SwingUtilities.invokeLater(() -> panel.rebuild());
        }
    }

    public void selectAccount(Account profile)
    {
        if (profile == null)
        {
            return;
        }

        clientThread.invokeLater(() -> {
            if (client.getGameState() != GameState.LOGIN_SCREEN)
            {
                log.debug("Not on login screen, cannot select account");
                return;
            }

            client.setUsername(profile.getUsername());
            // Swap selection. Both the previously-selected card (if visible) and
            // the new one need to repaint their background; everything else on
            // the panel is unchanged, so a full rebuild here would be wasteful.
            String previousId = selectedAccountId;
            selectedAccountId = profile.getId();
            // Arm the one-shot selection-to-login link so the next completed login
            // can learn this account's character name (see stampLastOnline).
            pendingLinkAccountId = profile.getId();

            log.debug("Selected account: {}", profile.getId());

            if (panel != null)
            {
                SwingUtilities.invokeLater(() -> {
                    if (previousId != null && !previousId.equals(selectedAccountId))
                    {
                        panel.refreshAccount(previousId);
                    }
                    panel.refreshAccount(selectedAccountId);
                });
            }
        });
    }

    public RosterPanel getPanel()
    {
        return panel;
    }

    /**
     * lastOnlineAt write throttle, per account. At 10 minutes, world hops within a
     * play session collapse to a single write, and a normal play session ends up
     * writing the timestamp only a handful of times rather than once a minute. The
     * "last online" label on the card has minute-granularity copy anyway ("logged
     * in 5m ago"), so this is invisible to users.
     */
    static final long LAST_ONLINE_THROTTLE_MS = 10L * 60_000L;

    /**
     * Records that {@code characterName} was just seen logged in: stamps
     * {@code lastOnlineAt} (throttled) and learns/refreshes the account's
     * {@code characterName}. The account is resolved by character name, then
     * display name, then the one-shot selection link armed by
     * {@link #selectAccount}, which is consumed here unconditionally so it can
     * never mis-attribute a later, unrelated login. EDT only.
     *
     * @return the matched account's id (for a scoped card refresh), or null if
     *         this character isn't in the roster.
     */
    private String stampLastOnline(String characterName)
    {
        if (characterName == null || accountStorage == null)
        {
            return null;
        }
        Account matched = findAccountByCharacterOrDisplayName(characterName);
        String pendingLink = pendingLinkAccountId;
        pendingLinkAccountId = null;
        if (matched == null && pendingLink != null)
        {
            matched = accountStorage.getProfile(pendingLink);
            if (matched != null)
            {
                log.debug("Linked login '{}' to selected account {}", characterName, matched.getId());
            }
        }
        if (matched == null)
        {
            return null;
        }

        boolean dirty = false;
        if (!characterName.equalsIgnoreCase(matched.getCharacterName()))
        {
            matched.setCharacterName(characterName);
            dirty = true;
        }
        AccountMetadata meta = matched.getMetadata();
        if (meta == null)
        {
            meta = AccountMetadata.createDefault();
            matched.setMetadata(meta);
            dirty = true;
        }
        long now = System.currentTimeMillis();
        Long previous = meta.getLastOnlineAt();
        if (previous == null || (now - previous) >= LAST_ONLINE_THROTTLE_MS)
        {
            meta.setLastOnlineAt(now);
            dirty = true;
        }
        if (dirty)
        {
            accountStorage.saveAccount(matched);
            log.debug("Stamped lastOnlineAt for {}", matched.getId());
        }
        return matched.getId();
    }

    @Provides
    RosterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RosterConfig.class);
    }
}
