package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.book.BookEntitySketch;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.EntityScanSavedData;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;

public record BookEntitySketchPayload(BookSketchTarget target, int pageIndex, BookEntitySketch sketch) implements CustomPacketPayload {
    private static final Codec<BookEntitySketchPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookEntitySketchPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookEntitySketchPayload::pageIndex),
        BookEntitySketch.CODEC.fieldOf("sketch").forGetter(BookEntitySketchPayload::sketch)
    ).apply(instance, BookEntitySketchPayload::new));
    public static final Type<BookEntitySketchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_entity_sketch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookEntitySketchPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<BookEntitySketchPayload> type() {
        return TYPE;
    }

    public static void handle(BookEntitySketchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            BookEntitySketch requestedSketch = payload.sketch();
            EntityScanSavedData scans = EntityScanSavedData.get(player.getServer());
            if (!scans.hasIdentified(player.getUUID(), requestedSketch.study())) {
                fail(player, payload, "message.sketchbook.entity_sketch_failed_unknown", "Sketchbook rejected entity sketch for player {} page {} target {} because the entity was not identified.");
                return;
            }

            if (payload.target().isLectern()) {
                if (!ScholarCommonCompat.handleEntitySketchUpdate(player, payload.target(), payload.pageIndex(), requestedSketch)) {
                    fail(player, payload, "message.sketchbook.sketch_failed_page_unavailable", "Sketchbook rejected lectern entity sketch for player {} page {} target {} because the lectern book or page was unavailable.");
                }
                return;
            }

            ItemStack book = player.getItemInHand(payload.target().hand());
            if (!book.is(Items.WRITABLE_BOOK)) {
                fail(player, payload, "message.sketchbook.sketch_failed_book_missing", "Sketchbook rejected entity sketch for player {} page {} target {} because no writable book was held.");
                return;
            }

            String pageText = BookSketches.getPageText(book, payload.pageIndex());
            java.util.Optional<BookEntitySketch> existingEntitySketch = BookSketches.getEntitySketch(book, payload.pageIndex());
            boolean hasExistingEntitySketch = existingEntitySketch.isPresent();
            if (!hasExistingEntitySketch && !SketchbookItems.hasPencil(player)) {
                fail(player, payload, "message.sketchbook.sketch_failed_no_pencil", "Sketchbook rejected entity sketch placement for player {} page {} target {} because no required pencil was available.");
                return;
            }
            if (existingEntitySketch.map(existing -> existing.detailMask() != requestedSketch.detailMask()).orElse(false) && !SketchbookItems.hasPencil(player)) {
                fail(player, payload, "message.sketchbook.sketch_failed_no_pencil", "Sketchbook rejected entity detail update for player {} page {} target {} because no required pencil was available.");
                return;
            }
            boolean colorChanged = existingEntitySketch.map(existing -> existing.colorMask() != requestedSketch.colorMask()).orElse(false);
            if (colorChanged && !SketchbookItems.hasPencil(player)) {
                fail(player, payload, "message.sketchbook.sketch_failed_no_pencil", "Sketchbook rejected entity color update for player {} page {} target {} because no required pencil was available.");
                return;
            }
            BookEntitySketch appliedSketch = requestedSketch;
            if (colorChanged) {
                appliedSketch = requestedSketch.withColorMask(requestedSketch.colorMask() & SketchbookItems.getAvailableColoredPencilMask(player));
            }
            if (BookSketches.hasSketch(book, payload.pageIndex()) && BookSketches.getEntitySketch(book, payload.pageIndex()).isEmpty()) {
                fail(player, payload, "message.sketchbook.sketch_failed_page_unavailable", "Sketchbook rejected entity sketch for player {} page {} target {} because the page was not sketchable.");
                return;
            }
            if (!hasExistingEntitySketch && !BookSketches.canSketchOnText(pageText)) {
                fail(player, payload, "message.sketchbook.sketch_failed_page_unavailable", "Sketchbook rejected entity sketch for player {} page {} target {} because the page was not empty.");
                return;
            }

            BookSketches.applyEntitySketch(book, payload.pageIndex(), appliedSketch);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            PacketDistributor.sendToPlayer(player, BookSketchSyncPayload.entity(payload.target(), payload.pageIndex(), appliedSketch));
        });
    }

    private static void fail(ServerPlayer player, BookEntitySketchPayload payload, String translationKey, String logMessage) {
        SketchbookLog.infoOnce(
            "entity-sketch-fail:" + player.getUUID() + ":" + payload.pageIndex() + ":" + payload.target() + ":" + translationKey,
            logMessage,
            player.getGameProfile().getName(),
            payload.pageIndex(),
            payload.target()
        );
        PacketDistributor.sendToPlayer(player, new SketchActionFeedbackPayload(translationKey));
    }
}
