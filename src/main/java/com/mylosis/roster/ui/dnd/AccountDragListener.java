package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.AccountCardPanel;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Mouse listener for dragging profiles between categories.
 * Attaches to a AccountCardPanel and all its children to ensure drag works from anywhere on the card.
 * Also handles click detection to trigger profile selection and edit.
 */
public class AccountDragListener extends MouseAdapter
{
    private final DragDropManager dragManager;
    private final Account profile;
    private final AccountCardPanel card;

    private Point pressPoint = null;
    private long pressTime = 0;
    private boolean dragStarted = false;
    private static long lastClickTime = 0;
    private static Account lastClickAccount = null;

    private static final int DRAG_THRESHOLD = 10;
    private static final int CLICK_TIME_THRESHOLD = 300;
    private static final int DOUBLE_CLICK_THRESHOLD = 400;

    public AccountDragListener(DragDropManager dragManager, Account profile, AccountCardPanel card)
    {
        this.dragManager = dragManager;
        this.profile = profile;
        this.card = card;
    }

    /**
     * Attach this listener to the card and all its children recursively.
     */
    public void attachToComponent(Component component)
    {
        component.addMouseListener(this);
        component.addMouseMotionListener(this);

        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                // Don't attach to buttons (let them handle their own clicks)
                if (!(child instanceof JButton))
                {
                    attachToComponent(child);
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON1 && !card.isExpanded())
        {
            pressPoint = e.getLocationOnScreen();
            pressTime = System.currentTimeMillis();
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
        else if (pressPoint != null)
        {
            // This was a click, not a drag - handle it
            long duration = System.currentTimeMillis() - pressTime;
            Point current = e.getLocationOnScreen();
            int dx = Math.abs(current.x - pressPoint.x);
            int dy = Math.abs(current.y - pressPoint.y);

            if (duration < CLICK_TIME_THRESHOLD && dx < DRAG_THRESHOLD && dy < DRAG_THRESHOLD)
            {
                // Check for double-click
                long now = System.currentTimeMillis();
                if (lastClickAccount == profile && (now - lastClickTime) < DOUBLE_CLICK_THRESHOLD)
                {
                    // Double-click: expand card for inline editing
                    SwingUtilities.invokeLater(() -> card.expand());
                    lastClickTime = 0;
                    lastClickAccount = null;
                }
                else
                {
                    // Single-click: select profile (only if card not expanded)
                    lastClickTime = now;
                    lastClickAccount = profile;
                    if (!card.isExpanded())
                    {
                        dragManager.getPlugin().selectAccount(profile);
                    }
                }
            }
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
                dragManager.startProfileDrag(profile, card, pressPoint);
            }
        }

        if (dragStarted)
        {
            dragManager.updateDrag(current);
        }
    }
}
