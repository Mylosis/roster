package com.mylosis.roster.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How accounts are ordered within each category in the panel. Persisted via
 * RosterConfig (and therefore cloud-syncs alongside the rest of the config).
 *
 * <p>{@link #MANUAL} preserves the user's hand-arranged drag order
 * ({@code AccountMetadata.sortOrder}). The other keys sort a copy at render
 * time without touching {@code sortOrder} — so dragging while a non-manual
 * sort is active silently records the new manual order, ready to re-emerge
 * when the user switches back to MANUAL.
 */
@Getter
@RequiredArgsConstructor
public enum SortKey
{
    MANUAL("Manual (drag order)"),
    NAME_ASC("Name — A → Z"),
    NAME_DESC("Name — Z → A"),
    LAST_ONLINE("Last online (recent first)"),
    DATE_ADDED("Date added (newest first)");

    private final String label;
}
