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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Small clickable badge representing an {@link AccountType}, rendered with
 * OSRS sprites:
 *
 * <ul>
 *   <li>The five Ironman variants (Ironman, HCIM, UIM, GIM, UGIM) use the
 *       official in-game chat badges.</li>
 *   <li>Main, Skiller, and Pure are not official in-game account types, so
 *       they use recognisable item/skill icons.</li>
 * </ul>
 *
 * Clicking opens a {@link JPopupMenu} listing all types with their matching
 * sprites. Selecting one invokes the supplied callback.
 *
 * Resources live under {@code /com/mylosis/roster/badges/}. EDT only.
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

    /** Sentinel for "load failed", so a missing resource is not retried on every paint. */
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /** Source sprites, loaded once. */
    private static final Map<AccountType, BufferedImage> SOURCE_CACHE = new EnumMap<>(AccountType.class);
    /** Sprites pre-scaled per display size, so painting is a plain blit. */
    private static final Map<String, BufferedImage> SCALED_CACHE = new HashMap<>();

    private AccountType type;
    private final int size;
    private final Consumer<AccountType> onChange;

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
        updateTooltip();
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

    private void setType(AccountType newType)
    {
        this.type = newType != null ? newType : AccountType.MAIN;
        updateTooltip();
        repaint();
    }

    private void updateTooltip()
    {
        setToolTipText(type.getDisplayName() + " (click to change)");
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (!drawSprite(g, type, 0, 0, size))
        {
            // Fallback if the resource failed to load: a dim grey dot, so the
            // click target is still visible.
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(110, 110, 110));
            g2.fillOval(0, 0, size - 1, size - 1);
            g2.dispose();
        }
    }

    /**
     * Draws the sprite centered in a {@code box x box} square at (x, y).
     * Scaled once per device-pixel size, so painting is a plain blit that
     * stays sharp on HiDPI displays. Returns false if the sprite is unavailable.
     */
    private static boolean drawSprite(Graphics g, AccountType t, int x, int y, int box)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        double scale = g2.getTransform().getScaleX();
        if (scale <= 0)
        {
            scale = 1;
        }
        BufferedImage icon = scaledIcon(t, (int) Math.round(box * scale));
        if (icon != null)
        {
            g2.translate(x + (box - icon.getWidth() / scale) / 2, y + (box - icon.getHeight() / scale) / 2);
            g2.scale(1 / scale, 1 / scale);
            g2.drawImage(icon, 0, 0, null);
        }
        g2.dispose();
        return icon != null;
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
        boolean active = t == type;
        item.setForeground(active ? Theme.ACCENT_ORANGE : Theme.TEXT_PRIMARY);
        item.setFont(active
            ? Theme.fontBold(item.getFont(), Theme.FONT_SIZE_SMALL)
            : Theme.fontRegular(item.getFont(), Theme.FONT_SIZE_SMALL));
        item.setBorder(new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
        item.addActionListener(e ->
        {
            setType(t);
            onChange.accept(t);
        });
        return item;
    }

    /**
     * Reusable icon matching the badge rendering. Used by the type dropdown
     * (InlineAccountForm) and the picker menu so both are self-describing.
     */
    public static Icon swatchIcon(AccountType t, int size)
    {
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                drawSprite(g, t, x, y, size);
            }

            @Override
            public int getIconWidth() { return size; }

            @Override
            public int getIconHeight() { return size; }
        };
    }

    /** The sprite scaled to fit a {@code box x box} square, preserving aspect ratio; null if unavailable. */
    private static BufferedImage scaledIcon(AccountType t, int box)
    {
        BufferedImage scaled = SCALED_CACHE.computeIfAbsent(t.name() + '@' + box, k -> {
            BufferedImage source = sourceIcon(t);
            return source != null ? scale(source, box) : MISSING;
        });
        return scaled == MISSING ? null : scaled;
    }

    private static BufferedImage sourceIcon(AccountType t)
    {
        BufferedImage cached = SOURCE_CACHE.get(t);
        if (cached != null)
        {
            return cached == MISSING ? null : cached;
        }
        String resource = RESOURCE.get(t);
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(AccountTypeBadge.class, RESOURCE_BASE + resource);
            SOURCE_CACHE.put(t, img);
            return img;
        }
        catch (Exception e)
        {
            SOURCE_CACHE.put(t, MISSING);
            log.warn("Failed to load badge icon {}", resource, e);
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage img, int box)
    {
        int iw = img.getWidth();
        int ih = img.getHeight();
        if (iw <= 0 || ih <= 0)
        {
            return MISSING;
        }
        double s = Math.min((double) box / iw, (double) box / ih);
        int w = Math.max(1, (int) Math.round(iw * s));
        int h = Math.max(1, (int) Math.round(ih * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(img, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }
}
