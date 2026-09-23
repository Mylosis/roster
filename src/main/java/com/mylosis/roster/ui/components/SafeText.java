package com.mylosis.roster.ui.components;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicHTML;

/**
 * Keeps user-controlled text (aliases, logins, notes, category names, all
 * importable from clipboard JSON) from activating Swing's HTML renderer. A
 * value starting with {@code <html>} would otherwise break rendering, and an
 * embedded {@code <img src>} fires an outbound HTTP request when it paints.
 */
public final class SafeText
{
    private SafeText() {}

    /** Disables HTML rendering on a label or other text component; returns it for chaining. */
    public static <T extends JComponent> T plain(T component)
    {
        component.putClientProperty(BasicHTML.propertyKey + ".disable", Boolean.TRUE);
        return component;
    }

    /**
     * Text for places where {@link #plain} can't be applied (tooltips, combo
     * box and dialog items). Ordinary strings pass through unchanged; strings
     * Swing would treat as HTML are escaped so they display literally.
     */
    public static String htmlSafe(String text)
    {
        if (text == null || !BasicHTML.isHTMLString(text))
        {
            return text;
        }
        return "<html>" + escapeHtml(text) + "</html>";
    }

    /**
     * For embedding user text in a JOptionPane message. The pane splits string
     * messages on newlines into separate labels, and a line starting with
     * {@code <html>} would render as HTML, so the text is flattened to one line.
     */
    public static String singleLine(String text)
    {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
    }

    /** Escapes HTML special characters, turning newlines into line breaks. */
    public static String escapeHtml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("\n", "<br>");
    }
}
