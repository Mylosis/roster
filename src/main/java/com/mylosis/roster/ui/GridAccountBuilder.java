package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Builds the compact grid card layout for AccountCardPanel when grid view is enabled.
 * Text layout with a small 3-dot menu button in the top-right corner.
 */
public class GridAccountBuilder
{
    private GridAccountBuilder() {}

    public static final int GRID_CARD_HEIGHT = 56;

    public static void build(JPanel card, RosterPlugin plugin, Account profile, Runnable onEdit)
    {
        card.setLayout(new BorderLayout(0, 0));

        // Centered text with overlaid menu button in top-right
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        String displayName = AccountCardHelper.resolveDisplayName(profile, plugin.getConfig());

        JLabel nameLabel = new JLabel(truncate(displayName, 14));
        nameLabel.setForeground(Theme.TEXT_PRIMARY);
        nameLabel.setFont(Theme.fontBold(card.getFont(), Theme.FONT_SIZE_BODY));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        if (displayName.length() > 14) nameLabel.setToolTipText(displayName);
        inner.add(nameLabel);

        String subtitle = getSubtitle(plugin, profile);
        if (subtitle != null)
        {
            inner.add(Box.createVerticalStrut(1));
            JLabel subLabel = new JLabel(truncate(subtitle, 14));
            subLabel.setForeground(Theme.TEXT_SECONDARY);
            subLabel.setFont(Theme.fontRegular(card.getFont(), Theme.FONT_SIZE_SMALL));
            subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            subLabel.setHorizontalAlignment(SwingConstants.CENTER);
            if (subtitle.length() > 14) subLabel.setToolTipText(subtitle);
            inner.add(subLabel);
        }

        content.add(inner);
        card.add(content, BorderLayout.CENTER);

        // 3-dot menu button on right, with matching spacer on left to keep text centered
        int btnSize = 16;
        JButton menuBtn = new JButton("\u22EE");
        menuBtn.setBackground(Theme.CARD_BACKGROUND);
        menuBtn.setForeground(Theme.TEXT_SECONDARY);
        menuBtn.setFocusPainted(false);
        menuBtn.setBorderPainted(false);
        menuBtn.setPreferredSize(new Dimension(btnSize, btnSize));
        menuBtn.setFont(menuBtn.getFont().deriveFont(12f));
        menuBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        menuBtn.setToolTipText("More options");

        AccountCardActions actions = new AccountCardActions(plugin, profile, onEdit);
        JPopupMenu menu = actions.createContextMenu();
        menuBtn.addActionListener(e -> menu.show(menuBtn, 0, menuBtn.getHeight()));
        menuBtn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { menuBtn.setBackground(Theme.CARD_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { menuBtn.setBackground(Theme.CARD_BACKGROUND); }
        });

        JPanel btnPanel = new JPanel(new GridBagLayout());
        btnPanel.setOpaque(false);
        btnPanel.setPreferredSize(new Dimension(btnSize + 4, 0));
        btnPanel.add(menuBtn);
        card.add(btnPanel, BorderLayout.EAST);

        // Matching spacer on left to balance the button width
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(btnSize + 4, 0));
        card.add(spacer, BorderLayout.WEST);
    }

    private static String getSubtitle(RosterPlugin plugin, Account profile)
    {
        boolean hideNotes = plugin.getConfig().hideNotes();
        boolean hideLogin = plugin.getConfig().hideLogin();
        boolean hideAlias = plugin.getConfig().hideAlias();

        if (!hideNotes && profile.getMetadata() != null && profile.getMetadata().getNotes() != null
            && !profile.getMetadata().getNotes().isEmpty())
        {
            return profile.getMetadata().getNotes();
        }
        if (!hideLogin && !hideAlias && profile.getUsername() != null
            && !profile.getUsername().equals(profile.getAlias()))
        {
            return profile.getUsername();
        }
        return null;
    }

    private static String truncate(String text, int maxChars)
    {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) + "..." : text;
    }
}
