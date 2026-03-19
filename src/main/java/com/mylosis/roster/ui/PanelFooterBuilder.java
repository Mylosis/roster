package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.storage.ExportResult;
import com.mylosis.roster.storage.ImportExportService;
import com.mylosis.roster.storage.ImportResult;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.ui.components.ExpandingMenuButton;
import com.mylosis.roster.ui.components.IconButton;
import com.mylosis.roster.ui.components.Icons;
import com.mylosis.roster.ui.components.NotificationToast;
import com.mylosis.roster.ui.components.Theme;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.BiConsumer;

/**
 * Builds the footer section of the Roster panel.
 * Contains profile count, clear-all button, and import/export menu.
 */
@Slf4j
public class PanelFooterBuilder
{
    private final RosterPlugin plugin;
    private final AccountStorage storage;
    private final BiConsumer<String, NotificationToast.Type> showNotification;
    private final Runnable onRebuild;

    public PanelFooterBuilder(RosterPlugin plugin, BiConsumer<String, NotificationToast.Type> showNotification,
                              Runnable onRebuild)
    {
        this.plugin = plugin;
        this.storage = plugin.getAccountStorage();
        this.showNotification = showNotification;
        this.onRebuild = onRebuild;
    }

    public JPanel build(JLabel accountCountLabel)
    {
        JPanel footer = new JPanel(new BorderLayout(Theme.SPACING_SM, Theme.SPACING_SM));
        footer.setBackground(Theme.BACKGROUND);
        footer.setBorder(new EmptyBorder(Theme.SPACING_LG, 0, 0, 0));

        footer.add(accountCountLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_XS, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(createViewToggleButton());
        rightPanel.add(createImportExportMenu());
        rightPanel.add(createClearAllButton());

        footer.add(rightPanel, BorderLayout.EAST);
        return footer;
    }

    private IconButton createViewToggleButton()
    {
        boolean isGrid = plugin.getConfig().gridView();
        String icon = isGrid ? Icons.LIST : Icons.GRID;
        String tooltip = isGrid ? "Switch to list view" : "Switch to grid view";
        IconButton button = new IconButton(icon, tooltip);
        button.addActionListener(e -> {
            net.runelite.client.config.ConfigManager configManager = plugin.getConfigManager();
            boolean current = plugin.getConfig().gridView();
            boolean next = !current;
            configManager.setConfiguration(RosterConfig.CONFIG_GROUP, "gridView", next);
            // Update button icon and tooltip to reflect new state
            button.updateIcon(next ? Icons.LIST : Icons.GRID);
            button.setToolTipText(next ? "Switch to list view" : "Switch to grid view");
        });
        return button;
    }

    private IconButton createClearAllButton()
    {
        IconButton button = new IconButton(Icons.DELETE, "Clear...");
        JPopupMenu clearMenu = new JPopupMenu();

        JMenuItem clearProfiles = new JMenuItem("Clear All Accounts");
        clearProfiles.addActionListener(e -> confirmClear("accounts"));
        clearMenu.add(clearProfiles);

        JMenuItem clearCategories = new JMenuItem("Clear All Categories");
        clearCategories.addActionListener(e -> confirmClear("categories"));
        clearMenu.add(clearCategories);

        clearMenu.addSeparator();

        JMenuItem clearBoth = new JMenuItem("Clear Everything");
        clearBoth.setForeground(Theme.BUTTON_DANGER);
        clearBoth.addActionListener(e -> confirmClear("both"));
        clearMenu.add(clearBoth);

        button.addActionListener(e -> clearMenu.show(button, 0, -clearMenu.getPreferredSize().height));
        return button;
    }

    private ExpandingMenuButton createImportExportMenu()
    {
        ExpandingMenuButton menu = new ExpandingMenuButton(Icons.CLIPBOARD, "Export/Import accounts");
        menu.addMenuItem("Import from clipboard", this::doImport);
        menu.addMenuItem("Copy to clipboard", this::doExport);
        return menu;
    }

    private void doExport()
    {
        ImportExportService service = plugin.getImportExportService();
        ExportResult result = service.exportToClipboard();

        if (result.success)
        {
            showNotification.accept("Copied " + result.accountCount + " accounts, " + result.groupCount + " categories",
                NotificationToast.Type.SUCCESS);
        }
        else
        {
            showNotification.accept("Copy failed: " + result.error, NotificationToast.Type.ERROR);
        }
    }

    private void doImport()
    {
        ImportExportService service = plugin.getImportExportService();
        ImportDuplicateMode duplicateMode = plugin.getConfig().importDuplicateMode();
        ImportResult result = service.importFromClipboard(false, duplicateMode);

        log.info("Import result: success={}, added={}, skipped={}, updated={}, error={}",
            result.success, result.accountsAdded, result.accountsSkipped, result.accountsUpdated, result.error);

        if (result.success)
        {
            NotificationToast.Type type = result.hasChanges() ? NotificationToast.Type.SUCCESS : NotificationToast.Type.WARNING;
            showNotification.accept(result.getDetailedMessage(), type);
            if (result.hasChanges()) onRebuild.run();
        }
        else
        {
            showNotification.accept("Import failed: " + result.error, NotificationToast.Type.ERROR);
        }
    }

    private void confirmClear(String mode)
    {
        int accountCount = storage.getAccounts().size();
        int groupCount = storage.getGroups().size();

        String message;
        String title;
        switch (mode)
        {
            case "accounts":
                if (accountCount == 0) { showNotification.accept("No accounts to clear", NotificationToast.Type.INFO); return; }
                message = "Delete all " + accountCount + " accounts?\n\nThis cannot be undone.";
                title = "Clear Accounts";
                break;
            case "categories":
                if (groupCount == 0) { showNotification.accept("No categories to clear", NotificationToast.Type.INFO); return; }
                message = "Delete all " + groupCount + " categories?\nProfiles will be moved to Uncategorized.";
                title = "Clear Categories";
                break;
            default:
                if (accountCount == 0 && groupCount == 0) { showNotification.accept("Nothing to clear", NotificationToast.Type.INFO); return; }
                message = "Delete all " + accountCount + " accounts and " + groupCount + " categories?\n\nThis cannot be undone.";
                title = "Clear Everything";
                break;
        }

        int result = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(plugin.getPanel()),
            message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION)
        {
            if ("accounts".equals(mode) || "both".equals(mode))
            {
                storage.deleteAllProfiles();
            }
            if ("categories".equals(mode) || "both".equals(mode))
            {
                storage.deleteAllGroups();
            }
            showNotification.accept("Cleared " + mode, NotificationToast.Type.SUCCESS);
            onRebuild.run();
        }
    }
}
