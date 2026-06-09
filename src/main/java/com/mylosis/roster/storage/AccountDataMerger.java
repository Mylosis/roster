package com.mylosis.roster.storage;

import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles merging imported profile data with existing data,
 * including duplicate detection and resolution.
 *
 * <p>Duplicate lookups are served from indexes built once per merge and
 * maintained as accounts are added — the previous per-account linear scans
 * (plus the id-index rebuild that every {@code addAccount} invalidation
 * forced) made large imports O(n×m) on the EDT.
 */
public class AccountDataMerger
{
    /** id → existing account. */
    private final Map<String, Account> byId = new HashMap<>();
    /** lowercase {@code username + '\0' + alias} → existing account. */
    private final Map<String, Account> byUsernameAlias = new HashMap<>();
    /** lowercase aliases currently in use. */
    private final Set<String> aliases = new HashSet<>();

    /**
     * Merge imported data into existing data with duplicate handling.
     * Returns import statistics.
     */
    public ImportStats merge(AccountData existing, AccountData importData, ImportDuplicateMode duplicateMode)
    {
        ImportStats stats = new ImportStats(0, 0, 0, 0, 0);

        if (importData.getAccounts() != null)
        {
            buildIndexes(existing);
            stats = mergeProfiles(existing, importData, duplicateMode, stats);
        }

        if (importData.getGroups() != null)
        {
            stats = mergeGroups(existing, importData, stats);
        }

        return stats;
    }

    private void buildIndexes(AccountData existing)
    {
        byId.clear();
        byUsernameAlias.clear();
        aliases.clear();
        if (existing.getAccounts() == null)
        {
            return;
        }
        for (Account p : existing.getAccounts())
        {
            indexAccount(p);
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
        // keeping it only makes suffix generation more conservative — matching
        // the old full-scan behavior is not required for correctness here.
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

    private ImportStats mergeProfiles(AccountData existing, AccountData importData,
                                     ImportDuplicateMode duplicateMode, ImportStats stats)
    {
        for (Account profile : importData.getAccounts())
        {
            Account duplicate = findDuplicate(profile);

            if (duplicate == null)
            {
                existing.addAccount(profile);
                indexAccount(profile);
                stats = stats.withAddedAccount();
            }
            else
            {
                stats = handleDuplicate(existing, profile, duplicate, duplicateMode, stats);
            }
        }
        return stats;
    }

    private ImportStats handleDuplicate(AccountData existing, Account profile, Account duplicate,
                                       ImportDuplicateMode mode, ImportStats stats)
    {
        switch (mode)
        {
            case ADD_WITH_SUFFIX:
                String uniqueAlias = generateUniqueAlias(profile.getAlias());
                profile.setAlias(uniqueAlias);
                profile.setId(java.util.UUID.randomUUID().toString());
                existing.addAccount(profile);
                indexAccount(profile);
                return stats.withSuffixedAccount();

            case SKIP:
                return stats.withSkippedAccount();

            case UPDATE:
                existing.removeAccount(duplicate.getId());
                unindexAccount(duplicate);
                profile.setId(duplicate.getId());
                existing.addAccount(profile);
                indexAccount(profile);
                return stats.withUpdatedAccount();

            default:
                return stats.withSkippedAccount();
        }
    }

    private ImportStats mergeGroups(AccountData existing, AccountData importData, ImportStats stats)
    {
        for (ProfileGroup group : importData.getGroups())
        {
            ProfileGroup existingGroup = existing.findGroupById(group.getId());
            if (existingGroup == null)
            {
                existing.addGroup(group);
                stats = stats.withAddedGroup();
            }
            else
            {
                stats = stats.withSkippedGroup();
            }
        }
        return stats;
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

        // Match by username AND alias — same login with different alias is intentional
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
