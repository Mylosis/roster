package com.mylosis.roster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountMetadata
{
    private String notes;
    private AccountType accountType;
    private int sortOrder;

    /**
     * Epoch millis of the last time this account was detected logged in on the current client.
     * Nullable for accounts that have never been seen online (or pre-v1.1 data). Updated by
     * {@code RosterPlugin.onGameStateChanged} with a 60s throttle to avoid save thrash.
     */
    private Long lastOnlineAt;

    /**
     * Epoch millis of when this account record was first created — used by the DATE_ADDED
     * sort key. Nullable for pre-v1.1 data; sorts treat missing values as oldest.
     */
    private Long createdAt;

    public static AccountMetadata createDefault()
    {
        return AccountMetadata.builder()
            .accountType(AccountType.MAIN)
            .sortOrder(0)
            .createdAt(System.currentTimeMillis())
            .build();
    }
}
