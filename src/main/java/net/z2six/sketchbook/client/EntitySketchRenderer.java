package net.z2six.sketchbook.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.z2six.sketchbook.book.BookEntitySketch;
import net.z2six.sketchbook.book.EntityDetail;
import net.z2six.sketchbook.book.EntityStudy;
import net.z2six.sketchbook.book.PageSketch;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class EntitySketchRenderer {
    private static final ResourceLocation HEART_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final Map<EntityStudy, LivingEntity> ENTITY_CACHE = new HashMap<>();
    private static final Map<GraphiteKey, GraphiteTexture> TEXTURE_CACHE = new HashMap<>();
    private static final int PAGE_WIDTH = 114;
    private static final int PAGE_HEIGHT = 128;
    private static final int CAPTURE_SIZE = PAGE_HEIGHT * 3;
    private static final int CAPTURE_WIDTH = CAPTURE_SIZE;
    private static final int CAPTURE_HEIGHT = CAPTURE_SIZE;
    private static final int CAPTURE_ANCHOR_X = CAPTURE_WIDTH / 2;
    private static final int CAPTURE_ANCHOR_Y = CAPTURE_HEIGHT / 2;
    private static final int MAX_TEXTURES = 48;
    private static final float MODEL_WIDTH_COMPENSATION = 1.22F;
    private static final int GRAPHITE_RED = PageSketch.INK_RED;
    private static final int GRAPHITE_GREEN = PageSketch.INK_GREEN;
    private static final int GRAPHITE_BLUE = PageSketch.INK_BLUE;
    private static ClientLevel cachedLevel;
    private static TextureTarget captureTarget;
    private static ByteBufferBuilder captureBuffer;
    private static MultiBufferSource.BufferSource captureBufferSource;
    private static int textureCounter;

    private EntitySketchRenderer() {
    }

    public static void render(GuiGraphics graphics, int pageLeft, int pageTop, int pageWidth, int pageHeight, BookEntitySketch sketch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || pageWidth != PAGE_WIDTH || pageHeight != PAGE_HEIGHT) {
            return;
        }

        GraphiteTexture texture = getGraphiteTexture(minecraft, sketch);
        if (texture == null) {
            return;
        }

        int left = pageLeft + Math.round(sketch.x()) - texture.centerX();
        int top = pageTop + Math.round(sketch.y()) - texture.centerY();
        graphics.pose().pushPose();
        graphics.enableScissor(pageLeft, pageTop, pageLeft + PAGE_WIDTH, pageTop + PAGE_HEIGHT);
        try {
            graphics.blit(texture.location(), left, top, CAPTURE_WIDTH, CAPTURE_HEIGHT, 0.0F, 0.0F, CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_WIDTH, CAPTURE_HEIGHT);
            renderDetails(graphics, minecraft, pageLeft, pageTop, sketch);
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }

    private static void renderDetails(GuiGraphics graphics, Minecraft minecraft, int pageLeft, int pageTop, BookEntitySketch sketch) {
        if (sketch.detailMask() == 0) {
            return;
        }

        Map<String, String> knownDetails = ClientEntityScanCache.getDetails(sketch.study());
        if (knownDetails.isEmpty()) {
            return;
        }

        int line = 0;
        for (EntityDetail detail : EntityDetail.values()) {
            if ((sketch.detailMask() & detail.bit()) == 0) {
                continue;
            }
            String value = knownDetails.get(detail.id());
            if (value == null || value.isBlank()) {
                continue;
            }
            int y = pageTop + 5 + line * 9;
            if (detail == EntityDetail.NAME) {
                line += renderWrappedName(graphics, minecraft, value, pageLeft + 5, y);
                continue;
            } else {
                renderDetailIcon(graphics, detail, pageLeft + 5, y - 1);
                graphics.drawString(minecraft.font, value, pageLeft + 16, y, 0x4A4034, false);
            }
            line++;
        }
    }

    private static int renderWrappedName(GuiGraphics graphics, Minecraft minecraft, String value, int x, int y) {
        int lines = 0;
        for (FormattedCharSequence line : minecraft.font.split(net.minecraft.network.chat.Component.literal(value), PAGE_WIDTH - 10)) {
            graphics.drawString(minecraft.font, line, x, y + lines * 9, 0x4A4034, false);
            lines++;
        }
        return Math.max(1, lines);
    }

    private static void renderDetailIcon(GuiGraphics graphics, EntityDetail detail, int x, int y) {
        if (detail == EntityDetail.HEALTH) {
            graphics.blitSprite(HEART_FULL_SPRITE, x, y, 9, 9);
            return;
        }

        ItemStack icon = switch (detail) {
            case ARMOR -> new ItemStack(Items.IRON_CHESTPLATE);
            case MOVEMENT_SPEED -> new ItemStack(Items.FEATHER);
            case ARMOR_TOUGHNESS -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case DAMAGE -> new ItemStack(Items.IRON_SWORD);
            case HEALTH -> ItemStack.EMPTY;
            case NAME -> ItemStack.EMPTY;
        };
        if (icon.isEmpty()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();
    }

    private static GraphiteTexture getGraphiteTexture(Minecraft minecraft, BookEntitySketch sketch) {
        GraphiteKey key = GraphiteKey.from(sketch);
        GraphiteTexture cached = TEXTURE_CACHE.get(key);
        if (cached != null) {
            cached.touch();
            return cached;
        }

        LivingEntity entity = getEntity(minecraft.level, sketch.study());
        if (entity == null) {
            return null;
        }

        GraphiteTexture generated = renderGraphiteTexture(minecraft, entity, key);
        if (generated == null) {
            return null;
        }

        TEXTURE_CACHE.put(key, generated);
        trimTextureCache();
        return generated;
    }

    private static GraphiteTexture renderGraphiteTexture(Minecraft minecraft, LivingEntity entity, GraphiteKey key) {
        ensureCaptureResources();
        if (captureTarget == null || captureBufferSource == null) {
            return null;
        }

        freezeEntity(entity);
        captureTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        captureTarget.clear(Minecraft.ON_OSX);
        captureTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        try {
            GuiGraphics targetGraphics = new GuiGraphics(minecraft, captureBufferSource);
            Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).rotateY((float)Math.toRadians(key.rotation()));
            targetGraphics.pose().pushPose();
            targetGraphics.pose().translate(CAPTURE_ANCHOR_X, 0.0F, 0.0F);
            targetGraphics.pose().scale(MODEL_WIDTH_COMPENSATION, 1.0F, 1.0F);
            targetGraphics.pose().translate(-CAPTURE_ANCHOR_X, 0.0F, 0.0F);
            try {
                InventoryScreen.renderEntityInInventory(
                    targetGraphics,
                    CAPTURE_ANCHOR_X,
                    CAPTURE_ANCHOR_Y,
                    key.scale(),
                    new Vector3f(0.0F, entity.getBbHeight() * 0.5F, 0.0F),
                    rotation,
                    null,
                    entity
                );
            } finally {
                targetGraphics.pose().popPose();
            }
            targetGraphics.flush();
        } finally {
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.viewport(0, 0, minecraft.getMainRenderTarget().viewWidth, minecraft.getMainRenderTarget().viewHeight);
        }

        try (NativeImage source = readCapture()) {
            GraphiteImage graphite = createGraphiteImage(source, key.colorMask());
            if (graphite == null) {
                return null;
            }
            DynamicTexture texture = new DynamicTexture(graphite.image());
            ResourceLocation location = minecraft.getTextureManager().register("sketchbook_entity_graphite_" + textureCounter++, texture);
            return new GraphiteTexture(location, texture, graphite.centerX(), graphite.centerY());
        }
    }

    private static void ensureCaptureResources() {
        if (captureTarget == null) {
            captureTarget = new TextureTarget(CAPTURE_WIDTH, CAPTURE_HEIGHT, true, Minecraft.ON_OSX);
        }
        if (captureBuffer == null) {
            captureBuffer = new ByteBufferBuilder(786432);
            captureBufferSource = MultiBufferSource.immediate(captureBuffer);
        }
    }

    private static NativeImage readCapture() {
        NativeImage image = new NativeImage(CAPTURE_WIDTH, CAPTURE_HEIGHT, false);
        RenderSystem.bindTexture(captureTarget.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();
        return image;
    }

    private static GraphiteImage createGraphiteImage(NativeImage source, int colorMask) {
        int size = CAPTURE_WIDTH * CAPTURE_HEIGHT;
        int[] luminance = new int[size];
        int[] normalized = new int[size];
        int[] alpha = new int[size];
        int[] histogram = new int[256];
        int foregroundPixels = 0;

        for (int y = 0; y < CAPTURE_HEIGHT; y++) {
            for (int x = 0; x < CAPTURE_WIDTH; x++) {
                int color = source.getPixelRGBA(x, y);
                int red = color & 0xFF;
                int green = color >> 8 & 0xFF;
                int blue = color >> 16 & 0xFF;
                int pixelAlpha = color >>> 24;
                int index = y * CAPTURE_WIDTH + x;
                alpha[index] = pixelAlpha;
                luminance[index] = Math.round(red * 0.299F + green * 0.587F + blue * 0.114F);
                if (pixelAlpha > 12) {
                    histogram[luminance[index]]++;
                    foregroundPixels++;
                }
            }
        }

        if (foregroundPixels < 24) {
            return null;
        }

        int low = percentile(histogram, foregroundPixels, 0.03F);
        int high = percentile(histogram, foregroundPixels, 0.98F);
        for (int index = 0; index < size; index++) {
            normalized[index] = alpha[index] > 12 ? clamp((luminance[index] - low) * 255 / Math.max(1, high - low), 0, 255) : 255;
        }

        NativeImage output = new NativeImage(CAPTURE_WIDTH, CAPTURE_HEIGHT, true);
        int minX = CAPTURE_WIDTH;
        int minY = CAPTURE_HEIGHT;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < CAPTURE_HEIGHT; y++) {
            for (int x = 0; x < CAPTURE_WIDTH; x++) {
                int index = y * CAPTURE_WIDTH + x;
                int pixelAlpha = alpha[index];
                if (pixelAlpha < 8) {
                    output.setPixelRGBA(x, y, 0);
                    continue;
                }

                int edge = edgeStrength(normalized, alpha, x, y);
                int outline = silhouetteStrength(alpha, x, y);
                float tone = normalized[index] / 255.0F;
                tone = smoothstep(0.10F, 0.92F, tone);
                tone = Math.max(0.12F, Math.min(0.94F, tone));
                tone -= Math.min(0.42F, edge / 520.0F);
                tone -= Math.min(0.34F, outline / 620.0F);
                tone = Math.max(0.0F, Math.min(1.0F, tone));

                int nativeRgb = colorMask == 0
                    ? GRAPHITE_BLUE << 16 | GRAPHITE_GREEN << 8 | GRAPHITE_RED
                    : SketchRenderColorizer.colorize(source.getPixelRGBA(x, y) & 0xFF, source.getPixelRGBA(x, y) >> 8 & 0xFF, source.getPixelRGBA(x, y) >> 16 & 0xFF, tone, colorMask);
                if (colorMask == 0) {
                    int red = lerp(GRAPHITE_RED, PageSketch.PAPER_RED, tone);
                    int green = lerp(GRAPHITE_GREEN, PageSketch.PAPER_GREEN, tone);
                    int blue = lerp(GRAPHITE_BLUE, PageSketch.PAPER_BLUE, tone);
                    nativeRgb = blue << 16 | green << 8 | red;
                }
                int outAlpha = clamp(Math.round(235.0F * (pixelAlpha / 255.0F)), 0, 235);
                output.setPixelRGBA(x, y, outAlpha << 24 | nativeRgb);
                if (outAlpha > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        int centerX = maxX >= minX ? (minX + maxX) / 2 : CAPTURE_ANCHOR_X;
        int centerY = maxY >= minY ? (minY + maxY) / 2 : CAPTURE_ANCHOR_Y;
        return new GraphiteImage(output, centerX, centerY);
    }

    private static int edgeStrength(int[] luminance, int[] alpha, int x, int y) {
        if (sample(alpha, x, y) <= 12) {
            return 0;
        }
        int gx = sample(luminance, x + 1, y - 1) + 2 * sample(luminance, x + 1, y) + sample(luminance, x + 1, y + 1)
            - sample(luminance, x - 1, y - 1) - 2 * sample(luminance, x - 1, y) - sample(luminance, x - 1, y + 1);
        int gy = sample(luminance, x - 1, y + 1) + 2 * sample(luminance, x, y + 1) + sample(luminance, x + 1, y + 1)
            - sample(luminance, x - 1, y - 1) - 2 * sample(luminance, x, y - 1) - sample(luminance, x + 1, y - 1);
        return Math.min(255, Math.round((float)Math.sqrt(gx * gx + gy * gy) / 3.4F));
    }

    private static int silhouetteStrength(int[] alpha, int x, int y) {
        int centerAlpha = sample(alpha, x, y);
        int edge = Math.abs(centerAlpha - sample(alpha, x + 1, y)) + Math.abs(centerAlpha - sample(alpha, x - 1, y))
            + Math.abs(centerAlpha - sample(alpha, x, y + 1)) + Math.abs(centerAlpha - sample(alpha, x, y - 1));
        return Math.min(255, edge / 2);
    }

    private static int sample(int[] values, int x, int y) {
        int clampedX = Math.max(0, Math.min(CAPTURE_WIDTH - 1, x));
        int clampedY = Math.max(0, Math.min(CAPTURE_HEIGHT - 1, y));
        return values[clampedY * CAPTURE_WIDTH + clampedX];
    }

    private static int percentile(int[] histogram, int total, float fraction) {
        int threshold = Math.max(0, Math.min(total - 1, Math.round(total * fraction)));
        int count = 0;
        for (int value = 0; value < histogram.length; value++) {
            count += histogram[value];
            if (count > threshold) {
                return value;
            }
        }
        return histogram.length - 1;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float normalized = Math.max(0.0F, Math.min(1.0F, (value - edge0) / Math.max(0.0001F, edge1 - edge0)));
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int lerp(int start, int end, float amount) {
        return clamp(Math.round(start + (end - start) * amount), 0, 255);
    }

    private static void freezeEntity(LivingEntity entity) {
        entity.tickCount = 0;
        entity.yBodyRot = 0.0F;
        entity.yBodyRotO = 0.0F;
        entity.setYRot(0.0F);
        entity.yHeadRot = 0.0F;
        entity.yHeadRotO = 0.0F;
        entity.setXRot(0.0F);
        entity.xRotO = 0.0F;
    }

    private static LivingEntity getEntity(ClientLevel level, EntityStudy study) {
        if (cachedLevel != level) {
            ENTITY_CACHE.clear();
            clearTextureCache();
            cachedLevel = level;
        }

        LivingEntity cached = ENTITY_CACHE.get(study);
        if (cached != null) {
            return cached;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(study.entityTypeId());
        if (type == null || !(type.create(level) instanceof LivingEntity entity)) {
            return null;
        }

        CompoundTag tag = study.variantTag();
        tag.putString("id", study.entityTypeId().toString());
        entity.load(tag);
        ENTITY_CACHE.put(study, entity);
        return entity;
    }

    private static void trimTextureCache() {
        while (TEXTURE_CACHE.size() > MAX_TEXTURES) {
            GraphiteKey oldestKey = null;
            long oldestUse = Long.MAX_VALUE;
            for (Map.Entry<GraphiteKey, GraphiteTexture> entry : TEXTURE_CACHE.entrySet()) {
                if (entry.getValue().lastUsed() < oldestUse) {
                    oldestUse = entry.getValue().lastUsed();
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            GraphiteTexture removed = TEXTURE_CACHE.remove(oldestKey);
            if (removed != null) {
                removed.close();
            }
        }
    }

    private static void clearTextureCache() {
        Iterator<GraphiteTexture> iterator = TEXTURE_CACHE.values().iterator();
        while (iterator.hasNext()) {
            iterator.next().close();
            iterator.remove();
        }
    }

    private record GraphiteKey(EntityStudy study, int scale, int rotation, int colorMask) {
        private static GraphiteKey from(BookEntitySketch sketch) {
            int quantizedScale = Math.max(8, Math.round(sketch.scale()));
            int quantizedRotation = Math.floorMod(Math.round(sketch.rotation()), 360);
            return new GraphiteKey(sketch.study(), quantizedScale, quantizedRotation, sketch.colorMask());
        }
    }

    private record GraphiteImage(NativeImage image, int centerX, int centerY) {
    }

    private static final class GraphiteTexture implements AutoCloseable {
        private final ResourceLocation location;
        private final DynamicTexture texture;
        private final int centerX;
        private final int centerY;
        private long lastUsed;

        private GraphiteTexture(ResourceLocation location, DynamicTexture texture, int centerX, int centerY) {
            this.location = location;
            this.texture = texture;
            this.centerX = centerX;
            this.centerY = centerY;
            this.touch();
        }

        private ResourceLocation location() {
            return this.location;
        }

        private long lastUsed() {
            return this.lastUsed;
        }

        private int centerX() {
            return this.centerX;
        }

        private int centerY() {
            return this.centerY;
        }

        private void touch() {
            this.lastUsed = System.currentTimeMillis();
        }

        @Override
        public void close() {
            this.texture.close();
        }
    }
}
