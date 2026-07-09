package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.RosterPanel;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;

/**
 * Manages drag and drop for profiles and categories.
 * Delegates drop target finding to DropTargetFinder and drop execution to DropExecutor.
 */
@Slf4j
public class DragDropManager
{
    private final RosterPlugin plugin;
    private final DragOverlay overlay;
    private final DropTargetFinder targetFinder;
    private final DropExecutor dropExecutor;

    // Drag state
    private boolean isDragging = false;
    private DragType dragType = null;
    private Account draggedAccount = null;
    private ProfileGroup draggedCategory = null;
    private Component dragSource = null;
    private Point dragStartPoint = null;
    private Point currentDragPoint = null;
    private DropTarget currentDropTarget = null;

    /**
     * ESC aborts an in-flight drag. A Toolkit-level listener (registered only for
     * the drag's duration, same pattern as ExpandingMenuButton's dismissal) rather
     * than a Swing key binding: during a drag, focus may sit on the game's
     * heavyweight Canvas, where WHEN_IN_FOCUSED_WINDOW bindings never fire.
     */
    private final AWTEventListener escapeListener = event -> {
        if (isDragging && event instanceof KeyEvent)
        {
            KeyEvent ke = (KeyEvent) event;
            if (ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                ke.consume();
                cancelDrag();
            }
        }
    };

    public enum DragType
    {
        PROFILE,
        CATEGORY
    }

    public static class DropTarget
    {
        public enum Type { CATEGORY_DROP, UNCATEGORIZED, CATEGORY_REORDER }

        public final Type type;
        public final String groupId;
        public final Rectangle bounds;
        public final int insertIndex;
        /** Exact screen-Y of the insert indicator line, measured from the real
         *  card bounds in the drag snapshot. -1 when unknown (empty category),
         *  in which case the overlay falls back to its geometric estimate. */
        public final int insertLineY;

        public DropTarget(Type type, String groupId, Rectangle bounds)
        {
            this(type, groupId, bounds, -1);
        }

        public DropTarget(Type type, String groupId, Rectangle bounds, int insertIndex)
        {
            this(type, groupId, bounds, insertIndex, -1);
        }

        public DropTarget(Type type, String groupId, Rectangle bounds, int insertIndex, int insertLineY)
        {
            this.type = type;
            this.groupId = groupId;
            this.bounds = bounds;
            this.insertIndex = insertIndex;
            this.insertLineY = insertLineY;
        }
    }

    public DragDropManager(RosterPlugin plugin, RosterPanel panel)
    {
        this.plugin = plugin;
        this.overlay = new DragOverlay(this);
        this.targetFinder = new DropTargetFinder(panel);
        this.dropExecutor = new DropExecutor(plugin, panel);
    }

    public DragOverlay getOverlay()
    {
        return overlay;
    }

    public RosterPlugin getPlugin()
    {
        return plugin;
    }

    public void startProfileDrag(Account profile, Component source, Point screenPoint)
    {
        if (isDragging) return;
        isDragging = true;
        dragType = DragType.PROFILE;
        draggedAccount = profile;
        dragSource = source;
        dragStartPoint = screenPoint;
        currentDragPoint = screenPoint;
        // Snapshot the panel structure once per drag so updateDrag() doesn't
        // re-walk the component tree on every mouse-moved event.
        targetFinder.primeSnapshot();
        overlay.setDragImage(source);
        overlay.setMouseLocation(screenPoint);
        overlay.setVisible(true);
        Toolkit.getDefaultToolkit().addAWTEventListener(escapeListener, AWTEvent.KEY_EVENT_MASK);
        log.debug("Started dragging account: {}", profile.getId());
    }

    public void startCategoryDrag(ProfileGroup category, Component source, Point screenPoint)
    {
        if (isDragging) return;
        isDragging = true;
        dragType = DragType.CATEGORY;
        draggedCategory = category;
        dragSource = source;
        dragStartPoint = screenPoint;
        currentDragPoint = screenPoint;
        targetFinder.primeSnapshot();
        overlay.setDragImage(source);
        overlay.setMouseLocation(screenPoint);
        overlay.setVisible(true);
        Toolkit.getDefaultToolkit().addAWTEventListener(escapeListener, AWTEvent.KEY_EVENT_MASK);
        log.debug("Started dragging category: {}", category.getName());
    }

    public void updateDrag(Point screenPoint)
    {
        if (!isDragging) return;
        currentDragPoint = screenPoint;
        currentDropTarget = findDropTarget(screenPoint);
        // setMouseLocation already repaints the overlay.
        overlay.setMouseLocation(screenPoint);
    }

    public void endDrag(Point screenPoint)
    {
        if (!isDragging) return;
        currentDragPoint = screenPoint;
        DropTarget target = findDropTarget(screenPoint);

        if (target != null)
        {
            performDrop(target);
        }

        resetDragState();
    }

    public void cancelDrag()
    {
        resetDragState();
    }

    public boolean isDragging()
    {
        return isDragging;
    }

    public DropTarget getCurrentDropTarget()
    {
        return currentDropTarget;
    }

    public Account getDraggedProfile()
    {
        return draggedAccount;
    }

    public ProfileGroup getDraggedCategory()
    {
        return draggedCategory;
    }

    private DropTarget findDropTarget(Point screenPoint)
    {
        if (dragType == DragType.PROFILE)
        {
            return targetFinder.findForProfile(screenPoint, draggedAccount);
        }
        else if (dragType == DragType.CATEGORY)
        {
            return targetFinder.findForCategory(screenPoint, draggedCategory);
        }
        return null;
    }

    private void performDrop(DropTarget target)
    {
        if (dragType == DragType.PROFILE && draggedAccount != null)
        {
            dropExecutor.executeProfileDrop(target, draggedAccount);
        }
        else if (dragType == DragType.CATEGORY && draggedCategory != null)
        {
            dropExecutor.executeCategoryDrop(target, draggedCategory);
        }
    }

    private void resetDragState()
    {
        // Removing a listener that was never added is a documented no-op, so
        // this is safe on every reset path (drop, cancel, ESC).
        Toolkit.getDefaultToolkit().removeAWTEventListener(escapeListener);
        isDragging = false;
        dragType = null;
        draggedAccount = null;
        draggedCategory = null;
        dragSource = null;
        dragStartPoint = null;
        currentDragPoint = null;
        currentDropTarget = null;
        targetFinder.clearSnapshot();
        overlay.clearDragImage();
        overlay.setVisible(false);
        overlay.repaint();
    }
}
