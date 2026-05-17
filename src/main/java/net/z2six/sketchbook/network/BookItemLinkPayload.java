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
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.BookItemLink;
import net.z2six.sketchbook.book.BookItemLinks;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;

import java.util.List;

public record BookItemLinkPayload(BookSketchTarget target, int pageIndex, String pageText, List<BookItemLink> links) implements CustomPacketPayload {
    private static final Codec<BookItemLinkPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookItemLinkPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookItemLinkPayload::pageIndex),
        Codec.string(0, 1024).fieldOf("page_text").forGetter(BookItemLinkPayload::pageText),
        Codec.list(BookItemLink.CODEC).optionalFieldOf("links", List.of()).forGetter(BookItemLinkPayload::links)
    ).apply(instance, BookItemLinkPayload::new));
    public static final Type<BookItemLinkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_item_link"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookItemLinkPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<BookItemLinkPayload> type() {
        return TYPE;
    }

    public static void handle(BookItemLinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !payload.linksAreValid()) {
                return;
            }

            if (payload.target().isLectern()) {
                ScholarCommonCompat.handleItemLinkUpdate(player, payload.target(), payload.pageIndex(), payload.pageText(), payload.links());
                return;
            }

            ItemStack book = player.getItemInHand(payload.target().hand());
            if (!book.is(Items.WRITABLE_BOOK)) {
                return;
            }

            apply(book, payload.pageIndex(), payload.pageText(), payload.links());
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        });
    }

    public static void apply(ItemStack book, int pageIndex, String pageText, List<BookItemLink> links) {
        BookSketches.setPageText(book, pageIndex, pageText);
        BookItemLinks itemLinks = book.getOrDefault(Sketchbook.BOOK_ITEM_LINKS, BookItemLinks.EMPTY).withPage(pageIndex, links);
        if (itemLinks.isEmpty()) {
            book.remove(Sketchbook.BOOK_ITEM_LINKS);
        } else {
            book.set(Sketchbook.BOOK_ITEM_LINKS, itemLinks);
        }
    }

    private boolean linksAreValid() {
        return this.links.stream().allMatch(link -> link.start() >= 0
            && link.end() <= this.pageText.length()
            && link.start() < link.end()
            && BuiltInRegistries.ITEM.getOptional(link.itemId()).filter(item -> item != Items.AIR).isPresent());
    }
}
