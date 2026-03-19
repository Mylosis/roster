package com.mylosis.roster.ui;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.Theme;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Manages the inline add-profile and add-category forms.
 */
@Slf4j
public class PanelFormManager
{
    private final RosterPlugin plugin;
    private final AccountStorage storage;
    private final Runnable onRebuild;
    private final Runnable onRevalidate;

    private JPanel addAccountFormPanel;
    private InlineAccountForm addAccountForm;
    private boolean addFormVisible = false;

    private JPanel addCategoryFormPanel;
    private JTextField categoryNameField;
    private boolean addCategoryFormVisible = false;

    public PanelFormManager(RosterPlugin plugin, Runnable onRebuild, Runnable onRevalidate)
    {
        this.plugin = plugin;
        this.storage = plugin.getAccountStorage();
        this.onRebuild = onRebuild;
        this.onRevalidate = onRevalidate;
    }

    public JPanel getAddProfileFormPanel()
    {
        if (addAccountFormPanel == null)
        {
            addAccountFormPanel = buildAddProfileFormPanel();
        }
        return addAccountFormPanel;
    }

    public JPanel getAddCategoryFormPanel()
    {
        if (addCategoryFormPanel == null)
        {
            addCategoryFormPanel = buildAddCategoryFormPanel();
        }
        return addCategoryFormPanel;
    }

    public boolean isAddFormVisible()
    {
        return addFormVisible;
    }

    public void hideAddForm()
    {
        if (addFormVisible)
        {
            addAccountFormPanel.setVisible(false);
            addFormVisible = false;
        }
    }

    public void toggleAddProfileForm(Runnable collapseExpandedCard)
    {
        if (addAccountFormPanel == null)
        {
            log.error("addAccountFormPanel is null!");
            return;
        }

        if (addCategoryFormVisible)
        {
            addCategoryFormPanel.setVisible(false);
            addCategoryFormVisible = false;
        }

        if (!addFormVisible)
        {
            collapseExpandedCard.run();
        }

        addFormVisible = !addFormVisible;
        addAccountFormPanel.setVisible(addFormVisible);

        if (addFormVisible)
        {
            SwingUtilities.invokeLater(() -> {
                if (addAccountForm != null)
                {
                    addAccountForm.focusUsernameField();
                }
            });
        }

        onRevalidate.run();
    }

    public void toggleAddCategoryForm(Runnable collapseExpandedCard)
    {
        if (addFormVisible)
        {
            addAccountFormPanel.setVisible(false);
            addFormVisible = false;
        }

        collapseExpandedCard.run();

        addCategoryFormVisible = !addCategoryFormVisible;
        addCategoryFormPanel.setVisible(addCategoryFormVisible);

        if (addCategoryFormVisible)
        {
            categoryNameField.setText("");
            SwingUtilities.invokeLater(() -> categoryNameField.requestFocusInWindow());
        }

        onRevalidate.run();
    }

    private JPanel buildAddProfileFormPanel()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.FORM_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(Theme.SPACING_SM, 0, 0, 0),
            BorderFactory.createLineBorder(Theme.CARD_BORDER, 1)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        panel.setPreferredSize(new Dimension(200, 170));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setVisible(false);

        addAccountForm = new InlineAccountForm(plugin, null, true, null);
        addAccountForm.setOnSave(profile -> {
            storage.saveAccount(profile);
            addAccountForm.clearFields();
            panel.setVisible(false);
            addFormVisible = false;
            onRebuild.run();
        });
        addAccountForm.setOnCancel(() -> {
            addAccountForm.clearFields();
            panel.setVisible(false);
            addFormVisible = false;
            onRevalidate.run();
        });

        panel.add(addAccountForm, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAddCategoryFormPanel()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.FORM_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(Theme.SPACING_SM, 0, 0, 0),
            BorderFactory.createLineBorder(Theme.CARD_BORDER, 1)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        panel.setPreferredSize(new Dimension(200, 70));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setVisible(false);

        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
        innerPanel.setBackground(Theme.FORM_BACKGROUND);
        innerPanel.setBorder(new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM));

        innerPanel.add(buildCategoryNameRow());
        innerPanel.add(Box.createVerticalStrut(Theme.SPACING_SM));
        innerPanel.add(buildCategoryButtonRow());

        panel.add(innerPanel, BorderLayout.CENTER);

        categoryNameField.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        categoryNameField.getActionMap().put("cancel", new AbstractAction()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                cancelCategoryForm();
            }
        });

        return panel;
    }

    private JPanel buildCategoryNameRow()
    {
        JPanel nameRow = new JPanel(new BorderLayout(Theme.SPACING_XS, 0));
        nameRow.setOpaque(false);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT));
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel("Name");
        nameLabel.setForeground(Theme.TEXT_SECONDARY);
        nameLabel.setFont(Theme.fontRegular(nameLabel.getFont(), Theme.FONT_SIZE_SMALL));
        nameLabel.setPreferredSize(new Dimension(55, Theme.BUTTON_HEIGHT));
        nameRow.add(nameLabel, BorderLayout.WEST);

        categoryNameField = new JTextField();
        categoryNameField.setBackground(Theme.BACKGROUND_DARKER);
        categoryNameField.setForeground(Theme.TEXT_PRIMARY);
        categoryNameField.setCaretColor(Theme.TEXT_PRIMARY);
        categoryNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.CARD_BORDER),
            new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM)
        ));
        categoryNameField.setFont(Theme.fontRegular(categoryNameField.getFont(), Theme.FONT_SIZE_BODY));
        categoryNameField.addActionListener(e -> saveCategoryForm());
        nameRow.add(categoryNameField, BorderLayout.CENTER);

        return nameRow;
    }

    private JPanel buildCategoryButtonRow()
    {
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, Theme.SPACING_XS, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT_SM));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(Theme.BUTTON_SECONDARY);
        cancelButton.setForeground(Theme.TEXT_PRIMARY);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelButton.setFont(Theme.fontRegular(cancelButton.getFont(), Theme.FONT_SIZE_HEADING));
        cancelButton.addActionListener(e -> cancelCategoryForm());
        buttonPanel.add(cancelButton);

        JButton saveButton = new JButton("Save");
        saveButton.setBackground(Theme.BUTTON_PRIMARY);
        saveButton.setForeground(Theme.TEXT_PRIMARY);
        saveButton.setFocusPainted(false);
        saveButton.setBorderPainted(false);
        saveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveButton.setFont(Theme.fontRegular(saveButton.getFont(), Theme.FONT_SIZE_HEADING));
        saveButton.addActionListener(e -> saveCategoryForm());
        buttonPanel.add(saveButton);

        return buttonPanel;
    }

    private void saveCategoryForm()
    {
        String name = categoryNameField.getText().trim();
        if (!name.isEmpty())
        {
            ProfileGroup group = ProfileGroup.createNew(name);
            // Auto-assign a default color based on existing category count
            int existingCount = storage.getGroups().size();
            Color[] palette = Theme.DEFAULT_CATEGORY_COLORS;
            if (existingCount < palette.length)
            {
                Color c = palette[existingCount];
                group.setColor(String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
            }
            storage.saveGroup(group);
            categoryNameField.setText("");
            addCategoryFormPanel.setVisible(false);
            addCategoryFormVisible = false;
            onRebuild.run();
        }
    }

    private void cancelCategoryForm()
    {
        categoryNameField.setText("");
        addCategoryFormPanel.setVisible(false);
        addCategoryFormVisible = false;
        onRevalidate.run();
    }
}
