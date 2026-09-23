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
    private String id;
    private String name;
    private boolean collapsed;
    private int sortOrder;
    private String color; // Nullable hex color string (e.g. "#FF5733") for category coloring

    /**
     * A new, empty category. Its {@code sortOrder} is assigned by
     * {@code AccountStorage} on first save so it lands after the existing ones.
     */
    public static ProfileGroup createNew(String name)
    {
        return ProfileGroup.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .collapsed(false)
            .build();
    }
}
