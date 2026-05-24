package com.mylosis.roster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountData
{
    private int version;
    private List<Account> accounts;
    private List<ProfileGroup> groups;

    /**
     * Schema version. Bumped to 2 in v1.1 alongside the move to ConfigManager-backed
     * storage and the addition of {@code AccountMetadata.lastOnlineAt}. New fields on
     * existing classes are backwards-compatible via Gson (missing → null/default), so
     * the migrate() hook below is currently a no-op — present so future schema changes
     * have a defined entry point.
     */
    public static final int CURRENT_VERSION = 2;

    /**
     * Forward-migrates this instance in place from {@code oldVersion} to {@link #CURRENT_VERSION}.
     * Currently a no-op for all known versions; reserved for future schema changes.
     */
    public void migrate(int oldVersion)
    {
        // v1 → v2: no destructive changes, added field is nullable. Nothing to do.
    }

    /**
     * Shared comparator for sorting accounts by their metadata sort order.
     */
    public static final Comparator<Account> SORT_ORDER_COMPARATOR = (a, b) -> {
        int orderA = a.getMetadata() != null ? a.getMetadata().getSortOrder() : 0;
        int orderB = b.getMetadata() != null ? b.getMetadata().getSortOrder() : 0;
        return Integer.compare(orderA, orderB);
    };

    public static AccountData createEmpty()
    {
        return AccountData.builder()
            .version(CURRENT_VERSION)
            .accounts(new ArrayList<>())
            .groups(new ArrayList<>())
            .build();
    }

    // Lazy id→entity indexes. Rebuilt on demand and invalidated whenever the
    // backing lists are mutated. Kept transient so Gson never serializes them.
    private transient Map<String, Account> accountIndex;
    private transient Map<String, ProfileGroup> groupIndex;

    public Account findAccountById(String id)
    {
        if (id == null || accounts == null) return null;
        Map<String, Account> idx = accountIndex;
        if (idx == null)
        {
            idx = new HashMap<>(accounts.size() * 2);
            for (Account a : accounts) idx.put(a.getId(), a);
            accountIndex = idx;
        }
        return idx.get(id);
    }

    public ProfileGroup findGroupById(String id)
    {
        if (id == null || groups == null) return null;
        Map<String, ProfileGroup> idx = groupIndex;
        if (idx == null)
        {
            idx = new HashMap<>(groups.size() * 2);
            for (ProfileGroup g : groups) idx.put(g.getId(), g);
            groupIndex = idx;
        }
        return idx.get(id);
    }

    /** Invalidates the lazy id→entity indexes; call after any structural change
     *  to {@link #accounts} or {@link #groups}. The next find* call will rebuild. */
    public void invalidateIndexes()
    {
        accountIndex = null;
        groupIndex = null;
    }

    public List<Account> getAccountsInGroup(String groupId)
    {
        List<Account> result = new ArrayList<>();
        for (Account profile : accounts)
        {
            String pGroupId = profile.getGroupId();
            boolean matches;

            if (groupId == null || groupId.equals(ProfileGroup.UNGROUPED_ID))
            {
                matches = pGroupId == null || pGroupId.isEmpty() || pGroupId.equals(ProfileGroup.UNGROUPED_ID);
            }
            else
            {
                matches = groupId.equals(pGroupId);
            }

            if (matches)
            {
                result.add(profile);
            }
        }
        result.sort(SORT_ORDER_COMPARATOR);
        return result;
    }

    public void addAccount(Account profile)
    {
        if (accounts == null)
        {
            accounts = new ArrayList<>();
        }
        accounts.add(profile);
        invalidateIndexes();
    }

    public void removeAccount(String id)
    {
        if (accounts != null)
        {
            accounts.removeIf(p -> p.getId().equals(id));
            invalidateIndexes();
        }
    }

    public void addGroup(ProfileGroup group)
    {
        if (groups == null)
        {
            groups = new ArrayList<>();
        }
        groups.add(group);
        invalidateIndexes();
    }

    public void removeGroup(String id)
    {
        if (groups != null)
        {
            groups.removeIf(g -> g.getId().equals(id));
        }
        // Move accounts from deleted group to ungrouped
        if (accounts != null)
        {
            for (Account profile : accounts)
            {
                if (id.equals(profile.getGroupId()))
                {
                    profile.setGroupId(null);
                }
            }
        }
        invalidateIndexes();
    }
}
