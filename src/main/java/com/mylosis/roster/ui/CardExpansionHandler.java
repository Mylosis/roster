package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Manages the expand/collapse behavior and inline edit form for a AccountCardPanel.
 */
public class CardExpansionHandler
{
    private final RosterPlugin plugin;
    private final Account profile;
    private final RosterConfig config;
    private final RosterPanel parentPanel;
    private final JPanel ownerCard;
    private final JPanel headerPanel;

    private JPanel formPanel;
    private InlineAccountForm editForm;
    private boolean expanded = false;

    public CardExpansionHandler(RosterPlugin plugin, Account profile,
                                RosterPanel parentPanel, JPanel ownerCard, JPanel headerPanel)
    {
        this.plugin = plugin;
        this.profile = profile;
        this.config = plugin.getConfig();
        this.parentPanel = parentPanel;
        this.ownerCard = ownerCard;
        this.headerPanel = headerPanel;
    }

    public JPanel createFormPanel()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.FORM_BACKGROUND);
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.CARD_BORDER));

        editForm = new InlineAccountForm(plugin, profile, true, null);
        editForm.setOnSave(savedAccount -> {
            plugin.getAccountStorage().saveAccount(savedAccount);
            collapse();
            plugin.getPanel().rebuild();
        });
        editForm.setOnCancel(this::collapse);

        panel.add(editForm, BorderLayout.CENTER);
        formPanel = panel;
        return panel;
    }

    public boolean isExpanded()
    {
        return expanded;
    }

    public void toggleExpanded()
    {
        if (expanded) collapse();
        else expand();
    }

    public void expand()
    {
        if (expanded) return;
        if (parentPanel != null)
        {
            parentPanel.collapseExpandedCard();
            parentPanel.setExpandedCard((AccountCardPanel) ownerCard);
        }
        expanded = true;
        formPanel.setVisible(true);

        // Calculate exact needed height: header + form + border
        int headerHeight = headerPanel.getPreferredSize().height;
        int formHeight = formPanel.getPreferredSize().height;
        Insets insets = ownerCard.getInsets();
        int totalHeight = headerHeight + formHeight + insets.top + insets.bottom;
        ownerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, totalHeight));
        ownerCard.setPreferredSize(new Dimension(ownerCard.getWidth(), totalHeight));

        editForm.focusUsernameField();
        revalidateAncestors();
    }

    public void collapse()
    {
        if (!expanded) return;
        expanded = false;
        formPanel.setVisible(false);
        int cardHeight = Theme.CARD_HEIGHT;
        ownerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, cardHeight));
        ownerCard.setPreferredSize(null);
        if (parentPanel != null) parentPanel.setExpandedCard(null);
        revalidateAncestors();
    }

    private void revalidateAncestors()
    {
        Container ancestor = ownerCard.getParent();
        while (ancestor != null)
        {
            ancestor.revalidate();
            ancestor.repaint();
            ancestor = ancestor.getParent();
        }
    }
}
