package com.mylosis.roster;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RosterPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(RosterPlugin.class);
        RuneLite.main(args);
    }
}
