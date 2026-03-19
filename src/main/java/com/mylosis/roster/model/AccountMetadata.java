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

    public static AccountMetadata createDefault()
    {
        return AccountMetadata.builder()
            .accountType(AccountType.MAIN)
            .sortOrder(0)
            .build();
    }
}
