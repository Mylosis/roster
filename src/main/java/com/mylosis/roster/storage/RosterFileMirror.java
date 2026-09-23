package com.mylosis.roster.storage;

import com.google.gson.Gson;
import com.mylosis.roster.model.AccountData;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The on-disk JSON mirror of the roster: {@code roster.json} plus a rotating
 * {@code roster.backup.json}, both in the RuneLite directory.
 *
 * <p>Every save lands here as well as in ConfigManager, so the file is a
 * permanent second copy that {@link AccountStorage#load()} can re-seed from
 * when ConfigManager comes back empty (e.g. the client was killed before
 * ConfigManager's debounced flush reached disk).
 *
 * <p>Writes are coalesced on RuneLite's shared scheduled executor: a burst of
 * saves (a drag renumbering a whole category) produces one disk write at the
 * tail of the burst. The JSON is serialized by the caller before it gets here,
 * so the writer never walks the live object graph.
 */
@Slf4j
class RosterFileMirror
{
    private static final String DATA_FILE_NAME = "roster.json";
    private static final String BACKUP_FILE_NAME = "roster.backup.json";
    /** Recovery source written by v1.1.0's non-idempotent migration. */
    private static final String LEGACY_FILE_NAME = "roster.json.pre-v1.1.bak";
    private static final long COALESCE_MS = 250;

    private final Gson gson;
    private final ScheduledExecutorService executor;
    private final File dataFile;
    private final File backupFile;
    private final File legacyFile;

    /** The JSON the next flush will write, or null when nothing is pending. */
    private final AtomicReference<String> pendingJson = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> pendingFlush = new AtomicReference<>();
    /**
     * Serializes flushes. The pending JSON is claimed inside this lock, so when
     * the timer flush and a shutdown flush race, the later writer always holds
     * the newer snapshot and the two never share the temp file.
     */
    private final Object writeLock = new Object();

    RosterFileMirror(Gson gson, File directory, ScheduledExecutorService executor)
    {
        this.gson = gson;
        this.executor = executor;
        this.dataFile = new File(directory, DATA_FILE_NAME);
        this.backupFile = new File(directory, BACKUP_FILE_NAME);
        this.legacyFile = new File(directory, LEGACY_FILE_NAME);
    }

    /**
     * Returns the first candidate file that parses, newest first:
     * {@code roster.json}, then {@code roster.backup.json}, then the legacy
     * v1.1.0 migration file. Null when none is readable.
     */
    AccountData readNewest()
    {
        for (File file : new File[]{dataFile, backupFile, legacyFile})
        {
            if (!file.exists())
            {
                continue;
            }
            AccountData data = read(file);
            if (data != null)
            {
                if (file != dataFile)
                {
                    log.warn("roster.json missing or unreadable; recovered from {}", file.getName());
                }
                return data;
            }
        }
        return null;
    }

    private AccountData read(File file)
    {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
        {
            AccountData data = gson.fromJson(reader, AccountData.class);
            if (data != null)
            {
                log.debug("Loaded {} accounts from {}",
                    data.getAccounts() != null ? data.getAccounts().size() : 0, file.getName());
            }
            return data;
        }
        catch (Exception e)
        {
            log.error("Failed to read {}", file.getName(), e);
            preserveCorruptFile(file);
            return null;
        }
    }

    /**
     * A file that exists but won't parse may still be the user's last good
     * backup. Set it aside before the normal save path rotates it over
     * {@code roster.backup.json}; without this, two saves after a bad load
     * every recovery source is gone.
     */
    private static void preserveCorruptFile(File file)
    {
        try
        {
            Path quarantine = file.toPath().resolveSibling(file.getName() + ".corrupt");
            Files.copy(file.toPath(), quarantine, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Preserved unparseable {} as {}", file.getName(), quarantine.getFileName());
        }
        catch (Exception e)
        {
            log.warn("Could not preserve corrupt file {}", file.getName(), e);
        }
    }

    /** Queues {@code json} for writing; a newer call before the delay replaces it. */
    void scheduleWrite(String json)
    {
        pendingJson.set(json);
        ScheduledFuture<?> previous = pendingFlush.getAndSet(
            executor.schedule(this::flushPending, COALESCE_MS, TimeUnit.MILLISECONDS));
        if (previous != null)
        {
            previous.cancel(false);
        }
    }

    /** Writes any pending JSON now, on the calling thread. */
    void flushNow()
    {
        ScheduledFuture<?> scheduled = pendingFlush.getAndSet(null);
        if (scheduled != null)
        {
            scheduled.cancel(false);
        }
        flushPending();
    }

    private void flushPending()
    {
        synchronized (writeLock)
        {
            String json = pendingJson.getAndSet(null);
            if (json == null)
            {
                return;
            }
            try
            {
                write(json);
            }
            catch (Exception e)
            {
                // Exceptions inside a ScheduledFuture are swallowed unless
                // someone calls get(); log them or the mirror fails invisibly.
                // The next save retries naturally.
                log.error("Failed to write {}", dataFile.getAbsolutePath(), e);
            }
        }
    }

    private void write(String json) throws IOException
    {
        if (dataFile.exists())
        {
            Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // Write a temp file, then move it over the real one, so a failure
        // mid-write can never leave a torn roster.json behind.
        Path target = dataFile.toPath();
        Path tmp = target.resolveSibling(DATA_FILE_NAME + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8))
        {
            writer.write(json);
        }
        try
        {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
