package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.components.Theme;
import com.mylosis.roster.ui.dnd.AccountDragListener;
import com.mylosis.roster.ui.dnd.DragDropManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the card container for one category or the Uncategorized area, in
 * whichever view mode is active, and wires each card for selection and drag.
 */
final class CardListBuilder
{
    private static final int GRID_COLUMNS = 2;

    private CardListBuilder() {}

    /**
     * @param categoryColor edge stripe color for the cards; null for none
     * @param trailing      a component placed after the cards (a category's add form), or null
     * @param cardsOut      receives the created cards in display order
     */
    static JPanel build(RosterPlugin plugin, RosterPanel panel, DragDropManager dragDropManager,
                        List<Account> accounts, Color categoryColor, JComponent trailing,
                        List<AccountCardPanel> cardsOut)
    {
        List<AccountCardPanel> cards = new ArrayList<>(accounts.size());
        for (Account account : accounts)
        {
            AccountCardPanel card = new AccountCardPanel(plugin, panel, account, categoryColor);
            card.attachTreeListener(new AccountDragListener(dragDropManager, card));
            panel.registerCard(account.getId(), card);
            cards.add(card);
        }
        cardsOut.addAll(cards);

        if (plugin.getConfig().gridView())
        {
            JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, Theme.SPACING_XS, Theme.SPACING_XS));
            grid.setBackground(Theme.BACKGROUND);
            cards.forEach(grid::add);

            // NORTH keeps the grid rows at their preferred height.
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(Theme.BACKGROUND);
            wrapper.add(grid, BorderLayout.NORTH);
            if (trailing != null)
            {
                wrapper.add(trailing, BorderLayout.CENTER);
            }
            return wrapper;
        }

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Theme.BACKGROUND);
        for (AccountCardPanel card : cards)
        {
            list.add(card);
            list.add(Box.createVerticalStrut(Theme.SPACING_XS));
        }
        if (trailing != null)
        {
            list.add(trailing);
        }
        return list;
    }
}
