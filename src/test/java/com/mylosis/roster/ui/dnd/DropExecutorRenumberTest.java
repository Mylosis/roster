package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
    public void siblingWithNullMetadataGetsMetadataAndIsRenumbered()
    {
        List<Account> siblings = new ArrayList<>();
        siblings.add(accountWithOrder("a", 0));
        Account noMeta = Account.builder().id("noMeta").build();
        siblings.add(noMeta);
        Account moved = accountWithOrder("moved", 99);

        List<Account> changed = DropExecutor.renumberWithInsert(siblings, moved, 2);

        assertEquals(3, changed.size());
        assertNotNull(noMeta.getMetadata());
        assertEquals(1, noMeta.getMetadata().getSortOrder());
        assertEquals(2, moved.getMetadata().getSortOrder());
    }

    // resolveInsertIndex: visible neighbours -> index in the stored order

    @Test
    public void insertsInFrontOfBeforeNeighbour()
    {
        assertEquals(1, DropExecutor.resolveInsertIndex(Arrays.asList("a", "b", "c"), "b", "a"));
    }

    @Test
    public void fallsBackToAfterNeighbourAtTheEnd()
    {
        assertEquals(3, DropExecutor.resolveInsertIndex(Arrays.asList("a", "b", "c"), null, "c"));
    }

    @Test
    public void emptyTargetAppends()
    {
        assertEquals(0, DropExecutor.resolveInsertIndex(Collections.emptyList(), null, null));
    }

    @Test
    public void downwardMoveInSameCategoryLandsBetweenTheNeighbours()
    {
        // Stored [A, B, C]; A dragged to between B and C. The visible
        // neighbours are B (after) and C (before); siblings exclude A.
        int index = DropExecutor.resolveInsertIndex(Arrays.asList("b", "c"), "c", "b");
        List<Account> changed = DropExecutor.renumberWithInsert(
            new ArrayList<>(Arrays.asList(accountWithOrder("b", 1), accountWithOrder("c", 2))),
            accountWithOrder("a", 0), index);

        assertEquals("b", changed.get(0).getId());
        assertEquals("a", changed.get(1).getId());
        assertEquals("c", changed.get(2).getId());
    }

    @Test
    public void neighboursHiddenBySearchStillResolveAgainstStoredOrder()
    {
        // Stored [a, x, b] with x filtered out of view; dropping between the
        // visible a and b lands in front of b in the stored order.
        assertEquals(2, DropExecutor.resolveInsertIndex(Arrays.asList("a", "x", "b"), "b", "a"));
    }
}
