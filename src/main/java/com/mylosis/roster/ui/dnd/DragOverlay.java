package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Overlay component that shows drag feedback - a ghost of the dragged item
 * following the mouse cursor, and highlighting of valid drop targets.
 */
public class DragOverlay extends JComponent
{
    private final DragDropManager dragManager;

    private Point mouseLocation = null;
    private Image dragImage = null;
    private int dragImageWidth = 0;
    private int dragImageHeight = 0;

    public DragOverlay(DragDropManager dragManager)
    {
        this.dragManager = dragManager;
        setOpaque(false);
    }

    public void setMouseLocation(Point screenPoint)
    {
        if (screenPoint != null && getParent() != null)
        {
            Point parentLocation = getParent().getLocationOnScreen();
            this.mouseLocation = new Point(
                screenPoint.x - parentLocation.x,
                screenPoint.y - parentLocation.y
            );
        }
        else
        {
            this.mouseLocation = null;
        }
        repaint();
    }

    public void setDragImage(Component source)
    {
        if (source != null && source.getWidth() > 0 && source.getHeight() > 0)
        {
            dragImageWidth = Math.min(source.getWidth(), 250);
            dragImageHeight = Math.min(source.getHeight(), 80);

            // Create image from component
            dragImage = new java.awt.image.BufferedImage(
                dragImageWidth, dragImageHeight,
                java.awt.image.BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2d = (Graphics2D) dragImage.getGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Scale if needed
            double scaleX = (double) dragImageWidth / source.getWidth();
            double scaleY = (double) dragImageHeight / source.getHeight();
            double scale = Math.min(scaleX, scaleY);

            g2d.scale(scale, scale);
            source.paint(g2d);
            g2d.dispose();
        }
        else
        {
            dragImage = null;
        }
    }

    public void clearDragImage()
    {
        dragImage = null;
        mouseLocation = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (!dragManager.isDragging())
        {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw drop target highlight
        DragDropManager.DropTarget target = dragManager.getCurrentDropTarget();
        if (target != null)
        {
            drawDropTargetHighlight(g2d, target);
        }

        // Draw drag ghost at mouse position
        if (mouseLocation != null && dragImage != null)
        {
            int x = mouseLocation.x - dragImageWidth / 2;
            int y = mouseLocation.y - dragImageHeight / 2;

            // Draw shadow
            g2d.setColor(new Color(0, 0, 0, 30));
            g2d.fillRoundRect(x + 4, y + 4, dragImageWidth, dragImageHeight, 8, 8);

            // Draw semi-transparent image
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            g2d.drawImage(dragImage, x, y, null);

            // Draw border
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2d.setColor(Theme.BUTTON_PRIMARY);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRoundRect(x, y, dragImageWidth - 1, dragImageHeight - 1, 8, 8);
        }
        else if (mouseLocation != null)
        {
            // Fallback: draw a simple indicator if no image
            drawDragIndicator(g2d);
        }

        g2d.dispose();
    }

    private void drawDropTargetHighlight(Graphics2D g2d, DragDropManager.DropTarget target)
    {
        try
        {
            // Convert target bounds from screen to local coordinates
            Rectangle bounds = target.bounds;
            Point parentScreen = getParent().getLocationOnScreen();

            int x = bounds.x - parentScreen.x;
            int y = bounds.y - parentScreen.y;
            int width = bounds.width;
            int height = bounds.height;

            // Draw highlight background
            g2d.setColor(new Color(Theme.BUTTON_PRIMARY.getRed(),
                Theme.BUTTON_PRIMARY.getGreen(),
                Theme.BUTTON_PRIMARY.getBlue(), 40));
            g2d.fillRoundRect(x, y, width, height, 6, 6);

            // Draw highlight border
            g2d.setColor(Theme.BUTTON_PRIMARY);
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{6, 4}, 0));
            g2d.drawRoundRect(x + 1, y + 1, width - 2, height - 2, 6, 6);

            // Draw "drop here" indicator for category reordering or profile positioning
            if (target.type == DragDropManager.DropTarget.Type.CATEGORY_REORDER)
            {
                g2d.setStroke(new BasicStroke(3));
                g2d.setColor(Theme.BUTTON_PRIMARY);

                // Draw line at insert position
                int lineY = (target.insertIndex <= 0) ? y : y + height;
                g2d.drawLine(x + 10, lineY, x + width - 10, lineY);

                // Draw arrow heads
                int arrowSize = 6;
                g2d.fillPolygon(
                    new int[]{x + 10, x + 10 + arrowSize, x + 10},
                    new int[]{lineY - arrowSize, lineY, lineY + arrowSize},
                    3
                );
                g2d.fillPolygon(
                    new int[]{x + width - 10, x + width - 10 - arrowSize, x + width - 10},
                    new int[]{lineY - arrowSize, lineY, lineY + arrowSize},
                    3
                );
            }
            else if (target.type == DragDropManager.DropTarget.Type.CATEGORY_DROP && target.insertIndex >= 0)
            {
                // Draw insert position indicator for profile drops
                g2d.setStroke(new BasicStroke(2));
                g2d.setColor(Theme.BUTTON_PRIMARY);

                // Draw a small "insert here" line
                int lineX = x + 20;
                int lineWidth = width - 40;

                // Calculate approximate Y position for insert indicator
                // This is an approximation - the actual card positions would need to be passed
                int insertY = y + 45 + (target.insertIndex * 75); // header height + card heights
                insertY = Math.min(insertY, y + height - 5);

                g2d.drawLine(lineX, insertY, lineX + lineWidth, insertY);

                // Draw small triangles
                int arrowSize = 5;
                g2d.fillPolygon(
                    new int[]{lineX, lineX + arrowSize, lineX},
                    new int[]{insertY - arrowSize, insertY, insertY + arrowSize},
                    3
                );
                g2d.fillPolygon(
                    new int[]{lineX + lineWidth, lineX + lineWidth - arrowSize, lineX + lineWidth},
                    new int[]{insertY - arrowSize, insertY, insertY + arrowSize},
                    3
                );
            }
        }
        catch (Exception e)
        {
            // Component might not be showing
        }
    }

    private void drawDragIndicator(Graphics2D g2d)
    {
        // Simple fallback indicator
        int size = 40;
        int x = mouseLocation.x - size / 2;
        int y = mouseLocation.y - size / 2;

        g2d.setColor(new Color(Theme.BUTTON_PRIMARY.getRed(),
            Theme.BUTTON_PRIMARY.getGreen(),
            Theme.BUTTON_PRIMARY.getBlue(), 100));
        g2d.fillOval(x, y, size, size);

        g2d.setColor(Theme.BUTTON_PRIMARY);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(x, y, size, size);

        // Draw move icon
        g2d.setColor(Theme.TEXT_PRIMARY);
        int cx = mouseLocation.x;
        int cy = mouseLocation.y;
        int arrowLen = 8;

        // Four arrows pointing outward
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(cx, cy - arrowLen, cx, cy + arrowLen);
        g2d.drawLine(cx - arrowLen, cy, cx + arrowLen, cy);
    }
}
