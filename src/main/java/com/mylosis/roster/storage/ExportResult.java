package com.mylosis.roster.storage;

import lombok.Value;

/**
 * Result of an account export operation.
 */
@Value
public class ExportResult
{
    boolean success;
    int accountCount;
    int groupCount;
    /** User-facing failure reason; null on success. */
    String error;

    static ExportResult success(int accountCount, int groupCount)
    {
        return new ExportResult(true, accountCount, groupCount, null);
    }

    static ExportResult failure(String error)
    {
        return new ExportResult(false, 0, 0, error);
    }
}
