package com.mylosis.roster.ui.dnd;

import lombok.Value;

import java.awt.Rectangle;

/**
 * Where a drag would land if released now. Positions are expressed as the
 * visible neighbours of the insertion point rather than an index, so they stay
 * correct when a search filter or non-manual sort hides or reorders entries
 * relative to the stored order.
 */
@Value
public class DropTarget
{
    public enum Type
    {
        /** An account dropped into a category. */
        CATEGORY_DROP,
        /** An account dropped into the Uncategorized area. */
        UNCATEGORIZED,
        /** A category dropped onto another category's header. */
        CATEGORY_REORDER
    }

    Type type;
    /** Target category id for account drops; null for Uncategorized and category reorders. */
    String groupId;
    /** Screen bounds of the highlighted drop zone. */
    Rectangle bounds;
    /** Id of the visible entry the drop lands in front of; null at the end. */
    String beforeId;
    /** Id of the visible entry the drop lands after; null at the start. */
    String afterId;
    /**
     * Screen-space insert indicator: a horizontal line when its height is 0, a
     * vertical one when its width is 0. Null when there is nothing to point at
     * (an empty category).
     */
    Rectangle indicator;
}
