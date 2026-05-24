package com.mylosis.roster.ui.components;

import com.mylosis.roster.model.AccountType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Small clickable circular badge representing an {@link AccountType}.
 *
 * <ul>
 *   <li>Non-MAIN types render as a coloured circle with a single letter
 *       (I, H, U, G, G?, S, P).</li>
 *   <li>MAIN renders as a dashed-outline "+" placeholder so users still
 *       have a discoverable click target on otherwise-clean Main cards.</li>
 * </ul>
 *
 * Clicking opens a {@link JPopupMenu} listing all 8 types with the same
 * swatch icons. Selecting one invokes the callback the caller supplied.
 */
public class AccountTypeBadge extends JComponent
{
    public static final int DEFAULT_SIZE = 14;

    private AccountType type;
    private final int size;
    private final Consumer<AccountType> onChange;

    public AccountTypeBadge(AccountType type, Consumer<AccountType> onChange)
    {
        this(type, DEFAULT_SIZE, onChange);
    }

    public AccountTypeBadge(AccountType type, int size, Consumer<AccountType> onChange)
    {
        this.type = type != null ? type : AccountType.MAIN;
        this.size = size;
        this.onChange = onChange;

        Dimension dim = new Dimension(size, size);
        setPreferredSize(dim);
        setMaximumSize(dim);
        setMinimumSize(dim);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(tooltipFor(this.type));
        setOpaque(false);

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1)
                {
                    showPicker();
                    e.consume();
                }
            }
        });
    }

    public void setType(AccountType newType)
    {
        this.type = newType != null ? newType : AccountType.MAIN;
        setToolTipText(tooltipFor(this.type));
        repaint();
    }

    private static String tooltipFor(AccountType t)
    {
        return t == AccountType.MAIN
            ? "Set account type"
            : t.getDisplayName() + " — click to change";
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (type == AccountType.MAIN)
        {
            paintPlaceholder(g2, 0, 0, size);
        }
        else
        {
            paintFilled(g2, type, 0, 0, size, getFont());
        }
        g2.dispose();
    }

    private static void paintPlaceholder(Graphics2D g2, int x, int y, int size)
    {
        g2.setColor(new Color(110, 110, 110));
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            1f, new float[]{2f, 2f}, 0f));
        g2.drawOval(x, y, size - 1, size - 1);

        // Faint "+" in the middle for affordance
        g2.setStroke(new BasicStroke(1f));
        int mid = size / 2;
        int half = Math.max(2, size / 5);
        g2.drawLine(x + mid - half, y + mid, x + mid + half, y + mid);
        g2.drawLine(x + mid, y + mid - half, x + mid, y + mid + half);
    }

    private static void paintFilled(Graphics2D g2, AccountType t, int x, int y, int size, Font baseFont)
    {
        g2.setColor(backgroundFor(t));
        g2.fillOval(x, y, size, size);

        g2.setColor(new Color(0, 0, 0, 90));
        g2.drawOval(x, y, size - 1, size - 1);

        String letter = letterFor(t);
        if (letter.isEmpty())
        {
            return;
        }
        g2.setColor(foregroundFor(t));
        Font font = baseFont.deriveFont(Font.BOLD, size * 0.55f);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int lw = fm.stringWidth(letter);
        int la = fm.getAscent();
        g2.drawString(letter, x + (size - lw) / 2, y + (size + la) / 2 - 1);
    }

    private void showPicker()
    {
        if (onChange == null)
        {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BACKGROUND_DARKER);
        menu.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        for (AccountType t : AccountType.values())
        {
            menu.add(createMenuItem(t));
        }
        menu.show(this, 0, getHeight());
    }

    private JMenuItem createMenuItem(AccountType t)
    {
        JMenuItem item = new JMenuItem(t.getDisplayName());
        item.setIcon(swatchIcon(t, 12));
        item.setIconTextGap(8);
        item.setBackground(Theme.BACKGROUND_DARKER);
        item.setForeground(Theme.TEXT_PRIMARY);
        item.setFont(Theme.fontRegular(item.getFont(), Theme.FONT_SIZE_SMALL));
        item.setBorder(new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
        if (t == type)
        {
            // Highlight the active choice with the brand accent
            item.setForeground(Theme.ACCENT_ORANGE);
            item.setFont(Theme.fontBold(item.getFont(), Theme.FONT_SIZE_SMALL));
        }
        item.addActionListener(e ->
        {
            setType(t);
            onChange.accept(t);
        });
        return item;
    }

    /**
     * A drawable swatch matching the badge styling for the given type.
     * Used in dropdowns and menu items so the picker UI is self-describing.
     */
    public static Icon swatchIcon(AccountType t, int size)
    {
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (t == AccountType.MAIN)
                {
                    paintPlaceholder(g2, x, y, size);
                }
                else
                {
                    Font baseFont = c != null && c.getFont() != null ? c.getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
                    paintFilled(g2, t, x, y, size, baseFont);
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() { return size; }

            @Override
            public int getIconHeight() { return size; }
        };
    }

    // ─── Type → visual mapping ─────────────────────────────────────────────

    private static String letterFor(AccountType t)
    {
        switch (t)
        {
            case IRONMAN:                return "I";
            case HARDCORE_IRONMAN:       return "H";
            case ULTIMATE_IRONMAN:       return "U";
            case GROUP_IRONMAN:          return "G";
            case UNRANKED_GROUP_IRONMAN: return "G?";
            case SKILLER:                return "S";
            case PURE:                   return "P";
            case MAIN:
            default:                     return "";
        }
    }

    private static Color backgroundFor(AccountType t)
    {
        switch (t)
        {
            case IRONMAN:                return new Color(170, 170, 170);
            case HARDCORE_IRONMAN:       return new Color(190,  40,  40);
            case ULTIMATE_IRONMAN:       return new Color(225, 225, 225);
            case GROUP_IRONMAN:          return new Color( 80, 160,  80);
            case UNRANKED_GROUP_IRONMAN: return new Color( 80, 160,  80, 153); // 60% alpha
            case SKILLER:                return new Color(220, 180,  80);
            case PURE:                   return new Color(200,  80,  80);
            default:                     return new Color(110, 110, 110);
        }
    }

    private static Color foregroundFor(AccountType t)
    {
        switch (t)
        {
            case HARDCORE_IRONMAN:
            case GROUP_IRONMAN:
            case UNRANKED_GROUP_IRONMAN:
            case PURE:
                return Color.WHITE;
            default:
                return new Color(15, 15, 15);
        }
    }
}
