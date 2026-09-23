package com.mylosis.roster.ui.components;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A rounded icon button with hover and pressed tints, used in the panel footer.
 */
public class IconButton extends JButton
{
    private boolean hovered = false;
    private boolean pressed = false;

    public IconButton(String iconName, String tooltip)
    {
        setIcon(Icons.get(iconName));
        setToolTipText(tooltip);

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setForeground(Theme.TEXT_PRIMARY);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
        setPreferredSize(new Dimension(Theme.BUTTON_HEIGHT + 8, Theme.BUTTON_HEIGHT));

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                hovered = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                pressed = false;
                repaint();
            }
        });
    }

    public void updateIcon(String iconName)
    {
        setIcon(Icons.get(iconName));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color background = pressed ? Theme.CARD_SELECTED
            : hovered ? Theme.BUTTON_SECONDARY_HOVER
            : Theme.BUTTON_SECONDARY;
        g2.setColor(background);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.BORDER_RADIUS_SM, Theme.BORDER_RADIUS_SM);

        g2.dispose();
        super.paintComponent(g);
    }
}
