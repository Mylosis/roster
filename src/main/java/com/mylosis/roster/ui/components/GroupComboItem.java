package com.mylosis.roster.ui.components;

import lombok.Value;

/**
 * A category choice in a combo box or selection dialog. Wraps the id so two
 * categories with the same name stay distinguishable.
 */
@Value
public class GroupComboItem
{
    public static final GroupComboItem UNCATEGORIZED = new GroupComboItem(null, "Uncategorized");

    /** Category id; null for Uncategorized. */
    String id;
    String name;

    /**
     * Rendered by JComboBox and JOptionPane through an HTML-capable JLabel, so
     * a user-controlled category name must be made HTML-safe here.
     */
    @Override
    public String toString()
    {
        return SafeText.htmlSafe(name);
    }
}
