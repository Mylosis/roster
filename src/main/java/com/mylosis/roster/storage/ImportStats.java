package com.mylosis.roster.storage;

import lombok.Value;

/**
 * Statistics from an account import operation.
 */
@Value
public class ImportStats
{
    int accountsAdded;
    int accountsSkipped;
    int accountsUpdated;
    int groupsAdded;
    int groupsSkipped;
}
