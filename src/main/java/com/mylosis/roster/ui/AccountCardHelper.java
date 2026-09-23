package com.mylosis.roster.ui;

import com.mylosis.roster.RosterConfig;
import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.ui.components.AccountTypeBadge;
import com.mylosis.roster.ui.components.SafeText;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.util.Arrays;

/**
 * Shared building blocks for account cards in both list and grid view.
 */
final class AccountCardHelper
{
    private AccountCardHelper() {} // Prevent instantiation

    /**
     * Creates a small green dot indicating the account is currently logged in.
     */
    static JPanel createOnlineDot()
    {
        int size = Theme.ONLINE_DOT_SIZE;
        JPanel dot = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ONLINE_INDICATOR);
                g2.fillOval(0, 0, size, size);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(size, size));
        dot.setMaximumSize(new Dimension(size, size));
        dot.setToolTipText("Currently logged in");
        return dot;
    }

    /**
     * A plain-text label showing at most {@code maxChars} characters, cut at a
     * word boundary where possible, with the full text as its tooltip.
     */
    static JLabel truncatingLabel(String text, Color color, Font font, int maxChars)
    {
        String display = text;
        boolean truncated = text != null && text.length() > maxChars;
        if (truncated)
        {
            String trimmed = text.substring(0, maxChars);
            int lastSpace = trimmed.lastIndexOf(' ');
            display = (lastSpace > maxChars / 2 ? trimmed.substring(0, lastSpace) : trimmed) + "...";
        }
        JLabel label = SafeText.plain(new JLabel(display));
        label.setForeground(color);
        label.setFont(font);
        if (truncated) label.setToolTipText(SafeText.htmlSafe(text));
        return label;
    }

    /**
     * Revalidates a component and repaints its enclosing scroll pane, the
     * layout boundary for everything inside the card list.
     */
    static void revalidateScrollPane(Component component)
    {
        component.revalidate();
        Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, component);
        if (scrollPane != null)
        {
            scrollPane.revalidate();
            scrollPane.repaint();
        }
        else
        {
            component.repaint();
        }
    }

    /**
     * Formats a millisecond timestamp as a coarse relative-time string
     * (e.g. "just now", "5m ago", "3h ago", "2d ago", "4mo ago", "1y ago").
     * Returns null for null or future-dated input.
     */
    static String formatTimeAgo(Long epochMillis)
    {
        if (epochMillis == null)
        {
            return null;
        }
        long diff = System.currentTimeMillis() - epochMillis;
        if (diff < 0)
        {
            return null;
        }

        long minute = 60_000L;
        long hour = 60 * minute;
        long day = 24 * hour;
        long month = 30 * day;
        long year = 365 * day;

        if (diff < 2 * minute) return "just now";
        if (diff < hour)       return (diff / minute) + "m ago";
        if (diff < day)        return (diff / hour)   + "h ago";
        if (diff < month)      return (diff / day)    + "d ago";
        if (diff < year)       return (diff / month)  + "mo ago";
        return (diff / year) + "y ago";
    }

    /**
     * The name to show on a card under the current privacy settings.
     */
    static String resolveDisplayName(Account account, RosterConfig config)
    {
        String displayName;
        if (config.hideAlias() && config.hideLogin())
        {
            displayName = "Account";
        }
        else if (config.hideAlias())
        {
            displayName = account.getUsername();
        }
        else
        {
            displayName = account.getDisplayName();
        }
        return displayName != null ? displayName : account.getUsername();
    }

    /** Whether {@code account} is the character currently logged in on this client. */
    static boolean isOnline(RosterPlugin plugin, Account account)
    {
        String loggedIn = plugin.getLoggedInName();
        // Prefer the learned in-game character name; the display name only
        // matches by coincidence when the alias equals the character name.
        String matchName = account.getCharacterName() != null
            ? account.getCharacterName()
            : account.getDisplayName();
        return loggedIn != null && loggedIn.equalsIgnoreCase(matchName);
    }

    /**
     * The clickable account-type badge. Picking a type saves it and refreshes
     * just this card.
     */
    static AccountTypeBadge createTypeBadge(RosterPlugin plugin, RosterPanel panel, Account account, int size)
    {
        return new AccountTypeBadge(account.resolveAccountType(), size, newType ->
        {
            account.ensureMetadata().setAccountType(newType);
            plugin.getAccountStorage().saveAccount(account);
            panel.refreshAccount(account.getId());
        });
    }

    /**
     * Adds {@code listener} to {@code root} and every descendant that doesn't
     * already have it, so it can be re-run after a card rebuilds its content.
     * With {@code skipControls}, buttons, badges, and fields (which handle
     * their own clicks) and their children are left out.
     */
    static void attachToTree(Component root, MouseAdapter listener, boolean skipControls)
    {
        if (!Arrays.asList(root.getMouseListeners()).contains(listener))
        {
            root.addMouseListener(listener);
            root.addMouseMotionListener(listener);
        }
        if (root instanceof Container)
        {
            for (Component child : ((Container) root).getComponents())
            {
                if (!skipControls || !isInteractive(child))
                {
                    attachToTree(child, listener, skipControls);
                }
            }
        }
    }

    private static boolean isInteractive(Component c)
    {
        return c instanceof AbstractButton || c instanceof AccountTypeBadge
            || c instanceof JTextComponent || c instanceof JComboBox;
    }
}
