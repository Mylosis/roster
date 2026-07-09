package com.mylosis.roster.storage;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.ProfileGroup;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AccountDataMergerTest
{
    private static Account account(String id, String username, String alias)
    {
        return Account.builder().id(id).username(username).alias(alias).build();
    }

    private static AccountData dataWith(Account... accounts)
    {
        AccountData data = AccountData.createEmpty();
        for (Account a : accounts)
        {
            data.addAccount(a);
        }
        return data;
    }

    @Test
    public void nonDuplicateAccountIsAdded()
    {
        AccountData existing = dataWith(account("e1", "userA", "AliasA"));
        AccountData importData = dataWith(account("i1", "userB", "AliasB"));

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.SKIP);

        assertEquals(1, stats.getAccountsAdded());
        assertEquals(2, existing.getAccounts().size());
    }

    @Test
    public void duplicateDetectedBySameId()
    {
        AccountData existing = dataWith(account("dup1", "userA", "AliasA"));
        AccountData importData = dataWith(account("dup1", "userDifferent", "AliasDifferent"));

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.SKIP);

        assertEquals(0, stats.getAccountsAdded());
        assertEquals(1, stats.getAccountsSkipped());
    }

    @Test
    public void duplicateDetectedBySameUsernameAndAliasCaseInsensitive()
    {
        AccountData existing = dataWith(account("e1", "UserA", "AliasA"));
        AccountData importData = dataWith(account("i1", "usera", "aliasa"));

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.SKIP);

        assertEquals(0, stats.getAccountsAdded());
        assertEquals(1, stats.getAccountsSkipped());
    }

    @Test
    public void sameUsernameDifferentAliasIsNotADuplicate()
    {
        AccountData existing = dataWith(account("e1", "userA", "AliasA"));
        AccountData importData = dataWith(account("i1", "userA", "AliasB"));

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.SKIP);

        assertEquals(1, stats.getAccountsAdded());
        assertEquals(0, stats.getAccountsSkipped());
        assertEquals(2, existing.getAccounts().size());
    }

    @Test
    public void skipModeLeavesExistingUntouched()
    {
        Account existingAccount = account("dup1", "userA", "AliasA");
        AccountData existing = dataWith(existingAccount);
        AccountData importData = dataWith(account("dup1", "userA", "AliasA"));

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.SKIP);

        assertEquals(1, stats.getAccountsSkipped());
        assertEquals(1, existing.getAccounts().size());
        assertTrue(existing.getAccounts().get(0) == existingAccount);
    }

    @Test
    public void updateModeReplacesExistingButKeepsExistingId()
    {
        // Duplicate detection keys on username+alias, so the incoming account
        // must keep that pair unchanged; the update is observed via a
        // different field (characterName) that differs from the existing record.
        AccountData existing = dataWith(account("existingId", "userA", "AliasA"));
        Account incoming = account("incomingId", "userA", "AliasA");
        incoming.setCharacterName("UpdatedCharacter");
        AccountData importData = dataWith(incoming);

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.UPDATE);

        assertEquals(1, stats.getAccountsUpdated());
        assertEquals(1, existing.getAccounts().size());
        Account result = existing.getAccounts().get(0);
        assertEquals("existingId", result.getId());
        assertEquals("UpdatedCharacter", result.getCharacterName());
    }

    @Test
    public void addWithSuffixGivesFreshUniqueAliasAndNewId()
    {
        AccountData existing = dataWith(account("existingId", "userA", "SameAlias"));
        Account incoming = account("incomingId", "userA", "SameAlias");
        AccountData importData = dataWith(incoming);

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.ADD_WITH_SUFFIX);

        assertEquals(1, stats.getAccountsAdded());
        assertEquals(2, existing.getAccounts().size());
        Account added = existing.getAccounts().get(1);
        assertEquals("SameAlias (2)", added.getAlias());
        assertNotEquals("incomingId", added.getId());
        assertNotNull(added.getId());
    }

    @Test
    public void addWithSuffixIsCaseInsensitiveAndIncrementsOnRepeat()
    {
        // Duplicate detection keys on username+alias, so both incoming accounts
        // must match the existing username (case-insensitively) to be flagged
        // as duplicates of the same original entry.
        AccountData existing = dataWith(account("existingId", "UserA", "SameAlias"));
        List<Account> incomingAccounts = new ArrayList<>();
        incomingAccounts.add(account("i1", "usera", "sameAlias"));
        incomingAccounts.add(account("i2", "USERA", "SameAlias"));
        AccountData importData = AccountData.createEmpty();
        for (Account a : incomingAccounts)
        {
            importData.addAccount(a);
        }

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.ADD_WITH_SUFFIX);

        assertEquals(2, stats.getAccountsAdded());
        assertEquals(3, existing.getAccounts().size());
        // The suffix base is the incoming account's own alias casing, not the
        // matched existing account's casing.
        assertEquals("sameAlias (2)", existing.getAccounts().get(1).getAlias());
        assertEquals("SameAlias (3)", existing.getAccounts().get(2).getAlias());
    }

    @Test
    public void groupsAreAddedOrSkippedWithCorrectStats()
    {
        AccountData existing = AccountData.createEmpty();
        existing.addGroup(ProfileGroup.builder().id("existingGroup").name("Existing").build());

        AccountData importData = AccountData.createEmpty();
        importData.addGroup(ProfileGroup.builder().id("existingGroup").name("Existing Renamed").build());
        importData.addGroup(ProfileGroup.builder().id("newGroup").name("New").build());

        ImportStats stats = new AccountDataMerger().merge(existing, importData, ImportDuplicateMode.SKIP);

        assertEquals(1, stats.getGroupsAdded());
        assertEquals(1, stats.getGroupsSkipped());
        assertEquals(2, existing.getGroups().size());
    }
}
