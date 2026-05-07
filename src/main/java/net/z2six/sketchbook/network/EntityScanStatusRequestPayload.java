package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.EntityDetailData;
import net.z2six.sketchbook.book.EntityScanSavedData;
import net.z2six.sketchbook.book.EntityStudy;

public record EntityScanStatusRequestPayload(int entityId) implements CustomPacketPayload {
    private static final double MAX_SCAN_DISTANCE = 96.0D;
    private static final Codec<EntityScanStatusRequestPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("entity_id").forGetter(EntityScanStatusRequestPayload::entityId)
    ).apply(instance, EntityScanStatusRequestPayload::new));
    public static final Type<EntityScanStatusRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "entity_scan_status_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityScanStatusRequestPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<EntityScanStatusRequestPayload> type() {
        return TYPE;
    }

    public static void handle(EntityScanStatusRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            Entity entity = player.level().getEntity(payload.entityId());
            int mode = EntityScanStatusSyncPayload.MODE_NONE;
            if (entity instanceof LivingEntity && entity != player && entity.isAlive()
                && player.isUsingItem() && player.getUseItem().is(Items.SPYGLASS)
                && player.distanceToSqr(entity) <= MAX_SCAN_DISTANCE * MAX_SCAN_DISTANCE
                && player.hasLineOfSight(entity)) {
                ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                EntityStudy study = EntityStudy.fromEntity(entityTypeId, entity);
                EntityScanSavedData scans = EntityScanSavedData.get(player.getServer());
                if (!scans.hasIdentified(player.getUUID(), study)) {
                    mode = EntityScanStatusSyncPayload.MODE_IDENTIFY;
                } else if (!scans.hasAllDetails(player.getUUID(), EntityDetailData.detailsOnly(study, (LivingEntity)entity))) {
                    mode = EntityScanStatusSyncPayload.MODE_DETAIL;
                }
            }

            PacketDistributor.sendToPlayer(player, new EntityScanStatusSyncPayload(payload.entityId(), mode));
        });
    }
}
