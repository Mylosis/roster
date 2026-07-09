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

    // Strokes are immutable — allocate once instead of every paintComponent.
    // At ~60Hz during a drag, the previous code created six BasicStroke objects
    // per frame for tiny ornaments that never change.
    private static final BasicStroke STROKE_GHOST_BORDER = new BasicStroke(2);
    private static final BasicStroke STROKE_DROP_DASHED = new BasicStroke(
        2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0);
    private static final BasicStroke STROKE_CATEGORY_REORDER = new BasicStroke(3);
    private static final BasicStroke STROKE_INSERT_LINE = new BasicStroke(2);
    private static final BasicStroke STROKE_FALLBACK_ARROW = new BasicStroke(
        2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // Same story for the colors and the ghost composite — all constant, all
    // previously allocated per frame. AWT only caches AlphaComposite instances
    // for alpha == 1.0, so the 0.85f ghost composite is worth holding onto.
    private static final Color GHOST_SHADOW = new Color(0, 0, 0, 30);
    private static final Color HIGHLIGHT_FILL = new Color(Theme.BUTTON_PRIMARY.getRed(),
        Theme.BUTTON_PRIMARY.getGreen(), Theme.BUTTON_PRIMARY.getBlue(), 40);
    private static final Color INDICATOR_FILL = new Color(Theme.BUTTON_PRIMARY.getRed(),
        Theme.BUTTON_PRIMARY.getGreen(), Theme.BUTTON_PRIMARY.getBlue(), 100);
    private static final AlphaComposite GHOST_COMPOSITE =
        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f);

    public DragOverlay(DragDropManager dragManager)
    {
        this.dragManager = dragManager;
        setOpaque(false);
    }

    public void setMouseLocation(Point screenPoint)
    {
        if (screenPoint != null && getParent() != null)
        {
            try
            {
                Point parentLocation = getParent().getLocationOnScreen();
                this.mouseLocation = new Point(
                    screenPoint.x - parentLocation.x,
                    screenPoint.y - parentLocation.y
                );
            }
            catch (IllegalComponentStateException e)
            {
                // Parent no longer showing (panel closed mid-drag)
                this.mouseLocation = null;
            }
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
            g2d.setColor(GHOST_SHADOW);
            g2d.fillRoundRect(x + 4, y + 4, dragImageWidth, dragImageHeight, 8, 8);

            // Draw semi-transparent image
            g2d.setComposite(GHOST_COMPOSITE);
            g2d.drawImage(dragImage, x, y, null);

            // Draw border
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setColor(Theme.BUTTON_PRIMARY);
            g2d.setStroke(STROKE_GHOST_BORDER);
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
            g2d.setColor(HIGHLIGHT_FILL);
            g2d.fillRoundRect(x, y, width, height, 6, 6);

            // Draw highlight border
            g2d.setColor(Theme.BUTTON_PRIMARY);
            g2d.setStroke(STROKE_DROP_DASHED);
            g2d.drawRoundRect(x + 1, y + 1, width - 2, height - 2, 6, 6);

            // Draw "drop here" indicator for category reordering or profile positioning
            if (target.type == DragDropManager.DropTarget.Type.CATEGORY_REORDER)
            {
                g2d.setStroke(STROKE_CATEGORY_REORDER);
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
                g2d.setStroke(STROKE_INSERT_LINE);
                g2d.setColor(Theme.BUTTON_PRIMARY);

                // Draw a small "insert here" line
                int lineX = x + 20;
                int lineWidth = width - 40;

                // Exact position measured from real card bounds by DropTargetFinder;
                // the geometric estimate remains only as the empty-category fallback.
                int insertY = target.insertLineY >= 0
                    ? target.insertLineY - parentScreen.y
                    : y + 45 + (target.insertIndex * 75);
                insertY = Math.max(y + 5, Math.min(insertY, y + height - 5));

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

        g2d.setColor(INDICATOR_FILL);
        g2d.fillOval(x, y, size, size);

        g2d.setColor(Theme.BUTTON_PRIMARY);
        g2d.setStroke(STROKE_GHOST_BORDER);
        g2d.drawOval(x, y, size, size);

        // Draw move icon
        g2d.setColor(Theme.TEXT_PRIMARY);
        int cx = mouseLocation.x;
        int cy = mouseLocation.y;
        int arrowLen = 8;

        // Four arrows pointing outward
        g2d.setStroke(STROKE_FALLBACK_ARROW);
        g2d.drawLine(cx, cy - arrowLen, cx, cy + arrowLen);
        g2d.drawLine(cx - arrowLen, cy, cx + arrowLen, cy);
    }
}
