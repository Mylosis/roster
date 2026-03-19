package com.mylosis.roster.ui.components;

import com.mylosis.roster.RosterPlugin;
import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.ui.GroupComboItem;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class InlineAccountForm extends JPanel
{
    private final RosterPlugin plugin;
    private final Account existingAccount;
    private final boolean showCategoryDropdown;
    private final String presetGroupId;

    private JTextField usernameField;
    private JTextField aliasField;
    private JTextField notesField;
    private JComboBox<GroupComboItem> categoryCombo;

    @Setter
    private Consumer<Account> onSave;
    @Setter
    private Runnable onCancel;

    public InlineAccountForm(RosterPlugin plugin, Account existingAccount,
                             boolean showCategoryDropdown, String presetGroupId)
    {
        this.plugin = plugin;
        this.existingAccount = existingAccount;
        this.showCategoryDropdown = showCategoryDropdown;
        this.presetGroupId = presetGroupId;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.FORM_BACKGROUND);
        setBorder(new EmptyBorder(Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM, Theme.SPACING_SM));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        buildForm();
        populateFields();
        setupKeyboardShortcuts();
    }

    private void buildForm()
    {
        // Username field (required)
        add(createFieldRow("Login *", usernameField = createTextField("Email or username")));
        add(Box.createVerticalStrut(Theme.SPACING_XS));

        // Alias field (optional)
        add(createFieldRow("Alias", aliasField = createTextField("Display name")));
        add(Box.createVerticalStrut(Theme.SPACING_XS));

        // Category dropdown (optional based on context)
        if (showCategoryDropdown)
        {
            categoryCombo = new JComboBox<>();
            categoryCombo.setBackground(Theme.BACKGROUND_DARKER);
            categoryCombo.setForeground(Theme.TEXT_PRIMARY);
            categoryCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT));
            populateCategories();
            add(createFieldRow("Category", categoryCombo));
            add(Box.createVerticalStrut(Theme.SPACING_XS));
        }

        // Notes field (single line for compactness)
        add(createFieldRow("Notes", notesField = createTextField("Notes")));
        add(Box.createVerticalStrut(Theme.SPACING_SM));

        // Buttons
        add(createButtonPanel());
    }

    private JPanel createFieldRow(String labelText, JComponent field)
    {
        JPanel row = new JPanel(new BorderLayout(Theme.SPACING_XS, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(labelText);
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setFont(Theme.fontRegular(label.getFont(), Theme.FONT_SIZE_SMALL));
        label.setPreferredSize(new Dimension(55, Theme.BUTTON_HEIGHT));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JTextField createTextField(String tooltip)
    {
        JTextField field = new JTextField();
        field.setBackground(Theme.BACKGROUND_DARKER);
        field.setForeground(Theme.TEXT_PRIMARY);
        field.setCaretColor(Theme.TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.CARD_BORDER),
            new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM)));
        field.setFont(Theme.fontRegular(field.getFont(), Theme.FONT_SIZE_BODY));
        field.setToolTipText(tooltip);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT));
        return field;
    }

    private void populateCategories()
    {
        if (categoryCombo == null) return;
        categoryCombo.addItem(new GroupComboItem(null, "Uncategorized"));
        for (ProfileGroup group : plugin.getAccountStorage().getGroups())
            categoryCombo.addItem(new GroupComboItem(group.getId(), group.getName()));
    }

    private void selectCategory(String groupId)
    {
        if (categoryCombo == null) return;
        for (int i = 0; i < categoryCombo.getItemCount(); i++)
        {
            GroupComboItem item = categoryCombo.getItemAt(i);
            if ((groupId == null && item.id == null) || (groupId != null && groupId.equals(item.id)))
            { categoryCombo.setSelectedIndex(i); return; }
        }
    }

    private JPanel createButtonPanel()
    {
        JPanel panel = new JPanel(new GridLayout(1, 2, Theme.SPACING_XS, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT_SM));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton cancelButton = createButton("Cancel", Theme.BUTTON_SECONDARY, Theme.BUTTON_SECONDARY_HOVER);
        cancelButton.addActionListener(e -> doCancel());
        panel.add(cancelButton);
        JButton saveButton = createButton("Save", Theme.BUTTON_PRIMARY, Theme.BUTTON_PRIMARY_HOVER);
        saveButton.addActionListener(e -> doSave());
        panel.add(saveButton);
        return panel;
    }

    private JButton createButton(String text, Color background, Color hoverBackground)
    {
        JButton button = new JButton(text);
        button.setBackground(background);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(Theme.fontRegular(button.getFont(), Theme.FONT_SIZE_HEADING));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { button.setBackground(hoverBackground); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { button.setBackground(background); }
        });
        return button;
    }

    private void populateFields()
    {
        if (existingAccount != null)
        {
            usernameField.setText(existingAccount.getUsername());
            aliasField.setText(existingAccount.getAlias());
            AccountMetadata meta = existingAccount.getMetadata();
            if (meta != null && meta.getNotes() != null) notesField.setText(meta.getNotes());
            if (showCategoryDropdown) selectCategory(existingAccount.getGroupId());
        }
        else if (showCategoryDropdown && presetGroupId != null) selectCategory(presetGroupId);
    }

    private void setupKeyboardShortcuts()
    {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "save");
        getActionMap().put("save", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { doSave(); }
        });

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { doCancel(); }
        });

        KeyAdapter enterAdapter = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isControlDown()) {
                    e.consume();
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent();
                }
            }
        };
        usernameField.addKeyListener(enterAdapter);
        aliasField.addKeyListener(enterAdapter);
        notesField.addKeyListener(enterAdapter);
    }

    private void doSave()
    {
        String username = usernameField.getText().trim();
        if (!validateUsername(username)) return;

        Account profile = buildProfile(username);
        applyGroupId(profile);
        applyMetadata(profile);

        log.debug("Inline form saving profile: {} (username: {})", profile.getDisplayName(), profile.getUsername());

        if (onSave != null)
        {
            onSave.accept(profile);
        }
    }

    private boolean validateUsername(String username)
    {
        if (!username.isEmpty()) return true;

        usernameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BUTTON_DANGER),
            new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM)
        ));
        usernameField.requestFocusInWindow();
        return false;
    }

    private Account buildProfile(String username)
    {
        String alias = aliasField.getText().trim();

        if (existingAccount != null)
        {
            existingAccount.setUsername(username);
            existingAccount.setAlias(alias.isEmpty() ? null : alias);
            return existingAccount;
        }
        return Account.createNew(alias.isEmpty() ? null : alias, username);
    }

    private void applyGroupId(Account profile)
    {
        if (showCategoryDropdown && categoryCombo != null)
        {
            GroupComboItem selected = (GroupComboItem) categoryCombo.getSelectedItem();
            profile.setGroupId(selected != null ? selected.id : null);
        }
        else if (presetGroupId != null)
        {
            profile.setGroupId(presetGroupId);
        }
    }

    private void applyMetadata(Account profile)
    {
        String notes = notesField.getText().trim();
        AccountMetadata meta = profile.getMetadata();
        if (meta == null)
        {
            meta = AccountMetadata.createDefault();
            profile.setMetadata(meta);
        }
        meta.setNotes(notes.isEmpty() ? null : notes);
    }

    private void doCancel()
    {
        if (onCancel != null) onCancel.run();
    }

    public void focusUsernameField()
    {
        SwingUtilities.invokeLater(() -> usernameField.requestFocusInWindow());
    }

    public void clearFields()
    {
        usernameField.setText("");
        aliasField.setText("");
        notesField.setText("");
        if (categoryCombo != null)
        {
            categoryCombo.setSelectedIndex(0);
        }
        // Reset username field border in case it was highlighted as error
        usernameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.CARD_BORDER),
            new EmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM)
        ));
    }

}
