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
public class Account
{
    private String id;
    private String alias;
    private String username;
    private String groupId;
    private AccountMetadata metadata;

    /**
     * The in-game character name, learned the first time this account is seen
     * logged in (matched by display name, or linked via the login-screen card
     * selection). Nullable for accounts never observed online. Online detection
     * and last-online stamping prefer this over the alias, so renaming the
     * alias no longer breaks them. Added in v1.2; Gson-backwards-compatible.
     */
    private String characterName;

    /**
     * Lazy lowercase concatenation of the fields the search UI matches against
     * (alias, username, notes). Built once on first search hit, dropped whenever
     * one of those fields mutates. Avoids the 3× toLowerCase + 3× null-check per
     * card per keystroke that the previous filterProfiles() did.
     */
    private transient String searchHaystackCache;

    public static Account createNew(String alias, String username)
    {
        return Account.builder()
            .id(UUID.randomUUID().toString())
            .alias(alias)
            .username(username)
            .metadata(AccountMetadata.createDefault())
            .build();
    }

    public String getDisplayName()
    {
        return alias != null && !alias.isEmpty() ? alias : username;
    }

    /**
     * Returns a lowercase blob containing alias + username + character name +
     * notes, suitable for substring search. Cached until
     * {@link #invalidateSearchHaystack()} is called.
     */
    public String getSearchHaystack()
    {
        String cached = searchHaystackCache;
        if (cached != null) return cached;
        StringBuilder sb = new StringBuilder(64);
        if (alias != null) sb.append(alias.toLowerCase()).append('\n');
        if (username != null) sb.append(username.toLowerCase()).append('\n');
        if (characterName != null) sb.append(characterName.toLowerCase()).append('\n');
        if (metadata != null && metadata.getNotes() != null) sb.append(metadata.getNotes().toLowerCase());
        cached = sb.toString();
        searchHaystackCache = cached;
        return cached;
    }

    public void invalidateSearchHaystack()
    {
        searchHaystackCache = null;
    }

    // Lombok @Data generates setAlias/setUsername; mutations need to invalidate
    // the search cache. Explicit overrides here are simpler than @Setter(onMethod_=…).
    public void setAlias(String alias) { this.alias = alias; invalidateSearchHaystack(); }
    public void setUsername(String username) { this.username = username; invalidateSearchHaystack(); }
    public void setCharacterName(String characterName) { this.characterName = characterName; invalidateSearchHaystack(); }
    public void setMetadata(AccountMetadata metadata)
    {
        this.metadata = metadata;
        invalidateSearchHaystack();
    }
}
