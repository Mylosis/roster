package com.mylosis.roster.ui.components;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Icon registry for the plugin. Provides access to icon resources with caching.
 */
@Slf4j
public final class Icons
{
    private Icons() {} // Prevent instantiation

    private static final String ICON_PATH = "/com/mylosis/roster/";
    private static final Map<String, ImageIcon> iconCache = new HashMap<>();

    // Icon names
    public static final String ADD = "add";
    public static final String EDIT = "edit";
    public static final String DELETE = "delete";
    public static final String EXPORT = "export";
    public static final String IMPORT = "import";
    public static final String CLIPBOARD = "clipboard";
    public static final String TRANSFER = "transfer";
    public static final String GROUP = "group";
    public static final String CATEGORY = "category";
    public static final String EXPAND = "expand";
    public static final String COLLAPSE = "collapse";
    public static final String MENU = "menu";
    public static final String SEARCH = "search";
    public static final String USER = "user";
    public static final String LOGIN = "login";
    public static final String GRID = "grid";
    public static final String LIST = "list";

    /**
     * Gets an icon by name. Returns a fallback if the icon is not found.
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
        String cacheKey = name + "_" + size;
        if (iconCache.containsKey(cacheKey))
        {
            return iconCache.get(cacheKey);
        }

        ImageIcon icon = loadIcon(name, size);
        iconCache.put(cacheKey, icon);
        return icon;
    }

    private static ImageIcon loadIcon(String name, int size)
    {
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(Icons.class, ICON_PATH + name + ".png");
            if (img.getWidth() != size || img.getHeight() != size)
            {
                img = ImageUtil.resizeImage(img, size, size);
            }
            return new ImageIcon(img);
        }
        catch (Exception e)
        {
            log.debug("Icon not found: {}, using fallback", name);
            return new ImageIcon(createFallbackIcon(name, size));
        }
    }

    /**
     * Creates a simple fallback icon when the actual icon is not found.
     * Delegates to specific draw methods per icon type.
     */
    private static BufferedImage createFallbackIcon(String name, int size)
    {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (name)
        {
            case ADD:
                drawAddIcon(g, size);
                break;
            case DELETE:
                drawDeleteIcon(g, size);
                break;
            case EXPAND:
                drawExpandIcon(g, size);
                break;
            case COLLAPSE:
                drawCollapseIcon(g, size);
                break;
            case MENU:
                drawMenuIcon(g, size);
                break;
            case USER:
                drawUserIcon(g, size);
                break;
            case GROUP:
            case CATEGORY:
                drawFolderIcon(g, size);
                break;
            case LOGIN:
                drawLoginIcon(g, size);
                break;
            case EDIT:
                drawEditIcon(g, size);
                break;
            case EXPORT:
            case IMPORT:
                drawExportImportIcon(g, size, EXPORT.equals(name));
                break;
            case CLIPBOARD:
                drawClipboardIcon(g, size);
                break;
            case TRANSFER:
                drawTransferIcon(g, size);
                break;
            case SEARCH:
                drawSearchIcon(g, size);
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

    private static void drawAddIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.BUTTON_PRIMARY);
        int center = size / 2;
        int barLength = (int) (size * 0.6);
        int barThickness = Math.max(2, size / 8);
        g.fillRect(center - barLength / 2, center - barThickness / 2, barLength, barThickness);
        g.fillRect(center - barThickness / 2, center - barLength / 2, barThickness, barLength);
    }

    private static void drawDeleteIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.BUTTON_DANGER);
        g.setStroke(new java.awt.BasicStroke(Math.max(2, size / 6f)));
        int margin = size / 4;
        g.drawLine(margin, margin, size - margin, size - margin);
        g.drawLine(size - margin, margin, margin, size - margin);
    }

    private static void drawExpandIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int[] xPoints = {size / 3, size * 2 / 3, size / 3};
        int[] yPoints = {size / 4, size / 2, size * 3 / 4};
        g.fillPolygon(xPoints, yPoints, 3);
    }

    private static void drawCollapseIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int[] xPoints = {size / 4, size / 2, size * 3 / 4};
        int[] yPoints = {size / 3, size * 2 / 3, size / 3};
        g.fillPolygon(xPoints, yPoints, 3);
    }

    private static void drawMenuIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int dotSize = Math.max(3, size / 5);
        int dotX = size / 2 - dotSize / 2;
        g.fillOval(dotX, size / 4 - dotSize / 2, dotSize, dotSize);
        g.fillOval(dotX, size / 2 - dotSize / 2, dotSize, dotSize);
        g.fillOval(dotX, size * 3 / 4 - dotSize / 2, dotSize, dotSize);
    }

    private static void drawUserIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int headRadius = size / 4;
        g.fillOval(size / 2 - headRadius, size / 6, headRadius * 2, headRadius * 2);
        g.fillArc(size / 6, size / 2, size * 2 / 3, size * 2 / 3, 0, 180);
    }

    private static void drawFolderIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        int folderMargin = size / 6;
        int folderWidth = size - folderMargin * 2;
        int folderHeight = (int) (folderWidth * 0.7);
        int tabWidth = folderWidth / 3;
        int tabHeight = folderHeight / 5;
        g.fillRoundRect(folderMargin, folderMargin + tabHeight, tabWidth, tabHeight, 2, 2);
        g.fillRoundRect(folderMargin, folderMargin + tabHeight * 2, folderWidth, folderHeight, 3, 3);
    }

    private static void drawLoginIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.BUTTON_PRIMARY);
        g.setStroke(new java.awt.BasicStroke(Math.max(2, size / 8f)));
        int arrowY = size / 2;
        g.drawLine(size / 4, arrowY, size * 3 / 4, arrowY);
        g.drawLine(size / 2, size / 4, size * 3 / 4, arrowY);
        g.drawLine(size / 2, size * 3 / 4, size * 3 / 4, arrowY);
    }

    private static void drawEditIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        g.setStroke(new java.awt.BasicStroke(Math.max(1, size / 8f)));
        g.drawLine(size / 4, size * 3 / 4, size * 3 / 4, size / 4);
        g.drawLine(size / 4 - 2, size * 3 / 4 + 2, size / 4 + 2, size * 3 / 4 - 2);
    }

    private static void drawExportImportIcon(Graphics2D g, int size, boolean isExport)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        g.setStroke(new java.awt.BasicStroke(Math.max(2, size / 8f)));
        int arrowStart = isExport ? size * 2 / 3 : size / 3;
        int arrowEnd = isExport ? size / 3 : size * 2 / 3;
        g.drawLine(size / 2, arrowStart, size / 2, arrowEnd);
        int tipY = arrowEnd;
        int tipDir = isExport ? 1 : -1;
        g.drawLine(size / 3, tipY + tipDir * size / 6, size / 2, tipY);
        g.drawLine(size * 2 / 3, tipY + tipDir * size / 6, size / 2, tipY);
    }

    private static void drawClipboardIcon(Graphics2D g, int size)
    {
        Color iconColor = Theme.TEXT_SECONDARY;
        g.setColor(iconColor);
        int clipMargin = size / 5;
        int clipWidth = size - clipMargin * 2;
        int clipHeight = (int) (size * 0.75);
        g.fillRoundRect(clipMargin, size / 4, clipWidth, clipHeight, 3, 3);
        g.setColor(Theme.BACKGROUND);
        int clipTopWidth = clipWidth / 2;
        g.fillRoundRect(size / 2 - clipTopWidth / 2, size / 6, clipTopWidth, size / 5, 2, 2);
        g.setColor(iconColor);
        g.drawRoundRect(size / 2 - clipTopWidth / 2, size / 6, clipTopWidth, size / 5, 2, 2);
    }

    private static void drawTransferIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        g.setStroke(new java.awt.BasicStroke(Math.max(2, size / 8f)));
        int leftX = size / 3;
        g.drawLine(leftX, size * 2 / 3, leftX, size / 4);
        g.drawLine(leftX - size / 8, size / 4 + size / 6, leftX, size / 4);
        g.drawLine(leftX + size / 8, size / 4 + size / 6, leftX, size / 4);
        int rightX = size * 2 / 3;
        g.drawLine(rightX, size / 3, rightX, size * 3 / 4);
        g.drawLine(rightX - size / 8, size * 3 / 4 - size / 6, rightX, size * 3 / 4);
        g.drawLine(rightX + size / 8, size * 3 / 4 - size / 6, rightX, size * 3 / 4);
    }

    private static void drawSearchIcon(Graphics2D g, int size)
    {
        g.setColor(Theme.TEXT_SECONDARY);
        g.setStroke(new java.awt.BasicStroke(Math.max(2, size / 8f)));
        int circleSize = size / 2;
        g.drawOval(size / 6, size / 6, circleSize, circleSize);
        g.drawLine(size / 6 + circleSize - 2, size / 6 + circleSize - 2, size * 5 / 6, size * 5 / 6);
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
        g.setStroke(new java.awt.BasicStroke(Math.max(2, size / 6f)));
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

    /**
     * Clears the icon cache. Useful when reloading resources.
     */
    public static void clearCache()
    {
        iconCache.clear();
    }
}
