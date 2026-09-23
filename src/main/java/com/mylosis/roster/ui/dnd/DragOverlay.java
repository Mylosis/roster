package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.ui.components.Theme;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Overlay that shows drag feedback: a ghost of the dragged item following the
 * pointer, the highlighted drop zone, and the insert position indicator.
 */
public class DragOverlay extends JComponent
{
    private static final int MAX_GHOST_WIDTH = 250;
    private static final int MAX_GHOST_HEIGHT = 80;
    private static final int ARROW_SIZE = 5;

    // Strokes, colors, and the ghost composite are constant; allocate once
    // rather than on every frame of a ~60Hz drag.
    private static final BasicStroke STROKE_GHOST_BORDER = new BasicStroke(2);
    private static final BasicStroke STROKE_DROP_DASHED = new BasicStroke(
        2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0);
    private static final BasicStroke STROKE_INDICATOR = new BasicStroke(2);
    private static final Color GHOST_SHADOW = new Color(0, 0, 0, 30);
    private static final Color HIGHLIGHT_FILL = new Color(Theme.BUTTON_PRIMARY.getRed(),
        Theme.BUTTON_PRIMARY.getGreen(), Theme.BUTTON_PRIMARY.getBlue(), 40);
    private static final AlphaComposite GHOST_COMPOSITE =
        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f);

    private final DragDropManager dragManager;

    /** Pointer position in this component's parent coordinates; null when unknown. */
    private Point mouseLocation = null;
    private BufferedImage dragImage = null;

    DragOverlay(DragDropManager dragManager)
    {
        this.dragManager = dragManager;
        setOpaque(false);
    }

    void setMouseLocation(Point screenPoint)
    {
        Point origin = parentOrigin();
        mouseLocation = screenPoint != null && origin != null
            ? new Point(screenPoint.x - origin.x, screenPoint.y - origin.y)
            : null;
        repaint();
    }

    /** Snapshots {@code source} (scaled down to fit) as the drag ghost. */
    void setDragImage(Component source)
    {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0)
        {
            dragImage = null;
            return;
        }
        double scale = Math.min(1.0, Math.min(
            (double) MAX_GHOST_WIDTH / source.getWidth(), (double) MAX_GHOST_HEIGHT / source.getHeight()));
        int width = Math.max(1, (int) (source.getWidth() * scale));
        int height = Math.max(1, (int) (source.getHeight() * scale));
        dragImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = dragImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.scale(scale, scale);
        source.paint(g2d);
        g2d.dispose();
    }

    void clearDragImage()
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
        Point origin = parentOrigin();
        if (origin == null)
        {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        DropTarget target = dragManager.getCurrentDropTarget();
        if (target != null)
        {
            drawDropTarget(g2d, target, origin);
        }
        if (mouseLocation != null && dragImage != null)
        {
            drawGhost(g2d);
        }
        g2d.dispose();
    }

    private void drawGhost(Graphics2D g2d)
    {
        int width = dragImage.getWidth();
        int height = dragImage.getHeight();
        int x = mouseLocation.x - width / 2;
        int y = mouseLocation.y - height / 2;

        g2d.setColor(GHOST_SHADOW);
        g2d.fillRoundRect(x + 4, y + 4, width, height, 8, 8);

        g2d.setComposite(GHOST_COMPOSITE);
        g2d.drawImage(dragImage, x, y, null);

        g2d.setComposite(AlphaComposite.SrcOver);
        g2d.setColor(Theme.BUTTON_PRIMARY);
        g2d.setStroke(STROKE_GHOST_BORDER);
        g2d.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
    }

    private static void drawDropTarget(Graphics2D g2d, DropTarget target, Point origin)
    {
        Rectangle zone = target.getBounds();
        int x = zone.x - origin.x;
        int y = zone.y - origin.y;

        g2d.setColor(HIGHLIGHT_FILL);
        g2d.fillRoundRect(x, y, zone.width, zone.height, 6, 6);
        g2d.setColor(Theme.BUTTON_PRIMARY);
        g2d.setStroke(STROKE_DROP_DASHED);
        g2d.drawRoundRect(x + 1, y + 1, zone.width - 2, zone.height - 2, 6, 6);

        Rectangle line = target.getIndicator();
        if (line != null)
        {
            drawIndicator(g2d, line.x - origin.x, line.y - origin.y, line.width, line.height);
        }
    }

    /** A line with inward-pointing arrowheads at both ends; horizontal when {@code h == 0}. */
    private static void drawIndicator(Graphics2D g2d, int x, int y, int w, int h)
    {
        g2d.setStroke(STROKE_INDICATOR);
        g2d.setColor(Theme.BUTTON_PRIMARY);
        g2d.drawLine(x, y, x + w, y + h);
        int a = ARROW_SIZE;
        if (h == 0)
        {
            g2d.fillPolygon(new int[]{x, x + a, x}, new int[]{y - a, y, y + a}, 3);
            g2d.fillPolygon(new int[]{x + w, x + w - a, x + w}, new int[]{y - a, y, y + a}, 3);
        }
        else
        {
            g2d.fillPolygon(new int[]{x - a, x, x + a}, new int[]{y, y + a, y}, 3);
            g2d.fillPolygon(new int[]{x - a, x, x + a}, new int[]{y + h, y + h - a, y + h}, 3);
        }
    }

    /** Screen position of this overlay's parent; null if it isn't showing (panel closed mid-drag). */
    private Point parentOrigin()
    {
        if (getParent() == null)
        {
            return null;
        }
        try
        {
            return getParent().getLocationOnScreen();
        }
        catch (IllegalComponentStateException e)
        {
            return null;
        }
    }
}
