package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;

import java.lang.reflect.InvocationTargetException;

public record EntityScanStatusSyncPayload(int entityId, int mode) implements CustomPacketPayload {
    public static final int MODE_NONE = 0;
    public static final int MODE_IDENTIFY = 1;
    public static final int MODE_DETAIL = 2;

    private static final Codec<EntityScanStatusSyncPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("entity_id").forGetter(EntityScanStatusSyncPayload::entityId),
        Codec.INT.fieldOf("mode").forGetter(EntityScanStatusSyncPayload::mode)
    ).apply(instance, EntityScanStatusSyncPayload::new));
    public static final Type<EntityScanStatusSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "entity_scan_status_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityScanStatusSyncPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<EntityScanStatusSyncPayload> type() {
        return TYPE;
    }

    public static void handle(EntityScanStatusSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.z2six.sketchbook.client.network.EntityScanStatusSyncClientHandler");
                handlerClass.getMethod("handle", EntityScanStatusSyncPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException exception) {
                // Dedicated servers do not load the client sync handler.
            } catch (IllegalAccessException | NoSuchMethodException exception) {
                throw new RuntimeException("Failed to access entity scan status sync client handler", exception);
            } catch (InvocationTargetException exception) {
                throw new RuntimeException("Entity scan status sync client handler failed", exception.getCause());
            }
        });
    }
}
