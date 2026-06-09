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
    // Cards per category, walked once at drag start. calculateInsertIndex runs
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
        snapshotUncategorizedHeader = findUncategorizedHeader();
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
        return checkUncategorizedHeader(screenPoint, draggedAccount);
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
                Rectangle headerBounds = new Rectangle(
                    panelLocation.x, panelLocation.y,
                    categoryPanel.getWidth(), 45
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
                    int insertIndex = calculateInsertIndex(categoryPanel, screenPoint);
                    return new DragDropManager.DropTarget(
                        DragDropManager.DropTarget.Type.CATEGORY_DROP,
                        groupId, panelBounds, insertIndex
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

    private DragDropManager.DropTarget checkUncategorizedHeader(Point screenPoint, Account draggedAccount)
    {
        JPanel header = snapshotUncategorizedHeader != null
            ? snapshotUncategorizedHeader
            : findUncategorizedHeader();
        if (header == null || !header.isShowing()) return null;

        try
        {
            Point headerLocation = header.getLocationOnScreen();
            Rectangle headerBounds = new Rectangle(
                headerLocation.x, headerLocation.y,
                header.getWidth(), header.getHeight()
            );

            if (headerBounds.contains(screenPoint))
            {
                if (draggedAccount != null && draggedAccount.getGroupId() == null)
                {
                    return null;
                }
                return new DragDropManager.DropTarget(
                    DragDropManager.DropTarget.Type.UNCATEGORIZED, null, headerBounds
                );
            }
        }
        catch (Exception e)
        {
            // Component might not be showing
        }
        return null;
    }

    /**
     * Calculate insert index based on mouse position relative to profile cards.
     */
    int calculateInsertIndex(CategoryPanel categoryPanel, Point screenPoint)
    {
        List<AccountCardPanel> cards = snapshotCardsByCategory != null
            ? snapshotCardsByCategory.get(categoryPanel)
            : null;
        if (cards == null)
        {
            cards = new ArrayList<>();
            findProfileCards(categoryPanel, cards);
        }

        if (cards.isEmpty()) return 0;

        for (int i = 0; i < cards.size(); i++)
        {
            AccountCardPanel card = cards.get(i);
            if (!card.isShowing()) continue;

            try
            {
                Point cardLocation = card.getLocationOnScreen();
                int cardMidY = cardLocation.y + card.getHeight() / 2;
                if (screenPoint.y < cardMidY) return i;
            }
            catch (Exception e)
            {
                // Component might not be showing
            }
        }
        return cards.size();
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

    private JPanel findUncategorizedHeader()
    {
        List<JPanel> result = new ArrayList<>();
        findUncategorizedHeaderRecursive(panel, result);
        return result.isEmpty() ? null : result.get(0);
    }

    private void findUncategorizedHeaderRecursive(Container container, List<JPanel> result)
    {
        if (container instanceof JScrollPane)
        {
            JScrollPane scrollPane = (JScrollPane) container;
            Component view = scrollPane.getViewport().getView();
            if (view instanceof JPanel && Boolean.TRUE.equals(((JPanel) view).getClientProperty("UNCATEGORIZED_HEADER")))
            {
                result.add((JPanel) view);
                return;
            }
            if (view instanceof Container) findUncategorizedHeaderRecursive((Container) view, result);
            return;
        }

        for (Component comp : container.getComponents())
        {
            if (comp instanceof JPanel && Boolean.TRUE.equals(((JPanel) comp).getClientProperty("UNCATEGORIZED_HEADER")))
            {
                result.add((JPanel) comp);
                return;
            }
            if (comp instanceof Container) findUncategorizedHeaderRecursive((Container) comp, result);
        }
    }
}
