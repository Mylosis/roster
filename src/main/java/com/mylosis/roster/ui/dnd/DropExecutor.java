package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.RosterPanel;
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
            SwingUtilities.invokeLater(() -> panel.rebuild());
            log.debug("Reordered account {} to position {} in category {}",
                draggedAccount.getDisplayName(), insertIndex, currentGroupId);
        }
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
            ProfileGroup g = groups.get(i);
            g.setSortOrder(i);
            plugin.getAccountStorage().saveGroup(g);
        }

        SwingUtilities.invokeLater(() -> panel.rebuild());
        log.debug("Reordered category {} to position {}", draggedCategory.getName(), insertIndex);
    }

    private void moveProfileToCategory(Account profile, String newGroupId, int insertIndex)
    {
        profile.setGroupId(newGroupId);
        if (profile.getMetadata() != null && insertIndex >= 0)
        {
            profile.getMetadata().setSortOrder(insertIndex);
        }
        plugin.getAccountStorage().saveAccount(profile);
        updateProfileSortOrders(newGroupId, profile.getId(), insertIndex);
        SwingUtilities.invokeLater(() -> panel.rebuild());
        log.debug("Moved account {} to category {} at position {}",
            profile.getDisplayName(), newGroupId, insertIndex);
    }

    private void updateProfileSortOrders(String groupId, String insertedProfileId, int insertIndex)
    {
        List<Account> categoryProfiles = getAccountsInCategory(groupId, insertedProfileId);
        categoryProfiles.sort(AccountData.SORT_ORDER_COMPARATOR);

        for (int i = 0; i < categoryProfiles.size(); i++)
        {
            Account p = categoryProfiles.get(i);
            int newOrder = i >= insertIndex ? i + 1 : i;
            if (p.getMetadata() != null)
            {
                p.getMetadata().setSortOrder(newOrder);
                plugin.getAccountStorage().saveAccount(p);
            }
        }
    }

    private void reorderProfileInCategory(String groupId, Account profile, int newIndex)
    {
        List<Account> categoryProfiles = getAccountsInCategory(groupId, null);
        categoryProfiles.sort(AccountData.SORT_ORDER_COMPARATOR);

        categoryProfiles.removeIf(p -> p.getId().equals(profile.getId()));
        newIndex = Math.max(0, Math.min(newIndex, categoryProfiles.size()));
        categoryProfiles.add(newIndex, profile);

        for (int i = 0; i < categoryProfiles.size(); i++)
        {
            Account p = categoryProfiles.get(i);
            if (p.getMetadata() != null)
            {
                p.getMetadata().setSortOrder(i);
                plugin.getAccountStorage().saveAccount(p);
            }
        }
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
