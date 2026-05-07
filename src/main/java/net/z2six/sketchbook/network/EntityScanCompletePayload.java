package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.book.EntityDetailData;
import net.z2six.sketchbook.book.EntityScanSavedData;
import net.z2six.sketchbook.book.EntityStudy;

public record EntityScanCompletePayload(int entityId) implements CustomPacketPayload {
    private static final double MAX_SCAN_DISTANCE = 96.0D;

    private static final Codec<EntityScanCompletePayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("entity_id").forGetter(EntityScanCompletePayload::entityId)
    ).apply(instance, EntityScanCompletePayload::new));
    public static final Type<EntityScanCompletePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "entity_scan_complete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityScanCompletePayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<EntityScanCompletePayload> type() {
        return TYPE;
    }

    public static void handle(EntityScanCompletePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            Entity entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof LivingEntity) || entity == player || !entity.isAlive()) {
                return;
            }

            if (!player.isUsingItem() || !player.getUseItem().is(Items.SPYGLASS)) {
                return;
            }

            if (player.distanceToSqr(entity) > MAX_SCAN_DISTANCE * MAX_SCAN_DISTANCE || !player.hasLineOfSight(entity)) {
                return;
            }

            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle().normalize();
            Vec3 end = eye.add(look.scale(MAX_SCAN_DISTANCE));
            if (entity.getBoundingBox().inflate(0.75D).clip(eye, end).isEmpty()) {
                return;
            }

            ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            EntityStudy study = EntityStudy.fromEntity(entityTypeId, entity);
            EntityScanSavedData scans = EntityScanSavedData.get(player.getServer());
            boolean newlyIdentified = scans.identify(player.getUUID(), study);
            if (newlyIdentified) {
                scans.learnDetails(player.getUUID(), EntityDetailData.nameOnly(study));
                player.displayClientMessage(Component.translatable("message.sketchbook.entity_scan_identified", entity.getType().getDescription()), true);
                PacketDistributor.sendToPlayer(player, new EntityScanListSyncPayload(scans.getIdentified(player.getUUID()), scans.getDetails(player.getUUID())));
                SketchbookLog.info(
                    "Sketchbook identified entity type {} for player {}.",
                    entityTypeId,
                    player.getGameProfile().getName()
                );
            }
        });
    }
}
