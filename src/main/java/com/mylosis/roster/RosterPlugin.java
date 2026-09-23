package com.mylosis.roster;

import com.google.inject.Provides;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.storage.ImportExportService;
import com.mylosis.roster.ui.RosterPanel;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

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

    private LoginTracker loginTracker;

    /** Written on the EDT, read from the client and config threads. Null while stopped. */
    private volatile RosterPanel panel;
    private NavigationButton navButton;

    @Getter
    private volatile String selectedAccountId;

    @Override
    protected void startUp()
    {
        loginTracker = new LoginTracker(accountStorage);
        panel = new RosterPanel(this);

        navButton = NavigationButton.builder()
            .tooltip("Roster")
            .icon(ImageUtil.loadImageResource(getClass(), ICON_PATH))
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        // Enabling the plugin mid-session gets no LOGGED_IN event; pick up the
        // current character directly so the online dot is right from the start.
        clientThread.invokeLater(() -> {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                return trackLogin();
            }
            return true;
        });
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
        panel = null;
        navButton = null;
        selectedAccountId = null;
        loginTracker.reset();
        // Flushes the pending file write and drops the cache, so the next
        // start reloads whatever ConfigManager holds by then.
        accountStorage.shutdown();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGGED_IN)
        {
            // The local player may not exist yet; retry each tick until it does.
            clientThread.invokeLater(() -> {
                if (client.getGameState() != GameState.LOGGED_IN)
                {
                    // Logged out again before the player resolved: stop
                    // retrying, or this closure would run every frame.
                    return true;
                }
                return trackLogin();
            });
        }
        else if (state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
        {
            String previous = loginTracker.setLoggedInName(null);
            if (previous != null)
            {
                onEdt(() -> refreshCards(Collections.singleton(loginTracker.findAccountIdByName(previous))));
            }
        }
    }

    /**
     * Client thread. Records the logged-in character once the local player has
     * a name; returns false (retry next tick) until then.
     */
    private boolean trackLogin()
    {
        Player player = client.getLocalPlayer();
        String name = player != null ? player.getName() : null;
        if (name == null)
        {
            return false;
        }
        String previous = loginTracker.setLoggedInName(name);
        // AccountStorage is EDT-confined; stamping here on the client thread
        // would race UI-driven saves.
        onEdt(() -> refreshCards(loginTracker.recordLogin(name, previous)));
        return true;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!RosterConfig.CONFIG_GROUP.equals(event.getGroup()) || event.getKey() == null)
        {
            return;
        }

        if (AccountStorage.CONFIG_KEY_DATA.equals(event.getKey()))
        {
            // Our own saves echo back here and are ignored: rebuilding on them
            // would bounce the whole panel on every badge click, drag, and
            // last-online stamp. Any other value means RuneLite replaced the
            // data (profile switch, synced config), possibly off the EDT.
            if (!accountStorage.isOwnWrite(event.getNewValue()))
            {
                onEdt(() -> {
                    if (accountStorage.reloadIfChangedExternally())
                    {
                        panel.rebuild();
                    }
                });
            }
            return;
        }

        // A user-visible setting (privacy toggles, grid view, sort key, ...).
        onEdt(() -> panel.rebuild());
    }

    /** Runs {@code task} on the EDT, skipping it if the plugin has stopped by then. */
    private void onEdt(Runnable task)
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                task.run();
            }
        });
    }

    private void refreshCards(Collection<String> accountIds)
    {
        for (String id : accountIds)
        {
            if (id != null)
            {
                panel.refreshAccount(id);
            }
        }
    }

    /** Fills the login screen's username field with this account's login. */
    public void selectAccount(Account account)
    {
        if (account == null)
        {
            return;
        }

        clientThread.invokeLater(() -> {
            if (client.getGameState() != GameState.LOGIN_SCREEN)
            {
                return;
            }

            client.setUsername(account.getUsername());
            String previousId = selectedAccountId;
            String newId = account.getId();
            selectedAccountId = newId;
            // Arm the one-shot link so the next completed login can learn this
            // account's character name.
            loginTracker.armSelectionLink(newId);

            if (newId.equals(previousId))
            {
                return;
            }
            // Only the two cards whose highlight changed need repainting.
            onEdt(() -> refreshCards(previousId != null ? Arrays.asList(previousId, newId)
                : Collections.singleton(newId)));
        });
    }

    /** The logged-in character's name, or null when logged out. */
    public String getLoggedInName()
    {
        return loginTracker.getLoggedInName();
    }

    @Provides
    RosterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RosterConfig.class);
    }
}
