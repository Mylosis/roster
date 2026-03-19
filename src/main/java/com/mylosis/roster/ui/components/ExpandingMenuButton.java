package com.mylosis.roster.ui.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A button that expands into a dropdown menu panel when clicked.
 * Similar to the "+" button pattern that shows options like "Add new setup.."
 */
public class ExpandingMenuButton extends JPanel
{
    private final IconButton triggerButton;
    private final JPanel menuPanel;
    private final List<MenuItem> menuItems = new ArrayList<>();
    private boolean expanded = false;
    private JWindow popupWindow;
    private AWTEventListener outsideClickListener;

    public ExpandingMenuButton(String iconName, String tooltip)
    {
        setLayout(new BorderLayout());
        setOpaque(false);

        // The trigger button
        triggerButton = new IconButton(iconName, tooltip);
        triggerButton.addActionListener(e -> toggleMenu());
        add(triggerButton, BorderLayout.CENTER);

        // The menu panel (shown in popup)
        menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBackground(Theme.CARD_BACKGROUND);
        menuPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.CARD_BORDER, 1),
            new EmptyBorder(Theme.SPACING_XS, 0, Theme.SPACING_XS, 0)
        ));

        // Initialize outside click listener (must be after triggerButton is set)
        outsideClickListener = createOutsideClickListener();
    }

    private AWTEventListener createOutsideClickListener()
    {
        return event -> {
            if (event instanceof MouseEvent)
            {
                MouseEvent me = (MouseEvent) event;
                if (me.getID() == MouseEvent.MOUSE_PRESSED)
                {
                    if (popupWindow != null && popupWindow.isVisible() && triggerButton.isShowing())
                    {
                        Point p = me.getLocationOnScreen();
                        Rectangle bounds = popupWindow.getBounds();
                        Rectangle buttonBounds = new Rectangle(
                            triggerButton.getLocationOnScreen(),
                            triggerButton.getSize()
                        );

                        if (!bounds.contains(p) && !buttonBounds.contains(p))
                        {
                            SwingUtilities.invokeLater(this::hideMenu);
                        }
                    }
                }
            }
        };
    }

    public void addMenuItem(String label, Runnable action)
    {
        MenuItem item = new MenuItem(label, action);
        menuItems.add(item);

        if (menuPanel.getComponentCount() > 0)
        {
            // Add subtle separator line between items
            JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
            sep.setForeground(Theme.CARD_BORDER);
            sep.setBackground(Theme.CARD_BACKGROUND);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            menuPanel.add(sep);
        }

        menuPanel.add(item);
    }

    private void toggleMenu()
    {
        if (expanded)
        {
            hideMenu();
        }
        else
        {
            showMenu();
        }
    }

    private void showMenu()
    {
        if (popupWindow != null)
        {
            popupWindow.dispose();
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        popupWindow = new JWindow(owner);
        popupWindow.setType(Window.Type.POPUP);
        popupWindow.add(menuPanel);
        popupWindow.pack();

        // Position above the button, aligned to the right edge
        Point buttonLocation = triggerButton.getLocationOnScreen();
        int x = buttonLocation.x + triggerButton.getWidth() - popupWindow.getWidth();
        int y = buttonLocation.y - popupWindow.getHeight() - 2;

        // If would go off screen top, show below instead
        if (y < 0)
        {
            y = buttonLocation.y + triggerButton.getHeight() + 2;
        }

        popupWindow.setLocation(x, y);
        popupWindow.setVisible(true);
        expanded = true;

        // Close when clicking outside
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void hideMenu()
    {
        if (popupWindow != null)
        {
            popupWindow.dispose();
            popupWindow = null;
        }
        expanded = false;
        Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
    }

    /**
     * Individual menu item with hover effect
     */
    private class MenuItem extends JPanel
    {
        private boolean hovered = false;

        MenuItem(String label, Runnable action)
        {
            setLayout(new BorderLayout());
            setBackground(Theme.CARD_BACKGROUND);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_MD));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            JLabel textLabel = new JLabel(label);
            textLabel.setForeground(Theme.TEXT_SECONDARY);
            textLabel.setFont(Theme.fontRegular(textLabel.getFont(), Theme.FONT_SIZE_SMALL));
            add(textLabel, BorderLayout.WEST);

            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hovered = true;
                    setBackground(Theme.CARD_HOVER);
                    textLabel.setForeground(Theme.TEXT_PRIMARY);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hovered = false;
                    setBackground(Theme.CARD_BACKGROUND);
                    textLabel.setForeground(Theme.TEXT_SECONDARY);
                }

                @Override
                public void mousePressed(MouseEvent e)
                {
                    hideMenu();
                    action.run();
                }
            });
        }
    }
}
