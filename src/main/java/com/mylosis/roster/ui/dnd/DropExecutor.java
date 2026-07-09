package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.model.SortKey;
import com.mylosis.roster.ui.RosterPanel;
import com.mylosis.roster.ui.components.NotificationToast;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes drop operations for profiles and categories.
 * Handles moving profiles between categories and reordering.
 */
@Slf4j
public class DropExecutor
{
    private final RosterPlugin plugin;
    private final RosterPanel panel;

    public DropExecutor(RosterPlugin plugin, RosterPanel panel)
    {
        this.plugin = plugin;
        this.panel = panel;
    }

    /**
     * Perform a profile drop (move to category or reorder).
     *
     * <p>If a non-MANUAL sort key is active, dragging still records the new
     * {@code sortOrder} so the user's drag intent is preserved — but the visible
     * order won't change until they switch back to MANUAL. We surface a toast
     * the first time this happens so users aren't confused by "nothing happened".
     */
    public void executeProfileDrop(DragDropManager.DropTarget target, Account draggedAccount)
    {
        if (target.type != DragDropManager.DropTarget.Type.CATEGORY_DROP &&
            target.type != DragDropManager.DropTarget.Type.UNCATEGORIZED)
        {
            return;
        }

        String newGroupId = target.groupId;
        String currentGroupId = draggedAccount.getGroupId();
        int insertIndex = target.insertIndex;

        boolean isDifferentCategory = (currentGroupId == null && newGroupId != null) ||
            (currentGroupId != null && !currentGroupId.equals(newGroupId));

        if (isDifferentCategory)
        {
            moveProfileToCategory(draggedAccount, newGroupId, insertIndex);
        }
        else if (insertIndex >= 0)
        {
            reorderProfileInCategory(currentGroupId, draggedAccount, insertIndex);
            notifyIfSortOverridden();
            SwingUtilities.invokeLater(() -> panel.rebuild());
            log.debug("Reordered account {} to position {} in category {}",
                draggedAccount.getId(), insertIndex, currentGroupId);
        }
    }

    /**
     * If a non-MANUAL sort is active, the drag has updated {@code sortOrder} but the
     * visible position won't change. Tell the user once per session so the lack of
     * visible movement doesn't read as a broken drag.
     */
    private boolean shownSortOverrideHint = false;

    private void notifyIfSortOverridden()
    {
        if (shownSortOverrideHint)
        {
            return;
        }
        SortKey active = plugin.getConfig().sortKey();
        if (active == SortKey.MANUAL)
        {
            return;
        }
        shownSortOverrideHint = true;
        SwingUtilities.invokeLater(() ->
            panel.showNotification(
                "Drag order saved — switch to Manual sort to see it",
                NotificationToast.Type.INFO));
    }

    /**
     * Perform a category drop (reorder categories).
     */
    public void executeCategoryDrop(DragDropManager.DropTarget target, ProfileGroup draggedCategory)
    {
        if (target.type != DragDropManager.DropTarget.Type.CATEGORY_REORDER) return;

        List<ProfileGroup> groups = new ArrayList<>(plugin.getAccountStorage().getGroups());
        groups.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));

        int currentIndex = findGroupIndex(groups, draggedCategory.getId());
        if (currentIndex == -1) return;

        groups.remove(currentIndex);

        int insertIndex = target.insertIndex;
        if (currentIndex < insertIndex) insertIndex--;
        insertIndex = Math.max(0, Math.min(insertIndex, groups.size()));
        groups.add(insertIndex, draggedCategory);

        for (int i = 0; i < groups.size(); i++)
        {
            groups.get(i).setSortOrder(i);
        }
        // One batched save — per-group saveGroup() serialized the whole roster
        // into ConfigManager once per category, on the EDT.
        plugin.getAccountStorage().saveGroups(groups);

        SwingUtilities.invokeLater(() -> panel.rebuild());
        log.debug("Reordered category {} to position {}", draggedCategory.getName(), insertIndex);
    }

    private void moveProfileToCategory(Account profile, String newGroupId, int insertIndex)
    {
        profile.setGroupId(newGroupId);

        // Batch the moved account and every renumbered sibling into one save —
        // per-account saveAccount() serialized the whole roster into
        // ConfigManager once per account, making a drop O(n²) on the EDT.
        List<Account> siblings = getAccountsInCategory(newGroupId, profile.getId());
        siblings.sort(AccountData.SORT_ORDER_COMPARATOR);
        List<Account> changed = renumberWithInsert(siblings, profile, insertIndex);
        plugin.getAccountStorage().saveAccounts(changed);

        SwingUtilities.invokeLater(() -> panel.rebuild());
        log.debug("Moved account {} to category {} at position {}",
            profile.getId(), newGroupId, insertIndex);
    }

    private void reorderProfileInCategory(String groupId, Account profile, int newIndex)
    {
        List<Account> siblings = getAccountsInCategory(groupId, profile.getId());
        siblings.sort(AccountData.SORT_ORDER_COMPARATOR);
        List<Account> changed = renumberWithInsert(siblings, profile, newIndex);
        plugin.getAccountStorage().saveAccounts(changed);
    }

    /**
     * Inserts {@code moved} into {@code siblings} (sorted, not containing it) at
     * {@code insertIndex} and renumbers everyone's {@code sortOrder} sequentially.
     * A negative index means "no position information" (e.g. a drop on the
     * Uncategorized header) and appends: previously that case renumbered the
     * siblings but left the moved account's stale sortOrder untouched, so it
     * landed at an unpredictable position. Package-private and side-effect-only
     * on the passed accounts, so tests can exercise the ordering math directly.
     *
     * @return the accounts whose sortOrder was written, ready for a batched save.
     */
    static List<Account> renumberWithInsert(List<Account> siblings, Account moved, int insertIndex)
    {
        if (insertIndex < 0)
        {
            insertIndex = siblings.size();
        }
        insertIndex = Math.min(insertIndex, siblings.size());

        List<Account> ordered = new ArrayList<>(siblings);
        ordered.add(insertIndex, moved);

        List<Account> changed = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++)
        {
            Account p = ordered.get(i);
            // Null metadata is backfilled by AccountStorage.ensureSchema on load,
            // so this guard is belt-and-braces for accounts built in memory.
            if (p.getMetadata() != null)
            {
                p.getMetadata().setSortOrder(i);
                changed.add(p);
            }
            else if (p == moved)
            {
                // The moved account must always ride the batched save: a cross-
                // category move changes its groupId, and if the batch came back
                // empty the save would be skipped and the move lost on reload.
                changed.add(p);
            }
        }
        return changed;
    }

    private List<Account> getAccountsInCategory(String groupId, String excludeProfileId)
    {
        List<Account> result = new ArrayList<>();
        for (Account p : plugin.getAccountStorage().getAccounts())
        {
            String pGroupId = p.getGroupId();
            boolean sameCategory = (groupId == null && pGroupId == null) ||
                (groupId != null && groupId.equals(pGroupId));

            if (sameCategory && (excludeProfileId == null || !p.getId().equals(excludeProfileId)))
            {
                result.add(p);
            }
        }
        return result;
    }

    private int findGroupIndex(List<ProfileGroup> groups, String groupId)
    {
        for (int i = 0; i < groups.size(); i++)
        {
            if (groups.get(i).getId().equals(groupId)) return i;
        }
        return -1;
    }
}
