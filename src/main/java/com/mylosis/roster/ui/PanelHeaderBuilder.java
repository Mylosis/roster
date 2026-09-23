package com.mylosis.roster.ui;

import com.mylosis.roster.model.SortKey;
import com.mylosis.roster.ui.components.Controls;
import com.mylosis.roster.ui.components.SearchBar;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds the header of the Roster panel: title and version, search bar, and
 * the + Account, + Category, and sort buttons.
 */
final class PanelHeaderBuilder
{
    private PanelHeaderBuilder() {}

    /**
     * @param onSearchChanged fired with the lowercase filter after the user pauses typing
     * @param onAddAccount    the "+ Account" button action
     * @param onAddCategory   the "+ Category" button action
     * @param currentSortKey  read each time the sort menu opens, so its check mark is current
     * @param onSortChanged   fired when the user picks a sort key
     */
    static JPanel build(Consumer<String> onSearchChanged,
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
        header.add(createSearchBar(onSearchChanged));
        header.add(Box.createVerticalStrut(Theme.SPACING_MD));
        header.add(createActionButtonsRow(onAddAccount, onAddCategory, currentSortKey, onSortChanged));
        return header;
    }

    private static JPanel createTitleRow()
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

    private static SearchBar createSearchBar(Consumer<String> onSearchChanged)
    {
        SearchBar bar = new SearchBar();
        bar.setPlaceholder("Filter...");
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setOnSearchChanged(onSearchChanged);
        return bar;
    }

    private static JPanel createActionButtonsRow(Runnable onAddAccount, Runnable onAddCategory,
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
        buttonRow.add(headerButton("+ Account", "Add a new account", false, onAddAccount), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.30;
        buttonRow.add(headerButton("+ Category", "Add category", false, onAddCategory), gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.15;
        gbc.insets = new Insets(0, 0, 0, 0);
        JButton sortButton = headerButton("\u21F5", "Sort accounts", true, null);
        sortButton.addActionListener(e -> showSortMenu(sortButton, currentSortKey.get(), onSortChanged));
        buttonRow.add(sortButton, gbc);

        return buttonRow;
    }

    private static JButton headerButton(String text, String tooltip, boolean bold, Runnable onClick)
    {
        JButton button = Controls.flatButton(text, Theme.BUTTON_SECONDARY, Theme.BUTTON_SECONDARY_HOVER);
        button.setFont(bold
            ? Theme.fontBold(button.getFont(), Theme.FONT_SIZE_BODY)
            : Theme.fontRegular(button.getFont(), Theme.FONT_SIZE_BODY));
        button.setToolTipText(tooltip);
        if (onClick != null)
        {
            button.addActionListener(e -> onClick.run());
        }
        return button;
    }

    /** Lists every {@link SortKey}, with the active one checked and highlighted. */
    private static void showSortMenu(JButton anchor, SortKey active, Consumer<SortKey> onSortChanged)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BACKGROUND_DARKER);
        menu.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));

        for (SortKey key : SortKey.values())
        {
            boolean isActive = key == active;
            // A leading check glyph marks the active item as a state, not a button.
            JMenuItem item = new JMenuItem((isActive ? "\u2713 " : "    ") + key.getLabel());
            item.setBackground(Theme.BACKGROUND_DARKER);
            item.setForeground(isActive ? Theme.ACCENT_ORANGE : Theme.TEXT_PRIMARY);
            item.setFont(isActive
                ? Theme.fontBold(item.getFont(), Theme.FONT_SIZE_SMALL)
                : Theme.fontRegular(item.getFont(), Theme.FONT_SIZE_SMALL));
            item.setBorder(new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
            item.addActionListener(e -> onSortChanged.accept(key));
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }
}
