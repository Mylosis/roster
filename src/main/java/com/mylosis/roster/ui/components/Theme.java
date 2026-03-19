package com.mylosis.roster.ui.components;

import net.runelite.client.ui.ColorScheme;

import java.awt.Color;
import java.awt.Font;

/**
 * Centralized theme constants for consistent UI styling throughout the plugin.
 */
public final class Theme
{
    private Theme() {} // Prevent instantiation

    /** Plugin version string displayed in the UI header. */
    public static final String VERSION = "1.0";

    // Base colors
    public static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
    public static final Color BACKGROUND_DARKER = ColorScheme.DARKER_GRAY_COLOR;
    public static final Color BACKGROUND_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;

    // Card colors
    public static final Color CARD_BACKGROUND = new Color(45, 45, 45);
    public static final Color CARD_HOVER = new Color(55, 55, 55);
    public static final Color CARD_SELECTED = new Color(65, 65, 65);
    public static final Color CARD_BORDER = ColorScheme.MEDIUM_GRAY_COLOR;

    // Group header colors
    public static final Color GROUP_HEADER = new Color(35, 35, 35);
    public static final Color GROUP_HEADER_HOVER = new Color(45, 45, 45);

    // Form colors (for inline editing)
    public static final Color FORM_BACKGROUND = new Color(38, 38, 38);

    // Text colors
    public static final Color TEXT_PRIMARY = Color.WHITE;
    public static final Color TEXT_SECONDARY = ColorScheme.LIGHT_GRAY_COLOR;
    public static final Color TEXT_MUTED = ColorScheme.MEDIUM_GRAY_COLOR;

    // Button colors
    public static final Color BUTTON_PRIMARY = new Color(50, 120, 65);
    public static final Color BUTTON_PRIMARY_HOVER = new Color(65, 145, 80);
    public static final Color BUTTON_SECONDARY = ColorScheme.DARKER_GRAY_COLOR;
    public static final Color BUTTON_SECONDARY_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
    public static final Color BUTTON_DANGER = new Color(220, 53, 69);
    public static final Color BUTTON_DANGER_HOVER = new Color(240, 73, 89);

    // Accent colors
    public static final Color ACCENT_ORANGE = ColorScheme.BRAND_ORANGE;
    public static final Color ACCENT_BLUE = new Color(66, 135, 245);
    public static final Color ACCENT_YELLOW = new Color(220, 180, 80);

    // Online indicator
    public static final Color ONLINE_INDICATOR = new Color(60, 180, 75);
    public static final int ONLINE_DOT_SIZE = 8;

    // Category color palette (preset options for user selection) — names paired with colors
    public static final String[] CATEGORY_COLOR_NAMES = {
        "Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink", "Teal"
    };
    public static final Color[] CATEGORY_COLORS = {
        new Color(220, 53, 69),   // Red
        new Color(255, 140, 0),   // Orange
        new Color(220, 180, 80),  // Yellow
        new Color(60, 180, 75),   // Green
        new Color(66, 135, 245),  // Blue
        new Color(130, 80, 200),  // Purple
        new Color(200, 80, 160),  // Pink
        new Color(0, 180, 180),   // Teal
    };

    // Default colors auto-assigned to new categories by creation order
    public static final Color[] DEFAULT_CATEGORY_COLORS = {
        new Color(66, 135, 245),  // 1. Blue
        new Color(130, 80, 200),  // 2. Purple
        new Color(60, 180, 75),   // 3. Green
        new Color(220, 180, 80),  // 4. Yellow
        new Color(140, 160, 180), // 5. Slate
        new Color(255, 140, 0),   // 6. Orange
        new Color(200, 80, 160),  // 7. Pink
        new Color(0, 180, 180),   // 8. Teal
        new Color(220, 53, 69),   // 9. Red
        new Color(180, 130, 80),  // 10. Bronze
    };

    public static final int CATEGORY_BORDER_WIDTH = 3;

    // Spacing constants
    public static final int SPACING_XS = 3;
    public static final int SPACING_SM = 6;
    public static final int SPACING_MD = 10;
    public static final int SPACING_LG = 14;
    public static final int SPACING_XL = 20;

    // Border radius (for reference, actual rounding done in painting)
    public static final int BORDER_RADIUS_SM = 4;
    public static final int BORDER_RADIUS_MD = 6;
    public static final int BORDER_RADIUS_LG = 8;

    // Component sizes
    public static final int BUTTON_HEIGHT = 32;
    public static final int BUTTON_HEIGHT_SM = 26;
    public static final int ICON_SIZE = 18;
    public static final int ICON_SIZE_SM = 14;
    public static final int CARD_HEIGHT = 80;
    public static final int SCROLLBAR_WIDTH = 18;
    public static final int MENU_BUTTON_SIZE = 32;

    // Font sizes
    public static final float FONT_SIZE_TITLE = 20f;
    public static final float FONT_SIZE_HEADING = 16f;
    public static final float FONT_SIZE_BODY = 14f;
    public static final float FONT_SIZE_SMALL = 13f;
    public static final float FONT_SIZE_TINY = 12f;

    /**
     * Parses a hex color string (e.g. "#FF5733") into a Color. Returns null if input is null/empty.
     */
    public static Color parseColor(String hex)
    {
        if (hex == null || hex.isEmpty())
        {
            return null;
        }
        try
        {
            return Color.decode(hex);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Converts a Color to a hex string (e.g. "#FF5733").
     */
    public static String colorToHex(Color color)
    {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Creates a font with the specified style and size.
     */
    public static Font font(Font base, int style, float size)
    {
        return base.deriveFont(style, size);
    }

    /**
     * Creates a bold font.
     */
    public static Font fontBold(Font base, float size)
    {
        return font(base, Font.BOLD, size);
    }

    /**
     * Creates a regular font.
     */
    public static Font fontRegular(Font base, float size)
    {
        return font(base, Font.PLAIN, size);
    }
}
