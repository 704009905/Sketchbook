package net.z2six.sketchbook.client;

import net.minecraft.client.gui.screens.Screen;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookEntitySketch;
import net.z2six.sketchbook.book.BookItemSketch;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SketchSourceImage;

import java.util.Optional;
import java.util.UUID;

public interface SketchBookScreenBridge {
    BookSketchTarget sketchbook$getTarget();

    boolean sketchbook$canCaptureSketch(int pageIndex);

    boolean sketchbook$hasSketch(int pageIndex);

    void sketchbook$applySketch(int pageIndex, PageSketch sketch);

    void sketchbook$setSketchReference(int pageIndex, UUID referenceId);

    Optional<UUID> sketchbook$getSketchReference(int pageIndex);

    default void sketchbook$setEntitySketch(int pageIndex, BookEntitySketch sketch) {
    }

    default void sketchbook$setItemSketch(int pageIndex, BookItemSketch sketch) {
    }

    void sketchbook$cacheSketch(UUID referenceId, PageSketch sketch, Optional<SketchSourceImage> sourceImage, int colorMask);

    void sketchbook$removeSketch(int pageIndex);

    default boolean sketchbook$handleContextScroll(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    default boolean sketchbook$handleOverlayChar(char codePoint) {
        return false;
    }

    default boolean sketchbook$handleOverlayKey(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    Screen sketchbook$asScreen();
}
