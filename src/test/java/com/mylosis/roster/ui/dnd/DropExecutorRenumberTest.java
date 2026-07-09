package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropExecutorRenumberTest
{
    private static Account accountWithOrder(String id, int sortOrder)
    {
        return Account.builder()
            .id(id)
            .metadata(AccountMetadata.builder().sortOrder(sortOrder).build())
            .build();
    }

    @Test
    public void insertsInMiddleAndRenumbersSequentially()
    {
        List<Account> siblings = new ArrayList<>();
        siblings.add(accountWithOrder("a", 0));
        siblings.add(accountWithOrder("b", 1));
        Account moved = accountWithOrder("moved", 99);

        List<Account> changed = DropExecutor.renumberWithInsert(siblings, moved, 1);

        assertEquals(3, changed.size());
        assertEquals("a", changed.get(0).getId());
        assertEquals("moved", changed.get(1).getId());
        assertEquals("b", changed.get(2).getId());
        assertEquals(0, changed.get(0).getMetadata().getSortOrder());
        assertEquals(1, changed.get(1).getMetadata().getSortOrder());
        assertEquals(2, changed.get(2).getMetadata().getSortOrder());
    }

    @Test
    public void insertsAtStart()
    {
        List<Account> siblings = new ArrayList<>();
        siblings.add(accountWithOrder("a", 0));
        siblings.add(accountWithOrder("b", 1));
        Account moved = accountWithOrder("moved", 99);

        List<Account> changed = DropExecutor.renumberWithInsert(siblings, moved, 0);

        assertEquals("moved", changed.get(0).getId());
        assertEquals("a", changed.get(1).getId());
        assertEquals("b", changed.get(2).getId());
        assertEquals(0, changed.get(0).getMetadata().getSortOrder());
        assertEquals(1, changed.get(1).getMetadata().getSortOrder());
        assertEquals(2, changed.get(2).getMetadata().getSortOrder());
    }

    @Test
    public void negativeIndexAppends()
    {
        List<Account> siblings = new ArrayList<>();
        siblings.add(accountWithOrder("a", 0));
        siblings.add(accountWithOrder("b", 1));
        Account moved = accountWithOrder("moved", 99);

        List<Account> changed = DropExecutor.renumberWithInsert(siblings, moved, -1);

        assertEquals("a", changed.get(0).getId());
        assertEquals("b", changed.get(1).getId());
        assertEquals("moved", changed.get(2).getId());
        assertEquals(2, changed.get(2).getMetadata().getSortOrder());
    }

    @Test
    public void indexBeyondSizeClampsToAppend()
    {
        List<Account> siblings = new ArrayList<>();
        siblings.add(accountWithOrder("a", 0));
        siblings.add(accountWithOrder("b", 1));
        Account moved = accountWithOrder("moved", 99);

        List<Account> changed = DropExecutor.renumberWithInsert(siblings, moved, 50);

        assertEquals("a", changed.get(0).getId());
        assertEquals("b", changed.get(1).getId());
        assertEquals("moved", changed.get(2).getId());
        assertEquals(2, changed.get(2).getMetadata().getSortOrder());
    }

    @Test
    public void returnedListContainsMovedPlusAllSiblingsWithMetadata()
    {
        List<Account> siblings = new ArrayList<>();
        siblings.add(accountWithOrder("a", 0));
        siblings.add(accountWithOrder("b", 1));
        siblings.add(accountWithOrder("c", 2));
        Account moved = accountWithOrder("moved", 99);

        List<Account> changed = DropExecutor.renumberWithInsert(siblings, moved, 2);

        assertEquals(4, changed.size());
        assertTrue(changed.stream().anyMatch(a -> a.getId().equals("moved")));
        assertTrue(changed.stream().anyMatch(a -> a.getId().equals("a")));
        assertTrue(changed.stream().anyMatch(a -> a.getId().equals("b")));
        assertTrue(changed.stream().anyMatch(a -> a.getId().equals("c")));
    }

    @Test
    public void siblingWithNullMetadataIsSkippedButOthersStillNumberedByPosition()
    {
        List<Account> siblings = new ArrayList<>();
        siblings.add(accountWithOrder("a", 0));
        // No metadata: excluded from the returned "changed" list, but still
        // occupies a slot in the combined ordering, so later positions shift.
        Account noMeta = Account.builder().id("noMeta").build();
        siblings.add(noMeta);
        siblings.add(accountWithOrder("b", 1));
        Account moved = accountWithOrder("moved", 99);

        List<Account> changed = DropExecutor.renumberWithInsert(siblings, moved, 3);

        assertFalse(changed.stream().anyMatch(a -> a.getId().equals("noMeta")));
        assertEquals(3, changed.size());
        // Order in siblings is [a, noMeta, b], moved inserted at index 3 -> [a, noMeta, b, moved]
        assertEquals("a", changed.get(0).getId());
        assertEquals(0, changed.get(0).getMetadata().getSortOrder());
        assertEquals("b", changed.get(1).getId());
        assertEquals(2, changed.get(1).getMetadata().getSortOrder());
        assertEquals("moved", changed.get(2).getId());
        assertEquals(3, changed.get(2).getMetadata().getSortOrder());
    }
}
