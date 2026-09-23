package com.mylosis.roster.ui.components;

import org.junit.Test;

import javax.swing.JLabel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SafeTextTest
{
    private static final String HOSTILE = "<html><img src='http://example.invalid/x'>";

    @Test
    public void ordinaryTextPassesThrough()
    {
        assertEquals("Iron Bob", SafeText.htmlSafe("Iron Bob"));
    }

    @Test
    public void htmlTextIsEscapedSoItRendersLiterally()
    {
        String safe = SafeText.htmlSafe(HOSTILE);

        assertTrue(safe.startsWith("<html>"));
        assertFalse(safe.contains("<img"));
        assertTrue(safe.contains("&lt;img"));
    }

    @Test
    public void categoryComboItemsAreHtmlSafe()
    {
        assertFalse(new GroupComboItem("id", HOSTILE).toString().contains("<img"));
        assertEquals("Mains", new GroupComboItem("id", "Mains").toString());
    }

    @Test
    public void plainDisablesTheHtmlRenderer()
    {
        JLabel label = SafeText.plain(new JLabel(HOSTILE));

        assertEquals(Boolean.TRUE, label.getClientProperty("html.disable"));
    }

    @Test
    public void singleLineFlattensNewlines()
    {
        assertEquals("a <html>b", SafeText.singleLine("a\n<html>b"));
    }
}
