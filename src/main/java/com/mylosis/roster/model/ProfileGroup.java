package com.mylosis.roster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileGroup
{
    public static final String UNGROUPED_ID = "__ungrouped__";

    private String id;
    private String name;
    private boolean collapsed;
    private int sortOrder;
    private String color; // Nullable hex color string (e.g. "#FF5733") for category coloring

    public static ProfileGroup createNew(String name)
    {
        return ProfileGroup.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .collapsed(false)
            .sortOrder(0)
            .build();
    }

    public static ProfileGroup createUngrouped()
    {
        return ProfileGroup.builder()
            .id(UNGROUPED_ID)
            .name("Ungrouped")
            .collapsed(false)
            .sortOrder(Integer.MAX_VALUE)
            .build();
    }

    public boolean isUngrouped()
    {
        return UNGROUPED_ID.equals(id);
    }
}
