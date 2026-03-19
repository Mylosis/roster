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
        label.setForeground(color);
        label.setFont(font);
        if (text != null && text.length() > maxChars) label.setToolTipText(text);
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
     * Forces revalidation up the entire component tree to the window ancestor.
     */
    public static void revalidateToWindow(Component component)
    {
        Container ancestor = component.getParent();
        while (ancestor != null)
        {
            ancestor.revalidate();
            ancestor.repaint();
            ancestor = ancestor.getParent();
        }
        SwingUtilities.invokeLater(() -> {
            Window window = SwingUtilities.getWindowAncestor(component);
            if (window != null)
            {
                window.revalidate();
                window.repaint();
            }
        });
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
