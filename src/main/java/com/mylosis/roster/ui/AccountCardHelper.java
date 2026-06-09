package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Shared utility methods for profile card rendering.
 * Extracted from AccountCardPanel to keep file sizes manageable.
 */
public final class AccountCardHelper
{
    private AccountCardHelper() {} // Prevent instantiation

    /**
     * Creates a small green dot indicating the profile is currently logged in.
     */
    public static JPanel createOnlineDot()
    {
        int size = Theme.ONLINE_DOT_SIZE;
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ONLINE_INDICATOR);
                g2.fillOval(0, 0, size, size);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(size, size));
        dot.setMaximumSize(new Dimension(size, size));
        dot.setToolTipText("Currently logged in");
        return dot;
    }

    /**
     * Creates a JLabel that truncates text beyond maxChars with an ellipsis.
     */
    public static JLabel createTruncatingLabel(String text, Color color, Font font, int maxChars)
    {
        String display = text;
        if (text != null && text.length() > maxChars)
        {
            // Truncate at last space before limit to avoid cutting mid-word
            String trimmed = text.substring(0, maxChars);
            int lastSpace = trimmed.lastIndexOf(' ');
            display = (lastSpace > maxChars / 2) ? trimmed.substring(0, lastSpace) + "..." : trimmed + "...";
        }
        JLabel label = new JLabel(display);
        // User-controlled text (alias/login/notes — importable from clipboard
        // JSON) must never activate Swing's HTML renderer: a value starting
        // with <html> would break rendering, and an embedded <img src> fires
        // an outbound HTTP request when the label paints.
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setForeground(color);
        label.setFont(font);
        if (text != null && text.length() > maxChars) label.setToolTipText(safeTooltip(text));
        return label;
    }

    /**
     * Returns a tooltip-safe version of user-controlled text. Normal strings
     * pass through untouched (identical rendering); strings Swing would treat
     * as HTML are escaped so they display as literal text instead.
     */
    public static String safeTooltip(String text)
    {
        if (text == null || !javax.swing.plaf.basic.BasicHTML.isHTMLString(text))
        {
            return text;
        }
        return "<html>" + escapeHtml(text) + "</html>";
    }

    /**
     * Disables HTML interpretation on a label that displays user-controlled
     * text. Returns the label for chaining.
     */
    public static JLabel plainTextOnly(JLabel label)
    {
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    /**
     * Escapes HTML special characters for safe display in HTML-rendered labels.
     */
    public static String escapeHtml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("\n", "<br>");
    }

    /**
     * Revalidates and repaints the component's enclosing scroll pane — the
     * layout boundary for anything inside the plugin's card list. This used to
     * walk every ancestor up to (and including) the RuneLite window, repainting
     * the whole client per call.
     */
    public static void revalidateToWindow(Component component)
    {
        component.revalidate();
        Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, component);
        if (scrollPane != null)
        {
            scrollPane.revalidate();
            scrollPane.repaint();
        }
        else
        {
            component.repaint();
        }
    }

    /**
     * Formats a millisecond timestamp as a coarse relative-time string
     * (e.g. "just now", "5m ago", "3h ago", "2d ago", "4mo ago", "1y ago").
     * Returns null for null or future-dated input.
     */
    public static String formatTimeAgo(Long epochMillis)
    {
        if (epochMillis == null)
        {
            return null;
        }
        long diff = System.currentTimeMillis() - epochMillis;
        if (diff < 0)
        {
            return null;
        }

        long minute = 60_000L;
        long hour = 60 * minute;
        long day = 24 * hour;
        long month = 30 * day;
        long year = 365 * day;

        if (diff < 2 * minute) return "just now";
        if (diff < hour)       return (diff / minute) + "m ago";
        if (diff < day)        return (diff / hour)   + "h ago";
        if (diff < month)      return (diff / day)    + "d ago";
        if (diff < year)       return (diff / month)  + "mo ago";
        return (diff / year) + "y ago";
    }

    /**
     * Resolves the display name for a profile based on privacy settings.
     * Used by both AccountCardPanel (list view) and GridAccountBuilder (grid view).
     */
    public static String resolveDisplayName(Account profile, RosterConfig config)
    {
        boolean allHidden = config.hideAlias() && config.hideLogin();
        String displayName;
        if (allHidden)
        {
            displayName = "Account";
        }
        else if (config.hideAlias())
        {
            displayName = profile.getUsername();
        }
        else
        {
            displayName = profile.getDisplayName();
        }
        if (displayName == null) displayName = profile.getUsername();
        return displayName;
    }
}
