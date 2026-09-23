package com.mylosis.roster.ui.components;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ThemeVersionTest
{
    /** The version shown in the panel header must be bumped with the build version. */
    @Test
    public void headerVersionMatchesBuildGradle() throws Exception
    {
        // Gradle runs tests from the project directory.
        String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?m)^version\\s*=\\s*'([^']+)'").matcher(build);

        assertTrue("version line not found in build.gradle", matcher.find());
        assertEquals(matcher.group(1), Theme.VERSION);
    }
}
