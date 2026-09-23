package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.model.SortKey;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.ui.RosterPanel;
import com.mylosis.roster.ui.components.NotificationToast;
import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies a completed drop: moves or reorders an account, or reorders a
 * category, then saves the renumbered entries in one batch.
 */
@Slf4j
final class DropExecutor
{
    private final RosterPlugin plugin;
    private final RosterPanel panel;

    /** Whether the "switch to Manual sort" hint was already shown this session. */
    private boolean shownSortOverrideHint = false;

    DropExecutor(RosterPlugin plugin, RosterPanel panel)
    {
        this.plugin = plugin;
        this.panel = panel;
    }

    /**
     * Drops an account into a category (or Uncategorized) between the target's
     * visible neighbours.
     *
     * <p>With a non-MANUAL sort active the new {@code sortOrder} is still
     * recorded, but the visible order won't change until the user switches
     * back to MANUAL; a one-time toast explains that.
     */
    void executeAccountDrop(DropTarget target, Account dragged)
    {
        if (target.getType() == DropTarget.Type.CATEGORY_REORDER)
        {
            return;
        }
        AccountStorage storage = plugin.getAccountStorage();
        String targetGroup = target.getGroupId();
        boolean sameCategory = Objects.equals(targetGroup, effectiveGroupId(storage, dragged));
        if (sameCategory && (dragged.getId().equals(target.getBeforeId()) || dragged.getId().equals(target.getAfterId())))
        {
            // Dropped back into its own slot.
            return;
        }

        List<Account> siblings = new ArrayList<>();
        for (Account account : storage.getAccounts())
        {
            if (!account.getId().equals(dragged.getId()) && Objects.equals(targetGroup, effectiveGroupId(storage, account)))
            {
                siblings.add(account);
            }
        }
        siblings.sort(AccountData.SORT_ORDER_COMPARATOR);

        List<String> siblingIds = new ArrayList<>(siblings.size());
        siblings.forEach(a -> siblingIds.add(a.getId()));
        int index = resolveInsertIndex(siblingIds, target.getBeforeId(), target.getAfterId());

        dragged.setGroupId(targetGroup);
        storage.saveAccounts(renumberWithInsert(siblings, dragged, index));
        log.debug("Dropped account {} into category {} at position {}", dragged.getId(), targetGroup, index);

        if (sameCategory)
        {
            notifyIfSortOverridden();
        }
        SwingUtilities.invokeLater(panel::rebuild);
    }

    /** Moves a category before or after the category it was dropped on. */
    void executeCategoryDrop(DropTarget target, ProfileGroup dragged)
    {
        if (target.getType() != DropTarget.Type.CATEGORY_REORDER)
        {
            return;
        }
        AccountStorage storage = plugin.getAccountStorage();
        List<ProfileGroup> groups = storage.getGroups();
        List<String> previousOrder = idsOf(groups);

        groups.removeIf(g -> g.getId().equals(dragged.getId()));
        int index = resolveInsertIndex(idsOf(groups), target.getBeforeId(), target.getAfterId());
        groups.add(index, dragged);
        if (idsOf(groups).equals(previousOrder))
        {
            return;
        }

        for (int i = 0; i < groups.size(); i++)
        {
            groups.get(i).setSortOrder(i);
        }
        storage.saveGroups(groups);
        log.debug("Moved category {} to position {}", dragged.getId(), index);
        SwingUtilities.invokeLater(panel::rebuild);
    }

    private void notifyIfSortOverridden()
    {
        if (shownSortOverrideHint || plugin.getConfig().sortKey() == SortKey.MANUAL)
        {
            return;
        }
        shownSortOverrideHint = true;
        SwingUtilities.invokeLater(() -> panel.showNotification(
            "Drag order saved. Switch to Manual sort to see it.", NotificationToast.Type.INFO));
    }

    /** The category an account is shown in: its group if that exists, else Uncategorized (null). */
    private static String effectiveGroupId(AccountStorage storage, Account account)
    {
        String gid = account.getGroupId();
        return gid != null && storage.getGroup(gid) != null ? gid : null;
    }

    private static List<String> idsOf(List<ProfileGroup> groups)
    {
        List<String> ids = new ArrayList<>(groups.size());
        groups.forEach(g -> ids.add(g.getId()));
        return ids;
    }

    /**
     * Converts a drop's visible neighbours into an index in the stored order
     * ({@code orderedIds}, which excludes the moved entry): in front of
     * {@code beforeId}, else right after {@code afterId}, else at the end.
     */
    static int resolveInsertIndex(List<String> orderedIds, String beforeId, String afterId)
    {
        int before = beforeId != null ? orderedIds.indexOf(beforeId) : -1;
        if (before >= 0)
        {
            return before;
        }
        int after = afterId != null ? orderedIds.indexOf(afterId) : -1;
        if (after >= 0)
        {
            return after + 1;
        }
        return orderedIds.size();
    }

    /**
     * Inserts {@code moved} into {@code siblings} (sorted, not containing it) at
     * {@code insertIndex} and renumbers everyone's {@code sortOrder}
     * sequentially. Out-of-range indexes append.
     *
     * @return every renumbered account, moved one included, ready for a batched save
     */
    static List<Account> renumberWithInsert(List<Account> siblings, Account moved, int insertIndex)
    {
        int index = insertIndex < 0 || insertIndex > siblings.size() ? siblings.size() : insertIndex;
        List<Account> ordered = new ArrayList<>(siblings);
        ordered.add(index, moved);
        for (int i = 0; i < ordered.size(); i++)
        {
            ordered.get(i).ensureMetadata().setSortOrder(i);
        }
        return ordered;
    }
}
