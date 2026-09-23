package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.ui.AccountCardPanel;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The single mouse handler for a card's clicks and drags, attached to the card
 * and its non-interactive children so a drag can start anywhere on it.
 * A click selects the account, a double-click opens the editor, and moving
 * past {@link #DRAG_THRESHOLD} starts a drag.
 */
public class AccountDragListener extends MouseAdapter
{
    private static final int DRAG_THRESHOLD = 10;
    private static final int DOUBLE_CLICK_MS = 400;

    // Double-click state is static and keyed by account id: the first click's
    // selection refresh can replace the card before the second click arrives.
    private static long lastClickTime = 0;
    private static String lastClickAccountId = null;

    private final DragDropManager dragManager;
    private final AccountCardPanel card;

    private Point pressPoint = null;
    private boolean dragStarted = false;

    public AccountDragListener(DragDropManager dragManager, AccountCardPanel card)
    {
        this.dragManager = dragManager;
        this.card = card;
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON1 && !card.isExpanded())
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
            onClick();
        }
        pressPoint = null;
        dragStarted = false;
    }

    private void onClick()
    {
        String id = card.getAccount().getId();
        long now = System.currentTimeMillis();
        if (id.equals(lastClickAccountId) && now - lastClickTime < DOUBLE_CLICK_MS)
        {
            lastClickTime = 0;
            lastClickAccountId = null;
            SwingUtilities.invokeLater(card::expand);
        }
        else
        {
            lastClickTime = now;
            lastClickAccountId = id;
            card.select();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (pressPoint == null) return;

        Point current = e.getLocationOnScreen();
        if (!dragStarted && movedPastThreshold(current))
        {
            dragStarted = true;
            dragManager.startAccountDrag(card.getAccount(), card, pressPoint);
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
