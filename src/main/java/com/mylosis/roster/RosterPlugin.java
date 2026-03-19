package com.mylosis.roster;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.mylosis.roster.model.Account;
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
                loggedInDisplayName = client.getLocalPlayer().getName();
                log.debug("Detected logged-in player: {}", loggedInDisplayName);
                SwingUtilities.invokeLater(() -> {
                    if (panel != null) panel.rebuild();
                });
                return true;
            });
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.CONNECTION_LOST)
        {
            loggedInDisplayName = null;
            SwingUtilities.invokeLater(() -> {
                if (panel != null) panel.rebuild();
            });
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!RosterConfig.CONFIG_GROUP.equals(event.getGroup()))
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
            selectedAccountId = profile.getId();

            log.info("Selected account: {}", profile.getDisplayName());

            if (panel != null)
            {
                SwingUtilities.invokeLater(() -> panel.rebuild());
            }
        });
    }

    public RosterPanel getPanel()
    {
        return panel;
    }

    @Provides
    RosterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RosterConfig.class);
    }
}
