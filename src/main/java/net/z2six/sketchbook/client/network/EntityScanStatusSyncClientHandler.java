package net.z2six.sketchbook.client.network;

import net.z2six.sketchbook.client.EntityScanController;
import net.z2six.sketchbook.network.EntityScanStatusSyncPayload;

public final class EntityScanStatusSyncClientHandler {
    private EntityScanStatusSyncClientHandler() {
    }

    public static void handle(EntityScanStatusSyncPayload payload) {
        EntityScanController.handleStatus(payload.entityId(), payload.mode());
    }
}
