package com.mylosis.roster.ui.components;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * Factories for the plugin's recurring control styles, so every button,
 * field, and popup behaves and looks the same.
 */
public final class Controls
{
    /** Width of the label column in form rows. */
    private static final int FIELD_LABEL_WIDTH = 55;

    private Controls() {}

    /** A borderless button with a solid background that changes on hover. */
    public static JButton flatButton(String text, Color background, Color hoverBackground)
    {
        JButton button = baseButton(text, Theme.TEXT_PRIMARY);
        button.setBackground(background);
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { button.setBackground(hoverBackground); }

            @Override
            public void mouseExited(MouseEvent e) { button.setBackground(background); }
        });
        return button;
    }

    /**
     * A borderless button that shows its container's background (hover and
     * selection tints included) and only fills in while the pointer is on it.
     */
    public static JButton ghostButton(String text, Color foreground, Color hoverBackground, Color hoverForeground)
    {
        JButton button = baseButton(text, foreground);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBackground(hoverBackground);
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                button.setContentAreaFilled(true);
                button.setForeground(hoverForeground);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setContentAreaFilled(false);
                button.setForeground(foreground);
            }
        });
        return button;
    }

    private static JButton baseButton(String text, Color foreground)
    {
        JButton button = new JButton(text);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * Opens a popup under {@code button} on click. The menu is built on first
     * use: most menus are never opened, and building them for every card on
     * every rebuild was measurable.
     */
    public static void attachLazyMenu(AbstractButton button, Supplier<JPopupMenu> menuFactory)
    {
        JPopupMenu[] menu = new JPopupMenu[1];
        button.addActionListener(e -> {
            if (menu[0] == null)
            {
                menu[0] = menuFactory.get();
            }
            menu[0].show(button, 0, button.getHeight());
        });
    }

    /** A dark single-line text field matching the panel theme. */
    public static JTextField textField(String tooltip)
    {
        JTextField field = new JTextField();
        field.setBackground(Theme.BACKGROUND_DARKER);
        field.setForeground(Theme.TEXT_PRIMARY);
        field.setCaretColor(Theme.TEXT_PRIMARY);
        field.setBorder(fieldBorder(Theme.CARD_BORDER));
        field.setFont(Theme.fontRegular(field.getFont(), Theme.FONT_SIZE_BODY));
        field.setToolTipText(tooltip);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT));
        return field;
    }

    /** Outlines a field in red (invalid) or restores its normal border. */
    public static void setInvalid(JTextField field, boolean invalid)
    {
        field.setBorder(fieldBorder(invalid ? Theme.BUTTON_DANGER : Theme.CARD_BORDER));
    }

    private static Border fieldBorder(Color outline)
    {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(outline),
            new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
    }

    /** A form row: a fixed-width caption on the left, the field filling the rest. */
    public static JPanel fieldRow(String labelText, JComponent field)
    {
        JPanel row = new JPanel(new BorderLayout(Theme.SPACING_XS, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(labelText);
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setFont(Theme.fontRegular(label.getFont(), Theme.FONT_SIZE_SMALL));
        label.setPreferredSize(new Dimension(FIELD_LABEL_WIDTH, Theme.BUTTON_HEIGHT));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    /** A Cancel / Save pair, laid out side by side like every form in the panel. */
    public static JPanel formButtons(Runnable onCancel, Runnable onSave)
    {
        JPanel panel = new JPanel(new GridLayout(1, 2, Theme.SPACING_XS, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT_SM));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton cancel = flatButton("Cancel", Theme.BUTTON_SECONDARY, Theme.BUTTON_SECONDARY_HOVER);
        cancel.setFont(Theme.fontRegular(cancel.getFont(), Theme.FONT_SIZE_HEADING));
        cancel.addActionListener(e -> onCancel.run());
        panel.add(cancel);

        JButton save = flatButton("Save", Theme.BUTTON_PRIMARY, Theme.BUTTON_PRIMARY_HOVER);
        save.setFont(Theme.fontRegular(save.getFont(), Theme.FONT_SIZE_HEADING));
        save.addActionListener(e -> onSave.run());
        panel.add(save);
        return panel;
    }
}
