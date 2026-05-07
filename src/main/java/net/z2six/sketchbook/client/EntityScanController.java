package net.z2six.sketchbook.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.EntityStudy;
import net.z2six.sketchbook.network.EntityDetailScanCompletePayload;
import net.z2six.sketchbook.network.EntityScanCompletePayload;
import net.z2six.sketchbook.network.EntityScanStatusRequestPayload;
import net.z2six.sketchbook.network.EntityScanStatusSyncPayload;

import java.util.Comparator;
import java.util.Optional;

@EventBusSubscriber(modid = Sketchbook.MODID, value = Dist.CLIENT)
public final class EntityScanController {
    private static final double RANGE = 96.0D;
    private static final int SCAN_TICKS = 60;
    private static final int DETAIL_SCAN_TICKS = SCAN_TICKS * 5;
    private static final int BAR_WIDTH = 96;
    private static final int BAR_HEIGHT = 6;
    private static final double TARGET_PICK_TOLERANCE = 0.2D;

    private static int targetEntityId = -1;
    private static int glowingEntityId = -1;
    private static boolean previousGlowingState;
    private static int scanTicks;
    private static int pendingStatusEntityId = -1;
    private static int scanMode = EntityScanStatusSyncPayload.MODE_NONE;
    private static Component targetName = Component.empty();

    private EntityScanController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || !isUsingSpyglass(minecraft.player)) {
            reset(minecraft);
            ClientEntityScanCache.ensureRequested();
            return;
        }

        ClientEntityScanCache.ensureRequested();
        Entity target = findTarget(minecraft).orElse(null);
        if (target == null) {
            reset(minecraft);
            return;
        }

        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        EntityStudy targetStudy = EntityStudy.fromEntity(targetTypeId, target);

        if (target.getId() != targetEntityId) {
            targetEntityId = target.getId();
            scanTicks = 0;
            scanMode = EntityScanStatusSyncPayload.MODE_NONE;
            targetName = target.getType().getDescription();
            clearGlow(minecraft);
            pendingStatusEntityId = target.getId();
            PacketDistributor.sendToServer(new EntityScanStatusRequestPayload(target.getId()));
            return;
        }

        if (pendingStatusEntityId == target.getId()) {
            return;
        }

        if (scanMode == EntityScanStatusSyncPayload.MODE_NONE) {
            reset(minecraft);
            return;
        }

        if (glowingEntityId != target.getId()) {
            scanTicks = 0;
            setGlowingTarget(minecraft, target);
        }

        spawnTargetParticles(minecraft, target);
        scanTicks++;
        if (scanTicks >= requiredScanTicks()) {
            if (scanMode == EntityScanStatusSyncPayload.MODE_DETAIL) {
                PacketDistributor.sendToServer(new EntityDetailScanCompletePayload(target.getId()));
            } else {
                ClientEntityScanCache.addIdentified(targetStudy);
                PacketDistributor.sendToServer(new EntityScanCompletePayload(target.getId()));
            }
            reset(minecraft);
        }
    }

    public static void handleStatus(int entityId, int mode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || entityId != targetEntityId || entityId != pendingStatusEntityId) {
            return;
        }

        pendingStatusEntityId = -1;
        Entity entity = minecraft.level.getEntity(entityId);
        if (mode == EntityScanStatusSyncPayload.MODE_NONE || entity == null) {
            reset(minecraft);
            return;
        }

        scanMode = mode;
        setGlowingTarget(minecraft, entity);
        scanTicks = 0;
        targetName = entity.getType().getDescription();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (targetEntityId < 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() / 2 + 34;

        if (pendingStatusEntityId >= 0) {
            return;
        }

        int fillWidth = Math.round((BAR_WIDTH - 2) * (scanTicks / (float)requiredScanTicks()));
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xAA000000);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xCC1E2428);
        graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + BAR_HEIGHT - 1, scanMode == EntityScanStatusSyncPayload.MODE_DETAIL ? 0xFFFFA63D : 0xFF66D9EF);

        Component label = Component.translatable(
            scanMode == EntityScanStatusSyncPayload.MODE_DETAIL ? "hud.sketchbook.entity_scan_studying_detail" : "hud.sketchbook.entity_scan_studying",
            targetName
        );
        int labelX = (graphics.guiWidth() - minecraft.font.width(label)) / 2;
        graphics.drawString(minecraft.font, label, labelX, y + 10, 0xFFECECEC, true);
    }

    private static boolean isUsingSpyglass(LocalPlayer player) {
        return player.isUsingItem() && player.getUseItem().is(Items.SPYGLASS);
    }

    private static Optional<Entity> findTarget(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 end = eye.add(look.scale(RANGE));
        AABB searchBounds = new AABB(eye, end).inflate(1.5D);

        return minecraft.level.getEntities(player, searchBounds, entity ->
                entity != player
                    && entity.isAlive()
                    && entity instanceof LivingEntity
                    && entity.isPickable()
                    && EntitySelector.NO_SPECTATORS.test(entity)
                    && !entity.isInvisibleTo(player)
                    && player.hasLineOfSight(entity)
            )
            .stream()
            .map(entity -> new TargetScore(entity, scoreEntity(entity, eye, look)))
            .filter(score -> score.score() < Double.MAX_VALUE)
            .min(Comparator.comparingDouble(TargetScore::score))
            .map(TargetScore::entity);
    }

    private static double scoreEntity(Entity entity, Vec3 eye, Vec3 look) {
        AABB bounds = entity.getBoundingBox().inflate(TARGET_PICK_TOLERANCE);
        Optional<Vec3> hit = bounds.clip(eye, eye.add(look.scale(RANGE)));
        if (hit.isPresent()) {
            return eye.distanceTo(hit.get());
        }

        return Double.MAX_VALUE;
    }

    private static void spawnTargetParticles(Minecraft minecraft, Entity target) {
        if (minecraft.level == null || minecraft.player == null || minecraft.player.tickCount % 3 != 0) {
            return;
        }

        AABB bounds = target.getBoundingBox();
        double centerX = target.getX();
        double centerZ = target.getZ();
        double y = bounds.minY + bounds.getYsize() * 0.72D;
        double radius = Math.max(bounds.getXsize(), bounds.getZsize()) * 0.62D + 0.18D;
        double angle = (minecraft.player.tickCount % 40) * (Math.PI * 2.0D / 40.0D);

        for (int i = 0; i < 2; i++) {
            double particleAngle = angle + i * Math.PI;
            double x = centerX + Math.cos(particleAngle) * radius;
            double z = centerZ + Math.sin(particleAngle) * radius;
            minecraft.level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0D, 0.03D, 0.0D);
            if (scanMode == EntityScanStatusSyncPayload.MODE_DETAIL) {
                minecraft.level.addParticle(ParticleTypes.WITCH, x, y + 0.18D, z, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    private static int requiredScanTicks() {
        return scanMode == EntityScanStatusSyncPayload.MODE_DETAIL ? DETAIL_SCAN_TICKS : SCAN_TICKS;
    }

    private static void setGlowingTarget(Minecraft minecraft, Entity target) {
        clearGlow(minecraft);
        glowingEntityId = target.getId();
        previousGlowingState = target.isCurrentlyGlowing();
        target.setGlowingTag(true);
    }

    private static void reset(Minecraft minecraft) {
        clearGlow(minecraft);
        targetEntityId = -1;
        scanTicks = 0;
        pendingStatusEntityId = -1;
        scanMode = EntityScanStatusSyncPayload.MODE_NONE;
        targetName = Component.empty();
    }

    private static void clearGlow(Minecraft minecraft) {
        if (glowingEntityId < 0 || minecraft.level == null) {
            glowingEntityId = -1;
            previousGlowingState = false;
            return;
        }

        Entity entity = minecraft.level.getEntity(glowingEntityId);
        if (entity != null) {
            entity.setGlowingTag(previousGlowingState);
        }
        glowingEntityId = -1;
        previousGlowingState = false;
    }

    private record TargetScore(Entity entity, double score) {
    }
}
