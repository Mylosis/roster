package com.mylosis.roster.storage;

import com.google.gson.Gson;
import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.ProfileGroup;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Two-layer persistence for {@link AccountData}.
 *
 * <ul>
 *   <li><b>Primary:</b> RuneLite {@link ConfigManager} under group {@code roster}, key {@code data}.
 *       Cloud-syncs with the user's RuneLite account when sync is enabled on the active profile.</li>
 *   <li><b>Backup:</b> {@link RosterFileMirror}, written on every {@link #save()}. When
 *       ConfigManager comes back empty, {@link #load()} re-seeds it from the newest readable file.
 *       The files are never renamed away, so that recovery stays available.</li>
 * </ul>
 *
 * <p>Confined to the Swing EDT: every reader and writer is UI code, and
 * {@link com.mylosis.roster.RosterPlugin} marshals game and config events onto
 * the EDT before touching it.
 */
@Slf4j
@Singleton
public class AccountStorage
{
    /** Visible so {@link com.mylosis.roster.RosterPlugin#onConfigChanged} can tell our own
     *  data writes apart from external ones (profile switches, synced config). */
    public static final String CONFIG_KEY_DATA = "data";

    private final Gson gson;
    private final Supplier<String> configReader;
    private final Consumer<String> configWriter;
    private final RosterFileMirror fileMirror;

    private AccountData cachedData;

    /** Sorted-by-sortOrder view of the groups, rebuilt lazily after a mutation. */
    private List<ProfileGroup> sortedGroupsCache;

    /**
     * The JSON most recently written to ConfigManager. A {@code data} change
     * carrying any other value came from outside this class (RuneLite switched
     * profile or loaded a synced copy), so the cache no longer matches it.
     */
    private volatile String lastWrittenJson;

    @Inject
    public AccountStorage(Gson gson, ConfigManager configManager, ScheduledExecutorService executor)
    {
        // The executor is RuneLite's shared scheduler, never ours to stop. This
        // class is a Guice singleton and RuneLite re-enables plugins on the
        // same instance, so saves keep working after a plugin toggle.
        this(gson,
            () -> configManager.getConfiguration(RosterConfig.CONFIG_GROUP, CONFIG_KEY_DATA),
            json -> configManager.setConfiguration(RosterConfig.CONFIG_GROUP, CONFIG_KEY_DATA, json),
            new RosterFileMirror(gson, RuneLite.RUNELITE_DIR, executor));
    }

    AccountStorage(Gson gson, Supplier<String> configReader, Consumer<String> configWriter, RosterFileMirror fileMirror)
    {
        this.gson = gson;
        this.configReader = configReader;
        this.configWriter = configWriter;
        this.fileMirror = fileMirror;
    }

    public AccountData load()
    {
        if (cachedData != null)
        {
            return cachedData;
        }

        AccountData fromConfig = loadFromConfigManager();
        if (fromConfig != null)
        {
            cachedData = fromConfig.normalize();
            log.info("Loaded {} accounts and {} groups from ConfigManager",
                cachedData.getAccounts().size(), cachedData.getGroups().size());
            return cachedData;
        }

        AccountData fromFile = fileMirror.readNewest();
        if (fromFile != null)
        {
            cachedData = fromFile.normalize();
            log.info("Re-seeding ConfigManager with {} accounts from the file backup", cachedData.getAccounts().size());
            writeToConfigManager(gson.toJson(cachedData));
            return cachedData;
        }

        cachedData = AccountData.createEmpty();
        log.info("No existing Roster data; starting fresh");
        return cachedData;
    }

    /**
     * Reads the primary copy from ConfigManager; null if absent or unreadable.
     *
     * <p>Stored as an explicit JSON string: ConfigManager's typed accessors only
     * round-trip {@code @ConfigSerializer} types. Values that fail to parse (the
     * pre-v1.1.3 toString() format) fall through to the file recovery path, and
     * the next save writes proper JSON.
     */
    private AccountData loadFromConfigManager()
    {
        try
        {
            String json = configReader.get();
            if (json == null || json.trim().isEmpty())
            {
                return null;
            }
            return gson.fromJson(json, AccountData.class);
        }
        catch (Exception e)
        {
            log.warn("Failed to read AccountData from ConfigManager (will try file backup)", e);
            return null;
        }
    }

    /** True when {@code value} is exactly what this class last wrote to ConfigManager. */
    public boolean isOwnWrite(String value)
    {
        String last = lastWrittenJson;
        return value != null && (value == last || value.equals(last));
    }

    /**
     * Reloads from ConfigManager if its {@code data} value differs from what
     * this class last wrote, e.g. after RuneLite switched to another profile.
     * Without this the stale cache would be written over the new data on the
     * next save.
     *
     * @return true if the cache was replaced and the UI needs a rebuild
     */
    public boolean reloadIfChangedExternally()
    {
        if (cachedData == null || Objects.equals(configReader.get(), lastWrittenJson))
        {
            return false;
        }
        log.info("Roster data changed outside the plugin; reloading");
        // Finish the old copy's pending file write first, so the file backup
        // stays in step with what the cascade in load() might read back.
        fileMirror.flushNow();
        invalidateCache();
        load();
        return true;
    }

    public void save()
    {
        if (cachedData == null)
        {
            log.warn("save() called but cachedData is null");
            return;
        }

        // Any save is preceded by a mutation; drop derived caches.
        sortedGroupsCache = null;
        cachedData.invalidateIndexes();

        // One serialization feeds both layers. Doing it here, on the mutating
        // thread, keeps the snapshot consistent however late the file write runs.
        String json = gson.toJson(cachedData);
        writeToConfigManager(json);
        fileMirror.scheduleWrite(json);
    }

    private void writeToConfigManager(String json)
    {
        // Recorded before the write: ConfigManager posts ConfigChanged
        // synchronously, and the listener must recognize this value as ours.
        lastWrittenJson = json;
        try
        {
            configWriter.accept(json);
        }
        catch (Exception e)
        {
            log.error("Failed to save AccountData to ConfigManager", e);
        }
    }

    /**
     * Flushes the pending file write and drops the cache, so the next start
     * reloads from ConfigManager (which may have changed while disabled).
     */
    public void shutdown()
    {
        fileMirror.flushNow();
        invalidateCache();
    }

    private void invalidateCache()
    {
        cachedData = null;
        sortedGroupsCache = null;
    }

    // Account operations

    public List<Account> getAccounts()
    {
        return new ArrayList<>(load().getAccounts());
    }

    public Account getAccount(String id)
    {
        return load().findAccountById(id);
    }

    public void saveAccount(Account account)
    {
        load().upsertAccount(account);
        save();
    }

    /**
     * Updates several accounts with a single {@link #save()}, so a drag that
     * renumbers a whole category serializes the roster once, not once per account.
     */
    public void saveAccounts(Collection<Account> accounts)
    {
        if (accounts.isEmpty())
        {
            return;
        }
        AccountData data = load();
        for (Account account : accounts)
        {
            data.upsertAccount(account);
        }
        save();
    }

    /** The sortOrder that places an account last in {@code groupId} (null = Uncategorized). */
    public int nextAccountSortOrder(String groupId)
    {
        AccountData data = load();
        return data.nextAccountSortOrder(data.findGroupById(groupId) != null ? groupId : null);
    }

    public void deleteAccount(String id)
    {
        load().removeAccount(id);
        save();
    }

    // Group operations

    public List<ProfileGroup> getGroups()
    {
        List<ProfileGroup> cached = sortedGroupsCache;
        if (cached == null)
        {
            cached = new ArrayList<>(load().getGroups());
            cached.sort(Comparator.comparingInt(ProfileGroup::getSortOrder));
            sortedGroupsCache = cached;
        }
        return new ArrayList<>(cached);
    }

    public ProfileGroup getGroup(String id)
    {
        return load().findGroupById(id);
    }

    public void saveGroup(ProfileGroup group)
    {
        load().upsertGroup(group);
        save();
    }

    /** Updates several groups with a single {@link #save()}. */
    public void saveGroups(Collection<ProfileGroup> groups)
    {
        if (groups.isEmpty())
        {
            return;
        }
        AccountData data = load();
        for (ProfileGroup group : groups)
        {
            data.upsertGroup(group);
        }
        save();
    }

    public void deleteGroup(String id)
    {
        load().removeGroup(id);
        save();
    }

    /**
     * Clears accounts, categories, or both, with a single save. Clearing only
     * the categories moves every account to Uncategorized: each category's
     * accounts are appended in category order, keeping their order within it.
     */
    public void clear(boolean accounts, boolean groups)
    {
        AccountData data = load();
        if (accounts)
        {
            data.getAccounts().clear();
        }
        if (groups)
        {
            for (ProfileGroup group : getGroups())
            {
                data.removeGroup(group.getId());
            }
        }
        data.invalidateIndexes();
        save();
    }

    // Import / export

    /** The live data, for serialization by {@link ImportExportService} on the EDT. */
    AccountData exportSnapshot()
    {
        return load();
    }

    /** Merges {@code importData} into the roster and saves once. */
    ImportStats importData(AccountData importData, ImportDuplicateMode duplicateMode)
    {
        ImportStats stats = new AccountDataMerger().merge(load(), importData, duplicateMode);
        save();
        return stats;
    }
}
