package com.mylosis.roster.storage;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.ProfileGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Merges imported data into existing data, with duplicate detection and
 * resolution. Duplicate lookups are served from indexes built once per merge
 * and maintained as accounts are added, so large imports stay linear.
 *
 * <p>Imported categories and accounts are appended after the existing ones
 * (keeping their relative order from the export) instead of keeping their
 * exported {@code sortOrder}, which would interleave them with existing
 * entries that happen to share the same numbers.
 *
 * <p>Single use: create a fresh instance for each merge.
 */
public class AccountDataMerger
{
    /** id -> existing account. */
    private final Map<String, Account> byId = new HashMap<>();
    /** lowercase {@code username + '\0' + alias} -> existing account. */
    private final Map<String, Account> byUsernameAlias = new HashMap<>();
    /** lowercase aliases currently in use. */
    private final Set<String> aliases = new HashSet<>();
    /** existing account -> its index in the accounts list, for in-place updates. */
    private final Map<Account, Integer> positions = new IdentityHashMap<>();
    /** effective category id (null key = Uncategorized) -> next free sortOrder. */
    private final Map<String, Integer> nextSortOrder = new HashMap<>();

    private int accountsAdded;
    private int accountsSkipped;
    private int accountsUpdated;
    private int groupsAdded;
    private int groupsSkipped;

    /**
     * Merge imported data into existing data with duplicate handling.
     * Returns import statistics.
     */
    public ImportStats merge(AccountData existing, AccountData importData, ImportDuplicateMode duplicateMode)
    {
        // Categories first: whether an imported account lands in a category or
        // in Uncategorized depends on that category existing after the merge.
        if (importData.getGroups() != null)
        {
            mergeGroups(existing, importData.getGroups());
        }
        if (importData.getAccounts() != null)
        {
            buildIndexes(existing);
            mergeAccounts(existing, importData.getAccounts(), duplicateMode);
        }
        return new ImportStats(accountsAdded, accountsSkipped, accountsUpdated, groupsAdded, groupsSkipped);
    }

    private void mergeGroups(AccountData existing, List<ProfileGroup> imported)
    {
        List<ProfileGroup> ordered = new ArrayList<>(imported);
        ordered.sort(Comparator.comparingInt(ProfileGroup::getSortOrder));
        int next = existing.nextGroupSortOrder();
        for (ProfileGroup group : ordered)
        {
            if (existing.findGroupById(group.getId()) == null)
            {
                group.setSortOrder(next++);
                existing.addGroup(group);
                groupsAdded++;
            }
            else
            {
                groupsSkipped++;
            }
        }
    }

    private void buildIndexes(AccountData existing)
    {
        List<Account> accounts = existing.getAccounts();
        for (int i = 0; i < accounts.size(); i++)
        {
            Account account = accounts.get(i);
            indexAccount(account);
            positions.put(account, i);
            String group = existing.effectiveGroupId(account);
            int order = account.getMetadata() != null ? account.getMetadata().getSortOrder() : 0;
            nextSortOrder.merge(group, order + 1, Math::max);
        }
    }

    private void indexAccount(Account p)
    {
        if (p.getId() != null)
        {
            byId.put(p.getId(), p);
        }
        String pairKey = usernameAliasKey(p);
        if (pairKey != null)
        {
            byUsernameAlias.put(pairKey, p);
        }
        if (p.getAlias() != null)
        {
            aliases.add(p.getAlias().toLowerCase(Locale.ROOT));
        }
    }

    private void unindexAccount(Account p)
    {
        if (p.getId() != null)
        {
            byId.remove(p.getId(), p);
        }
        String pairKey = usernameAliasKey(p);
        if (pairKey != null)
        {
            byUsernameAlias.remove(pairKey, p);
        }
        // Aliases are not removed: another account may share the alias, and
        // keeping it only makes suffix generation more conservative.
    }

    private static String usernameAliasKey(Account p)
    {
        if (p.getUsername() == null)
        {
            return null;
        }
        String alias = p.getAlias() != null ? p.getAlias() : "";
        return p.getUsername().toLowerCase(Locale.ROOT) + '\0' + alias.toLowerCase(Locale.ROOT);
    }

    private void mergeAccounts(AccountData existing, List<Account> imported, ImportDuplicateMode mode)
    {
        List<Account> ordered = new ArrayList<>(imported);
        ordered.sort(AccountData.SORT_ORDER_COMPARATOR);
        for (Account profile : ordered)
        {
            Account duplicate = findDuplicate(profile);
            if (duplicate == null)
            {
                append(existing, profile);
                accountsAdded++;
            }
            else
            {
                handleDuplicate(existing, profile, duplicate, mode);
            }
        }
    }

    private void handleDuplicate(AccountData existing, Account profile, Account duplicate, ImportDuplicateMode mode)
    {
        switch (mode)
        {
            case ADD_WITH_SUFFIX:
                profile.setAlias(generateUniqueAlias(profile.getAlias()));
                profile.setId(UUID.randomUUID().toString());
                append(existing, profile);
                accountsAdded++;
                break;

            case UPDATE:
                replace(existing, duplicate, profile);
                accountsUpdated++;
                break;

            case SKIP:
            default:
                accountsSkipped++;
                break;
        }
    }

    /** Adds {@code profile} at the end of the category it will be shown in. */
    private void append(AccountData existing, Account profile)
    {
        String group = existing.effectiveGroupId(profile);
        int order = nextSortOrder.getOrDefault(group, 0);
        profile.ensureMetadata().setSortOrder(order);
        nextSortOrder.put(group, order + 1);
        existing.addAccount(profile);
        positions.put(profile, existing.getAccounts().size() - 1);
        indexAccount(profile);
    }

    /**
     * Swaps {@code profile} into {@code duplicate}'s list slot and id. It keeps
     * the duplicate's position when it stays in the same category, and is
     * appended to its new category otherwise.
     */
    private void replace(AccountData existing, Account duplicate, Account profile)
    {
        int index = positions.remove(duplicate);
        unindexAccount(duplicate);
        profile.setId(duplicate.getId());

        String oldGroup = existing.effectiveGroupId(duplicate);
        String newGroup = existing.effectiveGroupId(profile);
        boolean sameGroup = oldGroup == null ? newGroup == null : oldGroup.equals(newGroup);
        if (sameGroup && duplicate.getMetadata() != null)
        {
            profile.ensureMetadata().setSortOrder(duplicate.getMetadata().getSortOrder());
        }
        else
        {
            int order = nextSortOrder.getOrDefault(newGroup, 0);
            profile.ensureMetadata().setSortOrder(order);
            nextSortOrder.put(newGroup, order + 1);
        }

        existing.replaceAccount(index, profile);
        positions.put(profile, index);
        indexAccount(profile);
    }

    private Account findDuplicate(Account importAccount)
    {
        // Match by ID first (exact same entry)
        if (importAccount.getId() != null)
        {
            Account byIdMatch = byId.get(importAccount.getId());
            if (byIdMatch != null)
            {
                return byIdMatch;
            }
        }

        // Match by username AND alias: same login with a different alias is intentional
        String pairKey = usernameAliasKey(importAccount);
        return pairKey != null ? byUsernameAlias.get(pairKey) : null;
    }

    private String generateUniqueAlias(String baseAlias)
    {
        if (baseAlias == null || baseAlias.isEmpty()) baseAlias = "Account";

        String alias = baseAlias;
        int suffix = 2;

        while (aliases.contains(alias.toLowerCase(Locale.ROOT)))
        {
            alias = baseAlias + " (" + suffix + ")";
            suffix++;
        }
        return alias;
    }
}
