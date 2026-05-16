package net.z2six.sketchbook.compat.scholar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.BookEntitySketch;
import net.z2six.sketchbook.book.BookItemSketch;
import net.z2six.sketchbook.book.CapturedSketch;
import net.z2six.sketchbook.book.ServerBookSketches;
import net.z2six.sketchbook.network.BookSketchSyncPayload;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class ScholarCommonCompat {
    private static final String SCHOLAR_MODID = "scholar";
    private static final String LECTERN_SPREAD_MENU_CLASS = "io.github.mortuusars.scholar.menu.LecternSpreadMenu";

    private ScholarCommonCompat() {
    }

    public static boolean handleSketchUpdate(ServerPlayer serverPlayer, BookSketchTarget target, int pageIndex, Optional<CapturedSketch> sketch) {
        ItemStack book = getLecternBook(serverPlayer, target);
        if (!book.is(Items.WRITABLE_BOOK)) {
            SketchbookLog.info(
                "Sketchbook rejected lectern sketch mutation for player {} page {} target {} because no writable lectern book was available.",
                serverPlayer.getGameProfile().getName(),
                pageIndex,
                target
            );
            return false;
        }

        if (sketch.isPresent()) {
            String pageText = BookSketches.getPageText(book, pageIndex);
            if (BookSketches.hasSketch(book, pageIndex) || !BookSketches.canSketchOnText(pageText)) {
                SketchbookLog.info(
                    "Sketchbook rejected lectern sketch placement for player {} page {} target {} because the page was not sketchable.",
                    serverPlayer.getGameProfile().getName(),
                    pageIndex,
                    target
                );
                return false;
            }
            UUID referenceId = ServerBookSketches.storeNewSketch(serverPlayer, sketch.get());
            BookSketches.applyReference(book, pageIndex, referenceId);
            broadcastLecternUpdate(
                serverPlayer,
                target,
                BookSketchSyncPayload.image(target, pageIndex, referenceId, sketch.get().sketch(), Optional.of(sketch.get().sourceImage()), 0)
            );
            SketchbookLog.info(
                "Sketchbook placed lectern sketch ref {} for player {} page {} target {}.",
                referenceId,
                serverPlayer.getGameProfile().getName(),
                pageIndex,
                target
            );
        } else {
            BookSketches.removeSketch(book, pageIndex);
            broadcastLecternUpdate(serverPlayer, target, BookSketchSyncPayload.remove(target, pageIndex));
            SketchbookLog.info(
                "Sketchbook removed lectern sketch for player {} page {} target {}.",
                serverPlayer.getGameProfile().getName(),
                pageIndex,
                target
            );
        }
        return true;
    }

    public static boolean handleEntitySketchUpdate(ServerPlayer serverPlayer, BookSketchTarget target, int pageIndex, BookEntitySketch sketch) {
        ItemStack book = getLecternBook(serverPlayer, target);
        if (!book.is(Items.WRITABLE_BOOK)) {
            return false;
        }
        BookEntitySketch requestedSketch = sketch;

        String pageText = BookSketches.getPageText(book, pageIndex);
        Optional<BookEntitySketch> existingEntitySketch = BookSketches.getEntitySketch(book, pageIndex);
        boolean hasExistingEntitySketch = existingEntitySketch.isPresent();
        if (!hasExistingEntitySketch && !SketchbookItems.hasPencil(serverPlayer)) {
            return false;
        }
        if (existingEntitySketch.map(existing -> existing.detailMask() != requestedSketch.detailMask()).orElse(false) && !SketchbookItems.hasPencil(serverPlayer)) {
            return false;
        }
        boolean colorChanged = existingEntitySketch.map(existing -> existing.colorMask() != requestedSketch.colorMask()).orElse(false);
        if (colorChanged && !SketchbookItems.hasPencil(serverPlayer)) {
            return false;
        }
        BookEntitySketch appliedSketch = requestedSketch;
        if (colorChanged) {
            appliedSketch = requestedSketch.withColorMask(requestedSketch.colorMask() & SketchbookItems.getAvailableColoredPencilMask(serverPlayer));
        }
        if (BookSketches.hasSketch(book, pageIndex) && BookSketches.getEntitySketch(book, pageIndex).isEmpty()) {
            return false;
        }
        if (!hasExistingEntitySketch && !BookSketches.canSketchOnText(pageText)) {
            return false;
        }

        BookSketches.applyEntitySketch(book, pageIndex, appliedSketch);
        broadcastLecternUpdate(serverPlayer, target, BookSketchSyncPayload.entity(target, pageIndex, appliedSketch));
        return true;
    }

    public static boolean handleItemSketchUpdate(ServerPlayer serverPlayer, BookSketchTarget target, int pageIndex, BookItemSketch sketch) {
        ItemStack book = getLecternBook(serverPlayer, target);
        if (!book.is(Items.WRITABLE_BOOK)) {
            return false;
        }
        BookItemSketch requestedSketch = sketch;

        String pageText = BookSketches.getPageText(book, pageIndex);
        Optional<BookItemSketch> existingItemSketch = BookSketches.getItemSketch(book, pageIndex);
        boolean hasExistingItemSketch = existingItemSketch.isPresent();
        if (!hasExistingItemSketch && !SketchbookItems.hasPencil(serverPlayer)) {
            return false;
        }
        boolean colorChanged = existingItemSketch.map(existing -> existing.colorMask() != requestedSketch.colorMask()).orElse(false);
        if (colorChanged && !SketchbookItems.hasPencil(serverPlayer)) {
            return false;
        }
        BookItemSketch appliedSketch = requestedSketch;
        if (colorChanged) {
            appliedSketch = requestedSketch.withColorMask(requestedSketch.colorMask() & SketchbookItems.getAvailableColoredPencilMask(serverPlayer));
        }
        if (BookSketches.hasSketch(book, pageIndex) && BookSketches.getItemSketch(book, pageIndex).isEmpty()) {
            return false;
        }
        if (!hasExistingItemSketch && !BookSketches.canSketchOnText(pageText)) {
            return false;
        }

        BookSketches.applyItemSketch(book, pageIndex, appliedSketch);
        broadcastLecternUpdate(serverPlayer, target, BookSketchSyncPayload.item(target, pageIndex, appliedSketch));
        return true;
    }

    public static ItemStack getLecternBook(ServerPlayer serverPlayer, BookSketchTarget target) {
        if (!ModList.get().isLoaded(SCHOLAR_MODID) || target.lecternPos().isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!isLecternSpreadMenu(serverPlayer.containerMenu)) {
            return ItemStack.EMPTY;
        }

        BlockPos lecternPos = target.lecternPos().get();
        if (!lecternPos.equals(getLecternPos(serverPlayer.containerMenu).orElse(null))) {
            return ItemStack.EMPTY;
        }

        if (!(serverPlayer.serverLevel().getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern)) {
            return ItemStack.EMPTY;
        }

        return lectern.getBook();
    }

    public static void broadcastLecternUpdate(ServerPlayer serverPlayer, BookSketchTarget target, BookSketchSyncPayload syncPayload) {
        BlockPos lecternPos = target.lecternPos().orElseThrow();
        if (serverPlayer.serverLevel().getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) {
            lectern.setChanged();
        }
        serverPlayer.containerMenu.broadcastChanges();
        for (ServerPlayer otherPlayer : serverPlayer.serverLevel().players()) {
            if (isLecternSpreadMenu(otherPlayer.containerMenu) && lecternPos.equals(getLecternPos(otherPlayer.containerMenu).orElse(null))) {
                otherPlayer.containerMenu.broadcastChanges();
                PacketDistributor.sendToPlayer(otherPlayer, syncPayload);
            }
        }
    }

    private static boolean isLecternSpreadMenu(AbstractContainerMenu menu) {
        try {
            return Class.forName(LECTERN_SPREAD_MENU_CLASS).isInstance(menu);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static Optional<BlockPos> getLecternPos(AbstractContainerMenu menu) {
        try {
            Method method = menu.getClass().getMethod("getLecternPos");
            Object value = method.invoke(menu);
            return value instanceof BlockPos pos ? Optional.of(pos) : Optional.empty();
        } catch (ReflectiveOperationException e) {
            SketchbookLog.infoOnce("scholar_lectern_pos_reflection_failed", "Sketchbook could not read Scholar lectern menu position: {}", e.toString());
            return Optional.empty();
        }
    }
}
