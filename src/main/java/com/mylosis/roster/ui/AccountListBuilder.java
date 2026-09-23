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
import com.mylosis.roster.ui.dnd.ListLayout;
import lombok.Value;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the scrollable list: categories in order, then Uncategorized, with
 * search filtering, per-category sorting, and the empty states. Records the
 * drop zones it creates in a {@link ListLayout}.
 */
class AccountListBuilder
{
    /** Visible and total account counts for the footer label. */
    @Value
    static class Counts
    {
        int visible;
        int total;
    }

    private static final Comparator<Account> NAME_ASC =
        Comparator.comparing(Account::getDisplayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    // Recent first; never-seen accounts sink to the bottom
    private static final Comparator<Account> LAST_ONLINE =
        Comparator.comparingLong((Account a) -> timestamp(a.getMetadata() != null ? a.getMetadata().getLastOnlineAt() : null)).reversed();
    // Newest first; accounts without createdAt (pre-v1.1) sink to the bottom
    private static final Comparator<Account> DATE_ADDED =
        Comparator.comparingLong((Account a) -> timestamp(a.getMetadata() != null ? a.getMetadata().getCreatedAt() : null)).reversed();

    private final RosterPlugin plugin;
    private final AccountStorage storage;
    private final RosterPanel panel;
    private final DragDropManager dragDropManager;

    AccountListBuilder(RosterPlugin plugin, RosterPanel panel, DragDropManager dragDropManager)
    {
        this.plugin = plugin;
        this.storage = plugin.getAccountStorage();
        this.panel = panel;
        this.dragDropManager = dragDropManager;
    }

    /**
     * Fills {@code target} with the list for the given (lowercase) search filter.
     */
    Counts build(JPanel target, String searchFilter, ListLayout layout)
    {
        boolean searching = !searchFilter.isEmpty();
        List<Account> all = storage.getAccounts();
        List<ProfileGroup> groups = storage.getGroups();
        Map<String, List<Account>> byGroup = new HashMap<>(groups.size() * 2);
        for (ProfileGroup group : groups)
        {
            byGroup.put(group.getId(), new ArrayList<>());
        }

        // Accounts whose category doesn't exist (deleted, or never imported)
        // are shown under Uncategorized.
        List<Account> uncategorized = new ArrayList<>();
        int visible = 0;
        for (Account account : all)
        {
            if (searching && !account.getSearchHaystack().contains(searchFilter))
            {
                continue;
            }
            visible++;
            byGroup.getOrDefault(account.getGroupId(), uncategorized).add(account);
        }

        Comparator<Account> order = comparatorFor(plugin.getConfig().sortKey());
        for (ProfileGroup group : groups)
        {
            List<Account> accounts = byGroup.get(group.getId());
            // While searching, categories without matches are hidden.
            if (searching && accounts.isEmpty())
            {
                continue;
            }
            accounts.sort(order);
            CategoryPanel category = new CategoryPanel(plugin, panel, group, accounts, dragDropManager);
            layout.addCategory(category, category.getCards());
            target.add(category);
            target.add(Box.createVerticalStrut(Theme.SPACING_LG));
        }

        uncategorized.sort(order);
        addUncategorized(target, uncategorized, !groups.isEmpty() && !(searching && uncategorized.isEmpty()), layout);

        if (visible == 0 && (searching || groups.isEmpty()))
        {
            target.add(createEmptyState(searching));
        }
        return new Counts(visible, all.size());
    }

    private void addUncategorized(JPanel target, List<Account> accounts, boolean showHeader, ListLayout layout)
    {
        if (showHeader)
        {
            JPanel header = createUncategorizedHeader();
            layout.addUncategorizedZone(header);
            target.add(header);
            target.add(Box.createVerticalStrut(Theme.SPACING_SM));
        }
        if (accounts.isEmpty())
        {
            return;
        }
        List<AccountCardPanel> cards = new ArrayList<>();
        JPanel container = CardListBuilder.build(plugin, panel, dragDropManager, accounts, null, null, cards);
        layout.addUncategorizedZone(container);
        layout.setUncategorizedCards(cards);
        target.add(container);
        target.add(Box.createVerticalStrut(Theme.SPACING_SM));
    }

    /**
     * MANUAL uses the stored drag order. Other keys sort a copy at render time
     * and never touch {@code sortOrder}, so switching back to MANUAL restores
     * the hand-arranged order.
     */
    private static Comparator<Account> comparatorFor(SortKey key)
    {
        switch (key)
        {
            case NAME_ASC:
                return NAME_ASC;
            case NAME_DESC:
                return NAME_ASC.reversed();
            case LAST_ONLINE:
                return LAST_ONLINE;
            case DATE_ADDED:
                return DATE_ADDED;
            case MANUAL:
            default:
                return AccountData.SORT_ORDER_COMPARATOR;
        }
    }

    private static long timestamp(Long millis)
    {
        return millis != null ? millis : Long.MIN_VALUE;
    }

    private static JPanel createEmptyState(boolean searching)
    {
        JPanel emptyState = new JPanel();
        emptyState.setLayout(new BoxLayout(emptyState, BoxLayout.Y_AXIS));
        emptyState.setBackground(Theme.BACKGROUND);
        emptyState.setBorder(new EmptyBorder(40, Theme.SPACING_LG, 40, Theme.SPACING_LG));

        JLabel iconLabel = new JLabel(Icons.get(Icons.USER, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyState.add(iconLabel);
        emptyState.add(Box.createVerticalStrut(Theme.SPACING_LG));

        JLabel message = new JLabel(searching ? "No matching accounts" : "No accounts yet");
        message.setForeground(Theme.TEXT_SECONDARY);
        message.setFont(Theme.fontRegular(message.getFont(), Theme.FONT_SIZE_HEADING));
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyState.add(message);

        if (!searching)
        {
            emptyState.add(Box.createVerticalStrut(Theme.SPACING_SM));
            JLabel hint = new JLabel("Click + to add your first account");
            hint.setForeground(Theme.TEXT_SECONDARY);
            hint.setFont(Theme.fontRegular(hint.getFont(), Theme.FONT_SIZE_BODY));
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            emptyState.add(hint);
        }
        return emptyState;
    }

    private JPanel createUncategorizedHeader()
    {
        JPanel header = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        header.setBackground(Theme.GROUP_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.CARD_BORDER),
            new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_MD)
        ));

        JLabel label = new JLabel("Uncategorized");
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setFont(Theme.fontBold(label.getFont(), Theme.FONT_SIZE_BODY));
        header.add(label, BorderLayout.CENTER);

        header.add(CategoryPanel.createHeaderAddButton("Add account",
            () -> panel.getFormManager().toggleAddAccountForm(panel::collapseExpandedCard)), BorderLayout.EAST);
        return header;
    }
}
