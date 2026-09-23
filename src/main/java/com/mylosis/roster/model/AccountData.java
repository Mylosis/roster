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
     * existing classes are backwards-compatible via Gson (missing fields read as
     * null/default), so the migrate() hook below is currently a no-op, present so
     * future schema changes have a defined entry point.
     */
    public static final int CURRENT_VERSION = 2;

    /**
     * Makes freshly parsed data safe to use: non-null collections, metadata on
     * every account, and the current schema version. Returns this instance.
     */
    public AccountData normalize()
    {
        if (accounts == null)
        {
            accounts = new ArrayList<>();
        }
        if (groups == null)
        {
            groups = new ArrayList<>();
        }
        // Legacy accounts can carry null metadata. Backfill a minimal default,
        // deliberately without createdAt, preserving the "unknown age sorts
        // oldest" semantics of the Date added sort.
        for (Account account : accounts)
        {
            if (account.getMetadata() == null)
            {
                account.setMetadata(AccountMetadata.builder().accountType(AccountType.MAIN).build());
            }
        }
        if (version < CURRENT_VERSION)
        {
            migrate(version);
            version = CURRENT_VERSION;
        }
        invalidateIndexes();
        return this;
    }

    /**
     * Forward-migrates this instance in place from {@code oldVersion} to {@link #CURRENT_VERSION}.
     * Currently a no-op for all known versions; reserved for future schema changes.
     */
    private void migrate(int oldVersion)
    {
        // v1 -> v2: no destructive changes, added field is nullable. Nothing to do.
    }

    /**
     * Stores {@code account}: an existing account (matched by id) is updated in
     * place, keeping its list position, which breaks ties between equal sort
     * orders; a new one is appended to the end of the category it is shown in.
     */
    public void upsertAccount(Account account)
    {
        // The alias/username/notes may have changed; drop the search blob.
        account.invalidateSearchHaystack();
        Account existing = findAccountById(account.getId());
        if (existing == account)
        {
            return;
        }
        if (existing != null)
        {
            replaceAccount(accounts.indexOf(existing), account);
            return;
        }
        account.ensureMetadata().setSortOrder(nextAccountSortOrder(effectiveGroupId(account)));
        addAccount(account);
    }

    /** Stores {@code group} with the same in-place/append rules as {@link #upsertAccount}. */
    public void upsertGroup(ProfileGroup group)
    {
        ProfileGroup existing = findGroupById(group.getId());
        if (existing == group)
        {
            return;
        }
        if (existing != null)
        {
            groups.set(groups.indexOf(existing), group);
            invalidateIndexes();
            return;
        }
        group.setSortOrder(nextGroupSortOrder());
        addGroup(group);
    }

    /**
     * Shared comparator for sorting accounts by their metadata sort order.
     */
    public static final Comparator<Account> SORT_ORDER_COMPARATOR =
        Comparator.comparingInt(AccountData::sortOrderOf);

    public static AccountData createEmpty()
    {
        return AccountData.builder()
            .version(CURRENT_VERSION)
            .accounts(new ArrayList<>())
            .groups(new ArrayList<>())
            .build();
    }

    // Lazy id->entity indexes. Rebuilt on demand and invalidated whenever the
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

    /** Invalidates the lazy id->entity indexes; call after any structural change
     *  to {@link #accounts} or {@link #groups}. The next find* call will rebuild. */
    public void invalidateIndexes()
    {
        accountIndex = null;
        groupIndex = null;
    }

    /**
     * The category an account is actually shown in: its {@code groupId} when
     * that category exists, otherwise null (Uncategorized). Accounts pointing at
     * a deleted or never-imported category render under Uncategorized, so
     * ordering and drag logic must treat them as members of it.
     */
    public String effectiveGroupId(Account account)
    {
        String gid = account.getGroupId();
        return gid != null && findGroupById(gid) != null ? gid : null;
    }

    /** One past the highest {@code sortOrder} among accounts shown in {@code groupId}. */
    public int nextAccountSortOrder(String groupId)
    {
        int max = -1;
        for (Account account : accounts)
        {
            String effective = effectiveGroupId(account);
            if (groupId == null ? effective == null : groupId.equals(effective))
            {
                max = Math.max(max, sortOrderOf(account));
            }
        }
        return max + 1;
    }

    /** One past the highest category {@code sortOrder}. */
    public int nextGroupSortOrder()
    {
        int max = -1;
        for (ProfileGroup group : groups)
        {
            max = Math.max(max, group.getSortOrder());
        }
        return max + 1;
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

    /** Swaps {@code replacement} into {@code index}, keeping list position stable. */
    public void replaceAccount(int index, Account replacement)
    {
        accounts.set(index, replacement);
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

    /**
     * Removes a category. Its accounts move to Uncategorized, appended after the
     * accounts already there and keeping their relative order, so they don't
     * interleave with existing Uncategorized accounts on equal sort orders.
     */
    public void removeGroup(String id)
    {
        List<Account> orphaned = new ArrayList<>();
        for (Account account : accounts)
        {
            if (id.equals(account.getGroupId()))
            {
                orphaned.add(account);
            }
        }
        orphaned.sort(SORT_ORDER_COMPARATOR);
        // Computed while the category still exists, so its own accounts
        // don't count as Uncategorized yet.
        int next = nextAccountSortOrder(null);

        groups.removeIf(g -> g.getId().equals(id));
        for (Account account : orphaned)
        {
            account.setGroupId(null);
            account.ensureMetadata().setSortOrder(next++);
        }
        invalidateIndexes();
    }

    private static int sortOrderOf(Account account)
    {
        return account.getMetadata() != null ? account.getMetadata().getSortOrder() : 0;
    }
}
