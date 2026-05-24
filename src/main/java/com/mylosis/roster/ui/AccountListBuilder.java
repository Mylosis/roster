package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.model.SortKey;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.ui.components.Icons;
import com.mylosis.roster.ui.components.Theme;
import com.mylosis.roster.ui.dnd.DragDropManager;
import com.mylosis.roster.ui.dnd.AccountDragListener;

import java.util.Comparator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the scrollable profile list content.
 * Handles filtering, grouping, and empty state rendering.
 */
public class AccountListBuilder
{
    private final RosterPlugin plugin;
    private final AccountStorage storage;
    private final RosterPanel parentPanel;
    private final DragDropManager dragDropManager;

    public AccountListBuilder(RosterPlugin plugin, RosterPanel parentPanel,
                              DragDropManager dragDropManager)
    {
        this.plugin = plugin;
        this.storage = plugin.getAccountStorage();
        this.parentPanel = parentPanel;
        this.dragDropManager = dragDropManager;
    }

    /**
     * Rebuild the profile list into the target panel.
     * Returns the count of filtered profiles for the footer label.
     */
    public int[] rebuild(JPanel targetPanel, String searchFilter)
    {
        targetPanel.removeAll();

        List<Account> allProfiles = storage.getAccounts();
        List<Account> filteredProfiles = filterProfiles(allProfiles, searchFilter);
        List<ProfileGroup> sortedGroups = getSortedGroups();
        List<String> addedProfileIds = new ArrayList<>();

        buildGroupedProfiles(targetPanel, filteredProfiles, sortedGroups, addedProfileIds, searchFilter);
        buildUngroupedProfiles(targetPanel, filteredProfiles, sortedGroups, addedProfileIds);

        if (filteredProfiles.isEmpty() && sortedGroups.isEmpty())
        {
            targetPanel.add(createEmptyStatePanel(searchFilter));
        }

        targetPanel.revalidate();
        targetPanel.repaint();

        return new int[]{filteredProfiles.size(), allProfiles.size()};
    }

    private List<ProfileGroup> getSortedGroups()
    {
        List<ProfileGroup> groups = new ArrayList<>(storage.getGroups());
        groups.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        return groups;
    }

    private void buildGroupedProfiles(JPanel target, List<Account> filteredProfiles,
                                      List<ProfileGroup> groups, List<String> addedProfileIds,
                                      String searchFilter)
    {
        for (ProfileGroup group : groups)
        {
            List<Account> groupProfiles = new ArrayList<>();
            for (Account profile : filteredProfiles)
            {
                if (group.getId().equals(profile.getGroupId()))
                {
                    groupProfiles.add(profile);
                    addedProfileIds.add(profile.getId());
                }
            }

            sortBySortOrder(groupProfiles);

            if (!groupProfiles.isEmpty() || searchFilter.isEmpty())
            {
                CategoryPanel categoryPanel = new CategoryPanel(plugin, group, groupProfiles, dragDropManager);
                target.add(categoryPanel);
                target.add(Box.createVerticalStrut(Theme.SPACING_LG));
            }
        }
    }

    private void buildUngroupedProfiles(JPanel target, List<Account> filteredProfiles,
                                        List<ProfileGroup> groups, List<String> addedProfileIds)
    {
        List<Account> ungrouped = new ArrayList<>();
        for (Account profile : filteredProfiles)
        {
            if (!addedProfileIds.contains(profile.getId()))
            {
                ungrouped.add(profile);
            }
        }

        if (ungrouped.isEmpty() && groups.isEmpty())
        {
            return;
        }

        sortBySortOrder(ungrouped);

        if (!groups.isEmpty())
        {
            target.add(createUncategorizedHeader());
            target.add(Box.createVerticalStrut(Theme.SPACING_SM));
        }

        if (!ungrouped.isEmpty())
        {
            boolean gridMode = plugin.getConfig().gridView();

            if (gridMode)
            {
                int cols = 2;
                JPanel gridPanel = new JPanel(new GridLayout(0, cols, Theme.SPACING_XS, Theme.SPACING_XS));
                gridPanel.setBackground(Theme.BACKGROUND);
                for (Account profile : ungrouped)
                {
                    AccountCardPanel card = new AccountCardPanel(plugin, profile, parentPanel);
                    AccountDragListener dragListener = new AccountDragListener(dragDropManager, profile, card);
                    dragListener.attachToComponent(card);
                    gridPanel.add(card);
                }
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.setBackground(Theme.BACKGROUND);
                wrapper.add(gridPanel, BorderLayout.NORTH);
                target.add(wrapper);
            }
            else
            {
                JPanel ungroupedPanel = new JPanel();
                ungroupedPanel.setLayout(new BoxLayout(ungroupedPanel, BoxLayout.Y_AXIS));
                ungroupedPanel.setBackground(Theme.BACKGROUND);
                for (Account profile : ungrouped)
                {
                    AccountCardPanel card = new AccountCardPanel(plugin, profile, parentPanel);
                    AccountDragListener dragListener = new AccountDragListener(dragDropManager, profile, card);
                    dragListener.attachToComponent(card);
                    ungroupedPanel.add(card);
                    ungroupedPanel.add(Box.createVerticalStrut(Theme.SPACING_XS));
                }
                target.add(ungroupedPanel);
            }

            target.add(Box.createVerticalStrut(Theme.SPACING_SM));
        }
    }

    /**
     * Sort the profile list by the currently-active SortKey. MANUAL falls back to the
     * explicit per-account {@code sortOrder} (drag order), preserving v1.0 behaviour.
     * Non-manual keys never touch {@code sortOrder} — they sort a copy in place, so
     * switching back to MANUAL restores the user's hand-arranged drag order verbatim.
     */
    private void sortBySortOrder(List<Account> profiles)
    {
        SortKey key = plugin.getConfig().sortKey();
        profiles.sort(comparatorFor(key));
    }

    private static Comparator<Account> comparatorFor(SortKey key)
    {
        switch (key)
        {
            case NAME_ASC:
                return Comparator.comparing(a -> safeLower(a.getDisplayName()));
            case NAME_DESC:
                return Comparator.comparing((Account a) -> safeLower(a.getDisplayName())).reversed();
            case LAST_ONLINE:
                // Recent first; never-seen accounts sink to the bottom
                return (a, b) -> Long.compare(lastOnline(b), lastOnline(a));
            case DATE_ADDED:
                // Newest first; pre-v1.1 accounts (no createdAt) sink to the bottom
                return (a, b) -> Long.compare(createdAt(b), createdAt(a));
            case MANUAL:
            default:
                return AccountData.SORT_ORDER_COMPARATOR;
        }
    }

    private static String safeLower(String s)
    {
        return s == null ? "" : s.toLowerCase();
    }

    private static long lastOnline(Account a)
    {
        if (a.getMetadata() == null || a.getMetadata().getLastOnlineAt() == null)
        {
            return Long.MIN_VALUE;
        }
        return a.getMetadata().getLastOnlineAt();
    }

    private static long createdAt(Account a)
    {
        if (a.getMetadata() == null || a.getMetadata().getCreatedAt() == null)
        {
            return Long.MIN_VALUE;
        }
        return a.getMetadata().getCreatedAt();
    }

    private List<Account> filterProfiles(List<Account> profiles, String searchFilter)
    {
        if (searchFilter.isEmpty())
        {
            return new ArrayList<>(profiles);
        }

        List<Account> filtered = new ArrayList<>();
        for (Account profile : profiles)
        {
            String alias = profile.getAlias() != null ? profile.getAlias().toLowerCase() : "";
            String username = profile.getUsername() != null ? profile.getUsername().toLowerCase() : "";
            String notes = profile.getMetadata() != null && profile.getMetadata().getNotes() != null
                ? profile.getMetadata().getNotes().toLowerCase() : "";

            if (alias.contains(searchFilter) || username.contains(searchFilter) || notes.contains(searchFilter))
            {
                filtered.add(profile);
            }
        }
        return filtered;
    }

    private JPanel createEmptyStatePanel(String searchFilter)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(40, Theme.SPACING_LG, 40, Theme.SPACING_LG));

        JLabel iconLabel = new JLabel(Icons.get(Icons.USER, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(iconLabel);
        panel.add(Box.createVerticalStrut(Theme.SPACING_LG));

        String messageText = searchFilter.isEmpty() ? "No accounts yet" : "No matching accounts";
        JLabel message = new JLabel(messageText);
        message.setForeground(Theme.TEXT_SECONDARY);
        message.setFont(Theme.fontRegular(message.getFont(), Theme.FONT_SIZE_HEADING));
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(message);

        if (searchFilter.isEmpty())
        {
            panel.add(Box.createVerticalStrut(Theme.SPACING_SM));
            JLabel hint = new JLabel("Click + to add your first account");
            hint.setForeground(Theme.TEXT_SECONDARY);
            hint.setFont(Theme.fontRegular(hint.getFont(), Theme.FONT_SIZE_BODY));
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(hint);
        }

        return panel;
    }

    private JPanel createUncategorizedHeader()
    {
        JPanel header = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        header.setBackground(Theme.GROUP_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.CARD_BORDER),
            new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_MD)
        ));

        header.putClientProperty("UNCATEGORIZED_HEADER", Boolean.TRUE);

        JLabel label = new JLabel("Uncategorized");
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setFont(Theme.fontBold(label.getFont(), Theme.FONT_SIZE_BODY));
        header.add(label, BorderLayout.CENTER);

        // Add profile button (matches category + button style)
        JButton addButton = new JButton("+");
        addButton.setBackground(Theme.GROUP_HEADER);
        addButton.setForeground(Theme.TEXT_SECONDARY);
        addButton.setFocusPainted(false);
        addButton.setBorderPainted(false);
        addButton.setPreferredSize(new Dimension(28, Theme.BUTTON_HEIGHT_SM));
        addButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addButton.setToolTipText("Add account");
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD, 18f));
        addButton.addActionListener(e ->
            parentPanel.getFormManager().toggleAddProfileForm(
                parentPanel::collapseExpandedCard));
        addButton.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) { addButton.setBackground(Theme.GROUP_HEADER_HOVER); }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) { addButton.setBackground(Theme.GROUP_HEADER); }
        });
        header.add(addButton, BorderLayout.EAST);

        return header;
    }
}
