package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.model.ProfileGroup;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Mouse handler for a category header: a short click toggles collapse,
 * moving past {@link #DRAG_THRESHOLD} starts a category reorder drag.
 */
public class CategoryDragListener extends MouseAdapter
{
    private static final int DRAG_THRESHOLD = 10;

    private final DragDropManager dragManager;
    private final ProfileGroup category;
    private final Runnable onClick;

    private Point pressPoint = null;
    private boolean dragStarted = false;

    public CategoryDragListener(DragDropManager dragManager, ProfileGroup category, Runnable onClick)
    {
        this.dragManager = dragManager;
        this.category = category;
        this.onClick = onClick;
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON1)
        {
            pressPoint = e.getLocationOnScreen();
            dragStarted = false;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (e.getButton() != MouseEvent.BUTTON1)
        {
            return;
        }
        if (dragStarted)
        {
            dragManager.endDrag(e.getLocationOnScreen());
        }
        else if (pressPoint != null && !movedPastThreshold(e.getLocationOnScreen()))
        {
            onClick.run();
        }
        pressPoint = null;
        dragStarted = false;
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (pressPoint == null) return;

        Point current = e.getLocationOnScreen();
        if (!dragStarted && movedPastThreshold(current))
        {
            dragStarted = true;
            dragManager.startCategoryDrag(category, e.getComponent(), pressPoint);
        }
        if (dragStarted)
        {
            dragManager.updateDrag(current);
        }
    }

    private boolean movedPastThreshold(Point current)
    {
        return Math.abs(current.x - pressPoint.x) > DRAG_THRESHOLD
            || Math.abs(current.y - pressPoint.y) > DRAG_THRESHOLD;
    }
}
