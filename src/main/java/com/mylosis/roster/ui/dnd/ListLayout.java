package com.mylosis.roster.ui.dnd;

import com.mylosis.roster.ui.AccountCardPanel;
import com.mylosis.roster.ui.CategoryPanel;
import lombok.Getter;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The drop zones of one rendered list, recorded by the list builder as it
 * creates them. Drag hit-testing reads this instead of searching the
 * component tree, and a rebuild simply replaces it.
 */
public final class ListLayout
{
    /** Whether cards are laid out in the two-column grid (row-major order). */
    @Getter
    private final boolean gridMode;
    private final List<CategoryPanel> categories = new ArrayList<>();
    private final List<List<AccountCardPanel>> categoryCards = new ArrayList<>();
    private final List<JComponent> uncategorizedZones = new ArrayList<>();
    private List<AccountCardPanel> uncategorizedCards = Collections.emptyList();

    public ListLayout(boolean gridMode)
    {
        this.gridMode = gridMode;
    }

    /** Records a category panel and its cards, in display order. */
    public void addCategory(CategoryPanel category, List<AccountCardPanel> cards)
    {
        categories.add(category);
        categoryCards.add(cards);
    }

    /** Records a component that is part of the Uncategorized drop area (its header or card list). */
    public void addUncategorizedZone(JComponent zone)
    {
        uncategorizedZones.add(zone);
    }

    public void setUncategorizedCards(List<AccountCardPanel> cards)
    {
        uncategorizedCards = cards;
    }

    List<CategoryPanel> getCategories()
    {
        return categories;
    }

    List<AccountCardPanel> getCards(int categoryIndex)
    {
        return categoryCards.get(categoryIndex);
    }

    List<JComponent> getUncategorizedZones()
    {
        return uncategorizedZones;
    }

    List<AccountCardPanel> getUncategorizedCards()
    {
        return uncategorizedCards;
    }
}
