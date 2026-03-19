package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.model.ProfileGroup;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Mouse listener for dragging categories to reorder them.
 */
public class CategoryDragListener extends MouseAdapter
{
    private final DragDropManager dragManager;
    private final ProfileGroup category;

    private Point pressPoint = null;
    private boolean dragStarted = false;

    private static final int DRAG_THRESHOLD = 10;

    public CategoryDragListener(DragDropManager dragManager, ProfileGroup category)
    {
        this.dragManager = dragManager;
        this.category = category;
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
        if (dragStarted)
        {
            dragManager.endDrag(e.getLocationOnScreen());
        }
        pressPoint = null;
        dragStarted = false;
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (pressPoint == null) return;

        Point current = e.getLocationOnScreen();

        if (!dragStarted)
        {
            int dx = Math.abs(current.x - pressPoint.x);
            int dy = Math.abs(current.y - pressPoint.y);

            if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)
            {
                dragStarted = true;
                dragManager.startCategoryDrag(category, e.getComponent(), pressPoint);
            }
        }

        if (dragStarted)
        {
            dragManager.updateDrag(current);
        }
    }
}
