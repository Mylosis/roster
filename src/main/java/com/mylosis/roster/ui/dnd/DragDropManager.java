package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.RosterPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;

/**
 * Drives drag and drop of accounts and categories: tracks the drag, asks
 * {@link DropTargetFinder} where it would land, and hands the drop to
 * {@link DropExecutor}. EDT only.
 */
@Slf4j
public class DragDropManager
{
    private final RosterPanel panel;
    @Getter
    private final DragOverlay overlay;
    private final DropExecutor dropExecutor;

    // Drag state; exactly one of the dragged fields is set while dragging.
    private Account draggedAccount;
    private ProfileGroup draggedCategory;
    /** The list's drop zones, captured when the drag starts. */
    private ListLayout layout;
    private DropTarget currentDropTarget;

    /**
     * ESC aborts an in-flight drag. A Toolkit-level listener (registered only
     * for the drag's duration) rather than a Swing key binding: during a drag,
     * focus may sit on the game's heavyweight canvas, where
     * WHEN_IN_FOCUSED_WINDOW bindings never fire.
     */
    private final AWTEventListener escapeListener = event -> {
        if (isDragging() && event instanceof KeyEvent)
        {
            KeyEvent ke = (KeyEvent) event;
            if (ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                ke.consume();
                cancelDrag();
            }
        }
    };

    public DragDropManager(RosterPlugin plugin, RosterPanel panel)
    {
        this.panel = panel;
        this.overlay = new DragOverlay(this);
        this.dropExecutor = new DropExecutor(plugin, panel);
    }

    public void startAccountDrag(Account account, Component source, Point screenPoint)
    {
        if (isDragging()) return;
        draggedAccount = account;
        begin(source, screenPoint);
        log.debug("Started dragging account: {}", account.getId());
    }

    public void startCategoryDrag(ProfileGroup category, Component source, Point screenPoint)
    {
        if (isDragging()) return;
        draggedCategory = category;
        begin(source, screenPoint);
        log.debug("Started dragging category: {}", category.getId());
    }

    private void begin(Component source, Point screenPoint)
    {
        layout = panel.getListLayout();
        overlay.setDragImage(source);
        overlay.setMouseLocation(screenPoint);
        overlay.setVisible(true);
        Toolkit.getDefaultToolkit().addAWTEventListener(escapeListener, AWTEvent.KEY_EVENT_MASK);
    }

    public void updateDrag(Point screenPoint)
    {
        if (!isDragging()) return;
        currentDropTarget = findDropTarget(screenPoint);
        // setMouseLocation also repaints the overlay.
        overlay.setMouseLocation(screenPoint);
    }

    public void endDrag(Point screenPoint)
    {
        if (!isDragging()) return;
        DropTarget target = findDropTarget(screenPoint);
        Account account = draggedAccount;
        ProfileGroup category = draggedCategory;
        reset();

        if (target != null)
        {
            if (account != null)
            {
                dropExecutor.executeAccountDrop(target, account);
            }
            else
            {
                dropExecutor.executeCategoryDrop(target, category);
            }
        }
    }

    public void cancelDrag()
    {
        reset();
    }

    public boolean isDragging()
    {
        return draggedAccount != null || draggedCategory != null;
    }

    DropTarget getCurrentDropTarget()
    {
        return currentDropTarget;
    }

    private DropTarget findDropTarget(Point screenPoint)
    {
        if (layout == null)
        {
            return null;
        }
        return draggedAccount != null
            ? DropTargetFinder.findForAccount(layout, screenPoint)
            : DropTargetFinder.findForCategory(layout, screenPoint, draggedCategory.getId());
    }

    private void reset()
    {
        // Removing a listener that was never added is a documented no-op.
        Toolkit.getDefaultToolkit().removeAWTEventListener(escapeListener);
        draggedAccount = null;
        draggedCategory = null;
        layout = null;
        currentDropTarget = null;
        overlay.clearDragImage();
        overlay.setVisible(false);
    }
}
