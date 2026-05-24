package com.mylosis.roster.ui;

import com.mylosis.roster.model.SortKey;
import com.mylosis.roster.ui.components.SearchBar;
import com.mylosis.roster.ui.components.Theme;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds the header section of the Roster panel.
 * Contains title, version badge, search bar, and action buttons
 * (+ Account, + Category, and the v1.1 sort selector).
 */
public class PanelHeaderBuilder
{
    @Getter
    private SearchBar searchBar;

    /**
     * Create the full header panel.
     *
     * @param onSearchChanged callback fired when the user types in the search bar
     * @param onAddAccount    callback for the "+ Account" button
     * @param onAddCategory   callback for the "+ Category" button
     * @param currentSortKey  supplier for the live SortKey (read each time the menu opens
     *                        so the check mark reflects the active value)
     * @param onSortChanged   callback when the user picks a new SortKey
     */
    public JPanel build(Consumer<String> onSearchChanged,
                        Runnable onAddAccount,
                        Runnable onAddCategory,
                        Supplier<SortKey> currentSortKey,
                        Consumer<SortKey> onSortChanged)
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

        header.add(createActionButtonsRow(onAddAccount, onAddCategory, currentSortKey, onSortChanged));

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

    private JPanel createActionButtonsRow(Runnable onAddAccount, Runnable onAddCategory,
                                          Supplier<SortKey> currentSortKey,
                                          Consumer<SortKey> onSortChanged)
    {
        JPanel buttonRow = new JPanel(new GridBagLayout());
        buttonRow.setBackground(Theme.BACKGROUND);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT + 4));
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0.55;
        gbc.insets = new Insets(0, 0, 0, Theme.SPACING_XS);
        buttonRow.add(createActionButton("+ Account", "Add a new account", onAddAccount), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.30;
        gbc.insets = new Insets(0, 0, 0, Theme.SPACING_XS);
        buttonRow.add(createActionButton("+ Category", "Add category", onAddCategory), gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.15;
        gbc.insets = new Insets(0, 0, 0, 0);
        buttonRow.add(createSortButton(currentSortKey, onSortChanged), gbc);

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

    /**
     * Compact icon button that opens a popup menu listing every {@link SortKey},
     * with the currently-active key checkmarked. Picking an option fires the
     * supplied {@code onSortChanged} callback (which writes config + rebuilds).
     */
    private JButton createSortButton(Supplier<SortKey> currentSortKey,
                                     Consumer<SortKey> onSortChanged)
    {
        JButton button = new JButton("⇵"); // ⇅
        button.setBackground(Theme.BUTTON_SECONDARY);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(Theme.fontBold(button.getFont(), Theme.FONT_SIZE_BODY));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("Sort accounts");
        button.addActionListener(e -> showSortMenu(button, currentSortKey, onSortChanged));
        button.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) { button.setBackground(Theme.BUTTON_SECONDARY_HOVER); }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) { button.setBackground(Theme.BUTTON_SECONDARY); }
        });
        return button;
    }

    private void showSortMenu(JButton anchor, Supplier<SortKey> currentSortKey,
                              Consumer<SortKey> onSortChanged)
    {
        SortKey active = currentSortKey.get();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BACKGROUND_DARKER);
        menu.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));

        for (SortKey key : SortKey.values())
        {
            JMenuItem item = new JMenuItem(key.getLabel());
            item.setBackground(Theme.BACKGROUND_DARKER);
            item.setForeground(key == active ? Theme.ACCENT_ORANGE : Theme.TEXT_PRIMARY);
            item.setFont(key == active
                ? Theme.fontBold(item.getFont(), Theme.FONT_SIZE_SMALL)
                : Theme.fontRegular(item.getFont(), Theme.FONT_SIZE_SMALL));
            item.setBorder(new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
            // Use a leading check glyph for the active item so it reads as a state, not a button
            if (key == active)
            {
                item.setText("✓ " + key.getLabel());
            }
            else
            {
                item.setText("    " + key.getLabel());
            }
            item.addActionListener(e -> onSortChanged.accept(key));
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }
}
