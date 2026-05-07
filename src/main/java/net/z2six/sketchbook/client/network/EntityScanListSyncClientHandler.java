package net.z2six.sketchbook.client.network;

import net.z2six.sketchbook.client.ClientEntityScanCache;
import net.z2six.sketchbook.network.EntityScanListSyncPayload;

public final class EntityScanListSyncClientHandler {
    private EntityScanListSyncClientHandler() {
    }

    public static void handle(EntityScanListSyncPayload payload) {
        ClientEntityScanCache.setIdentified(payload.identified(), payload.details());
    }
}
