package com.mylosis.roster.storage;

/**
 * Result of an account import operation with detailed duplicate handling stats.
 */
public class ImportResult
{
    public final boolean success;
    public final int accountCount;
    public final int groupCount;
    public final String error;

    public final int accountsAdded;
    public final int accountsSkipped;
    public final int accountsUpdated;
    public final int groupsAdded;
    public final int groupsSkipped;

    public ImportResult(boolean success, int accountCount, int groupCount, String error)
    {
        this.success = success;
        this.accountCount = accountCount;
        this.groupCount = groupCount;
        this.error = error;
        this.accountsAdded = accountCount;
        this.accountsSkipped = 0;
        this.accountsUpdated = 0;
        this.groupsAdded = groupCount;
        this.groupsSkipped = 0;
    }

    private ImportResult(boolean success, String error, ImportStats stats)
    {
        this.success = success;
        this.error = error;
        if (stats != null)
        {
            this.accountCount = stats.getAccountsAdded() + stats.getAccountsUpdated();
            this.groupCount = stats.getGroupsAdded();
            this.accountsAdded = stats.getAccountsAdded();
            this.accountsSkipped = stats.getAccountsSkipped();
            this.accountsUpdated = stats.getAccountsUpdated();
            this.groupsAdded = stats.getGroupsAdded();
            this.groupsSkipped = stats.getGroupsSkipped();
        }
        else
        {
            this.accountCount = 0;
            this.groupCount = 0;
            this.accountsAdded = 0;
            this.accountsSkipped = 0;
            this.accountsUpdated = 0;
            this.groupsAdded = 0;
            this.groupsSkipped = 0;
        }
    }

    public static ImportResult success(ImportStats stats)
    {
        return new ImportResult(true, null, stats);
    }

    public static ImportResult failure(String error)
    {
        return new ImportResult(false, error, null);
    }

    public boolean hasChanges()
    {
        return accountsAdded > 0 || accountsUpdated > 0 || groupsAdded > 0;
    }

    public String getDetailedMessage()
    {
        if (!success) return error;

        StringBuilder sb = new StringBuilder();

        if (accountsAdded > 0) sb.append("Added ").append(accountsAdded);

        if (accountsUpdated > 0)
        {
            if (sb.length() > 0) sb.append(", ");
            sb.append("updated ").append(accountsUpdated);
        }

        if (accountsSkipped > 0)
        {
            if (sb.length() > 0) sb.append(", ");
            sb.append("skipped ").append(accountsSkipped).append(" duplicate");
            if (accountsSkipped > 1) sb.append("s");
        }

        if (groupsAdded > 0)
        {
            if (sb.length() > 0) sb.append(", ");
            sb.append(groupsAdded).append(" categor").append(groupsAdded == 1 ? "y" : "ies");
        }

        if (sb.length() == 0) return "All accounts already exist";

        return sb.toString();
    }
}
