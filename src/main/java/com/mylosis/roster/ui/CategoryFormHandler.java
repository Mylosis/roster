package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.SafeText;
import com.mylosis.roster.ui.components.Theme;
import lombok.Getter;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;

import javax.swing.*;
import java.awt.*;

/**
 * A category's add-account form and its header menu (rename, add account,
 * color, delete).
 */
class CategoryFormHandler
{
    private static final int SWATCH_SIZE = 12;

    private final RosterPlugin plugin;
    private final RosterPanel panel;
    private final ProfileGroup group;
    private final CategoryPanel categoryPanel;

    /** Hidden container for the add form, which is only built on first show. */
    @Getter
    private final JPanel formPanel;
    private InlineAccountForm addForm;

    CategoryFormHandler(RosterPlugin plugin, RosterPanel panel, ProfileGroup group, CategoryPanel categoryPanel)
    {
        this.plugin = plugin;
        this.panel = panel;
        this.group = group;
        this.categoryPanel = categoryPanel;

        formPanel = new JPanel(new BorderLayout());
        formPanel.setBackground(Theme.FORM_BACKGROUND);
        formPanel.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        formPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        formPanel.setVisible(false);
    }

    /** Shows or hides the add form, expanding the category first if needed. */
    void toggleAddForm()
    {
        if (formPanel.isVisible())
        {
            hideAddForm();
            return;
        }
        if (addForm == null)
        {
            addForm = new InlineAccountForm(plugin.getAccountStorage(), null, false, group.getId());
            addForm.setOnSave(account -> {
                plugin.getAccountStorage().saveAccount(account);
                hideAddForm();
                panel.rebuild();
            });
            addForm.setOnCancel(this::hideAddForm);
            formPanel.add(addForm, BorderLayout.CENTER);
        }
        if (categoryPanel.isCollapsed())
        {
            categoryPanel.toggleCollapsed();
        }
        formPanel.setVisible(true);
        addForm.focusUsernameField();
        AccountCardHelper.revalidateScrollPane(categoryPanel);
    }

    private void hideAddForm()
    {
        addForm.clearFields();
        formPanel.setVisible(false);
        AccountCardHelper.revalidateScrollPane(categoryPanel);
    }

    JPopupMenu createContextMenu()
    {
        JPopupMenu menu = new JPopupMenu();
        addItem(menu, "Rename", this::renameCategory);
        addItem(menu, "Add Account", this::toggleAddForm);
        menu.addSeparator();
        menu.add(createColorMenu());
        menu.addSeparator();
        addItem(menu, "Delete Category", this::confirmDelete).setForeground(Theme.BUTTON_DANGER);
        return menu;
    }

    private static JMenuItem addItem(JComponent menu, String label, Runnable action)
    {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        menu.add(item);
        return item;
    }

    private JMenu createColorMenu()
    {
        JMenu colorMenu = new JMenu("Set Color");
        for (int i = 0; i < Theme.CATEGORY_COLORS.length; i++)
        {
            Color color = Theme.CATEGORY_COLORS[i];
            JMenuItem swatch = addItem(colorMenu, Theme.CATEGORY_COLOR_NAMES[i], () -> saveColor(Theme.colorToHex(color)));
            swatch.setIcon(swatchIcon(color));
        }
        colorMenu.addSeparator();
        addItem(colorMenu, "Custom...", this::pickCustomColor);
        addItem(colorMenu, "Reset Color", () -> saveColor(null));
        return colorMenu;
    }

    private void pickCustomColor()
    {
        Color current = Theme.parseColor(group.getColor());
        RuneliteColorPicker picker = plugin.getColorPickerManager().create(
            SwingUtilities.getWindowAncestor(panel),
            current != null ? current : Theme.ACCENT_ORANGE,
            group.getName() + " Color",
            true // hide alpha slider
        );
        picker.setOnClose(chosen -> saveColor(Theme.colorToHex(chosen)));
        picker.setVisible(true);
    }

    private void saveColor(String hex)
    {
        group.setColor(hex);
        plugin.getAccountStorage().saveGroup(group);
        panel.rebuild();
    }

    private static Icon swatchIcon(Color color)
    {
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                g.setColor(color);
                g.fillRect(x, y, SWATCH_SIZE, SWATCH_SIZE);
                g.setColor(Theme.CARD_BORDER);
                g.drawRect(x, y, SWATCH_SIZE - 1, SWATCH_SIZE - 1);
            }

            @Override
            public int getIconWidth() { return SWATCH_SIZE; }

            @Override
            public int getIconHeight() { return SWATCH_SIZE; }
        };
    }

    private void renameCategory()
    {
        String newName = JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(panel),
            "Enter new category name:",
            group.getName()
        );
        if (newName != null && !newName.trim().isEmpty())
        {
            group.setName(newName.trim());
            plugin.getAccountStorage().saveGroup(group);
            panel.rebuild();
        }
    }

    private void confirmDelete()
    {
        int result = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(panel),
            "Delete category \"" + SafeText.singleLine(group.getName()) + "\"?\n"
                + "Accounts in this category will be moved to Uncategorized.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION)
        {
            plugin.getAccountStorage().deleteGroup(group.getId());
            panel.rebuild();
        }
    }
}
