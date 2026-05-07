package net.z2six.sketchbook.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.EntityScanSavedData;

public record EntityScanListRequestPayload() implements CustomPacketPayload {
    public static final Type<EntityScanListRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "entity_scan_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityScanListRequestPayload> STREAM_CODEC = StreamCodec.unit(new EntityScanListRequestPayload());

    @Override
    public Type<EntityScanListRequestPayload> type() {
        return TYPE;
    }

    public static void handle(EntityScanListRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            EntityScanSavedData scans = EntityScanSavedData.get(player.getServer());
            PacketDistributor.sendToPlayer(player, new EntityScanListSyncPayload(scans.getIdentified(player.getUUID()), scans.getDetails(player.getUUID())));
        });
    }
}
