package com.mylosis.roster.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImportDuplicateMode
{
    ADD_WITH_SUFFIX("Add Suffix"),
    SKIP("Skip duplicates"),
    UPDATE("Update existing");

    private final String displayName;

    @Override
    public String toString()
    {
        return displayName;
    }
}
