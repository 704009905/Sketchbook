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
import net.z2six.sketchbook.book.EntityDetailData;
import net.z2six.sketchbook.book.EntityStudy;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public record EntityScanListSyncPayload(List<EntityStudy> identified, List<EntityDetailData> details) implements CustomPacketPayload {
    private static final Codec<EntityScanListSyncPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.list(EntityStudy.CODEC).fieldOf("identified").forGetter(EntityScanListSyncPayload::identified),
        Codec.list(EntityDetailData.CODEC).optionalFieldOf("details", List.of()).forGetter(EntityScanListSyncPayload::details)
    ).apply(instance, EntityScanListSyncPayload::new));
    public static final Type<EntityScanListSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "entity_scan_list_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityScanListSyncPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<EntityScanListSyncPayload> type() {
        return TYPE;
    }

    public static void handle(EntityScanListSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.z2six.sketchbook.client.network.EntityScanListSyncClientHandler");
                handlerClass.getMethod("handle", EntityScanListSyncPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException exception) {
                // Dedicated servers do not load the client sync handler.
            } catch (IllegalAccessException | NoSuchMethodException exception) {
                throw new RuntimeException("Failed to access entity scan sync client handler", exception);
            } catch (InvocationTargetException exception) {
                throw new RuntimeException("Entity scan sync client handler failed", exception.getCause());
            }
        });
    }
}
