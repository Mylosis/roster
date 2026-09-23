package com.mylosis.roster.storage;

import com.google.gson.Gson;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AccountStorageTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // Test-only Gson; the plugin itself always uses RuneLite's injected instance.
    private final Gson gson = new Gson();
    private ScheduledExecutorService executor;
    /** Stands in for the ConfigManager "roster.data" value. */
    private String configValue;
    private int configWrites;

    @Before
    public void setUp()
    {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown()
    {
        executor.shutdownNow();
    }

    private AccountStorage newStorage()
    {
        return new AccountStorage(gson, () -> configValue, json -> {
            configValue = json;
            configWrites++;
        }, new RosterFileMirror(gson, folder.getRoot(), executor));
    }

    private static Account account(String id, String groupId)
    {
        Account account = Account.createNew(null, id + "@example.com");
        account.setId(id);
        account.setGroupId(groupId);
        return account;
    }

    private static List<String> ids(List<Account> accounts)
    {
        return accounts.stream().map(Account::getId).collect(Collectors.toList());
    }

    @Test
    public void savingAnExistingAccountKeepsItsPositionAndSortOrder()
    {
        AccountStorage storage = newStorage();
        storage.saveAccount(account("a", null));
        storage.saveAccount(account("b", null));
        storage.saveAccount(account("c", null));

        Account a = storage.getAccount("a");
        a.setAlias("renamed");
        storage.saveAccount(a);

        assertEquals(Arrays.asList("a", "b", "c"), ids(storage.getAccounts()));
        assertEquals(0, storage.getAccount("a").getMetadata().getSortOrder());
    }

    @Test
    public void replacingAnAccountWithACopyKeepsItsPosition()
    {
        AccountStorage storage = newStorage();
        storage.saveAccount(account("a", null));
        storage.saveAccount(account("b", null));

        Account copy = account("a", null);
        copy.setAlias("copy");
        storage.saveAccount(copy);

        assertEquals(Arrays.asList("a", "b"), ids(storage.getAccounts()));
        assertEquals("copy", storage.getAccount("a").getAlias());
    }

    @Test
    public void newAccountsAppendToTheEndOfTheirCategory()
    {
        AccountStorage storage = newStorage();
        ProfileGroup group = ProfileGroup.createNew("Mains");
        storage.saveGroup(group);
        storage.saveAccount(account("g1", group.getId()));
        storage.saveAccount(account("u1", null));
        storage.saveAccount(account("g2", group.getId()));

        assertEquals(0, storage.getAccount("g1").getMetadata().getSortOrder());
        assertEquals(1, storage.getAccount("g2").getMetadata().getSortOrder());
        assertEquals(0, storage.getAccount("u1").getMetadata().getSortOrder());
    }

    @Test
    public void newCategoriesAppendAfterExistingOnes()
    {
        AccountStorage storage = newStorage();
        ProfileGroup first = ProfileGroup.createNew("First");
        ProfileGroup second = ProfileGroup.createNew("Second");
        storage.saveGroup(first);
        storage.saveGroup(second);

        // Renaming (or collapsing) must not move a category.
        first.setName("Renamed");
        storage.saveGroup(first);
        ProfileGroup third = ProfileGroup.createNew("Third");
        storage.saveGroup(third);

        List<ProfileGroup> groups = storage.getGroups();
        assertEquals(first.getId(), groups.get(0).getId());
        assertEquals(second.getId(), groups.get(1).getId());
        assertEquals(third.getId(), groups.get(2).getId());
    }

    @Test
    public void deletingACategoryAppendsItsAccountsToUncategorized()
    {
        AccountStorage storage = newStorage();
        ProfileGroup group = ProfileGroup.createNew("Alts");
        storage.saveGroup(group);
        storage.saveAccount(account("u1", null));
        storage.saveAccount(account("g1", group.getId()));

        storage.deleteGroup(group.getId());

        Account moved = storage.getAccount("g1");
        assertNull(moved.getGroupId());
        assertEquals(1, moved.getMetadata().getSortOrder());
    }

    @Test
    public void ownWritesAreRecognized()
    {
        AccountStorage storage = newStorage();
        storage.saveAccount(account("a", null));

        assertTrue(storage.isOwnWrite(configValue));
        assertFalse(storage.isOwnWrite("{\"accounts\":[]}"));
        assertFalse(storage.reloadIfChangedExternally());
    }

    @Test
    public void externalConfigChangeReplacesTheCache()
    {
        AccountStorage storage = newStorage();
        storage.saveAccount(account("a", null));

        // e.g. RuneLite switched to a profile holding different roster data
        AccountData other = AccountData.createEmpty();
        other.addAccount(account("z", null));
        configValue = gson.toJson(other);

        assertTrue(storage.reloadIfChangedExternally());
        assertEquals(Collections.singletonList("z"), ids(storage.getAccounts()));

        // The next save must not resurrect the old data.
        storage.saveAccount(account("y", null));
        assertTrue(configValue.contains("\"z\""));
        assertFalse(configValue.contains("\"a\""));
    }

    @Test
    public void shutdownFlushesTheFileAndReloadsOnNextUse() throws Exception
    {
        AccountStorage storage = newStorage();
        storage.saveAccount(account("a", null));
        storage.shutdown();

        File file = new File(folder.getRoot(), "roster.json");
        assertTrue(file.exists());
        assertTrue(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains("\"a\""));

        configValue = gson.toJson(AccountData.createEmpty());
        assertTrue(storage.getAccounts().isEmpty());
    }

    @Test
    public void emptyConfigIsReseededFromTheFileBackup()
    {
        AccountStorage first = newStorage();
        first.saveAccount(account("a", null));
        first.shutdown();

        configValue = null;
        configWrites = 0;
        AccountStorage second = newStorage();

        assertEquals(Collections.singletonList("a"), ids(second.getAccounts()));
        assertEquals(1, configWrites);
    }

    @Test
    public void unreadableFileIsQuarantinedAndBackupUsed() throws Exception
    {
        AccountStorage first = newStorage();
        first.saveAccount(account("a", null));
        first.shutdown();
        first.saveAccount(account("b", null));
        first.shutdown();

        File data = new File(folder.getRoot(), "roster.json");
        Files.write(data.toPath(), "{ not json".getBytes(StandardCharsets.UTF_8));
        configValue = null;

        List<Account> recovered = newStorage().getAccounts();

        assertEquals(Collections.singletonList("a"), ids(recovered));
        assertTrue(new File(folder.getRoot(), "roster.json.corrupt").exists());
    }
}
