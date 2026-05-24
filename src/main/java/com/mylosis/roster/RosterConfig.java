package com.mylosis.roster;

import com.mylosis.roster.model.ImportDuplicateMode;
import com.mylosis.roster.model.SortKey;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(RosterConfig.CONFIG_GROUP)
public interface RosterConfig extends Config
{
    String CONFIG_GROUP = "roster";

    @ConfigSection(
        name = "Display",
        description = "Display settings",
        position = 0
    )
    String displaySection = "display";

    @ConfigSection(
        name = "Privacy",
        description = "Control what information is visible",
        position = 1
    )
    String privacySection = "privacy";

    @ConfigSection(
        name = "General",
        description = "General settings",
        position = 2
    )
    String generalSection = "general";

    // Display settings

    @ConfigItem(
        keyName = "gridView",
        name = "Grid View",
        description = "Display accounts in a compact two-column grid",
        section = displaySection,
        position = 0
    )
    default boolean gridView()
    {
        return false;
    }

    @ConfigItem(
        keyName = "sortKey",
        name = "Sort accounts by",
        description = "Sort order for accounts within each category. Manual preserves drag order.",
        section = displaySection,
        position = 1
    )
    default SortKey sortKey()
    {
        return SortKey.MANUAL;
    }

    // Privacy settings

    @ConfigItem(
        keyName = "hideLogin",
        name = "Hide Login",
        description = "Hide login/username on account cards",
        section = privacySection,
        position = 0
    )
    default boolean hideLogin()
    {
        return false;
    }

    @ConfigItem(
        keyName = "hideNotes",
        name = "Hide Notes",
        description = "Hide notes on account cards",
        section = privacySection,
        position = 1
    )
    default boolean hideNotes()
    {
        return false;
    }

    @ConfigItem(
        keyName = "hideAlias",
        name = "Hide Alias",
        description = "Hide alias and show login name only",
        section = privacySection,
        position = 2
    )
    default boolean hideAlias()
    {
        return false;
    }

    @ConfigItem(
        keyName = "hideLastOnline",
        name = "Hide Last Online",
        description = "Hide the 'logged in X ago' stamp on account cards",
        section = privacySection,
        position = 3
    )
    default boolean hideLastOnline()
    {
        return false;
    }

    // General settings

    @ConfigItem(
        keyName = "confirmDelete",
        name = "Confirm Delete",
        description = "Show confirmation dialog when deleting accounts",
        section = generalSection,
        position = 0
    )
    default boolean confirmDelete()
    {
        return true;
    }

    @ConfigItem(
        keyName = "importDuplicateHandling",
        name = "Import Handling",
        description = "How to handle duplicate accounts when importing",
        section = generalSection,
        position = 1
    )
    default ImportDuplicateMode importDuplicateMode()
    {
        return ImportDuplicateMode.SKIP;
    }
}
