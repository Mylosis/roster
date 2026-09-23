package com.mylosis.roster.ui.dnd;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DropTargetFinderInsertTest
{
    private static final Rectangle ZONE = new Rectangle(0, 0, 200, 400);

    // Three stacked list cards, 50px tall with 4px gaps.
    private static final List<Rectangle> LIST = Arrays.asList(
        new Rectangle(0, 0, 200, 50),
        new Rectangle(0, 54, 200, 50),
        new Rectangle(0, 108, 200, 50));

    // 2-column grid, row-major: [0 1] [2 3] [4]
    private static final List<Rectangle> GRID = Arrays.asList(
        new Rectangle(0, 0, 98, 56),
        new Rectangle(102, 0, 98, 56),
        new Rectangle(0, 60, 98, 56),
        new Rectangle(102, 60, 98, 56),
        new Rectangle(0, 120, 98, 56));

    @Test
    public void listInsertsBeforeFirstCardBelowPointerMidpoint()
    {
        assertEquals(0, DropTargetFinder.listInsertPoint(LIST, new Point(10, 10), ZONE).index);
        assertEquals(1, DropTargetFinder.listInsertPoint(LIST, new Point(10, 40), ZONE).index);
        assertEquals(2, DropTargetFinder.listInsertPoint(LIST, new Point(10, 100), ZONE).index);
    }

    @Test
    public void listPastLastCardAppendsWithLineAtItsBottom()
    {
        DropTargetFinder.InsertPoint insert = DropTargetFinder.listInsertPoint(LIST, new Point(10, 300), ZONE);

        assertEquals(3, insert.index);
        assertEquals(158, insert.indicator.y);
        assertEquals(0, insert.indicator.height);
    }

    @Test
    public void emptyListHasNoIndicator()
    {
        DropTargetFinder.InsertPoint insert = DropTargetFinder.listInsertPoint(Collections.emptyList(), new Point(10, 10), ZONE);

        assertEquals(0, insert.index);
        assertNull(insert.indicator);
    }

    @Test
    public void gridUsesColumnWithinARow()
    {
        // Left half of card 1 (top-right): before card 1.
        assertEquals(1, DropTargetFinder.gridInsertPoint(GRID, new Point(110, 20)).index);
        // Right half of card 1: after it, i.e. before card 2.
        assertEquals(2, DropTargetFinder.gridInsertPoint(GRID, new Point(190, 20)).index);
        // Left half of card 3.
        assertEquals(3, DropTargetFinder.gridInsertPoint(GRID, new Point(110, 70)).index);
    }

    @Test
    public void gridIndicatorIsVerticalBesideTheNeighbourOnThePointersRow()
    {
        // Right half of card 1: line to the right of card 1, not left of card 2 (next row).
        DropTargetFinder.InsertPoint insert = DropTargetFinder.gridInsertPoint(GRID, new Point(190, 20));

        assertEquals(0, insert.indicator.width);
        assertEquals(0, insert.indicator.y);
        assertEquals(201, insert.indicator.x);
    }

    @Test
    public void gridBelowLastRowAppends()
    {
        assertEquals(5, DropTargetFinder.gridInsertPoint(GRID, new Point(150, 300)).index);
    }
}
