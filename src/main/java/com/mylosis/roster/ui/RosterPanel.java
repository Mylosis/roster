package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.components.NotificationToast;
import com.mylosis.roster.ui.components.Theme;
import com.mylosis.roster.ui.dnd.DragDropManager;
import com.mylosis.roster.ui.dnd.DragOverlay;
import com.mylosis.roster.ui.dnd.ListLayout;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Main panel for the Roster plugin. Owns the header, forms, card list,
 * footer, and notifications, and rebuilds or refreshes the list on demand.
 * EDT only.
 */
public class RosterPanel extends PluginPanel
{
    private static final int NOTIFICATION_DURATION_MS = 3000;

    private final RosterPlugin plugin;
    private final DragDropManager dragDropManager;
    private final AccountListBuilder listBuilder;
    @Getter(AccessLevel.PACKAGE)
    private final PanelFormManager formManager;
    private final PanelFooterBuilder footer;

    private final JScrollPane scrollPane;
    private final JLayeredPane layeredPane;
    private final JPanel notificationPanel;
    private final JLabel accountCountLabel = new JLabel();

    private String searchFilter = "";
    private AccountCardPanel expandedCard;

    /**
     * Live id -> card lookup, repopulated on every rebuild, so small state
     * changes (selection, online status, badge) refresh one card in place.
     */
    private final Map<String, AccountCardPanel> cardsByAccountId = new HashMap<>();
    /** Drop zones of the current list, for drag and drop. */
    @Getter
    private ListLayout listLayout;

    public RosterPanel(RosterPlugin plugin)
    {
        // No PluginPanel scroll wrapper: the card list has its own scroll pane,
        // and a second one swallowed mouse-wheel events.
        super(false);
        this.plugin = plugin;
        this.dragDropManager = new DragDropManager(plugin, this);
        this.listBuilder = new AccountListBuilder(plugin, this, dragDropManager);
        this.formManager = new PanelFormManager(plugin.getAccountStorage(), this::rebuild, this::revalidateAndRepaint);
        this.footer = new PanelFooterBuilder(plugin, this, this::showNotification, this::rebuild);

        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        scrollPane = createScrollPane();
        notificationPanel = createNotificationPanel();

        layeredPane = new JLayeredPane();
        layeredPane.setLayout(new OverlayLayout(layeredPane));
        layeredPane.add(createContentPanel(), JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(notificationPanel, JLayeredPane.POPUP_LAYER);
        DragOverlay dragOverlay = dragDropManager.getOverlay();
        dragOverlay.setVisible(false);
        layeredPane.add(dragOverlay, JLayeredPane.DRAG_LAYER);
        layeredPane.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                repositionNotificationPanel();
            }
        });

        add(layeredPane, BorderLayout.CENTER);
        rebuild();
    }

    private JPanel createContentPanel()
    {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(Theme.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM));

        JPanel header = PanelHeaderBuilder.build(
            this::onSearchChanged,
            () -> formManager.toggleAddAccountForm(this::collapseExpandedCardOnly),
            () -> formManager.toggleAddCategoryForm(this::collapseExpandedCardOnly),
            () -> plugin.getConfig().sortKey(),
            // Written to config; onConfigChanged triggers the rebuild.
            key -> plugin.getConfigManager().setConfiguration(RosterConfig.CONFIG_GROUP, "sortKey", key));

        JPanel headerWithForms = new JPanel();
        headerWithForms.setLayout(new BoxLayout(headerWithForms, BoxLayout.Y_AXIS));
        headerWithForms.setBackground(Theme.BACKGROUND);
        headerWithForms.add(header);
        headerWithForms.add(formManager.getAddAccountFormPanel());
        headerWithForms.add(formManager.getAddCategoryFormPanel());
        contentPanel.add(headerWithForms, BorderLayout.NORTH);

        contentPanel.add(scrollPane, BorderLayout.CENTER);

        accountCountLabel.setForeground(Theme.TEXT_SECONDARY);
        accountCountLabel.setFont(Theme.fontRegular(accountCountLabel.getFont(), Theme.FONT_SIZE_SMALL));
        contentPanel.add(footer.build(accountCountLabel), BorderLayout.SOUTH);
        return contentPanel;
    }

    private static JScrollPane createScrollPane()
    {
        JScrollPane pane = new JScrollPane();
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        pane.setBorder(null);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setBackground(Theme.BACKGROUND);
        pane.getViewport().setBackground(Theme.BACKGROUND);
        return pane;
    }

    private static JPanel createNotificationPanel()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0))
        {
            @Override
            public boolean isOptimizedDrawingEnabled()
            {
                return false;
            }
        };
        panel.setOpaque(false);
        panel.setAlignmentX(0f);
        panel.setAlignmentY(0.5f);
        panel.setMaximumSize(new Dimension(0, 0));
        return panel;
    }

    private void onSearchChanged(String filter)
    {
        // The debounce can fire with unchanged text (typed then deleted).
        if (!filter.equals(searchFilter))
        {
            searchFilter = filter;
            rebuild();
            // A new result set starts at the top.
            scrollPane.getVerticalScrollBar().setValue(0);
        }
    }

    /**
     * Rebuilds the card list from storage, keeping the scroll position.
     */
    public void rebuild()
    {
        int scrollValue = scrollPane.getVerticalScrollBar().getValue();

        // A fresh list panel avoids stale BoxLayout caches; the old cards,
        // including any open editor, are discarded with it.
        JPanel list = new ListPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Theme.BACKGROUND);
        cardsByAccountId.clear();
        expandedCard = null;
        listLayout = new ListLayout(plugin.getConfig().gridView());

        AccountListBuilder.Counts counts = listBuilder.build(list, searchFilter, listLayout);
        updateAccountCount(counts.getVisible(), counts.getTotal());
        footer.refresh();

        scrollPane.setViewportView(list);
        // Lay out now so the restored position is clamped against the new height.
        scrollPane.validate();
        scrollPane.getVerticalScrollBar().setValue(scrollValue);
        scrollPane.repaint();
    }

    private void updateAccountCount(int visible, int total)
    {
        accountCountLabel.setText(visible == total
            ? total + " Account" + (total != 1 ? "s" : "")
            : visible + " / " + total + " Accounts");
    }

    /**
     * Refreshes one card's transient state (selection, online status, badge)
     * in place. Falls back to a full rebuild when the card isn't shown, unless
     * it is only hidden by the search filter (a rebuild would change nothing).
     */
    public void refreshAccount(String accountId)
    {
        AccountCardPanel card = cardsByAccountId.get(accountId);
        if (card != null)
        {
            card.refreshDynamicState();
            return;
        }
        if (!searchFilter.isEmpty())
        {
            Account account = plugin.getAccountStorage().getAccount(accountId);
            if (account != null && !account.getSearchHaystack().contains(searchFilter))
            {
                return;
            }
        }
        rebuild();
    }

    /** Called by the list builders as each card is created. */
    void registerCard(String accountId, AccountCardPanel card)
    {
        cardsByAccountId.put(accountId, card);
    }

    void setExpandedCard(AccountCardPanel card)
    {
        expandedCard = card;
    }

    /** Collapses the card being edited, if any, and hides the add-account form. */
    void collapseExpandedCard()
    {
        collapseExpandedCardOnly();
        formManager.hideAddAccountForm();
    }

    private void collapseExpandedCardOnly()
    {
        if (expandedCard != null)
        {
            AccountCardPanel card = expandedCard;
            expandedCard = null;
            card.collapse();
        }
    }

    public void showNotification(String message, NotificationToast.Type type)
    {
        // Stop the replaced toast's timers, or they keep repainting a
        // parentless component until they finish.
        for (Component c : notificationPanel.getComponents())
        {
            if (c instanceof NotificationToast)
            {
                ((NotificationToast) c).stopTimers();
            }
        }
        notificationPanel.removeAll();
        notificationPanel.add(new NotificationToast(message, type, NOTIFICATION_DURATION_MS));
        notificationPanel.revalidate();
        SwingUtilities.invokeLater(this::repositionNotificationPanel);
    }

    private void repositionNotificationPanel()
    {
        if (notificationPanel.getComponentCount() == 0)
        {
            notificationPanel.setBounds(0, 0, 0, 0);
            return;
        }

        int width = layeredPane.getWidth();
        notificationPanel.setSize(width, 100);
        notificationPanel.doLayout();
        Dimension pref = notificationPanel.getPreferredSize();
        int y = layeredPane.getHeight() - pref.height - 50;
        notificationPanel.setBounds(0, y, width, pref.height + 20);
        notificationPanel.repaint();
    }

    private void revalidateAndRepaint()
    {
        revalidate();
        repaint();
    }

    /**
     * The scrolled card list. Always exactly as wide as the viewport: without
     * this, one over-wide row (a long category name) widened the whole list and
     * pushed every card's buttons out of view, since horizontal scrolling is off.
     */
    private static final class ListPanel extends JPanel implements Scrollable
    {
        private static final int SCROLL_UNIT = 16;

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return SCROLL_UNIT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
