package com.mylosis.roster;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.storage.TestStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LoginTrackerTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ScheduledExecutorService executor;
    private AccountStorage storage;
    private LoginTracker tracker;

    @Before
    public void setUp()
    {
        executor = Executors.newSingleThreadScheduledExecutor();
        storage = TestStorage.create(folder.getRoot(), executor);
        tracker = new LoginTracker(storage);
    }

    @After
    public void tearDown()
    {
        executor.shutdownNow();
    }

    private Account save(String alias, String username)
    {
        Account account = Account.createNew(alias, username);
        storage.saveAccount(account);
        return account;
    }

    @Test
    public void loginMatchingAliasLearnsCharacterNameAndStampsLastOnline()
    {
        Account main = save("Zezima", "zezima@example.com");

        Set<String> refreshed = tracker.recordLogin("Zezima", null);

        assertEquals(Collections.singleton(main.getId()), refreshed);
        assertEquals("Zezima", storage.getAccount(main.getId()).getCharacterName());
        assertNotNull(storage.getAccount(main.getId()).getMetadata().getLastOnlineAt());
    }

    @Test
    public void selectionLinkAttributesAnUnmatchedLoginOnce()
    {
        Account alt = save("My alt", "alt@example.com");
        tracker.armSelectionLink(alt.getId());

        tracker.recordLogin("Iron Bob", null);
        assertEquals("Iron Bob", storage.getAccount(alt.getId()).getCharacterName());

        // The link was consumed: a later unrelated login is not attributed.
        assertTrue(tracker.recordLogin("Someone Else", null).isEmpty());
    }

    @Test
    public void lastOnlineIsThrottled()
    {
        Account main = save("Zezima", "zezima@example.com");
        tracker.recordLogin("Zezima", null);
        long first = storage.getAccount(main.getId()).getMetadata().getLastOnlineAt();

        tracker.recordLogin("Zezima", null);

        assertEquals(first, (long) storage.getAccount(main.getId()).getMetadata().getLastOnlineAt());
    }

    @Test
    public void switchingCharactersRefreshesBothCards()
    {
        Account one = save("One", "one@example.com");
        Account two = save("Two", "two@example.com");
        tracker.recordLogin("One", null);

        Set<String> refreshed = tracker.recordLogin("Two", "One");

        assertEquals(new LinkedHashSet<>(Arrays.asList(one.getId(), two.getId())), refreshed);
    }

    @Test
    public void characterNameMatchWinsOverAlias()
    {
        Account aliasMatch = save("Bob", "a@example.com");
        Account learned = save("Something else", "b@example.com");
        learned.setCharacterName("Bob");
        storage.saveAccount(learned);

        String found = tracker.findAccountIdByName("bob");
        assertEquals(learned.getId(), found);
        assertNotEquals(aliasMatch.getId(), found);
        assertNull(tracker.findAccountIdByName("nobody"));
    }
}
