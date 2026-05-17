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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.book.BookItemSketch;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;

public record BookItemSketchPayload(BookSketchTarget target, int pageIndex, BookItemSketch sketch) implements CustomPacketPayload {
    private static final Codec<BookItemSketchPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookItemSketchPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookItemSketchPayload::pageIndex),
        BookItemSketch.CODEC.fieldOf("sketch").forGetter(BookItemSketchPayload::sketch)
    ).apply(instance, BookItemSketchPayload::new));
    public static final Type<BookItemSketchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_item_sketch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookItemSketchPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<BookItemSketchPayload> type() {
        return TYPE;
    }

    public static void handle(BookItemSketchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            BookItemSketch requestedSketch = payload.sketch();
            if (BuiltInRegistries.ITEM.getOptional(requestedSketch.itemId()).filter(item -> item != Items.AIR).isEmpty()) {
                fail(player, payload, "message.sketchbook.item_sketch_failed_unknown", "Sketchbook rejected item sketch for player {} page {} target {} because the item was unknown.");
                return;
            }

            if (payload.target().isLectern()) {
                if (!ScholarCommonCompat.handleItemSketchUpdate(player, payload.target(), payload.pageIndex(), requestedSketch)) {
                    fail(player, payload, "message.sketchbook.sketch_failed_page_unavailable", "Sketchbook rejected lectern item sketch for player {} page {} target {} because the lectern book or page was unavailable.");
                }
                return;
            }

            ItemStack book = player.getItemInHand(payload.target().hand());
            if (!book.is(Items.WRITABLE_BOOK)) {
                fail(player, payload, "message.sketchbook.sketch_failed_book_missing", "Sketchbook rejected item sketch for player {} page {} target {} because no writable book was held.");
                return;
            }

            String pageText = BookSketches.getPageText(book, payload.pageIndex());
            java.util.Optional<BookItemSketch> existingItemSketch = BookSketches.getItemSketch(book, payload.pageIndex());
            boolean hasExistingItemSketch = existingItemSketch.isPresent();
            if (!hasExistingItemSketch && !SketchbookItems.hasPencil(player)) {
                fail(player, payload, "message.sketchbook.sketch_failed_no_pencil", "Sketchbook rejected item sketch placement for player {} page {} target {} because no required pencil was available.");
                return;
            }
            boolean colorChanged = existingItemSketch.map(existing -> existing.colorMask() != requestedSketch.colorMask()).orElse(false);
            if (colorChanged && !SketchbookItems.hasPencil(player)) {
                fail(player, payload, "message.sketchbook.sketch_failed_no_pencil", "Sketchbook rejected item color update for player {} page {} target {} because no required pencil was available.");
                return;
            }
            boolean detailsChanged = existingItemSketch.map(existing -> existing.detailMask() != requestedSketch.detailMask()).orElse(false);
            if (detailsChanged && !SketchbookItems.hasPencil(player)) {
                fail(player, payload, "message.sketchbook.sketch_failed_no_pencil", "Sketchbook rejected item detail update for player {} page {} target {} because no required pencil was available.");
                return;
            }
            BookItemSketch appliedSketch = requestedSketch;
            if (colorChanged) {
                appliedSketch = requestedSketch.withColorMask(requestedSketch.colorMask() & SketchbookItems.getAvailableColoredPencilMask(player));
            }
            if (BookSketches.hasSketch(book, payload.pageIndex()) && BookSketches.getItemSketch(book, payload.pageIndex()).isEmpty()) {
                fail(player, payload, "message.sketchbook.sketch_failed_page_unavailable", "Sketchbook rejected item sketch for player {} page {} target {} because the page was not sketchable.");
                return;
            }
            if (!hasExistingItemSketch && !BookSketches.canSketchOnText(pageText)) {
                fail(player, payload, "message.sketchbook.sketch_failed_page_unavailable", "Sketchbook rejected item sketch for player {} page {} target {} because the page was not empty.");
                return;
            }

            BookSketches.applyItemSketch(book, payload.pageIndex(), appliedSketch);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            PacketDistributor.sendToPlayer(player, BookSketchSyncPayload.item(payload.target(), payload.pageIndex(), appliedSketch));
        });
    }

    private static void fail(ServerPlayer player, BookItemSketchPayload payload, String translationKey, String logMessage) {
        SketchbookLog.infoOnce(
            "item-sketch-fail:" + player.getUUID() + ":" + payload.pageIndex() + ":" + payload.target() + ":" + translationKey,
            logMessage,
            player.getGameProfile().getName(),
            payload.pageIndex(),
            payload.target()
        );
        PacketDistributor.sendToPlayer(player, new SketchActionFeedbackPayload(translationKey));
    }
}
