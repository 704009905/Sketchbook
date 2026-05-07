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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.book.EntityDetailData;
import net.z2six.sketchbook.book.EntityScanSavedData;
import net.z2six.sketchbook.book.EntityStudy;
import net.z2six.sketchbook.book.EntityStudyAdvancements;

public record EntityDetailScanCompletePayload(int entityId) implements CustomPacketPayload {
    private static final double MAX_SCAN_DISTANCE = 96.0D;

    private static final Codec<EntityDetailScanCompletePayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("entity_id").forGetter(EntityDetailScanCompletePayload::entityId)
    ).apply(instance, EntityDetailScanCompletePayload::new));
    public static final Type<EntityDetailScanCompletePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "entity_detail_scan_complete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityDetailScanCompletePayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<EntityDetailScanCompletePayload> type() {
        return TYPE;
    }

    public static void handle(EntityDetailScanCompletePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            Entity entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof LivingEntity livingEntity) || entity == player || !entity.isAlive()) {
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
            if (!scans.hasIdentified(player.getUUID(), study)) {
                return;
            }

            EntityDetailData details = EntityDetailData.detailsOnly(study, livingEntity);
            boolean changed = scans.learnDetails(player.getUUID(), details);
            if (changed) {
                EntityStudyAdvancements.awardStudiedInDetail(player, entityTypeId);
                player.displayClientMessage(Component.translatable("message.sketchbook.entity_scan_details_identified", entity.getType().getDescription()), true);
                PacketDistributor.sendToPlayer(player, new EntityScanListSyncPayload(scans.getIdentified(player.getUUID()), scans.getDetails(player.getUUID())));
                SketchbookLog.info(
                    "Sketchbook studied entity details {} for player {}.",
                    entityTypeId,
                    player.getGameProfile().getName()
                );
            }
        });
    }
}
