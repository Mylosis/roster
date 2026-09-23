package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.ui.AccountCardPanel;
import com.mylosis.roster.ui.CategoryPanel;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Hit-tests a {@link ListLayout} against the pointer during a drag. Bounds
 * are read live on every event, so scrolling mid-drag stays accurate.
 */
final class DropTargetFinder
{
    /** Horizontal inset of the insert line from the drop zone's edges. */
    private static final int LINE_INSET = 20;
    /** Header height assumed before a category has been laid out. */
    private static final int FALLBACK_HEADER_HEIGHT = 45;

    private DropTargetFinder() {}

    /** The drop target for an account drag at {@code p}, or null if outside every zone. */
    static DropTarget findForAccount(ListLayout layout, Point p)
    {
        List<CategoryPanel> categories = layout.getCategories();
        for (int i = 0; i < categories.size(); i++)
        {
            CategoryPanel category = categories.get(i);
            Rectangle bounds = screenBounds(category);
            if (bounds != null && bounds.contains(p))
            {
                return accountTarget(DropTarget.Type.CATEGORY_DROP, category.getGroup().getId(),
                    bounds, layout.getCards(i), p, layout.isGridMode());
            }
        }

        Rectangle area = null;
        for (JComponent zone : layout.getUncategorizedZones())
        {
            Rectangle bounds = screenBounds(zone);
            if (bounds != null)
            {
                area = area == null ? bounds : area.union(bounds);
            }
        }
        if (area != null && area.contains(p))
        {
            return accountTarget(DropTarget.Type.UNCATEGORIZED, null, area,
                layout.getUncategorizedCards(), p, layout.isGridMode());
        }
        return null;
    }

    /**
     * The drop target for a category drag at {@code p}: another category's
     * header, split at its midpoint into "before" and "after".
     */
    static DropTarget findForCategory(ListLayout layout, Point p, String draggedId)
    {
        for (CategoryPanel category : layout.getCategories())
        {
            String id = category.getGroup().getId();
            Rectangle panel = screenBounds(category);
            if (id.equals(draggedId) || panel == null)
            {
                continue;
            }
            int headerHeight = category.getHeaderHeight() > 0 ? category.getHeaderHeight() : FALLBACK_HEADER_HEIGHT;
            Rectangle header = new Rectangle(panel.x, panel.y, panel.width, headerHeight);
            if (header.contains(p))
            {
                boolean before = p.y < header.y + header.height / 2;
                int lineY = before ? panel.y : panel.y + panel.height;
                Rectangle line = new Rectangle(panel.x + LINE_INSET, lineY, panel.width - 2 * LINE_INSET, 0);
                return new DropTarget(DropTarget.Type.CATEGORY_REORDER, null, header,
                    before ? id : null, before ? null : id, line);
            }
        }
        return null;
    }

    private static DropTarget accountTarget(DropTarget.Type type, String groupId, Rectangle zone,
                                            List<AccountCardPanel> cards, Point p, boolean gridMode)
    {
        List<AccountCardPanel> visible = new ArrayList<>(cards.size());
        List<Rectangle> rects = new ArrayList<>(cards.size());
        for (AccountCardPanel card : cards)
        {
            Rectangle bounds = screenBounds(card);
            if (bounds != null)
            {
                visible.add(card);
                rects.add(bounds);
            }
        }
        InsertPoint insert = gridMode ? gridInsertPoint(rects, p) : listInsertPoint(rects, p, zone);
        String beforeId = insert.index < visible.size() ? visible.get(insert.index).getAccount().getId() : null;
        String afterId = insert.index > 0 ? visible.get(insert.index - 1).getAccount().getId() : null;
        return new DropTarget(type, groupId, zone, beforeId, afterId, insert.indicator);
    }

    /** Where a drop lands among visible cards: before {@code index} ({@code size} = at the end). */
    static final class InsertPoint
    {
        final int index;
        final Rectangle indicator;

        InsertPoint(int index, Rectangle indicator)
        {
            this.index = index;
            this.indicator = indicator;
        }
    }

    /** Single-column list: the first card whose vertical midpoint is below the pointer. */
    static InsertPoint listInsertPoint(List<Rectangle> cards, Point p, Rectangle zone)
    {
        int lineX = zone.x + LINE_INSET;
        int lineWidth = zone.width - 2 * LINE_INSET;
        for (int i = 0; i < cards.size(); i++)
        {
            Rectangle card = cards.get(i);
            if (p.y < card.y + card.height / 2)
            {
                return new InsertPoint(i, new Rectangle(lineX, card.y, lineWidth, 0));
            }
        }
        if (cards.isEmpty())
        {
            return new InsertPoint(0, null);
        }
        Rectangle last = cards.get(cards.size() - 1);
        return new InsertPoint(cards.size(), new Rectangle(lineX, last.y + last.height, lineWidth, 0));
    }

    /**
     * Two-column grid in row-major order: the first card whose row is below the
     * pointer, or which shares the pointer's row with its center to the right.
     */
    static InsertPoint gridInsertPoint(List<Rectangle> cards, Point p)
    {
        int index = cards.size();
        for (int i = 0; i < cards.size(); i++)
        {
            Rectangle card = cards.get(i);
            boolean rowBelowPointer = p.y < card.y;
            boolean sameRowToTheRight = p.y < card.y + card.height && p.x < card.x + card.width / 2;
            if (rowBelowPointer || sameRowToTheRight)
            {
                index = i;
                break;
            }
        }
        if (cards.isEmpty())
        {
            return new InsertPoint(0, null);
        }

        // Vertical indicator: right of the previous card when the pointer is
        // on its row, otherwise left of the next card (or right of the last).
        if (index > 0)
        {
            Rectangle prev = cards.get(index - 1);
            if (index == cards.size() || (p.y >= prev.y && p.y < prev.y + prev.height))
            {
                return new InsertPoint(index, new Rectangle(prev.x + prev.width + 1, prev.y, 0, prev.height));
            }
        }
        Rectangle next = cards.get(index);
        return new InsertPoint(index, new Rectangle(next.x - 2, next.y, 0, next.height));
    }

    /** On-screen bounds of a showing component; null when it isn't showing. */
    private static Rectangle screenBounds(Component component)
    {
        if (!component.isShowing())
        {
            return null;
        }
        try
        {
            Point location = component.getLocationOnScreen();
            return new Rectangle(location.x, location.y, component.getWidth(), component.getHeight());
        }
        catch (IllegalComponentStateException e)
        {
            return null;
        }
    }
}
