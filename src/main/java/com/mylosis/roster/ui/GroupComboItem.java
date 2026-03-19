package com.mylosis.roster.ui;

/**
 * Combo box item representing a profile group/category selection.
 * Used in profile editor dialogs and inline forms.
 */
public class GroupComboItem
{
    public final String id;
    public final String name;

    public GroupComboItem(String id, String name)
    {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
