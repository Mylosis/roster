package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.Theme;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;

/**
 * Expands a list-view card into its inline edit form and collapses it again.
 */
class CardExpansionHandler
{
    private final RosterPlugin plugin;
    private final RosterPanel panel;
    private final AccountCardPanel card;
    private final JPanel headerPanel;

    /** Hidden container for the edit form, which is only built on first expand. */
    @Getter
    private final JPanel formPanel;
    private InlineAccountForm editForm;
    @Getter
    private boolean expanded = false;

    CardExpansionHandler(RosterPlugin plugin, RosterPanel panel, AccountCardPanel card, JPanel headerPanel)
    {
        this.plugin = plugin;
        this.panel = panel;
        this.card = card;
        this.headerPanel = headerPanel;

        formPanel = new JPanel(new BorderLayout());
        formPanel.setBackground(Theme.FORM_BACKGROUND);
        formPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.CARD_BORDER));
        formPanel.setVisible(false);
    }

    private void ensureEditForm()
    {
        if (editForm != null)
        {
            return;
        }
        editForm = new InlineAccountForm(plugin.getAccountStorage(), card.getAccount(), true, null);
        editForm.setOnSave(saved -> {
            plugin.getAccountStorage().saveAccount(saved);
            collapse();
            panel.rebuild();
        });
        editForm.setOnCancel(this::collapse);
        formPanel.add(editForm, BorderLayout.CENTER);
    }

    void toggleExpanded()
    {
        if (expanded) collapse();
        else expand();
    }

    void expand()
    {
        if (expanded) return;
        panel.collapseExpandedCard();
        panel.setExpandedCard(card);
        expanded = true;
        ensureEditForm();
        formPanel.setVisible(true);

        // Size the card to exactly header + form + border.
        Insets insets = card.getInsets();
        int height = headerPanel.getPreferredSize().height + formPanel.getPreferredSize().height
            + insets.top + insets.bottom;
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        card.setPreferredSize(new Dimension(card.getWidth(), height));

        editForm.focusUsernameField();
        AccountCardHelper.revalidateScrollPane(card);
    }

    void collapse()
    {
        if (!expanded) return;
        expanded = false;
        formPanel.setVisible(false);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CARD_HEIGHT));
        card.setPreferredSize(null);
        panel.setExpandedCard(null);
        AccountCardHelper.revalidateScrollPane(card);
    }
}
