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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        // Resolve group-by-id once per rebuild. Used by both the grouping pass
        // below and by AccountCardPanel when it needs the category color for its
        // border — previously each card did its own storage.getGroup() lookup,
        // which was O(n*m) in card and group counts.
        Map<String, ProfileGroup> groupsById = new HashMap<>(sortedGroups.size() * 2);
        for (ProfileGroup g : sortedGroups) groupsById.put(g.getId(), g);
        Set<String> addedProfileIds = new HashSet<>();

        buildGroupedProfiles(targetPanel, filteredProfiles, sortedGroups, groupsById, addedProfileIds, searchFilter);
        buildUngroupedProfiles(targetPanel, filteredProfiles, sortedGroups, groupsById, addedProfileIds);

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
                                      List<ProfileGroup> groups, Map<String, ProfileGroup> groupsById,
                                      Set<String> addedProfileIds, String searchFilter)
    {
        // Bucket profiles by groupId in one pass instead of scanning the filtered
        // list once per group. Previous O(groups * profiles); now O(profiles + groups).
        Map<String, List<Account>> byGroup = new HashMap<>(groups.size() * 2);
        for (Account profile : filteredProfiles)
        {
            String gid = profile.getGroupId();
            if (gid != null && groupsById.containsKey(gid))
            {
                byGroup.computeIfAbsent(gid, k -> new ArrayList<>()).add(profile);
                addedProfileIds.add(profile.getId());
            }
        }

        for (ProfileGroup group : groups)
        {
            List<Account> groupProfiles = byGroup.get(group.getId());
            if (groupProfiles == null)
            {
                groupProfiles = new ArrayList<>();
            }
            sortBySortOrder(groupProfiles);

            if (!groupProfiles.isEmpty() || searchFilter.isEmpty())
            {
                CategoryPanel categoryPanel = new CategoryPanel(
                    plugin, group, groupProfiles, dragDropManager, groupsById, parentPanel);
                target.add(categoryPanel);
                target.add(Box.createVerticalStrut(Theme.SPACING_LG));
            }
        }
    }

    private void buildUngroupedProfiles(JPanel target, List<Account> filteredProfiles,
                                        List<ProfileGroup> groups, Map<String, ProfileGroup> groupsById,
                                        Set<String> addedProfileIds)
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
                    AccountCardPanel card = new AccountCardPanel(plugin, profile, parentPanel, groupsById);
                    AccountDragListener dragListener = new AccountDragListener(dragDropManager, profile, card);
                    dragListener.attachToComponent(card);
                    gridPanel.add(card);
                    parentPanel.registerCard(profile.getId(), card);
                }
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.setBackground(Theme.BACKGROUND);
                wrapper.add(gridPanel, BorderLayout.NORTH);
                // Marks the ungrouped card area as a drop zone: DropTargetFinder
                // hit-tests this container so drops land anywhere in the list,
                // not just on the header (which doesn't even exist without groups).
                wrapper.putClientProperty("UNCATEGORIZED_CONTAINER", Boolean.TRUE);
                target.add(wrapper);
            }
            else
            {
                JPanel ungroupedPanel = new JPanel();
                ungroupedPanel.setLayout(new BoxLayout(ungroupedPanel, BoxLayout.Y_AXIS));
                ungroupedPanel.setBackground(Theme.BACKGROUND);
                // Same drop-zone marker as the grid wrapper above.
                ungroupedPanel.putClientProperty("UNCATEGORIZED_CONTAINER", Boolean.TRUE);
                for (Account profile : ungrouped)
                {
                    AccountCardPanel card = new AccountCardPanel(plugin, profile, parentPanel, groupsById);
                    AccountDragListener dragListener = new AccountDragListener(dragDropManager, profile, card);
                    dragListener.attachToComponent(card);
                    ungroupedPanel.add(card);
                    ungroupedPanel.add(Box.createVerticalStrut(Theme.SPACING_XS));
                    parentPanel.registerCard(profile.getId(), card);
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

    private static final Comparator<Account> NAME_ASC_COMPARATOR =
        // String.CASE_INSENSITIVE_ORDER is allocation-free per compare; the previous
        // safeLower() form allocated a temp String for every comparison, multiplied
        // by O(n log n) for each sort, repeated for every category on each rebuild.
        Comparator.comparing(Account::getDisplayName,
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private static final Comparator<Account> NAME_DESC_COMPARATOR = NAME_ASC_COMPARATOR.reversed();

    private static Comparator<Account> comparatorFor(SortKey key)
    {
        switch (key)
        {
            case NAME_ASC:
                return NAME_ASC_COMPARATOR;
            case NAME_DESC:
                return NAME_DESC_COMPARATOR;
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

        // Account.getSearchHaystack() returns a cached lowercase concatenation of
        // the searchable fields, rebuilt only when the account changes. Previously
        // this was 3× toLowerCase per card per keystroke — at 100 accounts and a
        // 10-char query, that's 3000 throwaway String allocations.
        List<Account> filtered = new ArrayList<>();
        for (Account profile : profiles)
        {
            if (profile.getSearchHaystack().contains(searchFilter))
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
