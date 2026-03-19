package com.mylosis.roster.storage;

/**
 * Result of an account export operation.
 */
public class ExportResult
{
    public final boolean success;
    public final int accountCount;
    public final int groupCount;
    public final String error;

    public ExportResult(boolean success, int accountCount, int groupCount, String error)
    {
        this.success = success;
        this.accountCount = accountCount;
        this.groupCount = groupCount;
        this.error = error;
    }
}
