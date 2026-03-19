package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.components.Theme;
import com.mylosis.roster.ui.dnd.CategoryDragListener;
import com.mylosis.roster.ui.dnd.DragDropManager;
import com.mylosis.roster.ui.dnd.AccountDragListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

@Slf4j
public class CategoryPanel extends JPanel
{
    private final RosterPlugin plugin;
    @Getter
    private final ProfileGroup group;
    private final List<Account> profiles;
    private final DragDropManager dragDropManager;
    private final CategoryFormHandler formHandler;

    private final JPanel contentWrapper;
    private final JLabel chevronLabel;
    private boolean collapsed;

    public CategoryPanel(RosterPlugin plugin, ProfileGroup group, List<Account> profiles, DragDropManager dragDropManager)
    {
        this.plugin = plugin;
        this.group = group;
        this.profiles = profiles;
        this.dragDropManager = dragDropManager;
        this.collapsed = group.isCollapsed();
        this.formHandler = new CategoryFormHandler(plugin, group, this::toggleCollapsed);

        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        add(createHeader(), BorderLayout.NORTH);

        boolean gridMode = plugin.getConfig().gridView();

        contentWrapper = new JPanel();
        contentWrapper.setBackground(Theme.BACKGROUND);

        if (gridMode)
        {
            // Grid: use GridLayout inside a NORTH-aligned wrapper so it doesn't stretch
            int cols = 2;
            JPanel gridPanel = new JPanel(new GridLayout(0, cols, Theme.SPACING_XS, Theme.SPACING_XS));
            gridPanel.setBackground(Theme.BACKGROUND);

            for (Account profile : profiles)
            {
                AccountCardPanel card = new AccountCardPanel(plugin, profile, plugin.getPanel());
                AccountDragListener dragListener = new AccountDragListener(dragDropManager, profile, card);
                dragListener.attachToComponent(card);
                gridPanel.add(card);
            }

            contentWrapper.setLayout(new BorderLayout());
            contentWrapper.setBorder(new EmptyBorder(Theme.SPACING_SM, 0, 0, 0));
            contentWrapper.add(gridPanel, BorderLayout.NORTH);
            contentWrapper.add(formHandler.getOrCreateFormPanel(), BorderLayout.CENTER);
        }
        else
        {
            contentWrapper.setLayout(new BoxLayout(contentWrapper, BoxLayout.Y_AXIS));
            contentWrapper.setBorder(new EmptyBorder(Theme.SPACING_SM, 0, 0, 0));

            for (Account profile : profiles)
            {
                AccountCardPanel card = new AccountCardPanel(plugin, profile, plugin.getPanel());
                AccountDragListener dragListener = new AccountDragListener(dragDropManager, profile, card);
                dragListener.attachToComponent(card);
                contentWrapper.add(card);
                contentWrapper.add(Box.createVerticalStrut(Theme.SPACING_XS));
            }

            contentWrapper.add(formHandler.getOrCreateFormPanel());
        }

        add(contentWrapper, BorderLayout.CENTER);

        chevronLabel = new JLabel(collapsed ? "\u25B6" : "\u25BC");
        chevronLabel.setForeground(Theme.TEXT_SECONDARY);
        chevronLabel.setFont(Theme.fontRegular(chevronLabel.getFont(), Theme.FONT_SIZE_TINY));

        updateCollapsedState();
    }

    private JPanel createHeader()
    {
        JPanel headerPanel = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        headerPanel.setBackground(Theme.GROUP_HEADER);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.CARD_BORDER),
            new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_MD, Theme.SPACING_SM, Theme.SPACING_MD)
        ));
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_SM, 0));
        leftPanel.setOpaque(false);

        // Chevron colored by category color when enabled
        Color catColor = Theme.parseColor(group.getColor());

        JLabel chevron = new JLabel(collapsed ? "\u25B6" : "\u25BC");
        chevron.setForeground(catColor != null ? catColor : Theme.TEXT_SECONDARY);
        chevron.setFont(Theme.fontRegular(chevron.getFont(), Theme.FONT_SIZE_TINY));
        leftPanel.add(chevron);

        JLabel nameLabel = new JLabel(group.getName());
        nameLabel.setForeground(Theme.TEXT_PRIMARY);
        nameLabel.setFont(Theme.fontBold(nameLabel.getFont(), Theme.FONT_SIZE_BODY));
        leftPanel.add(nameLabel);

        JLabel countLabel = new JLabel("\u2022 " + profiles.size());
        countLabel.setForeground(Theme.TEXT_SECONDARY);
        countLabel.setFont(Theme.fontBold(countLabel.getFont(), Theme.FONT_SIZE_BODY));
        leftPanel.add(countLabel);

        headerPanel.add(leftPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(createAddButton());
        rightPanel.add(createMenuButton());
        headerPanel.add(rightPanel, BorderLayout.EAST);

        CategoryDragListener categoryDragListener = new CategoryDragListener(dragDropManager, group);
        headerPanel.addMouseListener(categoryDragListener);
        headerPanel.addMouseMotionListener(categoryDragListener);

        headerPanel.addMouseListener(new MouseAdapter()
        {
            private long pressTime = 0;
            private Point pressPoint = null;

            @Override
            public void mousePressed(MouseEvent e) { pressTime = System.currentTimeMillis(); pressPoint = e.getPoint(); }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (pressPoint != null)
                {
                    long duration = System.currentTimeMillis() - pressTime;
                    int dx = Math.abs(e.getPoint().x - pressPoint.x);
                    int dy = Math.abs(e.getPoint().y - pressPoint.y);
                    if (duration < 300 && dx < 5 && dy < 5 && e.getButton() == MouseEvent.BUTTON1)
                    {
                        toggleCollapsed();
                        Component[] components = leftPanel.getComponents();
                        if (components.length > 0 && components[0] instanceof JLabel)
                        {
                            ((JLabel) components[0]).setText(collapsed ? "\u25B6" : "\u25BC");
                        }
                    }
                }
                pressPoint = null;
            }

            @Override
            public void mouseEntered(MouseEvent e) { headerPanel.setBackground(Theme.GROUP_HEADER_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { headerPanel.setBackground(Theme.GROUP_HEADER); }
        });

        return headerPanel;
    }

    private JButton createAddButton()
    {
        JButton button = new JButton("+");
        button.setBackground(Theme.GROUP_HEADER);
        button.setForeground(Theme.TEXT_SECONDARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(28, Theme.BUTTON_HEIGHT_SM));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("Add account to this category");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
        button.addActionListener(e -> formHandler.toggleAddForm(collapsed, this));
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e) { e.consume(); }

            @Override
            public void mouseEntered(MouseEvent e) { button.setBackground(Theme.BUTTON_PRIMARY); button.setForeground(Theme.TEXT_PRIMARY); }

            @Override
            public void mouseExited(MouseEvent e) { button.setBackground(Theme.GROUP_HEADER); button.setForeground(Theme.TEXT_SECONDARY); }
        });
        return button;
    }

    private JButton createMenuButton()
    {
        JButton button = new JButton("\u22EE");
        button.setBackground(Theme.GROUP_HEADER);
        button.setForeground(Theme.TEXT_SECONDARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(24, Theme.BUTTON_HEIGHT_SM));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPopupMenu menu = formHandler.createContextMenu();
        button.addActionListener(e -> menu.show(button, 0, button.getHeight()));
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e) { e.consume(); }

            @Override
            public void mouseEntered(MouseEvent e) { button.setBackground(Theme.GROUP_HEADER_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { button.setBackground(Theme.GROUP_HEADER); }
        });
        return button;
    }

    private void toggleCollapsed()
    {
        collapsed = !collapsed;
        group.setCollapsed(collapsed);
        plugin.getAccountStorage().saveGroup(group);
        updateCollapsedState();
    }

    private void updateCollapsedState()
    {
        contentWrapper.setVisible(!collapsed);
        chevronLabel.setText(collapsed ? "\u25B6" : "\u25BC");
        revalidate();
        repaint();
    }
}
