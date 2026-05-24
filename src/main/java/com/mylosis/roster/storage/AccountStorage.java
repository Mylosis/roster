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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Two-layer persistence for {@link AccountData}.
 *
 * <ul>
 *   <li><b>Primary:</b> RuneLite {@link ConfigManager} under group {@code roster}, key {@code data}.
 *       Cloud-syncs with the user's RuneLite account when sync is enabled on the active profile.</li>
 *   <li><b>Backup:</b> {@code roster.json} (plus a rotating {@code roster.backup.json}) in
 *       {@link RuneLite#RUNELITE_DIR}. Every {@link #save()} writes to both layers, so the file
 *       is a permanent secondary store, not a one-shot snapshot.</li>
 * </ul>
 *
 * On first load after upgrading from v1.0 (file-only storage), {@link #load()} migrates the
 * existing {@code roster.json} into ConfigManager. The migration is <b>idempotent</b> — the
 * file is never renamed away — so a future ConfigManager loss (e.g. the client was killed
 * before ConfigManager's debounced flush hit disk) will re-seed from the same file on the
 * next launch. {@code roster.json.pre-v1.1.bak} is still checked during load() as a recovery
 * source for users who upgraded under the older, non-idempotent code path.
 */
@Slf4j
@Singleton
public class AccountStorage
{
    private static final String CONFIG_GROUP = "roster";
    /** Visible so {@link com.mylosis.roster.RosterPlugin#onConfigChanged} can filter out
     *  our own data writes — see the note there about the save/rebuild feedback loop. */
    public static final String CONFIG_KEY_DATA = "data";

    private static final String DATA_FILE_NAME = "roster.json";
    private static final String BACKUP_FILE_NAME = "roster.backup.json";
    private static final String MIGRATED_FILE_NAME = "roster.json.pre-v1.1.bak";

    private final Gson gson;
    private final ConfigManager configManager;
    private final File dataFile;
    private final File backupFile;
    private final File migratedFile;

    private AccountData cachedData;

    /**
     * Cached sorted-by-sortOrder view of the groups list. Built lazily on first
     * read after a mutation, then served as a defensive copy. Avoids the O(n log n)
     * sort that the previous {@link #getGroups()} did on every single call — and it
     * was called several times per UI rebuild.
     */
    private List<ProfileGroup> sortedGroupsCache;

    /**
     * Async writer for the on-disk JSON mirror. ConfigManager writes stay sync
     * (cheap in-process call into RuneLite's debouncer); the file write is the
     * expensive part — {@link Files#copy} for the backup rotation plus a fresh
     * {@link FileWriter} for the current file, on whichever thread called save().
     * Coalescing means a burst of saves (e.g. dragging that rewrites every
     * profile's sortOrder) only produces one disk write at the tail of the burst.
     */
    private static final long DISK_WRITE_COALESCE_MS = 250;
    private final ScheduledExecutorService diskExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "roster-disk-writer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<ScheduledFuture<?>> pendingFlush = new AtomicReference<>();
    /** The data snapshot the next flush will serialize. Replaced on every save(). */
    private volatile AccountData pendingFlushData;

    @Inject
    public AccountStorage(Gson gson, ConfigManager configManager)
    {
        // Storage Gson is compact — roster.json is internal, never read by humans.
        // Pretty-printing roughly doubles file size and serialization cost, which
        // adds up because every account/group write rewrites the whole blob.
        // ImportExportService keeps its own pretty Gson for clipboard/file exports
        // where readability matters.
        this.gson = gson.newBuilder().create();
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

        // Primary: ConfigManager (cloud-synced when the user has it enabled)
        AccountData fromConfig = loadFromConfigManager();
        if (fromConfig != null)
        {
            cachedData = ensureSchema(fromConfig);
            log.info("Loaded {} accounts and {} groups from ConfigManager",
                cachedData.getAccounts() != null ? cachedData.getAccounts().size() : 0,
                cachedData.getGroups() != null ? cachedData.getGroups().size() : 0);
            return cachedData;
        }

        // ConfigManager empty — try every on-disk backup we know about, in order
        // of recency. This is the "ConfigManager didn't flush" recovery path: if
        // the user's previous session was killed before ConfigManager persisted,
        // we re-seed it from whichever file is still around.
        //
        // Checked in this order:
        //   1. roster.json                  — normal save mirror, written every save()
        //   2. roster.backup.json           — previous-write rotating backup
        //   3. roster.json.pre-v1.1.bak     — legacy migration sentinel from older
        //                                     v1.1 builds (kept for users who upgraded
        //                                     before the migration was made idempotent)
        AccountData fromFile = loadAnyFile(dataFile, backupFile, migratedFile);
        if (fromFile != null)
        {
            cachedData = ensureSchema(fromFile);
            log.info("Re-seeding ConfigManager with {} accounts from file backup ({})",
                cachedData.getAccounts() != null ? cachedData.getAccounts().size() : 0,
                "primary/backup");
            saveToConfigManager(cachedData);
            // Do NOT rename the source file — keeping it around means a future
            // ConfigManager loss can be recovered from the same place, instead
            // of orphaning the only on-disk copy. The next save() will refresh
            // roster.json anyway so it stays current.
            return cachedData;
        }

        // Nothing anywhere
        cachedData = AccountData.createEmpty();
        log.info("No existing Roster data; starting fresh");
        return cachedData;
    }

    /**
     * Tries each candidate file in order, returning the first that parses to a
     * non-null {@link AccountData}. Used as a recovery cascade when ConfigManager
     * has no data — we'd rather pull from any backup we can find than show the
     * user an empty plugin.
     */
    private AccountData loadAnyFile(File... candidates)
    {
        for (File f : candidates)
        {
            if (f == null || !f.exists())
            {
                continue;
            }
            AccountData data = loadFromFile(f);
            if (data != null)
            {
                if (f != dataFile)
                {
                    log.warn("Primary roster.json missing or unreadable; recovered from {}", f.getName());
                }
                return data;
            }
        }
        return null;
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

    public void save()
    {
        if (cachedData == null)
        {
            log.warn("save() called but cachedData is null");
            return;
        }

        // Any save is preceded by a mutation; drop derived caches so the next
        // read recomputes against fresh data.
        sortedGroupsCache = null;
        cachedData.invalidateIndexes();

        // ConfigManager handles its own debouncing internally, so we hit it
        // directly — it's also our primary persistence layer.
        saveToConfigManager(cachedData);
        // Schedule the file mirror on a background thread; coalesce a burst of
        // saves (drag reorders write per-profile in a tight loop) into one write.
        scheduleDiskFlush(cachedData);
    }

    private void scheduleDiskFlush(AccountData data)
    {
        pendingFlushData = data;
        ScheduledFuture<?> previous = pendingFlush.getAndSet(
            diskExecutor.schedule(this::flushPending, DISK_WRITE_COALESCE_MS, TimeUnit.MILLISECONDS));
        if (previous != null)
        {
            previous.cancel(false);
        }
    }

    private void flushPending()
    {
        AccountData data = pendingFlushData;
        if (data == null)
        {
            return;
        }
        pendingFlushData = null;
        pendingFlush.set(null);
        saveToFile(data);
    }

    /**
     * Synchronously flush any pending coalesced disk write. Called from
     * {@code RosterPlugin.shutDown} so we don't leave a queued write behind when
     * the plugin (or client) is going down.
     */
    public void flushDiskWritesSync()
    {
        ScheduledFuture<?> current = pendingFlush.getAndSet(null);
        if (current != null)
        {
            current.cancel(false);
        }
        AccountData data = pendingFlushData;
        if (data != null)
        {
            pendingFlushData = null;
            saveToFile(data);
        }
    }

    /**
     * Stop the background writer. Called from {@code RosterPlugin.shutDown}
     * after {@link #flushDiskWritesSync()} so any in-flight write completes first.
     */
    public void shutdown()
    {
        flushDiskWritesSync();
        diskExecutor.shutdown();
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
        sortedGroupsCache = null;
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
        // Any save means the alias/username/notes may have changed; drop the
        // lazy search blob so the next filter pass rebuilds it.
        profile.invalidateSearchHaystack();
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
        List<ProfileGroup> cached = sortedGroupsCache;
        if (cached != null)
        {
            return new ArrayList<>(cached);
        }
        AccountData data = load();
        List<ProfileGroup> groups = data.getGroups() != null ? new ArrayList<>(data.getGroups()) : new ArrayList<>();
        groups.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        sortedGroupsCache = groups;
        return new ArrayList<>(groups);
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
