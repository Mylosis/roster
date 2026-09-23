package com.mylosis.roster.ui.components;

import com.mylosis.roster.model.Account;
import com.mylosis.roster.model.AccountMetadata;
import com.mylosis.roster.model.AccountType;
import com.mylosis.roster.model.ProfileGroup;
import com.mylosis.roster.storage.AccountStorage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The add/edit account form, shown inline in the panel header, a category, or
 * an expanded card. Enter moves to the next field, Ctrl+Enter saves, Esc cancels.
 */
@Slf4j
public class InlineAccountForm extends JPanel
{
    private final AccountStorage storage;
    private final Account existingAccount;
    private final String presetGroupId;

    private final JTextField usernameField = Controls.textField("Email or username");
    private final JTextField aliasField = Controls.textField("Display name");
    private final JTextField notesField = Controls.textField("Notes");
    /** Null when the form is pinned to one category (a category's own "+" form). */
    private final JComboBox<GroupComboItem> categoryCombo;
    /** Only present when creating an account; existing accounts change type via the card badge. */
    private final JComboBox<AccountType> typeCombo;

    @Setter
    private Consumer<Account> onSave;
    @Setter
    private Runnable onCancel;

    /**
     * @param existingAccount the account to edit, or null to create one
     * @param showCategoryDropdown whether the user can pick the category
     * @param presetGroupId the category to preselect (dropdown) or always use (no dropdown)
     */
    public InlineAccountForm(AccountStorage storage, Account existingAccount,
                             boolean showCategoryDropdown, String presetGroupId)
    {
        this.storage = storage;
        this.existingAccount = existingAccount;
        this.presetGroupId = presetGroupId;
        this.categoryCombo = showCategoryDropdown ? createCategoryCombo() : null;
        this.typeCombo = existingAccount == null ? createTypeCombo() : null;

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
        addRow("Login *", usernameField, Theme.SPACING_XS);
        addRow("Alias", aliasField, Theme.SPACING_XS);
        if (categoryCombo != null)
        {
            addRow("Category", categoryCombo, Theme.SPACING_XS);
        }
        if (typeCombo != null)
        {
            addRow("Type", typeCombo, Theme.SPACING_XS);
        }
        addRow("Notes", notesField, Theme.SPACING_SM);
        add(Controls.formButtons(this::doCancel, this::doSave));
    }

    private void addRow(String label, JComponent field, int gapBelow)
    {
        add(Controls.fieldRow(label, field));
        add(Box.createVerticalStrut(gapBelow));
    }

    private JComboBox<GroupComboItem> createCategoryCombo()
    {
        JComboBox<GroupComboItem> combo = new JComboBox<>();
        styleCombo(combo);
        combo.addItem(GroupComboItem.UNCATEGORIZED);
        for (ProfileGroup group : storage.getGroups())
        {
            combo.addItem(new GroupComboItem(group.getId(), group.getName()));
        }
        return combo;
    }

    private static JComboBox<AccountType> createTypeCombo()
    {
        JComboBox<AccountType> combo = new JComboBox<>(AccountType.values());
        styleCombo(combo);
        combo.setRenderer(new AccountTypeComboRenderer());
        combo.setSelectedItem(AccountType.MAIN);
        return combo;
    }

    private static void styleCombo(JComboBox<?> combo)
    {
        combo.setBackground(Theme.BACKGROUND_DARKER);
        combo.setForeground(Theme.TEXT_PRIMARY);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.BUTTON_HEIGHT));
    }

    private void selectCategory(String groupId)
    {
        for (int i = 0; i < categoryCombo.getItemCount(); i++)
        {
            if (Objects.equals(groupId, categoryCombo.getItemAt(i).getId()))
            {
                categoryCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void populateFields()
    {
        if (existingAccount != null)
        {
            usernameField.setText(existingAccount.getUsername());
            aliasField.setText(existingAccount.getAlias());
            AccountMetadata meta = existingAccount.getMetadata();
            if (meta != null && meta.getNotes() != null) notesField.setText(meta.getNotes());
            if (categoryCombo != null) selectCategory(existingAccount.getGroupId());
        }
        else if (categoryCombo != null && presetGroupId != null)
        {
            selectCategory(presetGroupId);
        }
    }

    private void setupKeyboardShortcuts()
    {
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "save", this::doSave);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::doCancel);

        KeyAdapter enterAdapter = new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isControlDown())
                {
                    e.consume();
                    // Component-local focus hop; the global focus-manager API is
                    // disallowed on the Plugin Hub (flagged in PR #13538).
                    e.getComponent().transferFocus();
                }
            }
        };
        usernameField.addKeyListener(enterAdapter);
        aliasField.addKeyListener(enterAdapter);
        notesField.addKeyListener(enterAdapter);
    }

    private void bindKey(KeyStroke key, String name, Runnable action)
    {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, name);
        getActionMap().put(name, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                action.run();
            }
        });
    }

    private void doSave()
    {
        String username = usernameField.getText().trim();
        if (username.isEmpty())
        {
            Controls.setInvalid(usernameField, true);
            usernameField.requestFocusInWindow();
            return;
        }

        String alias = emptyToNull(aliasField.getText());
        Account account;
        if (existingAccount != null)
        {
            account = existingAccount;
            account.setUsername(username);
            account.setAlias(alias);
        }
        else
        {
            account = Account.createNew(alias, username);
        }
        applyGroup(account);
        applyMetadata(account);
        Controls.setInvalid(usernameField, false);

        // Log the id only: username is the login email, and debug logs end up
        // in client.log files that users paste into bug reports.
        log.debug("Inline form saving account: {}", account.getId());

        if (onSave != null)
        {
            onSave.accept(account);
        }
    }

    private void applyGroup(Account account)
    {
        String groupId = presetGroupId;
        if (categoryCombo != null)
        {
            GroupComboItem selected = (GroupComboItem) categoryCombo.getSelectedItem();
            groupId = selected != null ? selected.getId() : null;
        }
        if (existingAccount != null && !Objects.equals(groupId, account.getGroupId()))
        {
            // Moved to another category: land at its end rather than at the
            // old position number, which means nothing in the new category.
            account.ensureMetadata().setSortOrder(storage.nextAccountSortOrder(groupId));
        }
        account.setGroupId(groupId);
    }

    private void applyMetadata(Account account)
    {
        AccountMetadata meta = account.ensureMetadata();
        meta.setNotes(emptyToNull(notesField.getText()));
        // Notes were mutated through the metadata object, which bypasses
        // Account's invalidating setters; drop the cached search haystack.
        account.invalidateSearchHaystack();
        if (typeCombo != null && typeCombo.getSelectedItem() != null)
        {
            meta.setAccountType((AccountType) typeCombo.getSelectedItem());
        }
    }

    private static String emptyToNull(String text)
    {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void doCancel()
    {
        if (onCancel != null) onCancel.run();
    }

    public void focusUsernameField()
    {
        SwingUtilities.invokeLater(usernameField::requestFocusInWindow);
    }

    public void clearFields()
    {
        usernameField.setText("");
        aliasField.setText("");
        notesField.setText("");
        if (categoryCombo != null)
        {
            categoryCombo.setSelectedIndex(0);
            if (presetGroupId != null)
            {
                selectCategory(presetGroupId);
            }
        }
        if (typeCombo != null)
        {
            typeCombo.setSelectedItem(AccountType.MAIN);
        }
        Controls.setInvalid(usernameField, false);
    }

    /**
     * Renders each AccountType in the dropdown with its badge swatch alongside the label.
     * Mirrors the popover that opens when clicking a card badge, so the visual mapping
     * is the same in both places.
     */
    private static class AccountTypeComboRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AccountType)
            {
                AccountType t = (AccountType) value;
                setText(t.getDisplayName());
                setIcon(AccountTypeBadge.swatchIcon(t, 12));
                setIconTextGap(8);
                setBorder(new EmptyBorder(2, 4, 2, 4));
            }
            return this;
        }
    }
}
