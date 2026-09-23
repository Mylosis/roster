package com.mylosis.roster.ui.components;

import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Icon registry for the plugin. Icons are drawn programmatically rather than
 * loaded from image resources, so they scale to any requested size. Results
 * are cached. EDT only.
 */
public final class Icons
{
    private Icons() {} // Prevent instantiation

    private static final Map<String, ImageIcon> ICON_CACHE = new HashMap<>();

    // Icon names
    public static final String DELETE = "delete";
    public static final String CLIPBOARD = "clipboard";
    public static final String USER = "user";
    public static final String GRID = "grid";
    public static final String LIST = "list";

    /**
     * Gets an icon by name at the default size. Unknown names get a generic placeholder.
     */
    public static ImageIcon get(String name)
    {
        return get(name, Theme.ICON_SIZE);
    }

    /**
     * Gets an icon by name with a specific size.
     */
    public static ImageIcon get(String name, int size)
    {
        return ICON_CACHE.computeIfAbsent(name + "_" + size, k -> new ImageIcon(drawIcon(name, size)));
    }

    private static BufferedImage drawIcon(String name, int size)
    {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (name)
        {
            case DELETE:
                drawDeleteIcon(g, size);
                break;
            case USER:
                drawUserIcon(g, size);
                break;
            case CLIPBOARD:
                drawClipboardIcon(g, size);
                break;
            case GRID:
                drawGridIcon(g, size);
                break;
            case LIST:
                drawListIcon(g, size);
                break;
            default:
                drawDefaultIcon(g, size);
                break;
        }

        g.dispose();
        return img;
    }

    private static void drawDeleteIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.BUTTON_DANGER);
        g.setStroke(new BasicStroke(Math.max(2, size / 6f)));
        int margin = size / 4;
        g.drawLine(margin, margin, size - margin, size - margin);
        g.drawLine(size - margin, margin, margin, size - margin);
    }

    private static void drawUserIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int headRadius = size / 4;
        g.fillOval(size / 2 - headRadius, size / 6, headRadius * 2, headRadius * 2);
        g.fillArc(size / 6, size / 2, size * 2 / 3, size * 2 / 3, 0, 180);
    }

    private static void drawClipboardIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int clipMargin = size / 5;
        int clipWidth = size - clipMargin * 2;
        int clipHeight = (int) (size * 0.75);
        g.fillRoundRect(clipMargin, size / 4, clipWidth, clipHeight, 3, 3);
        g.setColor(Theme.BACKGROUND);
        int clipTopWidth = clipWidth / 2;
        g.fillRoundRect(size / 2 - clipTopWidth / 2, size / 6, clipTopWidth, size / 5, 2, 2);
        g.setColor(Theme.TEXT_SECONDARY);
        g.drawRoundRect(size / 2 - clipTopWidth / 2, size / 6, clipTopWidth, size / 5, 2, 2);
    }

    private static void drawGridIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int gs = (size - 6) / 2;
        int gap = 2;
        int off = 2;
        g.fillRect(off, off, gs, gs);
        g.fillRect(off + gs + gap, off, gs, gs);
        g.fillRect(off, off + gs + gap, gs, gs);
        g.fillRect(off + gs + gap, off + gs + gap, gs, gs);
    }

    private static void drawListIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        g.setStroke(new BasicStroke(Math.max(2, size / 6f)));
        int lm = size / 5;
        g.drawLine(lm, size / 4, size - lm, size / 4);
        g.drawLine(lm, size / 2, size - lm, size / 2);
        g.drawLine(lm, size * 3 / 4, size - lm, size * 3 / 4);
    }

    private static void drawDefaultIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        g.fillOval(2, 2, size - 4, size - 4);
    }
}
