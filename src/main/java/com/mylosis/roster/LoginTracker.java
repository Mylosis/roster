package com.mylosis.roster;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.storage.AccountStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Links in-game logins to roster accounts: records which character is online,
 * learns each account's character name, and stamps {@code lastOnlineAt}.
 *
 * <p>{@link #setLoggedInName} and {@link #armSelectionLink} may be called from
 * the client thread; everything that touches {@link AccountStorage} is EDT only.
 */
@Slf4j
class LoginTracker
{
    /**
     * lastOnlineAt write throttle, per account. At 10 minutes, world hops within
     * a play session collapse to a single write. The card's "5m ago" label has
     * minute granularity anyway, so this is invisible to users.
     */
    static final long LAST_ONLINE_THROTTLE_MS = 10L * 60_000L;

    private final AccountStorage storage;

    private volatile String loggedInName;

    /**
     * One-shot link between a login-screen card selection and the next
     * completed login. Consumed (and cleared) by the first login that follows,
     * so a later, unrelated manual login is never mis-attributed to a stale
     * selection. Lets us learn the account's real character name even when it
     * matches neither alias nor login email.
     */
    private volatile String pendingLinkAccountId;

    LoginTracker(AccountStorage storage)
    {
        this.storage = storage;
    }

    /** The logged-in character's name, or null when logged out. */
    String getLoggedInName()
    {
        return loggedInName;
    }

    /** Records the current character (null on logout); returns the previous one. */
    String setLoggedInName(String name)
    {
        String previous = loggedInName;
        loggedInName = name;
        return previous;
    }

    void armSelectionLink(String accountId)
    {
        pendingLinkAccountId = accountId;
    }

    void reset()
    {
        loggedInName = null;
        pendingLinkAccountId = null;
    }

    /**
     * Handles a completed login (EDT only) and returns the ids of the accounts
     * whose cards need refreshing: the newly online one and the previously
     * online one, when they are in the roster.
     */
    Set<String> recordLogin(String characterName, String previousName)
    {
        Set<String> changed = new LinkedHashSet<>();
        if (previousName != null && !previousName.equalsIgnoreCase(characterName))
        {
            addIfPresent(changed, findAccountIdByName(previousName));
        }
        addIfPresent(changed, stampLastOnline(characterName));
        return changed;
    }

    /**
     * Returns the roster account matching an in-game character name: an exact
     * {@code characterName} match wins, else the first display-name match
     * (alias if set, otherwise login name). Case-insensitive. EDT only.
     */
    Account findAccountByName(String name)
    {
        if (name == null) return null;
        Account displayMatch = null;
        for (Account a : storage.getAccounts())
        {
            if (name.equalsIgnoreCase(a.getCharacterName()))
            {
                return a;
            }
            if (displayMatch == null && name.equalsIgnoreCase(a.getDisplayName()))
            {
                displayMatch = a;
            }
        }
        return displayMatch;
    }

    String findAccountIdByName(String name)
    {
        Account a = findAccountByName(name);
        return a != null ? a.getId() : null;
    }

    /**
     * Records that {@code characterName} was just seen logged in: stamps
     * {@code lastOnlineAt} (throttled) and learns the account's
     * {@code characterName}. The account is resolved by name, then by the
     * one-shot selection link, which is consumed here unconditionally.
     *
     * @return the matched account's id, or null if this character isn't in the roster
     */
    private String stampLastOnline(String characterName)
    {
        Account matched = findAccountByName(characterName);
        String pendingLink = pendingLinkAccountId;
        pendingLinkAccountId = null;
        if (matched == null && pendingLink != null)
        {
            matched = storage.getAccount(pendingLink);
            if (matched != null)
            {
                log.debug("Linked login to selected account {}", matched.getId());
            }
        }
        if (matched == null)
        {
            return null;
        }

        boolean dirty = false;
        if (!characterName.equalsIgnoreCase(matched.getCharacterName()))
        {
            matched.setCharacterName(characterName);
            dirty = true;
        }
        AccountMetadata meta = matched.ensureMetadata();
        long now = System.currentTimeMillis();
        Long previous = meta.getLastOnlineAt();
        if (previous == null || (now - previous) >= LAST_ONLINE_THROTTLE_MS)
        {
            meta.setLastOnlineAt(now);
            dirty = true;
        }
        if (dirty)
        {
            storage.saveAccount(matched);
        }
        return matched.getId();
    }

    private static void addIfPresent(Set<String> ids, String id)
    {
        if (id != null)
        {
            ids.add(id);
        }
    }
}
