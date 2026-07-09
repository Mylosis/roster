package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.CategoryPanel;
import com.mylosis.roster.ui.AccountCardPanel;
import com.mylosis.roster.ui.RosterPanel;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds drop targets for drag-and-drop operations by walking the component tree.
 */
@Slf4j
public class DropTargetFinder
{
    private final RosterPanel panel;

    // Per-drag snapshot of the panel structure. Populated by primeSnapshot() at
    // drag start, consulted on every mouse-moved event (~60Hz). The previous
    // implementation walked the entire component tree on every single event —
    // for a panel with ~50 components that's ~3000 traversals per second of dragging.
    private List<CategoryPanel> snapshotCategoryPanels;
    private JPanel snapshotUncategorizedHeader;
    private JPanel snapshotUncategorizedContainer;
    private List<AccountCardPanel> snapshotUncategorizedCards;
    // Cards per category, walked once at drag start. calculateInsertPoint runs
    // on every mouse-moved event while hovering a category; without this it
    // re-walked the category's whole component subtree each event.
    private java.util.Map<CategoryPanel, List<AccountCardPanel>> snapshotCardsByCategory;

    public DropTargetFinder(RosterPanel panel)
    {
        this.panel = panel;
    }

    /**
     * Snapshot the panel's drop-target component structure for the lifetime of a
     * single drag. Called by {@link DragDropManager#startProfileDrag} and
     * {@link DragDropManager#startCategoryDrag}; cleared by {@code resetDragState}.
     * The snapshot is invalidated if the panel rebuilds during the drag — but the
     * drag itself will end on mouse release, so a stale snapshot at worst causes
     * one final frame of imprecise highlighting.
     */
    public void primeSnapshot()
    {
        snapshotCategoryPanels = findCategoryPanels();
        snapshotUncategorizedHeader = findPanelWithProperty("UNCATEGORIZED_HEADER");
        snapshotUncategorizedContainer = findPanelWithProperty("UNCATEGORIZED_CONTAINER");
        snapshotUncategorizedCards = new ArrayList<>();
        if (snapshotUncategorizedContainer != null)
        {
            findProfileCards(snapshotUncategorizedContainer, snapshotUncategorizedCards);
        }
        snapshotCardsByCategory = new java.util.HashMap<>();
        for (CategoryPanel categoryPanel : snapshotCategoryPanels)
        {
            List<AccountCardPanel> cards = new ArrayList<>();
            findProfileCards(categoryPanel, cards);
            snapshotCardsByCategory.put(categoryPanel, cards);
        }
    }

    /**
     * Drop the per-drag snapshot. Called by {@code DragDropManager.resetDragState}.
     */
    public void clearSnapshot()
    {
        snapshotCategoryPanels = null;
        snapshotUncategorizedHeader = null;
        snapshotUncategorizedContainer = null;
        snapshotUncategorizedCards = null;
        snapshotCardsByCategory = null;
    }

    /**
     * Find the drop target at the given screen point for a profile drag.
     */
    public DragDropManager.DropTarget findForProfile(Point screenPoint, Account draggedAccount)
    {
        DragDropManager.DropTarget target = checkCategoryPanels(screenPoint, draggedAccount);
        if (target != null)
        {
            return target;
        }
        return checkUncategorizedArea(screenPoint);
    }

    /**
     * Find the drop target at the given screen point for a category drag.
     */
    public DragDropManager.DropTarget findForCategory(Point screenPoint, ProfileGroup draggedCategory)
    {
        List<CategoryPanel> categoryPanels = snapshotCategoryPanels != null
            ? snapshotCategoryPanels
            : findCategoryPanels();

        for (int i = 0; i < categoryPanels.size(); i++)
        {
            CategoryPanel categoryPanel = categoryPanels.get(i);
            if (!categoryPanel.isShowing()) continue;
            if (draggedCategory != null &&
                categoryPanel.getGroup().getId().equals(draggedCategory.getId()))
            {
                continue;
            }

            try
            {
                Point panelLocation = categoryPanel.getLocationOnScreen();
                int headerHeight = categoryPanel.getHeaderHeight();
                Rectangle headerBounds = new Rectangle(
                    panelLocation.x, panelLocation.y,
                    categoryPanel.getWidth(), headerHeight > 0 ? headerHeight : 45
                );

                if (headerBounds.contains(screenPoint))
                {
                    int midY = headerBounds.y + headerBounds.height / 2;
                    int insertIndex = screenPoint.y < midY ? i : i + 1;
                    return new DragDropManager.DropTarget(
                        DragDropManager.DropTarget.Type.CATEGORY_REORDER,
                        categoryPanel.getGroup().getId(),
                        headerBounds, insertIndex
                    );
                }
            }
            catch (Exception e)
            {
                // Component might not be showing
            }
        }
        return null;
    }

    private DragDropManager.DropTarget checkCategoryPanels(Point screenPoint, Account draggedAccount)
    {
        List<CategoryPanel> categoryPanels = snapshotCategoryPanels != null
            ? snapshotCategoryPanels
            : findCategoryPanels();

        for (CategoryPanel categoryPanel : categoryPanels)
        {
            if (!categoryPanel.isShowing()) continue;

            try
            {
                Point panelLocation = categoryPanel.getLocationOnScreen();
                Rectangle panelBounds = new Rectangle(
                    panelLocation.x, panelLocation.y,
                    categoryPanel.getWidth(), categoryPanel.getHeight()
                );

                if (panelBounds.contains(screenPoint))
                {
                    String groupId = categoryPanel.getGroup().getId();
                    InsertPoint insert = calculateInsertPoint(categoryPanel, screenPoint);
                    return new DragDropManager.DropTarget(
                        DragDropManager.DropTarget.Type.CATEGORY_DROP,
                        groupId, panelBounds, insert.index, insert.lineY
                    );
                }
            }
            catch (Exception e)
            {
                // Component might not be showing
            }
        }
        return null;
    }

    /**
     * Hit-tests the whole uncategorized region: the "Uncategorized" header (when
     * groups exist) plus the ungrouped card list below it. Previously only the
     * header was a target, so drops "into Uncategorized" felt broken next to
     * real categories (whose entire panel accepts drops), and reordering within
     * Uncategorized was impossible. Insert position comes from the real card
     * bounds, exactly like {@link #calculateInsertPoint}.
     */
    private DragDropManager.DropTarget checkUncategorizedArea(Point screenPoint)
    {
        JPanel header = snapshotUncategorizedHeader != null
            ? snapshotUncategorizedHeader
            : findPanelWithProperty("UNCATEGORIZED_HEADER");
        JPanel container = snapshotUncategorizedContainer != null
            ? snapshotUncategorizedContainer
            : findPanelWithProperty("UNCATEGORIZED_CONTAINER");

        Rectangle area = null;
        area = unionShowingBounds(area, header);
        area = unionShowingBounds(area, container);
        if (area == null || !area.contains(screenPoint))
        {
            return null;
        }

        List<AccountCardPanel> cards = snapshotUncategorizedCards;
        if (cards == null)
        {
            cards = new ArrayList<>();
            if (container != null)
            {
                findProfileCards(container, cards);
            }
        }
        InsertPoint insert = insertPointFromCards(cards, screenPoint);
        return new DragDropManager.DropTarget(
            DragDropManager.DropTarget.Type.UNCATEGORIZED, null, area,
            insert.index, insert.lineY
        );
    }

    /** Extends {@code area} to cover {@code panel}'s on-screen bounds; returns
     *  the (possibly new) rectangle, or {@code area} unchanged if the panel is
     *  null or not showing. */
    private static Rectangle unionShowingBounds(Rectangle area, JPanel panel)
    {
        if (panel == null || !panel.isShowing())
        {
            return area;
        }
        try
        {
            Point location = panel.getLocationOnScreen();
            Rectangle bounds = new Rectangle(location.x, location.y, panel.getWidth(), panel.getHeight());
            return area == null ? bounds : area.union(bounds);
        }
        catch (Exception e)
        {
            // Component might not be showing
            return area;
        }
    }

    /**
     * Insert position within a category: the index the dragged card would land
     * at, plus the exact screen-Y where the insert line should be drawn. The
     * line Y comes from the real card bounds; the overlay used to estimate it
     * from an assumed 75px card height, which drifted whenever cards varied
     * (notes line, last-online line, grid mode).
     */
    static final class InsertPoint
    {
        final int index;
        /** Screen Y for the indicator line; -1 when unknown (empty category). */
        final int lineY;

        InsertPoint(int index, int lineY)
        {
            this.index = index;
            this.lineY = lineY;
        }
    }

    InsertPoint calculateInsertPoint(CategoryPanel categoryPanel, Point screenPoint)
    {
        List<AccountCardPanel> cards = snapshotCardsByCategory != null
            ? snapshotCardsByCategory.get(categoryPanel)
            : null;
        if (cards == null)
        {
            cards = new ArrayList<>();
            findProfileCards(categoryPanel, cards);
        }
        return insertPointFromCards(cards, screenPoint);
    }

    static InsertPoint insertPointFromCards(List<AccountCardPanel> cards, Point screenPoint)
    {
        if (cards == null || cards.isEmpty()) return new InsertPoint(0, -1);

        int lastVisibleBottom = -1;
        for (int i = 0; i < cards.size(); i++)
        {
            AccountCardPanel card = cards.get(i);
            if (!card.isShowing()) continue;

            try
            {
                Point cardLocation = card.getLocationOnScreen();
                int cardMidY = cardLocation.y + card.getHeight() / 2;
                if (screenPoint.y < cardMidY)
                {
                    return new InsertPoint(i, cardLocation.y);
                }
                lastVisibleBottom = cardLocation.y + card.getHeight();
            }
            catch (Exception e)
            {
                // Component might not be showing
            }
        }
        return new InsertPoint(cards.size(), lastVisibleBottom);
    }

    private void findProfileCards(Container container, List<AccountCardPanel> result)
    {
        for (Component comp : container.getComponents())
        {
            if (comp instanceof AccountCardPanel)
            {
                result.add((AccountCardPanel) comp);
            }
            if (comp instanceof Container)
            {
                findProfileCards((Container) comp, result);
            }
        }
    }

    List<CategoryPanel> findCategoryPanels()
    {
        List<CategoryPanel> result = new ArrayList<>();
        findCategoryPanelsRecursive(panel, result);
        return result;
    }

    private void findCategoryPanelsRecursive(Container container, List<CategoryPanel> result)
    {
        if (container instanceof JScrollPane)
        {
            JScrollPane scrollPane = (JScrollPane) container;
            Component view = scrollPane.getViewport().getView();
            if (view instanceof CategoryPanel) result.add((CategoryPanel) view);
            if (view instanceof Container) findCategoryPanelsRecursive((Container) view, result);
            return;
        }

        for (Component comp : container.getComponents())
        {
            if (comp instanceof CategoryPanel) result.add((CategoryPanel) comp);
            if (comp instanceof Container) findCategoryPanelsRecursive((Container) comp, result);
        }
    }

    /** Finds the first JPanel in the tree tagged with the given client property
     *  set to {@code Boolean.TRUE} (e.g. "UNCATEGORIZED_HEADER" or
     *  "UNCATEGORIZED_CONTAINER", tagged by AccountListBuilder). */
    private JPanel findPanelWithProperty(String propertyKey)
    {
        List<JPanel> result = new ArrayList<>();
        findPanelWithPropertyRecursive(panel, propertyKey, result);
        return result.isEmpty() ? null : result.get(0);
    }

    private void findPanelWithPropertyRecursive(Container container, String propertyKey, List<JPanel> result)
    {
        if (!result.isEmpty()) return;

        if (container instanceof JScrollPane)
        {
            JScrollPane scrollPane = (JScrollPane) container;
            Component view = scrollPane.getViewport().getView();
            if (view instanceof JPanel && Boolean.TRUE.equals(((JPanel) view).getClientProperty(propertyKey)))
            {
                result.add((JPanel) view);
                return;
            }
            if (view instanceof Container) findPanelWithPropertyRecursive((Container) view, propertyKey, result);
            return;
        }

        for (Component comp : container.getComponents())
        {
            if (comp instanceof JPanel && Boolean.TRUE.equals(((JPanel) comp).getClientProperty(propertyKey)))
            {
                result.add((JPanel) comp);
                return;
            }
            if (comp instanceof Container) findPanelWithPropertyRecursive((Container) comp, propertyKey, result);
        }
    }
}
