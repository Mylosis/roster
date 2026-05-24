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
                if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
                {
                    return false;
                }
                String previousOnline = loggedInDisplayName;
                loggedInDisplayName = client.getLocalPlayer().getName();
                log.debug("Detected logged-in player: {}", loggedInDisplayName);
                stampLastOnline(loggedInDisplayName);
                String nowOnlineId = findAccountIdByDisplayName(loggedInDisplayName);
                String prevOnlineId = previousOnline != null ? findAccountIdByDisplayName(previousOnline) : null;
                SwingUtilities.invokeLater(() -> {
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
            String prevOnlineId = previousOnline != null ? findAccountIdByDisplayName(previousOnline) : null;
            SwingUtilities.invokeLater(() -> {
                if (panel != null && prevOnlineId != null)
                {
                    panel.refreshAccount(prevOnlineId);
                }
            });
        }
    }

    /**
     * Returns the id of the account whose display name matches {@code name}
     * (case-insensitive), or null if no roster entry corresponds. Used to scope
     * incremental UI refreshes to just the affected card.
     */
    private String findAccountIdByDisplayName(String name)
    {
        if (name == null || accountStorage == null) return null;
        for (Account a : accountStorage.getAccounts())
        {
            String n = a.getDisplayName();
            if (n != null && name.equalsIgnoreCase(n))
            {
                return a.getId();
            }
        }
        return null;
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

            log.debug("Selected account: {}", profile.getDisplayName());

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
     * Stamps {@code lastOnlineAt} on the account whose display name matches the freshly
     * detected character name. Throttled per-account: at 10 minutes, world hops within a
     * play session collapse to a single write, and a normal play session ends up writing
     * the timestamp only a handful of times rather than once a minute. The "last online"
     * label on the card has minute-granularity copy anyway ("logged in 5m ago"), so this
     * is invisible to users. Matching is case-insensitive against the account's display
     * name (alias if set, otherwise login name).
     */
    private static final long LAST_ONLINE_THROTTLE_MS = 10L * 60_000L;

    private void stampLastOnline(String displayName)
    {
        if (displayName == null || accountStorage == null)
        {
            return;
        }
        Account matched = null;
        for (Account a : accountStorage.getAccounts())
        {
            String name = a.getDisplayName();
            if (name != null && displayName.equalsIgnoreCase(name))
            {
                matched = a;
                break;
            }
        }
        if (matched == null)
        {
            return;
        }
        AccountMetadata meta = matched.getMetadata();
        if (meta == null)
        {
            meta = AccountMetadata.createDefault();
            matched.setMetadata(meta);
        }
        long now = System.currentTimeMillis();
        Long previous = meta.getLastOnlineAt();
        if (previous != null && (now - previous) < LAST_ONLINE_THROTTLE_MS)
        {
            return;
        }
        meta.setLastOnlineAt(now);
        accountStorage.saveAccount(matched);
        log.debug("Stamped lastOnlineAt for {}", matched.getDisplayName());
    }

    @Provides
    RosterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RosterConfig.class);
    }
}
