package com.mylosis.roster.storage;

import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;

/**
 * Handles merging imported profile data with existing data,
 * including duplicate detection and resolution.
 */
public class AccountDataMerger
{
    /**
     * Merge imported data into existing data with duplicate handling.
     * Returns import statistics.
     */
    public ImportStats merge(AccountData existing, AccountData importData, ImportDuplicateMode duplicateMode)
    {
        ImportStats stats = new ImportStats(0, 0, 0, 0, 0);

        if (importData.getAccounts() != null)
        {
            stats = mergeProfiles(existing, importData, duplicateMode, stats);
        }

        if (importData.getGroups() != null)
        {
            stats = mergeGroups(existing, importData, stats);
        }

        return stats;
    }

    private ImportStats mergeProfiles(AccountData existing, AccountData importData,
                                     ImportDuplicateMode duplicateMode, ImportStats stats)
    {
        for (Account profile : importData.getAccounts())
        {
            Account duplicate = findDuplicate(existing, profile);

            if (duplicate == null)
            {
                existing.addAccount(profile);
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
                String uniqueAlias = generateUniqueAlias(existing, profile.getAlias());
                profile.setAlias(uniqueAlias);
                profile.setId(java.util.UUID.randomUUID().toString());
                existing.addAccount(profile);
                return stats.withSuffixedAccount();

            case SKIP:
                return stats.withSkippedAccount();

            case UPDATE:
                existing.removeAccount(duplicate.getId());
                profile.setId(duplicate.getId());
                existing.addAccount(profile);
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

    private Account findDuplicate(AccountData existing, Account importAccount)
    {
        if (existing.getAccounts() == null) return null;

        // Match by ID first (exact same entry)
        Account byId = existing.findAccountById(importAccount.getId());
        if (byId != null) return byId;

        // Match by username AND alias — same login with different alias is intentional
        for (Account p : existing.getAccounts())
        {
            if (p.getUsername() != null && importAccount.getUsername() != null
                && p.getUsername().equalsIgnoreCase(importAccount.getUsername()))
            {
                String existingAlias = p.getAlias() != null ? p.getAlias() : "";
                String importAlias = importAccount.getAlias() != null ? importAccount.getAlias() : "";
                if (existingAlias.equalsIgnoreCase(importAlias))
                {
                    return p;
                }
            }
        }
        return null;
    }

    private String generateUniqueAlias(AccountData existing, String baseAlias)
    {
        if (baseAlias == null || baseAlias.isEmpty()) baseAlias = "Account";

        String alias = baseAlias;
        int suffix = 2;

        while (aliasExists(existing, alias))
        {
            alias = baseAlias + " (" + suffix + ")";
            suffix++;
        }
        return alias;
    }

    private boolean aliasExists(AccountData data, String alias)
    {
        if (data.getAccounts() == null) return false;

        for (Account p : data.getAccounts())
        {
            if (alias.equalsIgnoreCase(p.getAlias())) return true;
        }
        return false;
    }
}
