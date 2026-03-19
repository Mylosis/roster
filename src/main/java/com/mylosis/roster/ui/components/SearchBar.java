package com.mylosis.roster.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * A styled search bar component with live filtering support.
 */
public class SearchBar extends JPanel
{
    private final JTextField textField;
    private final JPanel clearButton;
    private Consumer<String> onSearchChanged;
    private boolean isFocused = false;
    private String placeholderText = "";

    public SearchBar()
    {
        setLayout(new BorderLayout(Theme.SPACING_SM, 0));
        setBackground(Theme.BACKGROUND_DARKER);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.CARD_BORDER, 1),
            new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_SM)
        ));

        // Search icon
        JPanel iconPanel = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEXT_MUTED);

                // Draw search icon (magnifying glass)
                int size = 12;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawOval(x, y, size - 4, size - 4);
                g2.drawLine(x + size - 5, y + size - 5, x + size - 1, y + size - 1);
                g2.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(20, 20));
        add(iconPanel, BorderLayout.WEST);

        // Text field with placeholder painting
        textField = new JTextField()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocused && !placeholderText.isEmpty())
                {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(Theme.TEXT_MUTED);
                    g2.setFont(getFont());
                    int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
                    g2.drawString(placeholderText, getInsets().left, y);
                    g2.dispose();
                }
            }
        };
        textField.setBorder(null);
        textField.setBackground(Theme.BACKGROUND_DARKER);
        textField.setForeground(Theme.TEXT_PRIMARY);
        textField.setCaretColor(Theme.TEXT_PRIMARY);
        textField.setOpaque(false);

        textField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                notifySearchChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                notifySearchChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                notifySearchChanged();
            }
        });

        textField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusGained(FocusEvent e)
            {
                isFocused = true;
                updateBorder();
                textField.repaint();
            }

            @Override
            public void focusLost(FocusEvent e)
            {
                isFocused = false;
                updateBorder();
                textField.repaint();
            }
        });

        add(textField, BorderLayout.CENTER);

        // Clear button
        clearButton = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                if (textField.getText().isEmpty())
                {
                    return;
                }

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEXT_MUTED);

                // Draw refresh/circular arrow icon
                int size = 12;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                g2.setStroke(new java.awt.BasicStroke(1.5f));

                // Draw arc (270 degrees, leaving gap for arrow)
                g2.drawArc(x, y, size, size, 45, 270);

                // Draw arrowhead at the end of arc
                int arrowSize = 3;
                int arrowX = x + size - 1;
                int arrowY = y + size / 2 - 2;
                g2.drawLine(arrowX, arrowY, arrowX + arrowSize, arrowY - arrowSize);
                g2.drawLine(arrowX, arrowY, arrowX + arrowSize, arrowY + arrowSize);

                g2.dispose();
            }
        };
        clearButton.setOpaque(false);
        clearButton.setPreferredSize(new Dimension(20, 20));
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                clear();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                clearButton.setBackground(Theme.BACKGROUND_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                clearButton.setBackground(null);
            }
        });
        add(clearButton, BorderLayout.EAST);

        setPreferredSize(new Dimension(0, 32));
    }

    private void updateBorder()
    {
        Color borderColor = isFocused ? Theme.ACCENT_BLUE : Theme.CARD_BORDER;
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_SM)
        ));
        repaint();
    }

    private void notifySearchChanged()
    {
        clearButton.repaint();
        if (onSearchChanged != null)
        {
            onSearchChanged.accept(textField.getText().toLowerCase().trim());
        }
    }

    public void setOnSearchChanged(Consumer<String> callback)
    {
        this.onSearchChanged = callback;
    }

    public String getText()
    {
        return textField.getText();
    }

    public void setText(String text)
    {
        textField.setText(text);
    }

    public void clear()
    {
        textField.setText("");
        textField.requestFocus();
    }

    public void setPlaceholder(String placeholder)
    {
        this.placeholderText = placeholder != null ? placeholder : "";
        textField.setToolTipText(placeholder);
        textField.repaint();
    }
}
