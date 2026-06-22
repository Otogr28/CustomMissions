package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.Constants;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;

/**
 * JourneyMap client plugin. JourneyMap discovers this via the {@code @ClientPlugin} annotation and hands us
 * the {@link IClientAPI} on {@link #initialize}; {@link JourneyMapBridge} uses that handle to create/remove
 * quest waypoints. This class (and everything in the journeymap.* namespace) is only ever loaded when
 * JourneyMap is present, so the dedicated server (no JourneyMap) never touches it.
 */
@ClientPlugin
public final class JourneyMapPlugin implements IClientPlugin {

    private static IClientAPI api;

    public static IClientAPI api() {
        return api;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        Constants.LOG.info("[custommissions] JourneyMap API connected");
    }

    @Override
    public String getModId() {
        return Constants.MODID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        // no JourneyMap events needed
    }
}
