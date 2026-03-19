package com.mylosis.roster.ui.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * A temporary in-panel notification toast that auto-dismisses.
 */
public class NotificationToast extends JPanel
{
    public enum Type { SUCCESS, ERROR, INFO, WARNING }

    private final Timer dismissTimer;
    private final JLabel messageLabel;
    private float opacity = 0f;
    private boolean fadingIn = true;
    private final Timer fadeTimer;

    private static final int MAX_WIDTH = 220;

    public NotificationToast(String message, Type type, int durationMs)
    {
        setLayout(new BorderLayout());
        setOpaque(false);

        // Inner panel with background
        JPanel inner = new JPanel(new BorderLayout(Theme.SPACING_SM, 0))
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                g2.setColor(getBackgroundColor(type));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.BORDER_RADIUS_MD, Theme.BORDER_RADIUS_MD);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(Theme.SPACING_MD, Theme.SPACING_MD, Theme.SPACING_MD, Theme.SPACING_MD));

        // Icon at top
        JLabel iconLabel = new JLabel(getIcon(type));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Message with HTML wrapping
        String htmlMessage = "<html><div style='width: " + (MAX_WIDTH - 50) + "px; text-align: center;'>" +
            escapeHtml(message) + "</div></html>";
        messageLabel = new JLabel(htmlMessage)
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

        // Stack icon on top, message below
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

        // Fade animation
        fadeTimer = new Timer(16, e -> {
            if (fadingIn)
            {
                opacity = Math.min(1f, opacity + 0.1f);
                if (opacity >= 1f)
                {
                    fadingIn = false;
                    ((Timer) e.getSource()).stop();
                }
            }
            else
            {
                opacity = Math.max(0f, opacity - 0.1f);
                if (opacity <= 0f)
                {
                    ((Timer) e.getSource()).stop();
                    Container parent = getParent();
                    if (parent != null)
                    {
                        parent.remove(NotificationToast.this);
                        parent.revalidate();
                        parent.repaint();
                    }
                }
            }
            repaint();
        });

        // Auto-dismiss timer
        dismissTimer = new Timer(durationMs, e -> {
            fadingIn = false;
            fadeTimer.start();
        });
        dismissTimer.setRepeats(false);

        // Start fade in
        fadeTimer.start();
        dismissTimer.start();
    }

    private Color getBackgroundColor(Type type)
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

    private Icon getIcon(Type type)
    {
        // Simple colored circle as icon
        int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

                Color color;
                switch (type)
                {
                    case SUCCESS:
                        color = new Color(100, 220, 120);
                        break;
                    case WARNING:
                        color = new Color(255, 210, 70);
                        break;
                    case ERROR:
                        color = new Color(255, 100, 100);
                        break;
                    case INFO:
                    default:
                        color = Theme.TEXT_SECONDARY;
                        break;
                }

                g2.setColor(color);
                g2.fillOval(x, y, size, size);
                g2.dispose();
            }

            @Override
            public int getIconWidth() { return size; }

            @Override
            public int getIconHeight() { return size; }
        };
    }

    public void dismiss()
    {
        dismissTimer.stop();
        fadingIn = false;
        fadeTimer.start();
    }

    private static String escapeHtml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
