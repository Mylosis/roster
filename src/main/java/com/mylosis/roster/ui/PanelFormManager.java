package com.mylosis.roster.ui;

import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.storage.AccountStorage;
import com.mylosis.roster.ui.components.Controls;
import com.mylosis.roster.ui.components.InlineAccountForm;
import com.mylosis.roster.ui.components.Theme;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * The add-account and add-category forms that open below the header buttons.
 * At most one of them is visible at a time.
 */
class PanelFormManager
{
    private final AccountStorage storage;
    private final Runnable onRebuild;
    private final Runnable onRevalidate;

    /** Holds a fresh form on every open, so its category dropdown lists the current categories. */
    @Getter
    private final JPanel addAccountFormPanel;

    @Getter
    private final JPanel addCategoryFormPanel;
    private final JTextField categoryNameField = Controls.textField(null);

    PanelFormManager(AccountStorage storage, Runnable onRebuild, Runnable onRevalidate)
    {
        this.storage = storage;
        this.onRebuild = onRebuild;
        this.onRevalidate = onRevalidate;

        addAccountFormPanel = formContainer(170);

        addCategoryFormPanel = formContainer(70);
        addCategoryFormPanel.add(buildCategoryForm(), BorderLayout.CENTER);
    }

    private static JPanel formContainer(int height)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.FORM_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(Theme.SPACING_SM, 0, 0, 0),
            BorderFactory.createLineBorder(Theme.CARD_BORDER, 1)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        panel.setPreferredSize(new Dimension(200, height));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setVisible(false);
        return panel;
    }

    private JPanel buildCategoryForm()
    {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Theme.FORM_BACKGROUND);
        form.setBorder(new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM));

        categoryNameField.addActionListener(e -> saveCategory());
        categoryNameField.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        categoryNameField.getActionMap().put("cancel", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                cancelCategoryForm();
            }
        });

        form.add(Controls.fieldRow("Name", categoryNameField));
        form.add(Box.createVerticalStrut(Theme.SPACING_SM));
        form.add(Controls.formButtons(this::cancelCategoryForm, this::saveCategory));
        return form;
    }

    private void showAddAccountForm()
    {
        InlineAccountForm addAccountForm = new InlineAccountForm(storage, null, true, null);
        addAccountForm.setOnSave(account -> {
            storage.saveAccount(account);
            hideAddAccountForm();
            onRebuild.run();
        });
        addAccountForm.setOnCancel(() -> {
            hideAddAccountForm();
            onRevalidate.run();
        });
        addAccountFormPanel.removeAll();
        addAccountFormPanel.add(addAccountForm, BorderLayout.CENTER);
        addAccountFormPanel.setVisible(true);
        addAccountForm.focusUsernameField();
    }

    /** Hides the add-account form if it is open. */
    void hideAddAccountForm()
    {
        addAccountFormPanel.setVisible(false);
        addAccountFormPanel.removeAll();
    }

    void toggleAddAccountForm(Runnable collapseExpandedCard)
    {
        addCategoryFormPanel.setVisible(false);
        boolean show = !addAccountFormPanel.isVisible();
        if (show)
        {
            collapseExpandedCard.run();
            showAddAccountForm();
        }
        else
        {
            hideAddAccountForm();
        }
        onRevalidate.run();
    }

    void toggleAddCategoryForm(Runnable collapseExpandedCard)
    {
        hideAddAccountForm();
        collapseExpandedCard.run();

        boolean show = !addCategoryFormPanel.isVisible();
        addCategoryFormPanel.setVisible(show);
        if (show)
        {
            categoryNameField.setText("");
            SwingUtilities.invokeLater(categoryNameField::requestFocusInWindow);
        }
        onRevalidate.run();
    }

    private void saveCategory()
    {
        String name = categoryNameField.getText().trim();
        if (name.isEmpty())
        {
            return;
        }
        ProfileGroup group = ProfileGroup.createNew(name);
        // Auto-assign a default color by creation order.
        int existingCount = storage.getGroups().size();
        if (existingCount < Theme.DEFAULT_CATEGORY_COLORS.length)
        {
            group.setColor(Theme.colorToHex(Theme.DEFAULT_CATEGORY_COLORS[existingCount]));
        }
        storage.saveGroup(group);
        categoryNameField.setText("");
        addCategoryFormPanel.setVisible(false);
        onRebuild.run();
    }

    private void cancelCategoryForm()
    {
        categoryNameField.setText("");
        addCategoryFormPanel.setVisible(false);
        onRevalidate.run();
    }
}
