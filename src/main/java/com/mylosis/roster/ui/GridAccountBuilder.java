package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.ui.components.Controls;
import com.mylosis.roster.ui.components.SafeText;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Builds the body of a compact grid-view card: a centered name with its type
 * badge, one subtitle line, and a small menu button in the corner.
 */
final class GridAccountBuilder
{
    static final int GRID_CARD_HEIGHT = 56;

    private static final int NAME_MAX_CHARS = 12;
    private static final int SUBTITLE_MAX_CHARS = 14;
    private static final int BADGE_SIZE = 16;
    private static final int MENU_BUTTON_SIZE = 16;

    private GridAccountBuilder() {}

    static void build(AccountCardPanel card, RosterPlugin plugin, RosterPanel panel, Account account, Runnable onEdit)
    {
        card.setLayout(new BorderLayout(0, 0));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        String displayName = AccountCardHelper.resolveDisplayName(account, plugin.getConfig());

        // Name + clickable type badge, centered as a unit
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.SPACING_SM, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameRow.add(truncatedLabel(displayName, NAME_MAX_CHARS, Theme.TEXT_PRIMARY,
            Theme.fontBold(card.getFont(), Theme.FONT_SIZE_BODY)));
        nameRow.add(AccountCardHelper.createTypeBadge(plugin, panel, account, BADGE_SIZE));
        inner.add(nameRow);

        String subtitle = subtitle(plugin.getConfig(), account);
        if (subtitle != null)
        {
            inner.add(Box.createVerticalStrut(1));
            JLabel subLabel = truncatedLabel(subtitle, SUBTITLE_MAX_CHARS, Theme.TEXT_SECONDARY,
                Theme.fontRegular(card.getFont(), Theme.FONT_SIZE_SMALL));
            subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            subLabel.setHorizontalAlignment(SwingConstants.CENTER);
            inner.add(subLabel);
        }

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.add(inner);
        card.add(content, BorderLayout.CENTER);

        // Menu button on the right, with a matching spacer on the left so the
        // text stays centered.
        JButton menuButton = Controls.ghostButton("\u22EE", Theme.TEXT_SECONDARY, Theme.CARD_HOVER, Theme.TEXT_PRIMARY);
        menuButton.setPreferredSize(new Dimension(MENU_BUTTON_SIZE, MENU_BUTTON_SIZE));
        menuButton.setFont(menuButton.getFont().deriveFont(12f));
        menuButton.setToolTipText("More options");
        Controls.attachLazyMenu(menuButton, () -> new AccountCardActions(plugin, panel, account, onEdit).createContextMenu());

        JPanel buttonHolder = new JPanel(new GridBagLayout());
        buttonHolder.setOpaque(false);
        buttonHolder.setPreferredSize(new Dimension(MENU_BUTTON_SIZE + 4, 0));
        buttonHolder.add(menuButton);
        card.add(buttonHolder, BorderLayout.EAST);

        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(MENU_BUTTON_SIZE + 4, 0));
        card.add(spacer, BorderLayout.WEST);
    }

    private static JLabel truncatedLabel(String text, int maxChars, Color color, Font font)
    {
        String value = text != null ? text : "";
        boolean truncated = value.length() > maxChars;
        JLabel label = SafeText.plain(new JLabel(truncated ? value.substring(0, maxChars) + "..." : value));
        label.setForeground(color);
        label.setFont(font);
        if (truncated) label.setToolTipText(SafeText.htmlSafe(value));
        return label;
    }

    /** The most useful second line allowed by the privacy settings: notes, then login, then last online. */
    private static String subtitle(RosterConfig config, Account account)
    {
        AccountMetadata meta = account.getMetadata();
        if (!config.hideNotes() && meta != null && meta.getNotes() != null && !meta.getNotes().isEmpty())
        {
            return meta.getNotes();
        }
        // Skip the login when the name line already shows it (alias hidden or unset).
        if (!config.hideLogin() && !config.hideAlias() && account.getUsername() != null
            && !account.getUsername().equals(account.getDisplayName()))
        {
            return account.getUsername();
        }
        if (!config.hideLastOnline() && meta != null)
        {
            return AccountCardHelper.formatTimeAgo(meta.getLastOnlineAt());
        }
        return null;
    }
}
