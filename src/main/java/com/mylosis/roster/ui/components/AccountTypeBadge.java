package com.mylosis.roster.ui.components;

import com.mylosis.roster.model.AccountType;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Small clickable badge representing an {@link AccountType}, rendered with
 * the actual OSRS sprites from the wiki:
 *
 * <ul>
 *   <li>The six Ironman variants (Ironman, HCIM, UIM, GIM, UGIM) use the
 *       official in-game chat badges.</li>
 *   <li>Main, Skiller, and Pure are not official in-game account types,
 *       so we use community-recognisable item/skill icons: a Bronze sword
 *       for Main, a Bronze pickaxe for Skiller, and a Granite maul for Pure.</li>
 * </ul>
 *
 * Clicking opens a {@link JPopupMenu} listing all 8 types with their
 * matching sprite icons. Selecting one invokes the supplied callback.
 *
 * Resources live under {@code /com/mylosis/roster/badges/}.
 */
@Slf4j
public class AccountTypeBadge extends JComponent
{
    public static final int DEFAULT_SIZE = 20;

    private static final String RESOURCE_BASE = "/com/mylosis/roster/badges/";

    private static final Map<AccountType, String> RESOURCE = new EnumMap<>(AccountType.class);
    static
    {
        RESOURCE.put(AccountType.MAIN,                   "main.png");
        RESOURCE.put(AccountType.IRONMAN,                "ironman.png");
        RESOURCE.put(AccountType.HARDCORE_IRONMAN,       "hcim.png");
        RESOURCE.put(AccountType.ULTIMATE_IRONMAN,       "uim.png");
        RESOURCE.put(AccountType.GROUP_IRONMAN,          "gim.png");
        RESOURCE.put(AccountType.UNRANKED_GROUP_IRONMAN, "ugim.png");
        RESOURCE.put(AccountType.SKILLER,                "skiller.png");
        RESOURCE.put(AccountType.PURE,                   "pure.png");
    }

    /** Class-loaded once; image is cheap to re-use across cards/popovers. */
    private static final Map<AccountType, BufferedImage> CACHE = new EnumMap<>(AccountType.class);

    private static BufferedImage iconFor(AccountType t)
    {
        if (t == null)
        {
            return null;
        }
        BufferedImage cached = CACHE.get(t);
        if (cached != null)
        {
            // MISSING means a previous load failed — report "no icon" so the
            // caller paints the same grey-dot fallback it always painted.
            return cached == MISSING ? null : cached;
        }
        String resource = RESOURCE.get(t);
        if (resource == null)
        {
            return null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(AccountTypeBadge.class, RESOURCE_BASE + resource);
            CACHE.put(t, img);
            return img;
        }
        catch (Exception e)
        {
            // Negative-cache the failure: paintComponent calls iconFor on
            // every repaint, and an uncached miss meant re-attempting the
            // resource load (and swallowing a fresh exception) per paint.
            // MISSING is non-null so the cache hit short-circuits; painting
            // treats it as "no icon" and draws the grey-dot fallback.
            CACHE.put(t, MISSING);
            log.warn("Failed to load badge icon {}", resource, e);
            return null;
        }
    }

    /** Sentinel for "load failed" — see iconFor. */
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

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
        setToolTipText(this.type.getDisplayName() + " — click to change");
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
        setToolTipText(this.type.getDisplayName() + " — click to change");
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        BufferedImage icon = iconFor(type);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        if (icon == null)
        {
            // Defensive fallback if the resource fails to load — a dim grey dot
            // so the click target is still visible.
            g2.setColor(new Color(110, 110, 110));
            g2.fillOval(0, 0, size - 1, size - 1);
        }
        else
        {
            drawIconCentered(g2, icon, 0, 0, size);
        }
        g2.dispose();
    }

    /**
     * Scale the icon to fit within a {@code box × box} square, preserving
     * aspect ratio, and draw it centered at ({@code x},{@code y}).
     */
    private static void drawIconCentered(Graphics2D g2, BufferedImage img, int x, int y, int box)
    {
        int iw = img.getWidth();
        int ih = img.getHeight();
        if (iw <= 0 || ih <= 0)
        {
            return;
        }
        double s = Math.min((double) box / iw, (double) box / ih);
        int w = Math.max(1, (int) Math.round(iw * s));
        int h = Math.max(1, (int) Math.round(ih * s));
        int dx = x + (box - w) / 2;
        int dy = y + (box - h) / 2;
        g2.drawImage(img, dx, dy, w, h, null);
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
        item.setIcon(swatchIcon(t, 14));
        item.setIconTextGap(8);
        item.setBackground(Theme.BACKGROUND_DARKER);
        item.setForeground(Theme.TEXT_PRIMARY);
        item.setFont(Theme.fontRegular(item.getFont(), Theme.FONT_SIZE_SMALL));
        item.setBorder(new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
        if (t == type)
        {
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
     * Reusable icon swatch matching the badge rendering. Used by dropdown
     * renderers (InlineAccountForm) and menu items so the picker UI is
     * self-describing.
     */
    public static Icon swatchIcon(AccountType t, int size)
    {
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                BufferedImage icon = iconFor(t);
                if (icon == null)
                {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
                drawIconCentered(g2, icon, x, y, size);
                g2.dispose();
            }

            @Override
            public int getIconWidth() { return size; }

            @Override
            public int getIconHeight() { return size; }
        };
    }
}
