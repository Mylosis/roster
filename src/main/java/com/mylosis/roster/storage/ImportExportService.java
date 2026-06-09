package com.mylosis.roster.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.model.AccountType;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Slf4j
@Singleton
public class ImportExportService
{
    /**
     * Upper bound on accepted import JSON. Anything this size is not a roster
     * (2 MB is thousands of accounts) — without the cap, an oversized clipboard
     * blob gets parsed, stored, and pushed through ConfigManager's cloud sync
     * on every subsequent save.
     */
    private static final int MAX_IMPORT_CHARS = 2_000_000;

    private final AccountStorage accountStorage;
    private final Gson gson;

    @Inject
    public ImportExportService(AccountStorage accountStorage, Gson gson)
    {
        this.accountStorage = accountStorage;
        this.gson = gson.newBuilder()
            .setPrettyPrinting()
            .create();
    }

    public ExportResult exportToClipboard()
    {
        try
        {
            AccountData exportData = accountStorage.getExportData();
            String json = gson.toJson(exportData);

            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(json), null);

            int accountCount = exportData.getAccounts() != null ? exportData.getAccounts().size() : 0;
            int groupCount = exportData.getGroups() != null ? exportData.getGroups().size() : 0;

            log.info("Exported {} accounts and {} categories to clipboard", accountCount, groupCount);
            return new ExportResult(true, accountCount, groupCount, null);
        }
        catch (Exception e)
        {
            log.error("Failed to export to clipboard", e);
            return new ExportResult(false, 0, 0, "Failed to copy to clipboard: " + e.getMessage());
        }
    }

    public ImportResult importFromClipboard(boolean replace)
    {
        return importFromClipboard(replace, ImportDuplicateMode.ADD_WITH_SUFFIX);
    }

    public ImportResult importFromClipboard(boolean replace, ImportDuplicateMode duplicateMode)
    {
        try
        {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor))
            {
                return ImportResult.failure("Clipboard doesn't contain text");
            }

            String json = (String) clipboard.getContents(null).getTransferData(DataFlavor.stringFlavor);
            if (json == null || json.trim().isEmpty())
            {
                return ImportResult.failure("Clipboard is empty");
            }

            log.debug("Clipboard content length: {} chars", json.length());

            if (json.length() > MAX_IMPORT_CHARS)
            {
                return ImportResult.failure("Clipboard data too large to be account data");
            }

            return importFromJson(json, replace, duplicateMode);
        }
        catch (JsonSyntaxException e)
        {
            log.error("Invalid JSON format in clipboard", e);
            return ImportResult.failure("Clipboard doesn't contain valid account data");
        }
        catch (Exception e)
        {
            log.error("Failed to import from clipboard", e);
            return ImportResult.failure("Failed to read clipboard: " + e.getMessage());
        }
    }

    public ExportResult exportToFile(File file)
    {
        try
        {
            AccountData exportData = accountStorage.getExportData();
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
            {
                gson.toJson(exportData, writer);
            }

            int accountCount = exportData.getAccounts() != null ? exportData.getAccounts().size() : 0;
            int groupCount = exportData.getGroups() != null ? exportData.getGroups().size() : 0;

            log.info("Exported {} accounts and {} groups to {}", accountCount, groupCount, file.getAbsolutePath());
            return new ExportResult(true, accountCount, groupCount, null);
        }
        catch (IOException e)
        {
            log.error("Failed to export accounts", e);
            return new ExportResult(false, 0, 0, "Failed to write file: " + e.getMessage());
        }
    }

    public ImportResult importFromFile(File file, boolean replace)
    {
        return importFromFile(file, replace, ImportDuplicateMode.ADD_WITH_SUFFIX);
    }

    public ImportResult importFromFile(File file, boolean replace, ImportDuplicateMode duplicateMode)
    {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
        {
            AccountData importData = gson.fromJson(reader, AccountData.class);
            if (importData == null)
            {
                return ImportResult.failure("Invalid or empty file");
            }
            return doImport(importData, replace, duplicateMode);
        }
        catch (JsonSyntaxException e)
        {
            log.error("Invalid JSON format in import file", e);
            return ImportResult.failure("Invalid JSON format");
        }
        catch (IOException e)
        {
            log.error("Failed to import accounts", e);
            return ImportResult.failure("Failed to read file: " + e.getMessage());
        }
    }

    private ImportResult importFromJson(String json, boolean replace, ImportDuplicateMode duplicateMode)
    {
        AccountData importData = gson.fromJson(json, AccountData.class);
        if (importData == null)
        {
            return ImportResult.failure("Invalid data format");
        }

        // Detect single-account JSON (backwards compat with pre-1.1 single exports)
        if (importData.getAccounts() == null && importData.getGroups() == null)
        {
            Account singleAccount = gson.fromJson(json, Account.class);
            if (singleAccount != null && singleAccount.getId() != null && singleAccount.getUsername() != null)
            {
                importData = AccountData.builder()
                    .version(AccountData.CURRENT_VERSION)
                    .accounts(java.util.Collections.singletonList(singleAccount))
                    .groups(new java.util.ArrayList<>())
                    .build();
                log.debug("Detected single-account import, wrapped in AccountData");
            }
            else
            {
                return ImportResult.failure("Invalid data format");
            }
        }

        return doImport(importData, replace, duplicateMode);
    }

    private ImportResult doImport(AccountData importData, boolean replace, ImportDuplicateMode duplicateMode)
    {
        String validationError = validateImportData(importData);
        if (validationError != null)
        {
            return ImportResult.failure(validationError);
        }

        normalizeImportData(importData);

        ImportStats stats = accountStorage.importData(importData, replace, duplicateMode);
        log.info("Imported accounts: added={}, skipped={}, updated={}, groups added={}",
            stats.getAccountsAdded(), stats.getAccountsSkipped(), stats.getAccountsUpdated(), stats.getGroupsAdded());

        return ImportResult.success(stats);
    }

    /**
     * Foreign JSON can carry {@code metadata: null} or an unrecognized
     * {@code accountType} (Gson maps unknown enum constants to null). Backfill
     * defaults so downstream UI code never NPEs on imported accounts.
     */
    private void normalizeImportData(AccountData data)
    {
        if (data.getAccounts() == null)
        {
            return;
        }
        for (Account profile : data.getAccounts())
        {
            if (profile.getMetadata() == null)
            {
                profile.setMetadata(AccountMetadata.createDefault());
            }
            else if (profile.getMetadata().getAccountType() == null)
            {
                profile.getMetadata().setAccountType(AccountType.MAIN);
            }
        }
    }

    private String validateImportData(AccountData data)
    {
        if (data.getVersion() > AccountData.CURRENT_VERSION)
        {
            return "File version " + data.getVersion() + " is newer than supported version " + AccountData.CURRENT_VERSION;
        }

        if (data.getAccounts() != null)
        {
            for (var profile : data.getAccounts())
            {
                if (profile.getId() == null || profile.getId().isEmpty())
                {
                    return "Account missing required ID field";
                }
                if (profile.getUsername() == null || profile.getUsername().isEmpty())
                {
                    return "Account '" + profile.getAlias() + "' missing username";
                }
            }
        }

        if (data.getGroups() != null)
        {
            for (var group : data.getGroups())
            {
                if (group.getId() == null || group.getId().isEmpty())
                {
                    return "Group missing required ID field";
                }
            }
        }

        return null;
    }
}
