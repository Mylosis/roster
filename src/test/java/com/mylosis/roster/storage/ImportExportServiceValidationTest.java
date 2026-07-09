package com.mylosis.roster.storage;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.model.AccountType;
import com.mylosis.roster.model.ProfileGroup;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ImportExportServiceValidationTest
{
    private static AccountData validData()
    {
        AccountData data = AccountData.createEmpty();
        data.addAccount(Account.builder().id("a1").username("userA").build());
        return data;
    }

    @Test
    public void validDataReturnsNull()
    {
        assertNull(ImportExportService.validateImportData(validData()));
    }

    @Test
    public void versionNewerThanCurrentReturnsError()
    {
        AccountData data = validData();
        data.setVersion(AccountData.CURRENT_VERSION + 1);

        assertNotNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void accountWithNullIdReturnsError()
    {
        AccountData data = AccountData.createEmpty();
        data.addAccount(Account.builder().id(null).username("userA").build());

        assertNotNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void accountWithEmptyIdReturnsError()
    {
        AccountData data = AccountData.createEmpty();
        data.addAccount(Account.builder().id("").username("userA").build());

        assertNotNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void accountWithNullUsernameReturnsError()
    {
        AccountData data = AccountData.createEmpty();
        data.addAccount(Account.builder().id("a1").username(null).build());

        assertNotNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void accountWithEmptyUsernameReturnsError()
    {
        AccountData data = AccountData.createEmpty();
        data.addAccount(Account.builder().id("a1").username("").build());

        assertNotNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void groupWithNullIdReturnsError()
    {
        AccountData data = AccountData.createEmpty();
        data.addGroup(ProfileGroup.builder().id(null).name("Group").build());

        assertNotNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void groupWithEmptyIdReturnsError()
    {
        AccountData data = AccountData.createEmpty();
        data.addGroup(ProfileGroup.builder().id("").name("Group").build());

        assertNotNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void nullAccountsAndGroupsListsAreTolerated()
    {
        AccountData data = AccountData.builder()
            .version(AccountData.CURRENT_VERSION)
            .accounts(null)
            .groups(null)
            .build();

        assertNull(ImportExportService.validateImportData(data));
    }

    @Test
    public void accountWithNullMetadataGetsDefaultMetadata()
    {
        AccountData data = AccountData.createEmpty();
        Account account = Account.builder().id("a1").username("userA").metadata(null).build();
        data.addAccount(account);

        ImportExportService.normalizeImportData(data);

        assertNotNull(account.getMetadata());
        assertEquals(AccountType.MAIN, account.getMetadata().getAccountType());
    }

    @Test
    public void accountWithMetadataButNullAccountTypeGetsMainBackfilled()
    {
        AccountData data = AccountData.createEmpty();
        Account account = Account.builder().id("a1").username("userA")
            .metadata(AccountMetadata.builder().accountType(null).build())
            .build();
        data.addAccount(account);

        ImportExportService.normalizeImportData(data);

        assertEquals(AccountType.MAIN, account.getMetadata().getAccountType());
    }

    @Test
    public void normalizeToleratesNullAccountsList()
    {
        AccountData data = AccountData.builder()
            .version(AccountData.CURRENT_VERSION)
            .accounts(null)
            .groups(new ArrayList<>())
            .build();

        ImportExportService.normalizeImportData(data);
        // No exception thrown means success; nothing else to assert.
    }
}
