package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.ui.components.NotificationToast;
import com.mylosis.roster.ui.components.Theme;
import com.mylosis.roster.ui.dnd.DragDropManager;
import com.mylosis.roster.ui.dnd.DragOverlay;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Main panel for the Roster plugin.
 * Orchestrates header, form, profile list, footer, and notification components.
 */
@Slf4j
public class RosterPanel extends PluginPanel
{
    private final RosterPlugin plugin;
    private final AccountStorage storage;

    @Getter
    private final DragDropManager dragDropManager;
    private final AccountListBuilder listBuilder;
    @Getter
    private final PanelFormManager formManager;

    private JPanel profileListPanel;
    private JScrollPane scrollPane;
    private final JLayeredPane layeredPane;
    private final DragOverlay dragOverlay;
    private JPanel notificationPanel;
    private JLabel accountCountLabel;

    private String searchFilter = "";

    @Setter
    private AccountCardPanel expandedCard = null;

    /**
     * Live id→card lookup populated during each rebuild. Lets callers like
     * {@link #refreshCard(String)} update a single visible card in place instead
     * of forcing a full panel rebuild for trivial state changes (selection
     * highlight, last-online timestamp). Stale entries (e.g. a deleted account)
     * naturally drop out on the next rebuild; missing entries fall back to a
     * full rebuild via {@link #refreshAccount(String)}.
     */
    private final Map<String, AccountCardPanel> cardsByAccountId = new HashMap<>();

    public RosterPanel(RosterPlugin plugin)
    {
        // Disable PluginPanel's default outer JScrollPane wrap — we manage our own
        // scrolling via the inner scrollPane built in buildContentPanel(). With the
        // outer wrap in place wheel events were swallowed by the inner viewport
        // (which never needed to scroll, since it sized to its content), leaving
        // the user with a dead scroll wheel.
        super(false);
        this.plugin = plugin;
        this.storage = plugin.getAccountStorage();
        this.dragDropManager = new DragDropManager(plugin, this);
        this.dragOverlay = dragDropManager.getOverlay();
        this.listBuilder = new AccountListBuilder(plugin, this, dragDropManager);
        this.formManager = new PanelFormManager(plugin, this::rebuild, this::doRevalidate);

        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        layeredPane = new JLayeredPane();
        layeredPane.setLayout(new OverlayLayout(layeredPane));

        JPanel contentPanel = buildContentPanel();
        layeredPane.add(contentPanel, JLayeredPane.DEFAULT_LAYER);

        notificationPanel = buildNotificationPanel();
        layeredPane.add(notificationPanel, JLayeredPane.POPUP_LAYER);

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e)
            {
                repositionNotificationPanel();
            }
        });

        dragOverlay.setVisible(false);
        layeredPane.add(dragOverlay, JLayeredPane.DRAG_LAYER);

        add(layeredPane, BorderLayout.CENTER);
        rebuild();
    }

    private JPanel buildContentPanel()
    {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(Theme.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM));

        // Header with search and action buttons
        PanelHeaderBuilder headerBuilder = new PanelHeaderBuilder();
        JPanel headerPanel = headerBuilder.build(
            filter -> { searchFilter = filter; rebuild(); },
            () -> formManager.toggleAddProfileForm(this::collapseExpandedCardOnly),
            () -> formManager.toggleAddCategoryForm(this::collapseExpandedCardOnly),
            () -> plugin.getConfig().sortKey(),
            newKey -> {
                plugin.getConfigManager().setConfiguration(
                    RosterConfig.CONFIG_GROUP, "sortKey", newKey);
                // onConfigChanged will trigger the rebuild
            }
        );

        // Attach form panels below action buttons
        JPanel headerWithForms = new JPanel();
        headerWithForms.setLayout(new BoxLayout(headerWithForms, BoxLayout.Y_AXIS));
        headerWithForms.setBackground(Theme.BACKGROUND);
        headerWithForms.add(headerPanel);
        headerWithForms.add(formManager.getAddProfileFormPanel());
        headerWithForms.add(formManager.getAddCategoryFormPanel());

        contentPanel.add(headerWithForms, BorderLayout.NORTH);

        // Scrollable profile list
        profileListPanel = new JPanel();
        profileListPanel.setLayout(new BoxLayout(profileListPanel, BoxLayout.Y_AXIS));
        profileListPanel.setBackground(Theme.BACKGROUND);

        scrollPane = new JScrollPane(profileListPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(Theme.BACKGROUND);
        scrollPane.getViewport().setBackground(Theme.BACKGROUND);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer with count and actions
        accountCountLabel = new JLabel("0 Accounts");
        accountCountLabel.setForeground(Theme.TEXT_SECONDARY);
        accountCountLabel.setFont(Theme.fontRegular(accountCountLabel.getFont(), Theme.FONT_SIZE_SMALL));

        PanelFooterBuilder footerBuilder = new PanelFooterBuilder(plugin, this::showNotification, this::rebuild);
        contentPanel.add(footerBuilder.build(accountCountLabel), BorderLayout.SOUTH);

        return contentPanel;
    }

    private JPanel buildNotificationPanel()
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

    public void rebuild()
    {
        // Create a fresh panel to avoid stale layout caches from BoxLayout
        JPanel newListPanel = new JPanel();
        newListPanel.setLayout(new BoxLayout(newListPanel, BoxLayout.Y_AXIS));
        newListPanel.setBackground(Theme.BACKGROUND);

        // Clear the card index — the rebuild below will repopulate it as each
        // AccountCardPanel is created. Old card references are dropped here so
        // they can be GC'd along with the discarded list panel.
        cardsByAccountId.clear();

        int[] counts = listBuilder.rebuild(newListPanel, searchFilter);
        updateProfileCount(counts[0], counts[1]);

        // Swap the viewport to the fresh panel — forces complete layout recalculation
        profileListPanel = newListPanel;
        scrollPane.setViewportView(profileListPanel);
        scrollPane.getVerticalScrollBar().setValue(0);

        // Scope the revalidate to the scrollpane subtree. The previous version
        // walked all the way to the root window — overkill for a sidebar redraw,
        // and on Windows it visibly flickered other RuneLite panels. Validating
        // the viewport then the scrollpane is enough: the viewport view changed,
        // and the scrollpane is the boundary of layout invalidation for our panel.
        scrollPane.getViewport().revalidate();
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    private void updateProfileCount(int filtered, int total)
    {
        if (filtered == total)
        {
            accountCountLabel.setText(total + " Account" + (total != 1 ? "s" : ""));
        }
        else
        {
            accountCountLabel.setText(filtered + " / " + total + " Accounts");
        }
    }

    /**
     * Collapse any currently expanded profile card and hide add form.
     */
    public void collapseExpandedCard()
    {
        collapseExpandedCardOnly();
        formManager.hideAddForm();
    }

    private void collapseExpandedCardOnly()
    {
        if (expandedCard != null)
        {
            expandedCard.collapse();
            expandedCard = null;
        }
    }

    public void showNotification(String message, NotificationToast.Type type)
    {
        NotificationToast toast = new NotificationToast(message, type, 3000);
        notificationPanel.removeAll();
        notificationPanel.add(toast);
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

        int panelWidth = layeredPane.getWidth();
        int panelHeight = layeredPane.getHeight();
        notificationPanel.setSize(panelWidth, 100);
        notificationPanel.doLayout();
        Dimension pref = notificationPanel.getPreferredSize();
        int y = panelHeight - pref.height - 50;
        notificationPanel.setBounds(0, y, panelWidth, pref.height + 20);
        notificationPanel.repaint();
    }

    private void doRevalidate()
    {
        revalidate();
        repaint();
    }

    /**
     * Called by {@link AccountListBuilder} and {@link CategoryPanel} as each card is
     * inserted into the rebuilt list. Should not be called from elsewhere.
     */
    public void registerCard(String accountId, AccountCardPanel card)
    {
        if (accountId != null && card != null)
        {
            cardsByAccountId.put(accountId, card);
        }
    }

    /**
     * Refreshes a single card's mutable visual state (selected-highlight, online
     * indicator, last-online label) without rebuilding the panel. Falls back to a
     * full {@link #rebuild()} if the card isn't in the live index — e.g. because
     * it's currently filtered out by search, or the account was deleted.
     */
    public void refreshAccount(String accountId)
    {
        if (accountId == null)
        {
            return;
        }
        AccountCardPanel card = cardsByAccountId.get(accountId);
        if (card == null)
        {
            // Not visible — full rebuild is a safe fallback that costs us nothing
            // here because the user isn't looking at the card anyway.
            rebuild();
            return;
        }
        card.refreshDynamicState();
    }
}
