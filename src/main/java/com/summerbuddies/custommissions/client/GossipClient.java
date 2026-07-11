package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.net.OpenGossipS2C;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Client entry point for the gossip picker — called from the {@code OpenGossipS2C} handler. */
public final class GossipClient {

    private GossipClient() {}

    public static void open(List<OpenGossipS2C.Target> targets) {
        Minecraft.getInstance().setScreen(new GossipScreen(targets));
    }
}
