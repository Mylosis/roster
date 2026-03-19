package com.mylosis.roster.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class AccountStorage
{
    private static final String DATA_FILE_NAME = "roster.json";
    private static final String BACKUP_FILE_NAME = "roster.backup.json";

    private final Gson gson;
    private final File dataFile;
    private final File backupFile;

    private AccountData cachedData;

    @Inject
    public AccountStorage(Gson gson)
    {
        this.gson = gson.newBuilder()
            .setPrettyPrinting()
            .create();

        File runeliteDir = RuneLite.RUNELITE_DIR;
        this.dataFile = new File(runeliteDir, DATA_FILE_NAME);
        this.backupFile = new File(runeliteDir, BACKUP_FILE_NAME);
    }

    public AccountData load()
    {
        if (cachedData != null)
        {
            return cachedData;
        }

        if (!dataFile.exists())
        {
            log.info("No account data file found, creating new");
            cachedData = AccountData.createEmpty();
            return cachedData;
        }

        try (FileReader reader = new FileReader(dataFile))
        {
            cachedData = gson.fromJson(reader, AccountData.class);
            if (cachedData == null)
            {
                cachedData = AccountData.createEmpty();
            }
            log.info("Loaded {} accounts and {} groups",
                cachedData.getAccounts() != null ? cachedData.getAccounts().size() : 0,
                cachedData.getGroups() != null ? cachedData.getGroups().size() : 0);
            return cachedData;
        }
        catch (Exception e)
        {
            log.error("Failed to load account data, attempting backup", e);
            return loadFromBackup();
        }
    }

    private AccountData loadFromBackup()
    {
        if (!backupFile.exists())
        {
            log.warn("No backup file found, creating empty data");
            cachedData = AccountData.createEmpty();
            return cachedData;
        }

        try (FileReader reader = new FileReader(backupFile))
        {
            cachedData = gson.fromJson(reader, AccountData.class);
            if (cachedData == null)
            {
                cachedData = AccountData.createEmpty();
            }
            log.info("Restored from backup");
            save(); // Save restored data to main file
            return cachedData;
        }
        catch (Exception e)
        {
            log.error("Failed to load backup, creating empty data", e);
            cachedData = AccountData.createEmpty();
            return cachedData;
        }
    }

    public void save()
    {
        if (cachedData == null)
        {
            log.warn("save() called but cachedData is null");
            return;
        }

        try
        {
            // Create backup of existing file
            if (dataFile.exists())
            {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            try (FileWriter writer = new FileWriter(dataFile))
            {
                gson.toJson(cachedData, writer);
            }
            log.info("Saved account data to {}", dataFile.getAbsolutePath());
        }
        catch (IOException e)
        {
            log.error("Failed to save account data to {}", dataFile.getAbsolutePath(), e);
        }
    }

    public void invalidateCache()
    {
        cachedData = null;
    }

    // Account operations
    public List<Account> getAccounts()
    {
        AccountData data = load();
        return data.getAccounts() != null ? new ArrayList<>(data.getAccounts()) : new ArrayList<>();
    }

    public Account getProfile(String id)
    {
        return load().findAccountById(id);
    }

    public void saveAccount(Account profile)
    {
        AccountData data = load();
        Account existing = data.findAccountById(profile.getId());
        if (existing != null)
        {
            data.getAccounts().remove(existing);
            log.debug("Updating existing account: {}", profile.getDisplayName());
        }
        else
        {
            log.debug("Adding new account: {}", profile.getDisplayName());
        }
        data.addAccount(profile);
        save();
        log.info("Saved account: {} (total accounts: {})", profile.getDisplayName(), data.getAccounts().size());
    }

    public void deleteProfile(String id)
    {
        AccountData data = load();
        data.removeAccount(id);
        save();
    }

    // Group operations
    public List<ProfileGroup> getGroups()
    {
        AccountData data = load();
        List<ProfileGroup> groups = data.getGroups() != null ? new ArrayList<>(data.getGroups()) : new ArrayList<>();
        groups.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        return groups;
    }

    public ProfileGroup getGroup(String id)
    {
        return load().findGroupById(id);
    }

    public void saveGroup(ProfileGroup group)
    {
        AccountData data = load();
        ProfileGroup existing = data.findGroupById(group.getId());
        if (existing != null)
        {
            data.getGroups().remove(existing);
        }
        data.addGroup(group);
        save();
    }

    public void deleteGroup(String id)
    {
        AccountData data = load();
        data.removeGroup(id);
        save();
    }

    /**
     * Deletes all profiles in a single save operation.
     */
    public void deleteAllProfiles()
    {
        AccountData data = load();
        data.getAccounts().clear();
        save();
    }

    /**
     * Deletes all groups in a single save operation, moving profiles to uncategorized.
     */
    public void deleteAllGroups()
    {
        AccountData data = load();
        for (Account p : data.getAccounts())
        {
            p.setGroupId(null);
        }
        data.getGroups().clear();
        save();
    }

    // Export data (for ImportExportService)
    public AccountData getExportData()
    {
        return load();
    }

    public void importData(AccountData importData, boolean replace)
    {
        importData(importData, replace, ImportDuplicateMode.ADD_WITH_SUFFIX);
    }

    public ImportStats importData(AccountData importData, boolean replace, ImportDuplicateMode duplicateMode)
    {
        ImportStats stats;

        if (replace)
        {
            cachedData = importData;
            stats = new ImportStats(
                importData.getAccounts() != null ? importData.getAccounts().size() : 0,
                0, 0,
                importData.getGroups() != null ? importData.getGroups().size() : 0,
                0
            );
        }
        else
        {
            AccountData existing = load();
            AccountDataMerger merger = new AccountDataMerger();
            stats = merger.merge(existing, importData, duplicateMode);
        }

        save();
        return stats;
    }
}
