package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.storage.ExportResult;
import com.mylosis.roster.storage.ImportResult;
import com.mylosis.roster.ui.components.IconButton;
import com.mylosis.roster.ui.components.Icons;
import com.mylosis.roster.ui.components.NotificationToast;
import com.mylosis.roster.ui.components.Theme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.BiConsumer;

/**
 * Builds the footer of the Roster panel: account count, view toggle,
 * import/export menu, and the clear menu.
 */
@Slf4j
class PanelFooterBuilder
{
    /** What a "Clear" menu entry removes. */
    @RequiredArgsConstructor
    private enum ClearScope
    {
        ACCOUNTS("Clear All Accounts", true, false),
        CATEGORIES("Clear All Categories", false, true),
        EVERYTHING("Clear Everything", true, true);

        private final String label;
        private final boolean accounts;
        private final boolean categories;
    }

    private final RosterPlugin plugin;
    private final AccountStorage storage;
    private final JComponent dialogParent;
    private final BiConsumer<String, NotificationToast.Type> showNotification;
    private final Runnable onRebuild;

    private IconButton viewToggleButton;

    PanelFooterBuilder(RosterPlugin plugin, JComponent dialogParent,
                       BiConsumer<String, NotificationToast.Type> showNotification, Runnable onRebuild)
    {
        this.plugin = plugin;
        this.storage = plugin.getAccountStorage();
        this.dialogParent = dialogParent;
        this.showNotification = showNotification;
        this.onRebuild = onRebuild;
    }

    JPanel build(JLabel accountCountLabel)
    {
        JPanel footer = new JPanel(new BorderLayout(Theme.SPACING_SM, Theme.SPACING_SM));
        footer.setBackground(Theme.BACKGROUND);
        footer.setBorder(new EmptyBorder(Theme.SPACING_LG, 0, 0, 0));
        footer.add(accountCountLabel, BorderLayout.WEST);

        viewToggleButton = new IconButton(Icons.GRID, null);
        viewToggleButton.addActionListener(e -> plugin.getConfigManager().setConfiguration(
            RosterConfig.CONFIG_GROUP, "gridView", !plugin.getConfig().gridView()));
        refresh();

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_XS, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(viewToggleButton);
        rightPanel.add(createImportExportButton());
        rightPanel.add(createClearButton());
        footer.add(rightPanel, BorderLayout.EAST);
        return footer;
    }

    /**
     * Syncs the view toggle with the config. Called on every rebuild, which
     * also covers the setting being changed from RuneLite's config panel.
     */
    void refresh()
    {
        boolean grid = plugin.getConfig().gridView();
        viewToggleButton.updateIcon(grid ? Icons.LIST : Icons.GRID);
        viewToggleButton.setToolTipText(grid ? "Switch to list view" : "Switch to grid view");
    }

    private IconButton createImportExportButton()
    {
        JPopupMenu menu = new JPopupMenu();
        addItem(menu, "Import from Clipboard", this::doImport);
        addItem(menu, "Export to Clipboard", this::doExport);
        return menuButton(Icons.CLIPBOARD, "Export/Import accounts", menu);
    }

    private IconButton createClearButton()
    {
        JPopupMenu menu = new JPopupMenu();
        addItem(menu, ClearScope.ACCOUNTS.label, () -> confirmClear(ClearScope.ACCOUNTS));
        addItem(menu, ClearScope.CATEGORIES.label, () -> confirmClear(ClearScope.CATEGORIES));
        menu.addSeparator();
        addItem(menu, ClearScope.EVERYTHING.label, () -> confirmClear(ClearScope.EVERYTHING))
            .setForeground(Theme.BUTTON_DANGER);
        return menuButton(Icons.DELETE, "Clear...", menu);
    }

    /** An icon button that opens {@code menu} above itself (the footer sits at the bottom). */
    private static IconButton menuButton(String icon, String tooltip, JPopupMenu menu)
    {
        IconButton button = new IconButton(icon, tooltip);
        button.addActionListener(e -> menu.show(button, 0, -menu.getPreferredSize().height));
        return button;
    }

    private static JMenuItem addItem(JPopupMenu menu, String label, Runnable action)
    {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        menu.add(item);
        return item;
    }

    private void doExport()
    {
        ExportResult result = plugin.getImportExportService().exportToClipboard();
        if (result.isSuccess())
        {
            showNotification.accept("Copied " + result.getAccountCount() + " accounts, "
                + result.getGroupCount() + " categories", NotificationToast.Type.SUCCESS);
        }
        else
        {
            showNotification.accept("Copy failed: " + result.getError(), NotificationToast.Type.ERROR);
        }
    }

    private void doImport()
    {
        ImportResult result = plugin.getImportExportService().importFromClipboard(plugin.getConfig().importDuplicateMode());
        log.debug("Import result: {}", result);

        if (!result.isSuccess())
        {
            showNotification.accept("Import failed: " + result.getError(), NotificationToast.Type.ERROR);
            return;
        }
        showNotification.accept(result.getDetailedMessage(),
            result.hasChanges() ? NotificationToast.Type.SUCCESS : NotificationToast.Type.WARNING);
        if (result.hasChanges())
        {
            onRebuild.run();
        }
    }

    private void confirmClear(ClearScope scope)
    {
        int accountCount = storage.getAccounts().size();
        int groupCount = storage.getGroups().size();
        int affectedAccounts = scope.accounts ? accountCount : 0;
        int affectedGroups = scope.categories ? groupCount : 0;
        if (affectedAccounts == 0 && affectedGroups == 0)
        {
            showNotification.accept("Nothing to clear", NotificationToast.Type.INFO);
            return;
        }

        String message;
        switch (scope)
        {
            case ACCOUNTS:
                message = "Delete all " + accountCount + " accounts?\n\nThis cannot be undone.";
                break;
            case CATEGORIES:
                message = "Delete all " + groupCount + " categories?\nAccounts will be moved to Uncategorized.";
                break;
            case EVERYTHING:
            default:
                message = "Delete all " + accountCount + " accounts and " + groupCount
                    + " categories?\n\nThis cannot be undone.";
                break;
        }

        int result = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(dialogParent),
            message, scope.label, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result == JOptionPane.YES_OPTION)
        {
            storage.clear(scope.accounts, scope.categories);
            showNotification.accept("Cleared", NotificationToast.Type.SUCCESS);
            onRebuild.run();
        }
    }
}
