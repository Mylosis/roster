package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.model.AccountType;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.components.AccountTypeBadge;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.Theme;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Map;

public class AccountCardPanel extends JPanel
{
    private final RosterPlugin plugin;
    @Getter
    private final Account profile;
    private final RosterConfig config;

    private boolean hovered = false;
    private Color currentBackground;
    private JPanel headerPanel;
    private CardExpansionHandler expansionHandler;
    private boolean gridEditMode = false;
    private RosterPanel parentPanel;
    /** Pre-resolved group lookup passed down from the rebuild — used by
     *  {@link #createCardBorder()} so each card no longer does its own
     *  O(n) storage scan to find its parent group's color. */
    private final Map<String, ProfileGroup> groupsById;

    public AccountCardPanel(RosterPlugin plugin, Account profile, RosterPanel parentPanel)
    {
        this(plugin, profile, parentPanel, Collections.emptyMap());
    }

    public AccountCardPanel(RosterPlugin plugin, Account profile, RosterPanel parentPanel,
                            Map<String, ProfileGroup> groupsById)
    {
        this.plugin = plugin;
        this.profile = profile;
        this.config = plugin.getConfig();
        this.parentPanel = parentPanel;
        this.groupsById = groupsById != null ? groupsById : Collections.emptyMap();
        boolean isSelected = profile.getId().equals(plugin.getSelectedAccountId());
        this.currentBackground = isSelected ? Theme.CARD_SELECTED : Theme.CARD_BACKGROUND;

        setLayout(new BorderLayout(0, 0));
        setBackground(currentBackground);
        setBorder(createCardBorder());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (config.gridView())
        {
            GridAccountBuilder.build(this, plugin, profile, this::expandGridEdit);
            setPreferredSize(new Dimension(0, GridAccountBuilder.GRID_CARD_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, GridAccountBuilder.GRID_CARD_HEIGHT));
        }
        else
        {
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CARD_HEIGHT));
            buildCard(parentPanel);
        }
        setupMouseListeners();
    }

    private javax.swing.border.Border createCardBorder()
    {
        String gid = profile.getGroupId();
        if (gid != null)
        {
            // Prefer the pre-resolved map; fall back to storage only when this card
            // was constructed via the legacy single-arg constructor (e.g. dialogs).
            ProfileGroup group = groupsById.get(gid);
            if (group == null)
            {
                group = plugin.getAccountStorage().getGroup(gid);
            }
            if (group != null)
            {
                Color catColor = Theme.parseColor(group.getColor());
                if (catColor != null)
                {
                    return BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, Theme.CATEGORY_BORDER_WIDTH, 0, 0, catColor),
                        BorderFactory.createLineBorder(Theme.CARD_BORDER, 1)
                    );
                }
            }
        }
        return BorderFactory.createLineBorder(Theme.CARD_BORDER, 1);
    }

    private JPanel infoPanelHolder;

    private void buildCard(RosterPanel parentPanel)
    {
        headerPanel = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(Theme.SPACING_MD, Theme.SPACING_LG, Theme.SPACING_MD, Theme.SPACING_LG));

        // Wrap the info panel so refreshDynamicState() can replace it in place
        // without disturbing the action buttons (which carry their own listeners).
        infoPanelHolder = new JPanel(new BorderLayout());
        infoPanelHolder.setOpaque(false);
        infoPanelHolder.add(createInfoPanel(), BorderLayout.CENTER);
        headerPanel.add(infoPanelHolder, BorderLayout.CENTER);
        headerPanel.add(createActionButtons(), BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        expansionHandler = new CardExpansionHandler(plugin, profile, parentPanel, this, headerPanel);
        JPanel formPanel = expansionHandler.createFormPanel();
        formPanel.setVisible(false);
        add(formPanel, BorderLayout.CENTER);
    }

    /**
     * Recompute the parts of this card that depend on transient plugin state
     * (selected highlight, online dot, last-online label) without rebuilding
     * the full panel. Called by {@link RosterPanel#refreshAccount(String)} after
     * {@code selectAccount} or {@code stampLastOnline}.
     *
     * <p>Grid cards are rebuilt by {@code GridAccountBuilder}, which composes the
     * whole card body in a single call — for those we fall back to a full panel
     * rebuild via the caller (which handles a missing card id the same way).
     * No-op when the card is currently expanded for editing.
     */
    public void refreshDynamicState()
    {
        if (config.gridView() || infoPanelHolder == null)
        {
            // Grid cards aren't covered by the holder pattern. Punt to a full
            // rebuild — incremental work isn't worth the complexity for the
            // compact view. The caller will catch this via the cardsByAccountId
            // miss path on the next refresh.
            if (plugin.getPanel() != null) plugin.getPanel().rebuild();
            return;
        }
        if (expansionHandler != null && expansionHandler.isExpanded())
        {
            // Don't rebuild the info panel while the user is editing — would
            // visually flicker the form open beneath them.
            return;
        }
        boolean isSelected = profile.getId().equals(plugin.getSelectedAccountId());
        currentBackground = isSelected ? Theme.CARD_SELECTED : Theme.CARD_BACKGROUND;
        setBackground(currentBackground);

        infoPanelHolder.removeAll();
        infoPanelHolder.add(createInfoPanel(), BorderLayout.CENTER);
        infoPanelHolder.revalidate();
        infoPanelHolder.repaint();
    }

    private JPanel createInfoPanel()
    {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Display name — determine what to show based on privacy settings
        String displayName = AccountCardHelper.resolveDisplayName(profile, config);
        String loggedIn = plugin.getLoggedInDisplayName();
        boolean isOnline = loggedIn != null && displayName != null && loggedIn.equalsIgnoreCase(profile.getDisplayName());

        // Always render in a row so we can fit the type badge alongside the name.
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_SM, 0));
        nameRow.setOpaque(false);
        if (isOnline)
        {
            nameRow.add(AccountCardHelper.createOnlineDot());
        }
        // Truncate slightly shorter to leave room for the badge — keeps the
        // common case (online + Main placeholder) from clipping.
        int nameMax = isOnline ? 13 : 15;
        nameRow.add(AccountCardHelper.createTruncatingLabel(displayName, Theme.TEXT_PRIMARY,
            Theme.fontBold(getFont(), Theme.FONT_SIZE_HEADING), nameMax));
        nameRow.add(createTypeBadge());
        infoPanel.add(nameRow, gbc);
        gbc.gridy++;

        // Username (shown if not hidden and different from display name)
        if (!config.hideLogin())
        {
            String username = profile.getUsername();
            if (username != null && !username.equals(displayName))
            {
                infoPanel.add(AccountCardHelper.createTruncatingLabel(username, Theme.TEXT_SECONDARY,
                    Theme.fontRegular(getFont(), Theme.FONT_SIZE_HEADING), 25), gbc);
                gbc.gridy++;
            }
        }

        // Notes preview
        if (!config.hideNotes())
        {
            AccountMetadata meta = profile.getMetadata();
            if (meta != null && meta.getNotes() != null && !meta.getNotes().isEmpty())
            {
                String notes = meta.getNotes();
                JLabel notesLabel = AccountCardHelper.createTruncatingLabel(notes, Theme.TEXT_SECONDARY,
                    Theme.fontRegular(getFont(), Theme.FONT_SIZE_BODY), 22);
                notesLabel.setToolTipText("<html><body style='width: 200px'>" + AccountCardHelper.escapeHtml(notes) + "</body></html>");
                infoPanel.add(notesLabel, gbc);
                gbc.gridy++;
            }
        }

        // Last-online stamp (suppressed while the account is currently logged in —
        // the green online dot already conveys that, and "just now" would be misleading
        // once they've been online for hours).
        if (!isOnline && !config.hideLastOnline())
        {
            AccountMetadata meta = profile.getMetadata();
            if (meta != null && meta.getLastOnlineAt() != null)
            {
                String relative = AccountCardHelper.formatTimeAgo(meta.getLastOnlineAt());
                if (relative != null)
                {
                    JLabel lastOnlineLabel = new JLabel(relative);
                    lastOnlineLabel.setForeground(Theme.TEXT_MUTED);
                    lastOnlineLabel.setFont(Theme.fontRegular(getFont(), Theme.FONT_SIZE_TINY));
                    lastOnlineLabel.setToolTipText("Last seen logged in on this client");
                    infoPanel.add(lastOnlineLabel, gbc);
                }
            }
        }

        return infoPanel;
    }

    /**
     * Creates the clickable type badge that appears beside the account name.
     * Clicking it opens a popup that lets the user change the account's type
     * without opening the full edit form.
     */
    private AccountTypeBadge createTypeBadge()
    {
        AccountType current = (profile.getMetadata() != null
            && profile.getMetadata().getAccountType() != null)
            ? profile.getMetadata().getAccountType()
            : AccountType.MAIN;

        return new AccountTypeBadge(current, newType ->
        {
            AccountMetadata meta = profile.getMetadata();
            if (meta == null)
            {
                meta = AccountMetadata.createDefault();
                profile.setMetadata(meta);
            }
            meta.setAccountType(newType);
            plugin.getAccountStorage().saveAccount(profile);
            // Only this card's badge changed — refresh it in place instead of
            // tearing down the whole panel. (Grid view falls back to a full
            // rebuild inside refreshDynamicState.)
            if (parentPanel != null)
            {
                parentPanel.refreshAccount(profile.getId());
            }
            else
            {
                plugin.getPanel().rebuild();
            }
        });
    }

    private JPanel createActionButtons()
    {
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);
        rightPanel.add(createEditButton());
        rightPanel.add(createMenuButton());
        return rightPanel;
    }

    private JButton createEditButton()
    {
        JButton button = new JButton("\u270E");
        button.setBackground(Theme.CARD_BACKGROUND);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(Theme.MENU_BUTTON_SIZE, Theme.MENU_BUTTON_SIZE));
        button.setFont(button.getFont().deriveFont(14f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("Edit account");
        button.addActionListener(e -> { if (expansionHandler != null) expansionHandler.toggleExpanded(); });
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { button.setBackground(Theme.CARD_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { button.setBackground(hovered ? Theme.CARD_HOVER : getBaseBackground()); }
        });
        return button;
    }

    private JButton createMenuButton()
    {
        JButton button = new JButton("\u22EE");
        button.setBackground(Theme.CARD_BACKGROUND);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(Theme.MENU_BUTTON_SIZE, Theme.MENU_BUTTON_SIZE));
        button.setFont(button.getFont().deriveFont(16f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("More options");

        Runnable onExpand = () -> { if (expansionHandler != null) expansionHandler.expand(); };
        AccountCardActions actions = new AccountCardActions(plugin, profile, onExpand);
        // Build the menu on first click — constructing it eagerly added six
        // menu items + listeners per card per rebuild, for menus rarely opened.
        JPopupMenu[] menuRef = new JPopupMenu[1];
        button.addActionListener(e -> {
            if (menuRef[0] == null)
            {
                menuRef[0] = actions.createContextMenu();
            }
            menuRef[0].show(button, 0, button.getHeight());
        });

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { button.setBackground(Theme.CARD_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { button.setBackground(hovered ? Theme.CARD_HOVER : getBaseBackground()); }
        });
        return button;
    }

    public boolean isExpanded()
    {
        if (gridEditMode) return true;
        return expansionHandler != null && expansionHandler.isExpanded();
    }

    public void expand()
    {
        if (config.gridView()) expandGridEdit();
        else if (expansionHandler != null) expansionHandler.expand();
    }

    public void collapse()
    {
        if (gridEditMode) collapseGridEdit();
        else if (expansionHandler != null) expansionHandler.collapse();
    }

    private void expandGridEdit()
    {
        if (gridEditMode) return;
        // Collapse any other expanded card first
        if (parentPanel != null)
        {
            parentPanel.collapseExpandedCard();
            parentPanel.setExpandedCard(this);
        }
        gridEditMode = true;

        // Replace grid content with inline edit form
        removeAll();
        setLayout(new BorderLayout(0, 0));
        setBackground(Theme.FORM_BACKGROUND);
        setCursor(Cursor.getDefaultCursor());

        InlineAccountForm editForm = new InlineAccountForm(plugin, profile, true, null);
        editForm.setOnSave(savedAccount -> {
            plugin.getAccountStorage().saveAccount(savedAccount);
            collapseGridEdit();
            plugin.getPanel().rebuild();
        });
        editForm.setOnCancel(this::collapseGridEdit);
        add(editForm, BorderLayout.CENTER);

        // Remove fixed height so the form can size naturally
        setPreferredSize(null);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        editForm.focusUsernameField();
        AccountCardHelper.revalidateToWindow(this);
    }

    private void collapseGridEdit()
    {
        if (!gridEditMode) return;
        gridEditMode = false;
        if (parentPanel != null) parentPanel.setExpandedCard(null);
        // Just rebuild the whole panel — simpler than trying to restore grid card state
        plugin.getPanel().rebuild();
    }

    private void setupMouseListeners()
    {
        if (headerPanel != null)
        {
            headerPanel.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e) { hovered = true; animateBackground(Theme.CARD_HOVER); }

                @Override
                public void mouseExited(MouseEvent e) { hovered = false; animateBackground(getBaseBackground()); }

                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1
                        && (expansionHandler == null || !expansionHandler.isExpanded()))
                    {
                        plugin.selectAccount(profile);
                    }
                }
            });
        }

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { hovered = true; animateBackground(Theme.CARD_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { hovered = false; animateBackground(getBaseBackground()); }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (headerPanel == null && e.getButton() == MouseEvent.BUTTON1)
                {
                    if (e.getClickCount() == 2 && !gridEditMode)
                    {
                        expandGridEdit();
                    }
                    else if (e.getClickCount() == 1 && !gridEditMode)
                    {
                        plugin.selectAccount(profile);
                    }
                }
            }
        });
    }

    private Color getBaseBackground()
    {
        boolean isSelected = profile.getId().equals(plugin.getSelectedAccountId());
        return isSelected ? Theme.CARD_SELECTED : Theme.CARD_BACKGROUND;
    }

    private void animateBackground(Color target)
    {
        currentBackground = target;
        setBackground(target);
        repaint();
    }
}
