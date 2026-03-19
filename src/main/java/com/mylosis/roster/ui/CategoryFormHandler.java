package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Handles the inline add-profile form within a CategoryPanel.
 * Also handles category rename and delete actions.
 */
public class CategoryFormHandler
{
    private final RosterPlugin plugin;
    private final ProfileGroup group;
    private final Runnable onToggleCollapsed;

    private JPanel addFormPanel;
    private InlineAccountForm addForm;
    private boolean addFormVisible = false;

    public CategoryFormHandler(RosterPlugin plugin, ProfileGroup group, Runnable onToggleCollapsed)
    {
        this.plugin = plugin;
        this.group = group;
        this.onToggleCollapsed = onToggleCollapsed;
    }

    public JPanel getOrCreateFormPanel()
    {
        if (addFormPanel == null)
        {
            addFormPanel = buildFormPanel();
        }
        return addFormPanel;
    }

    public void toggleAddForm(boolean isCollapsed, Component parent)
    {
        addFormVisible = !addFormVisible;
        addFormPanel.setVisible(addFormVisible);

        if (addFormVisible)
        {
            if (isCollapsed) onToggleCollapsed.run();
            addForm.focusUsernameField();
        }

        parent.revalidate();
        ((JComponent) parent).repaint();
    }

    public JPopupMenu createContextMenu()
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem renameItem = new JMenuItem("Rename");
        renameItem.addActionListener(e -> renameCategory());
        menu.add(renameItem);

        JMenuItem addAccountItem = new JMenuItem("Add Account");
        addAccountItem.addActionListener(e ->
            toggleAddForm(false, plugin.getPanel()));
        menu.add(addAccountItem);

        menu.addSeparator();
        JMenu colorMenu = new JMenu("Set Color");
            for (int i = 0; i < Theme.CATEGORY_COLORS.length; i++)
            {
                JMenuItem swatch = createColorSwatch(Theme.CATEGORY_COLORS[i], Theme.CATEGORY_COLOR_NAMES[i]);
                colorMenu.add(swatch);
            }
            colorMenu.addSeparator();
            JMenuItem customColor = new JMenuItem("Custom...");
            customColor.addActionListener(e ->
            {
                Color current = Theme.parseColor(group.getColor());
                net.runelite.client.ui.components.colorpicker.RuneliteColorPicker picker =
                    plugin.getColorPickerManager().create(
                        SwingUtilities.getWindowAncestor(plugin.getPanel()),
                        current != null ? current : Theme.ACCENT_ORANGE,
                        group.getName() + " Color",
                        true // hide alpha slider
                    );
                picker.setOnClose(chosen -> {
                    group.setColor(Theme.colorToHex(chosen));
                    plugin.getAccountStorage().saveGroup(group);
                    plugin.getPanel().rebuild();
                });
                picker.setVisible(true);
            });
            colorMenu.add(customColor);
            JMenuItem resetColor = new JMenuItem("Reset Color");
            resetColor.addActionListener(e -> {
                group.setColor(null);
                plugin.getAccountStorage().saveGroup(group);
                plugin.getPanel().rebuild();
            });
            colorMenu.add(resetColor);
        menu.add(colorMenu);

        menu.addSeparator();

        JMenuItem deleteItem = new JMenuItem("Delete Category");
        deleteItem.setForeground(Theme.BUTTON_DANGER);
        deleteItem.addActionListener(e -> confirmDelete());
        menu.add(deleteItem);

        return menu;
    }

    private JMenuItem createColorSwatch(Color color, String name)
    {
        // Create a small colored icon for the menu item
        ImageIcon icon = new ImageIcon(new java.awt.image.BufferedImage(12, 12, java.awt.image.BufferedImage.TYPE_INT_ARGB))
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                g.setColor(color);
                g.fillRect(x, y, 12, 12);
                g.setColor(Theme.CARD_BORDER);
                g.drawRect(x, y, 11, 11);
            }

            @Override
            public int getIconWidth() { return 12; }

            @Override
            public int getIconHeight() { return 12; }
        };

        JMenuItem item = new JMenuItem(name, icon);
        item.addActionListener(e -> {
            group.setColor(Theme.colorToHex(color));
            plugin.getAccountStorage().saveGroup(group);
            plugin.getPanel().rebuild();
        });
        return item;
    }

    private JPanel buildFormPanel()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.FORM_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.CARD_BORDER, 1),
            new EmptyBorder(0, 0, 0, 0)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        panel.setVisible(false);

        addForm = new InlineAccountForm(plugin, null, false, group.getId());
        addForm.setOnSave(profile -> {
            plugin.getAccountStorage().saveAccount(profile);
            addForm.clearFields();
            addFormPanel.setVisible(false);
            addFormVisible = false;
            plugin.getPanel().rebuild();
        });
        addForm.setOnCancel(() -> {
            addForm.clearFields();
            addFormPanel.setVisible(false);
            addFormVisible = false;
        });

        panel.add(addForm, BorderLayout.CENTER);
        return panel;
    }

    private void renameCategory()
    {
        String newName = JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(plugin.getPanel()),
            "Enter new category name:",
            group.getName()
        );
        if (newName != null && !newName.trim().isEmpty())
        {
            group.setName(newName.trim());
            plugin.getAccountStorage().saveGroup(group);
            plugin.getPanel().rebuild();
        }
    }

    private void confirmDelete()
    {
        int result = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(plugin.getPanel()),
            "Delete category \"" + group.getName() + "\"?\n" +
                "Accounts in this category will be moved to Uncategorized.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION)
        {
            plugin.getAccountStorage().deleteGroup(group.getId());
            plugin.getPanel().rebuild();
        }
    }
}
