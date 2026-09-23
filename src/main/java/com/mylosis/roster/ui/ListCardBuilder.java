package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.ui.components.AccountTypeBadge;
import com.mylosis.roster.ui.components.SafeText;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Builds the text column of a list-view card: name row (online dot, name,
 * type badge), login, notes preview, and the last-online stamp, each subject
 * to the privacy settings. Rebuilt in place when the card's state changes.
 */
final class ListCardBuilder
{
    private static final int NAME_MAX_CHARS = 15;
    /** Shorter when the online dot takes room in the name row. */
    private static final int NAME_MAX_CHARS_ONLINE = 13;
    private static final int LOGIN_MAX_CHARS = 25;
    private static final int NOTES_MAX_CHARS = 22;

    private ListCardBuilder() {}

    static JPanel buildInfo(RosterPlugin plugin, RosterPanel panel, Account account, Font baseFont)
    {
        RosterConfig config = plugin.getConfig();
        JPanel info = new JPanel(new GridBagLayout());
        info.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        String displayName = AccountCardHelper.resolveDisplayName(account, config);
        boolean online = AccountCardHelper.isOnline(plugin, account);

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_SM, 0));
        nameRow.setOpaque(false);
        if (online)
        {
            nameRow.add(AccountCardHelper.createOnlineDot());
        }
        nameRow.add(AccountCardHelper.truncatingLabel(displayName, Theme.TEXT_PRIMARY,
            Theme.fontBold(baseFont, Theme.FONT_SIZE_HEADING), online ? NAME_MAX_CHARS_ONLINE : NAME_MAX_CHARS));
        nameRow.add(AccountCardHelper.createTypeBadge(plugin, panel, account, AccountTypeBadge.DEFAULT_SIZE));
        info.add(nameRow, gbc);
        gbc.gridy++;

        String username = account.getUsername();
        if (!config.hideLogin() && username != null && !username.equals(displayName))
        {
            info.add(AccountCardHelper.truncatingLabel(username, Theme.TEXT_SECONDARY,
                Theme.fontRegular(baseFont, Theme.FONT_SIZE_HEADING), LOGIN_MAX_CHARS), gbc);
            gbc.gridy++;
        }

        AccountMetadata meta = account.getMetadata();
        String notes = meta != null ? meta.getNotes() : null;
        if (!config.hideNotes() && notes != null && !notes.isEmpty())
        {
            JLabel notesLabel = AccountCardHelper.truncatingLabel(notes, Theme.TEXT_SECONDARY,
                Theme.fontRegular(baseFont, Theme.FONT_SIZE_BODY), NOTES_MAX_CHARS);
            notesLabel.setToolTipText("<html><body style='width: 200px'>" + SafeText.escapeHtml(notes) + "</body></html>");
            info.add(notesLabel, gbc);
            gbc.gridy++;
        }

        // Hidden while online: the green dot already says so, and "just now"
        // would be misleading after hours of play.
        String lastOnline = meta != null ? AccountCardHelper.formatTimeAgo(meta.getLastOnlineAt()) : null;
        if (!online && !config.hideLastOnline() && lastOnline != null)
        {
            JLabel lastOnlineLabel = new JLabel(lastOnline);
            lastOnlineLabel.setForeground(Theme.TEXT_MUTED);
            lastOnlineLabel.setFont(Theme.fontRegular(baseFont, Theme.FONT_SIZE_TINY));
            lastOnlineLabel.setToolTipText("Last seen logged in on this client");
            info.add(lastOnlineLabel, gbc);
        }

        return info;
    }
}
