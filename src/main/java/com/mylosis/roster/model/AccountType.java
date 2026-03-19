package com.mylosis.roster.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountType
{
    MAIN("Main"),
    IRONMAN("Ironman"),
    HARDCORE_IRONMAN("Hardcore Ironman"),
    ULTIMATE_IRONMAN("Ultimate Ironman"),
    GROUP_IRONMAN("Group Ironman"),
    UNRANKED_GROUP_IRONMAN("Unranked Group Ironman"),
    SKILLER("Skiller"),
    PURE("Pure");

    private final String displayName;
}
