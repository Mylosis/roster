package com.mylosis.roster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountData
{
    private int version;
    private List<Account> accounts;
    private List<ProfileGroup> groups;

    public static final int CURRENT_VERSION = 1;

    /**
     * Shared comparator for sorting accounts by their metadata sort order.
     */
    public static final Comparator<Account> SORT_ORDER_COMPARATOR = (a, b) -> {
        int orderA = a.getMetadata() != null ? a.getMetadata().getSortOrder() : 0;
        int orderB = b.getMetadata() != null ? b.getMetadata().getSortOrder() : 0;
        return Integer.compare(orderA, orderB);
    };

    public static AccountData createEmpty()
    {
        return AccountData.builder()
            .version(CURRENT_VERSION)
            .accounts(new ArrayList<>())
            .groups(new ArrayList<>())
            .build();
    }

    public Account findAccountById(String id)
    {
        return accounts.stream()
            .filter(p -> p.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public ProfileGroup findGroupById(String id)
    {
        return groups.stream()
            .filter(g -> g.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public List<Account> getAccountsInGroup(String groupId)
    {
        List<Account> result = new ArrayList<>();
        for (Account profile : accounts)
        {
            String pGroupId = profile.getGroupId();
            boolean matches;

            if (groupId == null || groupId.equals(ProfileGroup.UNGROUPED_ID))
            {
                matches = pGroupId == null || pGroupId.isEmpty() || pGroupId.equals(ProfileGroup.UNGROUPED_ID);
            }
            else
            {
                matches = groupId.equals(pGroupId);
            }

            if (matches)
            {
                result.add(profile);
            }
        }
        result.sort(SORT_ORDER_COMPARATOR);
        return result;
    }

    public void addAccount(Account profile)
    {
        if (accounts == null)
        {
            accounts = new ArrayList<>();
        }
        accounts.add(profile);
    }

    public void removeAccount(String id)
    {
        if (accounts != null)
        {
            accounts.removeIf(p -> p.getId().equals(id));
        }
    }

    public void addGroup(ProfileGroup group)
    {
        if (groups == null)
        {
            groups = new ArrayList<>();
        }
        groups.add(group);
    }

    public void removeGroup(String id)
    {
        if (groups != null)
        {
            groups.removeIf(g -> g.getId().equals(id));
        }
        // Move accounts from deleted group to ungrouped
        if (accounts != null)
        {
            for (Account profile : accounts)
            {
                if (id.equals(profile.getGroupId()))
                {
                    profile.setGroupId(null);
                }
            }
        }
    }
}
