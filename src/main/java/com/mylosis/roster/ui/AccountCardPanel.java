package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.components.Controls;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.Theme;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * One account in the list: a list-view card (details plus edit and menu
 * buttons, expanding into an inline editor) or a compact grid-view tile.
 * Transient state (selection, online status, last-online) is refreshed in
 * place by {@link #refreshDynamicState()} without rebuilding the panel.
 */
public class AccountCardPanel extends JPanel
{
    private final RosterPlugin plugin;
    private final RosterPanel panel;
    @Getter
    private final Account account;
    private final boolean gridMode;
    /** Click/drag listeners spread over the card's non-interactive subtree; re-applied when content is rebuilt. */
    private final List<MouseAdapter> treeListeners = new ArrayList<>();
    /** Spread over the whole subtree, buttons included, so leaving via a button still ends the hover. */
    private final HoverTracker hoverTracker = new HoverTracker();

    private boolean hovered = false;

    // List view
    private JPanel infoHolder;
    private CardExpansionHandler expansion;

    // Grid view
    private boolean gridEditing = false;

    /**
     * @param categoryColor the owning category's color for the left edge stripe; null for none
     */
    public AccountCardPanel(RosterPlugin plugin, RosterPanel panel, Account account, Color categoryColor)
    {
        this.plugin = plugin;
        this.panel = panel;
        this.account = account;
        this.gridMode = plugin.getConfig().gridView();

        setLayout(new BorderLayout(0, 0));
        setBorder(createBorder(categoryColor));
        if (gridMode)
        {
            showGridContent();
        }
        else
        {
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CARD_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            buildListCard();
        }
        updateBackground();
        AccountCardHelper.attachToTree(this, hoverTracker, false);
    }

    private static Border createBorder(Color categoryColor)
    {
        Border line = BorderFactory.createLineBorder(Theme.CARD_BORDER, 1);
        if (categoryColor == null)
        {
            return line;
        }
        return BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, Theme.CATEGORY_BORDER_WIDTH, 0, 0, categoryColor), line);
    }

    private void buildListCard()
    {
        JPanel header = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(Theme.SPACING_MD, Theme.SPACING_LG, Theme.SPACING_MD, Theme.SPACING_LG));

        // The info column sits in a holder so refreshDynamicState() can swap
        // it without touching the buttons.
        infoHolder = new JPanel(new BorderLayout());
        infoHolder.setOpaque(false);
        infoHolder.add(ListCardBuilder.buildInfo(plugin, panel, account, getFont()), BorderLayout.CENTER);
        header.add(infoHolder, BorderLayout.CENTER);
        header.add(createActionButtons(), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        expansion = new CardExpansionHandler(plugin, panel, this, header);
        add(expansion.getFormPanel(), BorderLayout.CENTER);
    }

    private JPanel createActionButtons()
    {
        JButton edit = cardButton("\u270E", 14f, "Edit account");
        edit.addActionListener(e -> expansion.toggleExpanded());

        JButton menu = cardButton("\u22EE", 16f, "More options");
        Controls.attachLazyMenu(menu, () -> new AccountCardActions(plugin, panel, account, this::expand).createContextMenu());

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.setOpaque(false);
        buttons.add(edit);
        buttons.add(menu);
        return buttons;
    }

    private static JButton cardButton(String glyph, float fontSize, String tooltip)
    {
        JButton button = Controls.ghostButton(glyph, Theme.TEXT_PRIMARY, Theme.CARD_HOVER, Theme.TEXT_PRIMARY);
        button.setPreferredSize(new Dimension(Theme.MENU_BUTTON_SIZE, Theme.MENU_BUTTON_SIZE));
        button.setFont(button.getFont().deriveFont(fontSize));
        button.setToolTipText(tooltip);
        return button;
    }

    private void showGridContent()
    {
        removeAll();
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(0, GridAccountBuilder.GRID_CARD_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, GridAccountBuilder.GRID_CARD_HEIGHT));
        GridAccountBuilder.build(this, plugin, panel, account, this::expand);
        reattachTreeListeners();
    }

    /**
     * Re-renders the parts of the card that depend on transient state
     * (selection, online dot, last-online label, badge). No-op for content
     * while the card is being edited.
     */
    void refreshDynamicState()
    {
        updateBackground();
        if (isExpanded())
        {
            return;
        }
        if (gridMode)
        {
            showGridContent();
        }
        else
        {
            infoHolder.removeAll();
            infoHolder.add(ListCardBuilder.buildInfo(plugin, panel, account, getFont()), BorderLayout.CENTER);
            reattachTreeListeners();
        }
        revalidate();
        repaint();
    }

    /**
     * Spreads {@code listener} over the card and its non-interactive children
     * (so hover and drag work anywhere on it), now and after every content rebuild.
     */
    void attachTreeListener(MouseAdapter listener)
    {
        treeListeners.add(listener);
        AccountCardHelper.attachToTree(this, listener, true);
    }

    private void reattachTreeListeners()
    {
        AccountCardHelper.attachToTree(this, hoverTracker, false);
        for (MouseAdapter listener : treeListeners)
        {
            AccountCardHelper.attachToTree(this, listener, true);
        }
    }

    /** Fills the login screen with this account (the card's click action). */
    public void select()
    {
        if (!isExpanded())
        {
            plugin.selectAccount(account);
        }
    }

    public boolean isExpanded()
    {
        return gridEditing || (expansion != null && expansion.isExpanded());
    }

    public void expand()
    {
        if (gridMode) expandGridEdit();
        else expansion.expand();
    }

    void collapse()
    {
        if (gridMode) collapseGridEdit();
        else expansion.collapse();
    }

    /** Grid tiles have no room for an inline form, so editing swaps the tile's content for it. */
    private void expandGridEdit()
    {
        if (gridEditing) return;
        panel.collapseExpandedCard();
        panel.setExpandedCard(this);
        gridEditing = true;

        removeAll();
        setCursor(Cursor.getDefaultCursor());
        InlineAccountForm editForm = new InlineAccountForm(plugin.getAccountStorage(), account, true, null);
        editForm.setOnSave(saved -> {
            plugin.getAccountStorage().saveAccount(saved);
            gridEditing = false;
            panel.setExpandedCard(null);
            panel.rebuild();
        });
        editForm.setOnCancel(this::collapseGridEdit);
        add(editForm, BorderLayout.CENTER);

        // Let the form size naturally.
        setPreferredSize(null);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        updateBackground();
        editForm.focusUsernameField();
        AccountCardHelper.revalidateScrollPane(this);
    }

    private void collapseGridEdit()
    {
        if (!gridEditing) return;
        gridEditing = false;
        panel.setExpandedCard(null);
        showGridContent();
        updateBackground();
        AccountCardHelper.revalidateScrollPane(this);
    }

    private void updateBackground()
    {
        Color background;
        if (gridEditing)
        {
            background = Theme.FORM_BACKGROUND;
        }
        else if (hovered)
        {
            background = Theme.CARD_HOVER;
        }
        else
        {
            background = account.getId().equals(plugin.getSelectedAccountId()) ? Theme.CARD_SELECTED : Theme.CARD_BACKGROUND;
        }
        setBackground(background);
        repaint();
    }

    /**
     * Tracks hover across the whole card. Moving onto a child fires an exit
     * on the parent, so an exit only counts once the pointer has left the
     * card's bounds.
     */
    private final class HoverTracker extends MouseAdapter
    {
        @Override
        public void mouseEntered(MouseEvent e)
        {
            if (!hovered)
            {
                hovered = true;
                updateBackground();
            }
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), AccountCardPanel.this);
            if (hovered && !contains(p))
            {
                hovered = false;
                updateBackground();
            }
        }
    }
}
