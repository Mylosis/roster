package com.mylosis.roster.storage;

import com.google.gson.Gson;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ProfileGroup;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

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

/**
 * Two-layer persistence for {@link AccountData}.
 *
 * <ul>
 *   <li><b>Primary:</b> RuneLite {@link ConfigManager} under group {@code roster}, key {@code data}.
 *       Cloud-syncs with the user's RuneLite account when sync is enabled on the active profile.</li>
 *   <li><b>Backup:</b> {@code roster.json} (plus a rotating {@code roster.backup.json}) in
 *       {@link RuneLite#RUNELITE_DIR}. Local belt-and-braces so users have an offline copy.</li>
 * </ul>
 *
 * On first load after upgrading from v1.0 (file-only storage), {@link #load()} migrates the
 * existing {@code roster.json} into ConfigManager and renames the source file to
 * {@code roster.json.pre-v1.1.bak} as an idempotency sentinel — keeping it on disk so the user
 * has a recovery snapshot if anything goes wrong.
 */
@Slf4j
@Singleton
public class AccountStorage
{
    private static final String CONFIG_GROUP = "roster";
    private static final String CONFIG_KEY_DATA = "data";

    private static final String DATA_FILE_NAME = "roster.json";
    private static final String BACKUP_FILE_NAME = "roster.backup.json";
    private static final String MIGRATED_FILE_NAME = "roster.json.pre-v1.1.bak";

    private final Gson gson;
    private final ConfigManager configManager;
    private final File dataFile;
    private final File backupFile;
    private final File migratedFile;

    private AccountData cachedData;

    @Inject
    public AccountStorage(Gson gson, ConfigManager configManager)
    {
        this.gson = gson.newBuilder()
            .setPrettyPrinting()
            .create();
        this.configManager = configManager;

        File runeliteDir = RuneLite.RUNELITE_DIR;
        this.dataFile = new File(runeliteDir, DATA_FILE_NAME);
        this.backupFile = new File(runeliteDir, BACKUP_FILE_NAME);
        this.migratedFile = new File(runeliteDir, MIGRATED_FILE_NAME);
    }

    public AccountData load()
    {
        if (cachedData != null)
        {
            return cachedData;
        }

        // Primary: ConfigManager (cloud-synced)
        AccountData fromConfig = loadFromConfigManager();
        if (fromConfig != null)
        {
            cachedData = ensureSchema(fromConfig);
            log.info("Loaded {} accounts and {} groups from ConfigManager",
                cachedData.getAccounts() != null ? cachedData.getAccounts().size() : 0,
                cachedData.getGroups() != null ? cachedData.getGroups().size() : 0);
            return cachedData;
        }

        // Migration path: ConfigManager empty but file exists (v1.0 upgrade)
        if (dataFile.exists())
        {
            AccountData fromFile = loadFromFile(dataFile);
            if (fromFile == null && backupFile.exists())
            {
                log.warn("Main file failed to parse, attempting backup");
                fromFile = loadFromFile(backupFile);
            }
            if (fromFile != null)
            {
                cachedData = ensureSchema(fromFile);
                log.info("Migrating {} accounts from roster.json into ConfigManager (cloud-syncable)",
                    cachedData.getAccounts() != null ? cachedData.getAccounts().size() : 0);
                saveToConfigManager(cachedData);
                markMigrated();
                return cachedData;
            }
        }

        // Nothing anywhere
        cachedData = AccountData.createEmpty();
        log.info("No existing Roster data; starting fresh");
        return cachedData;
    }

    /**
     * Pulls the primary copy from ConfigManager. Returns null if absent or unreadable.
     */
    private AccountData loadFromConfigManager()
    {
        try
        {
            AccountData data = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_DATA, AccountData.class);
            return data;
        }
        catch (Exception e)
        {
            log.warn("Failed to read AccountData from ConfigManager (will try file backup)", e);
            return null;
        }
    }

    /**
     * Reads {@link AccountData} from a JSON file. Returns null on failure.
     */
    private AccountData loadFromFile(File file)
    {
        try (FileReader reader = new FileReader(file))
        {
            AccountData data = gson.fromJson(reader, AccountData.class);
            if (data == null)
            {
                return null;
            }
            log.info("Loaded {} accounts from {}",
                data.getAccounts() != null ? data.getAccounts().size() : 0, file.getName());
            return data;
        }
        catch (Exception e)
        {
            log.error("Failed to read {}", file.getName(), e);
            return null;
        }
    }

    /**
     * Ensures the loaded data has non-null collections and applies any
     * forward migration steps for older schema versions.
     */
    private AccountData ensureSchema(AccountData data)
    {
        if (data.getAccounts() == null)
        {
            data.setAccounts(new ArrayList<>());
        }
        if (data.getGroups() == null)
        {
            data.setGroups(new ArrayList<>());
        }
        int oldVersion = data.getVersion();
        if (oldVersion < AccountData.CURRENT_VERSION)
        {
            log.info("Migrating AccountData schema v{} → v{}", oldVersion, AccountData.CURRENT_VERSION);
            data.migrate(oldVersion);
            data.setVersion(AccountData.CURRENT_VERSION);
        }
        return data;
    }

    /**
     * Renames the source roster.json to a frozen .pre-v1.1.bak so we don't re-migrate on subsequent
     * launches. If the rename fails (e.g. file lock), we just log and continue — the next launch
     * will see ConfigManager is populated and skip the migration path anyway, so this is a
     * cosmetic concern.
     */
    private void markMigrated()
    {
        try
        {
            Files.move(dataFile.toPath(), migratedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Migration sentinel created: {}", migratedFile.getName());
        }
        catch (IOException e)
        {
            log.warn("Could not rename {} to {} after migration — will retry on next launch",
                dataFile.getName(), migratedFile.getName(), e);
        }
    }

    public void save()
    {
        if (cachedData == null)
        {
            log.warn("save() called but cachedData is null");
            return;
        }

        saveToConfigManager(cachedData);
        saveToFile(cachedData);
    }

    private void saveToConfigManager(AccountData data)
    {
        try
        {
            configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_DATA, data);
        }
        catch (Exception e)
        {
            log.error("Failed to save AccountData to ConfigManager", e);
        }
    }

    private void saveToFile(AccountData data)
    {
        try
        {
            if (dataFile.exists())
            {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            try (FileWriter writer = new FileWriter(dataFile))
            {
                gson.toJson(data, writer);
            }
        }
        catch (IOException e)
        {
            log.error("Failed to save backup file at {}", dataFile.getAbsolutePath(), e);
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
        log.info("Saved account (total accounts: {})", data.getAccounts().size());
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
