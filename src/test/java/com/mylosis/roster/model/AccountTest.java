package com.mylosis.roster.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AccountTest
{
    @Test
    public void searchHaystackContainsLowercaseAliasUsernameCharacterNameAndNotes()
    {
        Account account = Account.builder()
            .alias("MyAlias")
            .username("MyUsername")
            .characterName("MyCharacter")
            .metadata(AccountMetadata.builder().notes("MyNotes").build())
            .build();

        String haystack = account.getSearchHaystack();

        assertTrue(haystack.contains("myalias"));
        assertTrue(haystack.contains("myusername"));
        assertTrue(haystack.contains("mycharacter"));
        assertTrue(haystack.contains("mynotes"));
    }

    @Test
    public void setAliasInvalidatesCache()
    {
        Account account = Account.builder().alias("Old").username("user").build();
        account.getSearchHaystack();

        account.setAlias("NewAlias");

        assertTrue(account.getSearchHaystack().contains("newalias"));
    }

    @Test
    public void setUsernameInvalidatesCache()
    {
        Account account = Account.builder().alias("alias").username("OldUser").build();
        account.getSearchHaystack();

        account.setUsername("NewUser");

        assertTrue(account.getSearchHaystack().contains("newuser"));
    }

    @Test
    public void setCharacterNameInvalidatesCache()
    {
        Account account = Account.builder().alias("alias").username("user").characterName("OldChar").build();
        account.getSearchHaystack();

        account.setCharacterName("NewChar");

        assertTrue(account.getSearchHaystack().contains("newchar"));
    }

    @Test
    public void setMetadataInvalidatesCache()
    {
        Account account = Account.builder().alias("alias").username("user")
            .metadata(AccountMetadata.builder().notes("OldNotes").build())
            .build();
        account.getSearchHaystack();

        account.setMetadata(AccountMetadata.builder().notes("NewNotes").build());

        assertTrue(account.getSearchHaystack().contains("newnotes"));
    }

    @Test
    public void displayNameReturnsAliasWhenNonEmpty()
    {
        Account account = Account.builder().alias("MyAlias").username("MyUsername").build();

        assertEquals("MyAlias", account.getDisplayName());
    }

    @Test
    public void displayNameReturnsUsernameWhenAliasEmpty()
    {
        Account account = Account.builder().alias("").username("MyUsername").build();

        assertEquals("MyUsername", account.getDisplayName());
    }
}
