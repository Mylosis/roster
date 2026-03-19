package com.mylosis.roster.ui.components;

import javax.swing.JButton;
import javax.swing.ImageIcon;
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
 * A styled icon button with hover effects.
 */
public class IconButton extends JButton
{
    private Color normalBackground;
    private Color hoverBackground;
    private Color activeBackground;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private int cornerRadius = Theme.BORDER_RADIUS_SM;

    public IconButton(String iconName)
    {
        this(iconName, null);
    }

    public IconButton(String iconName, String tooltip)
    {
        super();
        setIcon(Icons.get(iconName));
        if (tooltip != null)
        {
            setToolTipText(tooltip);
        }

        initDefaults();
        setupListeners();
    }

    public IconButton(ImageIcon icon, String tooltip)
    {
        super(icon);
        if (tooltip != null)
        {
            setToolTipText(tooltip);
        }

        initDefaults();
        setupListeners();
    }

    private void initDefaults()
    {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);

        normalBackground = Theme.BUTTON_SECONDARY;
        hoverBackground = Theme.BUTTON_SECONDARY_HOVER;
        activeBackground = Theme.CARD_SELECTED;

        setForeground(Theme.TEXT_PRIMARY);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));

        setPreferredSize(new Dimension(Theme.BUTTON_HEIGHT + 8, Theme.BUTTON_HEIGHT));
    }

    private void setupListeners()
    {
        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                isHovered = false;
                isPressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                isPressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                isPressed = false;
                repaint();
            }
        });
    }

    public void updateIcon(String iconName)
    {
        setIcon(Icons.get(iconName));
        repaint();
    }

    public void setColors(Color normal, Color hover, Color active)
    {
        this.normalBackground = normal;
        this.hoverBackground = hover;
        this.activeBackground = active;
        repaint();
    }

    public void setPrimaryStyle()
    {
        setColors(Theme.BUTTON_PRIMARY, Theme.BUTTON_PRIMARY_HOVER, Theme.BUTTON_PRIMARY);
    }

    public void setDangerStyle()
    {
        setColors(Theme.BUTTON_DANGER, Theme.BUTTON_DANGER_HOVER, Theme.BUTTON_DANGER);
    }

    public void setCornerRadius(int radius)
    {
        this.cornerRadius = radius;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bgColor;
        if (isPressed)
        {
            bgColor = activeBackground;
        }
        else if (isHovered)
        {
            bgColor = hoverBackground;
        }
        else
        {
            bgColor = normalBackground;
        }

        g2.setColor(bgColor);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

        g2.dispose();
        super.paintComponent(g);
    }
}
