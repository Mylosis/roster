package com.mylosis.roster.storage;

import com.google.gson.Gson;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds an {@link AccountStorage} backed by an in-memory config value and a
 * file mirror in {@code directory}, for tests outside this package.
 */
public final class TestStorage
{
    private TestStorage() {}

    public static AccountStorage create(File directory, ScheduledExecutorService executor)
    {
        // Test-only Gson; the plugin itself always uses RuneLite's injected instance.
        Gson gson = new Gson();
        String[] config = new String[1];
        return new AccountStorage(gson, () -> config[0], json -> config[0] = json,
            new RosterFileMirror(gson, directory, executor));
    }
}
