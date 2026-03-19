package com.mylosis.roster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account
{
    private String id;
    private String alias;
    private String username;
    private String groupId;
    private AccountMetadata metadata;

    public static Account createNew(String alias, String username)
    {
        return Account.builder()
            .id(UUID.randomUUID().toString())
            .alias(alias)
            .username(username)
            .metadata(AccountMetadata.createDefault())
            .build();
    }

    public String getDisplayName()
    {
        return alias != null && !alias.isEmpty() ? alias : username;
    }

}
