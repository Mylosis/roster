package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.components.Controls;
import com.mylosis.roster.ui.components.Theme;
import com.mylosis.roster.ui.dnd.CategoryDragListener;
import com.mylosis.roster.ui.dnd.DragDropManager;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One category in the list: a header (collapse chevron, name, count, add and
 * menu buttons; click to collapse, drag to reorder) above its account cards.
 */
public class CategoryPanel extends JPanel
{
    private static final String CHEVRON_EXPANDED = "\u25BC";
    private static final String CHEVRON_COLLAPSED = "\u25B6";
    /** Longer names are cut so the header row never outgrows the panel. */
    private static final int NAME_MAX_CHARS = 14;

    private final RosterPlugin plugin;
    @Getter
    private final ProfileGroup group;
    private final CategoryFormHandler formHandler;

    private final JPanel headerPanel;
    private final JPanel contentWrapper;
    private final JLabel chevronLabel = new JLabel();
    /** This category's cards, in display order. */
    @Getter
    private final List<AccountCardPanel> cards;
    private boolean collapsed;

    public CategoryPanel(RosterPlugin plugin, RosterPanel panel, ProfileGroup group, List<Account> accounts,
                         DragDropManager dragDropManager)
    {
        this.plugin = plugin;
        this.group = group;
        this.collapsed = group.isCollapsed();
        this.formHandler = new CategoryFormHandler(plugin, panel, group, this);

        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        headerPanel = createHeader(accounts.size(), dragDropManager);
        add(headerPanel, BorderLayout.NORTH);

        List<AccountCardPanel> built = new ArrayList<>();
        contentWrapper = CardListBuilder.build(plugin, panel, dragDropManager, accounts,
            Theme.parseColor(group.getColor()), formHandler.getFormPanel(), built);
        contentWrapper.setBorder(new EmptyBorder(Theme.SPACING_SM, 0, 0, 0));
        cards = Collections.unmodifiableList(built);
        add(contentWrapper, BorderLayout.CENTER);

        updateCollapsedState();
    }

    /**
     * Height of the header row, or 0 if not yet laid out. Used to size the
     * category-reorder hit zone.
     */
    public int getHeaderHeight()
    {
        return headerPanel.getHeight();
    }

    boolean isCollapsed()
    {
        return collapsed;
    }

    private JPanel createHeader(int accountCount, DragDropManager dragDropManager)
    {
        JPanel header = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        header.setBackground(Theme.GROUP_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.CARD_BORDER),
            new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_MD)
        ));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // A single row that never wraps: anything too wide is clipped instead.
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        left.add(Box.createHorizontalStrut(Theme.SPACING_SM));

        Color categoryColor = Theme.parseColor(group.getColor());
        chevronLabel.setForeground(categoryColor != null ? categoryColor : Theme.TEXT_SECONDARY);
        chevronLabel.setFont(Theme.fontRegular(chevronLabel.getFont(), Theme.FONT_SIZE_TINY));
        left.add(chevronLabel);
        left.add(Box.createHorizontalStrut(Theme.SPACING_SM));

        left.add(AccountCardHelper.truncatingLabel(group.getName(), Theme.TEXT_PRIMARY,
            Theme.fontBold(chevronLabel.getFont(), Theme.FONT_SIZE_BODY), NAME_MAX_CHARS));
        left.add(Box.createHorizontalStrut(Theme.SPACING_SM));

        JLabel countLabel = new JLabel("\u2022 " + accountCount);
        countLabel.setForeground(Theme.TEXT_SECONDARY);
        countLabel.setFont(Theme.fontBold(countLabel.getFont(), Theme.FONT_SIZE_BODY));
        left.add(countLabel);
        header.add(left, BorderLayout.CENTER);

        JButton menuButton = Controls.ghostButton("\u22EE", Theme.TEXT_SECONDARY, Theme.GROUP_HEADER_HOVER, Theme.TEXT_PRIMARY);
        menuButton.setPreferredSize(new Dimension(24, Theme.BUTTON_HEIGHT_SM));
        menuButton.setToolTipText("Category options");
        Controls.attachLazyMenu(menuButton, formHandler::createContextMenu);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        right.setOpaque(false);
        right.add(createHeaderAddButton("Add account to this category", formHandler::toggleAddForm));
        right.add(menuButton);
        header.add(right, BorderLayout.EAST);

        CategoryDragListener dragListener = new CategoryDragListener(dragDropManager, group, this::toggleCollapsed);
        header.addMouseListener(dragListener);
        header.addMouseMotionListener(dragListener);
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { header.setBackground(Theme.GROUP_HEADER_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { header.setBackground(Theme.GROUP_HEADER); }
        });
        return header;
    }

    /** The "+" button used in category headers and the Uncategorized header. */
    static JButton createHeaderAddButton(String tooltip, Runnable onClick)
    {
        JButton button = Controls.ghostButton("+", Theme.TEXT_SECONDARY, Theme.BUTTON_PRIMARY, Theme.TEXT_PRIMARY);
        button.setPreferredSize(new Dimension(28, Theme.BUTTON_HEIGHT_SM));
        button.setToolTipText(tooltip);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
        button.addActionListener(e -> onClick.run());
        return button;
    }

    /** Collapses or expands the category and persists the state. */
    void toggleCollapsed()
    {
        collapsed = !collapsed;
        group.setCollapsed(collapsed);
        plugin.getAccountStorage().saveGroup(group);
        updateCollapsedState();
    }

    private void updateCollapsedState()
    {
        contentWrapper.setVisible(!collapsed);
        chevronLabel.setText(collapsed ? CHEVRON_COLLAPSED : CHEVRON_EXPANDED);
        revalidate();
        repaint();
    }
}
