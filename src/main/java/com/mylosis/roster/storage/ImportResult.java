package com.mylosis.roster.storage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Result of an account import: either the merge statistics or a user-facing error.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImportResult
{
    boolean success;
    /** User-facing failure reason; null on success. */
    String error;
    /** Merge statistics; null on failure. */
    ImportStats stats;

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
        return stats != null
            && (stats.getAccountsAdded() > 0 || stats.getAccountsUpdated() > 0 || stats.getGroupsAdded() > 0);
    }

    public String getDetailedMessage()
    {
        if (!success) return error;

        StringBuilder sb = new StringBuilder();
        appendPart(sb, stats.getAccountsAdded(), "Added " + stats.getAccountsAdded());
        appendPart(sb, stats.getAccountsUpdated(), "updated " + stats.getAccountsUpdated());
        int skipped = stats.getAccountsSkipped();
        appendPart(sb, skipped, "skipped " + skipped + " duplicate" + (skipped == 1 ? "" : "s"));
        int groups = stats.getGroupsAdded();
        appendPart(sb, groups, groups + (groups == 1 ? " category" : " categories"));

        return sb.length() == 0 ? "All accounts already exist" : sb.toString();
    }

    private static void appendPart(StringBuilder sb, int count, String text)
    {
        if (count <= 0) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(text);
    }
}
