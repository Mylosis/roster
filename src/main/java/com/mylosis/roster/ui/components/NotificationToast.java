package com.mylosis.roster.ui.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A temporary in-panel notification toast that fades in, then fades out and
 * removes itself.
 */
public class NotificationToast extends JPanel
{
    public enum Type { SUCCESS, ERROR, INFO, WARNING }

    private static final int MAX_WIDTH = 220;
    private static final int ICON_SIZE = 12;
    private static final int FRAME_MS = 16;
    private static final float FADE_STEP = 0.1f;

    private final Timer dismissTimer;
    private final Timer fadeTimer;
    private float opacity = 0f;
    private boolean fadingIn = true;

    public NotificationToast(String message, Type type, int durationMs)
    {
        setLayout(new BorderLayout());
        setOpaque(false);

        Color background = backgroundColor(type);
        JPanel inner = new JPanel(new BorderLayout(Theme.SPACING_SM, 0))
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.BORDER_RADIUS_MD, Theme.BORDER_RADIUS_MD);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(Theme.SPACING_MD, Theme.SPACING_MD, Theme.SPACING_MD, Theme.SPACING_MD));

        JLabel iconLabel = new JLabel(dotIcon(iconColor(type)));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // HTML for word wrapping; the message is escaped because it can carry
        // user-controlled text (account names).
        String htmlMessage = "<html><div style='width: " + (MAX_WIDTH - 50) + "px; text-align: center;'>"
            + SafeText.escapeHtml(message) + "</div></html>";
        JLabel messageLabel = new JLabel(htmlMessage)
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        messageLabel.setForeground(Theme.TEXT_PRIMARY);
        messageLabel.setFont(Theme.fontRegular(messageLabel.getFont(), Theme.FONT_SIZE_SMALL));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(iconLabel);
        contentPanel.add(Box.createVerticalStrut(Theme.SPACING_XS));
        contentPanel.add(messageLabel);

        inner.add(contentPanel, BorderLayout.CENTER);
        add(inner, BorderLayout.CENTER);

        fadeTimer = new Timer(FRAME_MS, e -> stepFade());
        dismissTimer = new Timer(durationMs, e -> {
            fadingIn = false;
            fadeTimer.start();
        });
        dismissTimer.setRepeats(false);

        fadeTimer.start();
        dismissTimer.start();
    }

    private void stepFade()
    {
        if (fadingIn)
        {
            opacity = Math.min(1f, opacity + FADE_STEP);
            if (opacity >= 1f)
            {
                fadingIn = false;
                fadeTimer.stop();
            }
        }
        else
        {
            opacity = Math.max(0f, opacity - FADE_STEP);
            if (opacity <= 0f)
            {
                fadeTimer.stop();
                Container parent = getParent();
                if (parent != null)
                {
                    parent.remove(this);
                    parent.revalidate();
                    parent.repaint();
                }
            }
        }
        repaint();
    }

    /**
     * Hard-stops both timers. Called when the toast is replaced by a newer
     * one; otherwise the orphaned fade timer keeps repainting a parentless
     * component until it finishes.
     */
    public void stopTimers()
    {
        dismissTimer.stop();
        fadeTimer.stop();
    }

    private static Color backgroundColor(Type type)
    {
        switch (type)
        {
            case SUCCESS:
                return new Color(40, 120, 60, 230);
            case WARNING:
                return new Color(160, 130, 30, 230);
            case ERROR:
                return new Color(150, 50, 50, 230);
            case INFO:
            default:
                return new Color(60, 60, 60, 230);
        }
    }

    private static Color iconColor(Type type)
    {
        switch (type)
        {
            case SUCCESS:
                return new Color(100, 220, 120);
            case WARNING:
                return new Color(255, 210, 70);
            case ERROR:
                return new Color(255, 100, 100);
            case INFO:
            default:
                return Theme.TEXT_SECONDARY;
        }
    }

    /** A small colored dot that fades with the toast. */
    private Icon dotIcon(Color color)
    {
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                g2.setColor(color);
                g2.fillOval(x, y, ICON_SIZE, ICON_SIZE);
                g2.dispose();
            }

            @Override
            public int getIconWidth() { return ICON_SIZE; }

            @Override
            public int getIconHeight() { return ICON_SIZE; }
        };
    }
}
