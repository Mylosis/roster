package com.mylosis.roster.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Statistics from an account import operation.
 */
@Data
@AllArgsConstructor
public class ImportStats
{
    private final int accountsAdded;
    private final int accountsSkipped;
    private final int accountsUpdated;
    private final int groupsAdded;
    private final int groupsSkipped;

    public ImportStats withAddedAccount()
    {
        return new ImportStats(accountsAdded + 1, accountsSkipped, accountsUpdated, groupsAdded, groupsSkipped);
    }

    public ImportStats withSkippedAccount()
    {
        return new ImportStats(accountsAdded, accountsSkipped + 1, accountsUpdated, groupsAdded, groupsSkipped);
    }

    public ImportStats withUpdatedAccount()
    {
        return new ImportStats(accountsAdded, accountsSkipped, accountsUpdated + 1, groupsAdded, groupsSkipped);
    }

    public ImportStats withSuffixedAccount()
    {
        return new ImportStats(accountsAdded + 1, accountsSkipped, accountsUpdated, groupsAdded, groupsSkipped);
    }

    public ImportStats withAddedGroup()
    {
        return new ImportStats(accountsAdded, accountsSkipped, accountsUpdated, groupsAdded + 1, groupsSkipped);
    }

    public ImportStats withSkippedGroup()
    {
        return new ImportStats(accountsAdded, accountsSkipped, accountsUpdated, groupsAdded, groupsSkipped + 1);
    }

    public int getTotalAccounts()
    {
        return accountsAdded + accountsSkipped + accountsUpdated;
    }

    public int getTotalGroups()
    {
        return groupsAdded + groupsSkipped;
    }
}
