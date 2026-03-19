package com.mylosis.roster.ui;

import com.mylosis.roster.ui.components.SearchBar;
import com.mylosis.roster.ui.components.Theme;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Builds the header section of the Roster panel.
 * Contains title, version badge, search bar, and action buttons.
 */
public class PanelHeaderBuilder
{
    @Getter
    private SearchBar searchBar;

    /**
     * Create the full header panel with title, search, and action buttons.
     */
    public JPanel build(Consumer<String> onSearchChanged, Runnable onAddAccount, Runnable onAddCategory)
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(Theme.BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACING_LG, 0));

        header.add(createTitleRow());
        header.add(Box.createVerticalStrut(Theme.SPACING_MD));

        searchBar = createSearchBar(onSearchChanged);
        header.add(searchBar);
        header.add(Box.createVerticalStrut(Theme.SPACING_MD));

        header.add(createActionButtonsRow(onAddAccount, onAddCategory));

        return header;
    }

    private JPanel createTitleRow()
    {
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(Theme.BACKGROUND);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("Roster");
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setFont(Theme.fontBold(titleLabel.getFont(), Theme.FONT_SIZE_TITLE));
        titleRow.add(titleLabel, BorderLayout.WEST);

        JLabel versionLabel = new JLabel("v" + Theme.VERSION);
        versionLabel.setForeground(Theme.TEXT_MUTED);
        versionLabel.setFont(Theme.fontRegular(versionLabel.getFont(), Theme.FONT_SIZE_TINY));
        titleRow.add(versionLabel, BorderLayout.EAST);

        return titleRow;
    }

    private SearchBar createSearchBar(Consumer<String> onSearchChanged)
    {
        SearchBar bar = new SearchBar();
        bar.setPlaceholder("Filter...");
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setOnSearchChanged(onSearchChanged::accept);
        return bar;
    }

    private JPanel createActionButtonsRow(Runnable onAddAccount, Runnable onAddCategory)
    {
        JPanel buttonRow = new JPanel(new GridBagLayout());
        buttonRow.setBackground(Theme.BACKGROUND);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT + 4));
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0.75;
        gbc.insets = new Insets(0, 0, 0, Theme.SPACING_XS);
        buttonRow.add(createActionButton("+ Account", "Add a new account", onAddAccount), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.25;
        gbc.insets = new Insets(0, 0, 0, 0);
        buttonRow.add(createActionButton("+ Category", "Add category", onAddCategory), gbc);

        return buttonRow;
    }

    private JButton createActionButton(String text, String tooltip, Runnable onClick)
    {
        JButton button = new JButton(text);
        button.setBackground(Theme.BUTTON_SECONDARY);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(Theme.fontRegular(button.getFont(), Theme.FONT_SIZE_BODY));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(tooltip);
        button.addActionListener(e -> onClick.run());
        button.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e)
            {
                button.setBackground(Theme.BUTTON_SECONDARY_HOVER);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e)
            {
                button.setBackground(Theme.BUTTON_SECONDARY);
            }
        });
        return button;
    }
}
