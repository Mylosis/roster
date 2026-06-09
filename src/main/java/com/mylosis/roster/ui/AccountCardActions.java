package com.mylosis.roster.ui;

import com.google.gson.Gson;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.ui.components.NotificationToast;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Handles context menu actions for an account card:
 * edit, copy username, move to category, and delete.
 */
public class AccountCardActions
{
    private final RosterPlugin plugin;
    private final Account profile;
    private final Runnable onExpand;

    public AccountCardActions(RosterPlugin plugin, Account profile, Runnable onExpand)
    {
        this.plugin = plugin;
        this.profile = profile;
        this.onExpand = onExpand;
    }

    public JPopupMenu createContextMenu()
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem editItem = new JMenuItem("Edit");
        editItem.addActionListener(e -> onExpand.run());
        menu.add(editItem);

        JMenuItem copyUsernameItem = new JMenuItem("Copy Username");
        copyUsernameItem.addActionListener(e -> copyToClipboard(profile.getUsername()));
        menu.add(copyUsernameItem);

        JMenuItem exportItem = new JMenuItem("Export to Clipboard");
        exportItem.addActionListener(e -> exportProfile());
        menu.add(exportItem);

        menu.addSeparator();

        JMenuItem moveToCategoryItem = new JMenuItem("Move to Category...");
        moveToCategoryItem.addActionListener(e -> showMoveToCategoryDialog());
        menu.add(moveToCategoryItem);

        menu.addSeparator();

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.setForeground(Theme.BUTTON_DANGER);
        deleteItem.addActionListener(e -> confirmDelete());
        menu.add(deleteItem);

        return menu;
    }

    private void showMoveToCategoryDialog()
    {
        var groups = plugin.getAccountStorage().getGroups();

        // Wrapper objects (toString = name) instead of name strings: resolving
        // the selection back via indexOf(name) sent the account to the FIRST
        // category with that name whenever two categories shared a name.
        GroupComboItem[] choices = new GroupComboItem[groups.size() + 1];
        choices[0] = new GroupComboItem(null, "Uncategorized");
        for (int i = 0; i < groups.size(); i++)
        {
            choices[i + 1] = new GroupComboItem(groups.get(i).getId(), groups.get(i).getName());
        }

        GroupComboItem selected = (GroupComboItem) JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(plugin.getPanel()),
            "Select category:",
            "Move to Category",
            JOptionPane.PLAIN_MESSAGE,
            null,
            choices,
            choices[0]
        );

        if (selected != null)
        {
            profile.setGroupId(selected.id);
            plugin.getAccountStorage().saveAccount(profile);
            plugin.getPanel().rebuild();
        }
    }

    private void confirmDelete()
    {
        if (plugin.getConfig().confirmDelete())
        {
            int result = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(plugin.getPanel()),
                "Are you sure you want to delete \"" + profile.getDisplayName() + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );

            if (result != JOptionPane.YES_OPTION)
            {
                return;
            }
        }

        plugin.getAccountStorage().deleteProfile(profile.getId());
        plugin.getPanel().rebuild();
    }

    private void exportProfile()
    {
        Gson gson = plugin.getGson().newBuilder().setPrettyPrinting().create();
        // Wrap single profile in AccountData envelope for consistent import format
        AccountData wrapper = AccountData.builder()
            .version(AccountData.CURRENT_VERSION)
            .accounts(java.util.Collections.singletonList(profile))
            .groups(new java.util.ArrayList<>())
            .build();
        String json = gson.toJson(wrapper);
        copyToClipboard(json);
        if (plugin.getPanel() != null)
        {
            plugin.getPanel().showNotification("Exported " + profile.getDisplayName(), NotificationToast.Type.SUCCESS);
        }
    }

    private void copyToClipboard(String text)
    {
        if (text != null)
        {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(text), null);
        }
    }
}
