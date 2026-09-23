package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.storage.ExportResult;
import com.mylosis.roster.ui.components.GroupComboItem;
import com.mylosis.roster.ui.components.NotificationToast;
import com.mylosis.roster.ui.components.SafeText;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

/**
 * The context menu of an account card: edit, copy login, export, move to
 * category, and delete.
 */
class AccountCardActions
{
    private final RosterPlugin plugin;
    private final RosterPanel panel;
    private final Account account;
    private final Runnable onEdit;

    AccountCardActions(RosterPlugin plugin, RosterPanel panel, Account account, Runnable onEdit)
    {
        this.plugin = plugin;
        this.panel = panel;
        this.account = account;
        this.onEdit = onEdit;
    }

    JPopupMenu createContextMenu()
    {
        JPopupMenu menu = new JPopupMenu();

        addItem(menu, "Edit", onEdit);
        addItem(menu, "Copy Username", () -> plugin.getImportExportService().copyToClipboard(account.getUsername()));
        addItem(menu, "Export to Clipboard", this::exportAccount);
        menu.addSeparator();
        addItem(menu, "Move to Category...", this::showMoveToCategoryDialog);
        menu.addSeparator();
        addItem(menu, "Delete", this::confirmDelete).setForeground(Theme.BUTTON_DANGER);

        return menu;
    }

    private static JMenuItem addItem(JPopupMenu menu, String label, Runnable action)
    {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        menu.add(item);
        return item;
    }

    private void showMoveToCategoryDialog()
    {
        AccountStorage storage = plugin.getAccountStorage();
        List<ProfileGroup> groups = storage.getGroups();

        // Items wrap the id (two categories can share a name) and render the
        // name HTML-safe (the dialog shows them through a JLabel).
        GroupComboItem[] choices = new GroupComboItem[groups.size() + 1];
        choices[0] = GroupComboItem.UNCATEGORIZED;
        for (int i = 0; i < groups.size(); i++)
        {
            choices[i + 1] = new GroupComboItem(groups.get(i).getId(), groups.get(i).getName());
        }

        GroupComboItem selected = (GroupComboItem) JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(panel),
            "Select category:",
            "Move to Category",
            JOptionPane.PLAIN_MESSAGE,
            null,
            choices,
            choices[0]
        );

        if (selected == null || Objects.equals(selected.getId(), account.getGroupId()))
        {
            return;
        }
        // Land at the end of the new category.
        account.ensureMetadata().setSortOrder(storage.nextAccountSortOrder(selected.getId()));
        account.setGroupId(selected.getId());
        storage.saveAccount(account);
        panel.rebuild();
    }

    private void confirmDelete()
    {
        if (plugin.getConfig().confirmDelete())
        {
            int result = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(panel),
                "Are you sure you want to delete \"" + SafeText.singleLine(account.getDisplayName()) + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (result != JOptionPane.YES_OPTION)
            {
                return;
            }
        }

        plugin.getAccountStorage().deleteAccount(account.getId());
        panel.rebuild();
    }

    private void exportAccount()
    {
        ExportResult result = plugin.getImportExportService().exportAccountToClipboard(account);
        if (result.isSuccess())
        {
            panel.showNotification("Exported " + account.getDisplayName(), NotificationToast.Type.SUCCESS);
        }
        else
        {
            panel.showNotification("Export failed: " + result.getError(), NotificationToast.Type.ERROR);
        }
    }
}
