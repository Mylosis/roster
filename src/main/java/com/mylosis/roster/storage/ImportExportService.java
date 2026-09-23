package com.mylosis.roster.storage;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountData;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.model.AccountType;
import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.ProfileGroup;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Clipboard import and export of the roster as pretty-printed JSON.
 */
@Slf4j
@Singleton
public class ImportExportService
{
    /**
     * Upper bound on accepted import JSON. Anything this size is not a roster
     * (2 MB is thousands of accounts); without the cap, an oversized clipboard
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

    /** Copies the whole roster to the clipboard. */
    public ExportResult exportToClipboard()
    {
        AccountData data = accountStorage.exportSnapshot();
        return export(data, data.getAccounts().size(), data.getGroups().size());
    }

    /**
     * Copies one account to the clipboard, wrapped in the same envelope as a
     * full export so either can be imported the same way.
     */
    public ExportResult exportAccountToClipboard(Account account)
    {
        AccountData wrapper = AccountData.builder()
            .version(AccountData.CURRENT_VERSION)
            .accounts(Collections.singletonList(account))
            .groups(new ArrayList<>())
            .build();
        return export(wrapper, 1, 0);
    }

    private ExportResult export(AccountData data, int accountCount, int groupCount)
    {
        try
        {
            copyToClipboard(gson.toJson(data));
            log.debug("Exported {} accounts and {} categories to clipboard", accountCount, groupCount);
            return ExportResult.success(accountCount, groupCount);
        }
        catch (Exception e)
        {
            log.error("Failed to export to clipboard", e);
            return ExportResult.failure("Failed to copy to clipboard: " + e.getMessage());
        }
    }

    /** Puts plain text on the system clipboard. */
    public void copyToClipboard(String text)
    {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public ImportResult importFromClipboard(ImportDuplicateMode duplicateMode)
    {
        try
        {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor))
            {
                return ImportResult.failure("Clipboard doesn't contain text");
            }

            String json = (String) contents.getTransferData(DataFlavor.stringFlavor);
            if (json == null || json.trim().isEmpty())
            {
                return ImportResult.failure("Clipboard is empty");
            }
            if (json.length() > MAX_IMPORT_CHARS)
            {
                return ImportResult.failure("Clipboard data too large to be account data");
            }

            return importFromJson(json, duplicateMode);
        }
        catch (JsonSyntaxException e)
        {
            log.warn("Invalid JSON format in clipboard", e);
            return ImportResult.failure("Clipboard doesn't contain valid account data");
        }
        catch (Exception e)
        {
            log.error("Failed to import from clipboard", e);
            return ImportResult.failure("Failed to read clipboard: " + e.getMessage());
        }
    }

    private ImportResult importFromJson(String json, ImportDuplicateMode duplicateMode)
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
            if (singleAccount == null || singleAccount.getId() == null || singleAccount.getUsername() == null)
            {
                return ImportResult.failure("Invalid data format");
            }
            importData = AccountData.builder()
                .version(AccountData.CURRENT_VERSION)
                .accounts(new ArrayList<>(Collections.singletonList(singleAccount)))
                .groups(new ArrayList<>())
                .build();
            log.debug("Detected single-account import, wrapped in AccountData");
        }

        String validationError = validateImportData(importData);
        if (validationError != null)
        {
            return ImportResult.failure(validationError);
        }

        normalizeImportData(importData);

        ImportStats stats = accountStorage.importData(importData, duplicateMode);
        log.debug("Imported accounts: {}", stats);
        return ImportResult.success(stats);
    }

    /**
     * Foreign JSON can carry {@code metadata: null} or an unrecognized
     * {@code accountType} (Gson maps unknown enum constants to null). Backfill
     * defaults so downstream UI code never NPEs on imported accounts.
     * Static (stateless) so tests can exercise it without a ConfigManager.
     */
    static void normalizeImportData(AccountData data)
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

    /** Returns a user-facing error, or null if the data is importable.
     *  Static (stateless) so tests can exercise it without a ConfigManager. */
    static String validateImportData(AccountData data)
    {
        if (data.getVersion() > AccountData.CURRENT_VERSION)
        {
            return "File version " + data.getVersion() + " is newer than supported version " + AccountData.CURRENT_VERSION;
        }

        if (data.getAccounts() != null)
        {
            for (Account profile : data.getAccounts())
            {
                if (profile == null)
                {
                    return "Account entry is empty";
                }
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
            for (ProfileGroup group : data.getGroups())
            {
                if (group == null)
                {
                    return "Category entry is empty";
                }
                if (group.getId() == null || group.getId().isEmpty())
                {
                    return "Group missing required ID field";
                }
            }
        }

        return null;
    }
}
