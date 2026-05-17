package net.z2six.sketchbook.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.z2six.sketchbook.book.BookItemSketch;
import net.z2six.sketchbook.book.ItemDetail;
import net.z2six.sketchbook.book.ItemDetailData;
import net.z2six.sketchbook.book.PageSketch;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ItemSketchRenderer {
    private static final int PAGE_WIDTH = 114;
    private static final int PAGE_HEIGHT = 128;
    private static final int CAPTURE_SIZE = 256;
    private static final int MAX_TEXTURES = 96;
    private static final Map<GraphiteKey, GraphiteTexture> TEXTURE_CACHE = new HashMap<>();
    private static TextureTarget captureTarget;
    private static ByteBufferBuilder captureBuffer;
    private static MultiBufferSource.BufferSource captureBufferSource;
    private static int textureCounter;

    private ItemSketchRenderer() {
    }

    public static void render(GuiGraphics graphics, int pageLeft, int pageTop, int pageWidth, int pageHeight, BookItemSketch sketch) {
        if (pageWidth != PAGE_WIDTH || pageHeight != PAGE_HEIGHT) {
            return;
        }
        GraphiteTexture texture = getTexture(Minecraft.getInstance(), sketch);
        if (texture == null) {
            return;
        }
        int left = pageLeft + Math.round(sketch.x()) - texture.centerX();
        int top = pageTop + Math.round(sketch.y()) - texture.centerY();
        graphics.pose().pushPose();
        graphics.enableScissor(pageLeft, pageTop, pageLeft + PAGE_WIDTH, pageTop + PAGE_HEIGHT);
        try {
            graphics.blit(texture.location(), left, top, CAPTURE_SIZE, CAPTURE_SIZE, 0.0F, 0.0F, CAPTURE_SIZE, CAPTURE_SIZE, CAPTURE_SIZE, CAPTURE_SIZE);
            renderDetails(graphics, Minecraft.getInstance(), pageLeft, pageTop, sketch);
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }

    private static void renderDetails(GuiGraphics graphics, Minecraft minecraft, int pageLeft, int pageTop, BookItemSketch sketch) {
        if (sketch.detailMask() == 0) {
            return;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(sketch.itemId()).orElse(Items.AIR);
        if (item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        Map<ItemDetail, String> details = ItemDetailData.valuesFor(stack);
        if (details.isEmpty()) {
            return;
        }

        int line = 0;
        for (ItemDetail detail : ItemDetail.values()) {
            if ((sketch.detailMask() & detail.bit()) == 0) {
                continue;
            }
            String value = details.get(detail);
            if (value == null || value.isBlank()) {
                continue;
            }
            int y = pageTop + 5 + line * 9;
            if (detail == ItemDetail.NAME) {
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

    private static void renderDetailIcon(GuiGraphics graphics, ItemDetail detail, int x, int y) {
        ItemStack icon = switch (detail) {
            case ARMOR -> new ItemStack(Items.IRON_CHESTPLATE);
            case ARMOR_TOUGHNESS -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case KNOCKBACK_RESISTANCE -> new ItemStack(Items.SHIELD);
            case DAMAGE -> new ItemStack(Items.IRON_SWORD);
            case ATTACK_SPEED -> new ItemStack(Items.FEATHER);
            case DURABILITY -> new ItemStack(Items.ANVIL);
            case NUTRITION -> new ItemStack(Items.BREAD);
            case SATURATION -> new ItemStack(Items.GOLDEN_CARROT);
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

    private static GraphiteTexture getTexture(Minecraft minecraft, BookItemSketch sketch) {
        GraphiteKey key = GraphiteKey.from(sketch);
        GraphiteTexture cached = TEXTURE_CACHE.get(key);
        if (cached != null) {
            cached.touch();
            return cached;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(sketch.itemId()).orElse(Items.AIR);
        if (item == Items.AIR) {
            return null;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        GraphiteTexture generated = renderTexture(minecraft, stack, key);
        if (generated == null) {
            return null;
        }
        TEXTURE_CACHE.put(key, generated);
        trimTextureCache();
        return generated;
    }

    private static GraphiteTexture renderTexture(Minecraft minecraft, ItemStack stack, GraphiteKey key) {
        ensureCaptureResources();
        if (captureTarget == null || captureBufferSource == null) {
            return null;
        }
        captureTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        captureTarget.clear(Minecraft.ON_OSX);
        captureTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, CAPTURE_SIZE, CAPTURE_SIZE);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        boolean flatLighting = false;
        try {
            GuiGraphics targetGraphics = new GuiGraphics(minecraft, captureBufferSource);
            BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
            targetGraphics.pose().pushPose();
            try {
                targetGraphics.pose().translate(CAPTURE_SIZE / 2.0F, CAPTURE_SIZE / 2.0F, 0.0F);
                targetGraphics.pose().mulPose(new Quaternionf(new AxisAngle4f((float)Math.toRadians(key.roll()), 0.0F, 0.0F, 1.0F)));
                targetGraphics.pose().mulPose(new Quaternionf(new AxisAngle4f((float)Math.toRadians(key.yaw()), 0.0F, 1.0F, 0.0F)));
                targetGraphics.pose().scale(key.scale(), -key.scale(), key.scale());
                flatLighting = !model.usesBlockLight();
                if (flatLighting) {
                    Lighting.setupForFlatItems();
                }
                minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, targetGraphics.pose(), targetGraphics.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, model);
            } catch (RuntimeException exception) {
                return null;
            } finally {
                targetGraphics.pose().popPose();
                targetGraphics.flush();
                if (flatLighting) {
                    Lighting.setupFor3DItems();
                }
            }
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
            ResourceLocation location = minecraft.getTextureManager().register("sketchbook_item_graphite_" + textureCounter++, texture);
            return new GraphiteTexture(location, texture, graphite.centerX(), graphite.centerY());
        }
    }

    private static void ensureCaptureResources() {
        if (captureTarget == null) {
            captureTarget = new TextureTarget(CAPTURE_SIZE, CAPTURE_SIZE, true, Minecraft.ON_OSX);
        }
        if (captureBuffer == null) {
            captureBuffer = new ByteBufferBuilder(524288);
            captureBufferSource = MultiBufferSource.immediate(captureBuffer);
        }
    }

    private static NativeImage readCapture() {
        NativeImage image = new NativeImage(CAPTURE_SIZE, CAPTURE_SIZE, false);
        RenderSystem.bindTexture(captureTarget.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();
        return image;
    }

    private static GraphiteImage createGraphiteImage(NativeImage source, int colorMask) {
        NativeImage output = new NativeImage(CAPTURE_SIZE, CAPTURE_SIZE, true);
        int minX = CAPTURE_SIZE;
        int minY = CAPTURE_SIZE;
        int maxX = -1;
        int maxY = -1;
        int[] light = new int[CAPTURE_SIZE * CAPTURE_SIZE];
        int[] alpha = new int[CAPTURE_SIZE * CAPTURE_SIZE];
        for (int y = 0; y < CAPTURE_SIZE; y++) {
            for (int x = 0; x < CAPTURE_SIZE; x++) {
                int color = source.getPixelRGBA(x, y);
                int red = color & 0xFF;
                int green = color >> 8 & 0xFF;
                int blue = color >> 16 & 0xFF;
                int index = y * CAPTURE_SIZE + x;
                alpha[index] = color >>> 24;
                light[index] = Math.round(red * 0.299F + green * 0.587F + blue * 0.114F);
            }
        }
        int count = 0;
        for (int y = 0; y < CAPTURE_SIZE; y++) {
            for (int x = 0; x < CAPTURE_SIZE; x++) {
                int index = y * CAPTURE_SIZE + x;
                int pixelAlpha = alpha[index];
                if (pixelAlpha < 8) {
                    output.setPixelRGBA(x, y, 0);
                    continue;
                }
                int edge = edgeStrength(light, alpha, x, y);
                float tone = Math.max(0.12F, Math.min(0.94F, light[index] / 255.0F));
                tone -= Math.min(0.42F, edge / 520.0F);
                tone = Math.max(0.0F, Math.min(1.0F, tone));
                int sourceColor = source.getPixelRGBA(x, y);
                int nativeRgb = SketchRenderColorizer.colorize(sourceColor & 0xFF, sourceColor >> 8 & 0xFF, sourceColor >> 16 & 0xFF, tone, colorMask);
                int outAlpha = Math.min(235, Math.round(235.0F * pixelAlpha / 255.0F));
                output.setPixelRGBA(x, y, outAlpha << 24 | nativeRgb);
                if (outAlpha > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    count++;
                }
            }
        }
        if (count < 12) {
            output.close();
            return null;
        }
        return new GraphiteImage(output, (minX + maxX) / 2, (minY + maxY) / 2);
    }

    private static int edgeStrength(int[] luminance, int[] alpha, int x, int y) {
        if (sample(alpha, x, y) <= 12) {
            return 0;
        }
        int gx = sample(luminance, x + 1, y) - sample(luminance, x - 1, y);
        int gy = sample(luminance, x, y + 1) - sample(luminance, x, y - 1);
        int silhouette = Math.abs(sample(alpha, x + 1, y) - sample(alpha, x - 1, y)) + Math.abs(sample(alpha, x, y + 1) - sample(alpha, x, y - 1));
        return Math.min(255, Math.round((float)Math.sqrt(gx * gx + gy * gy)) + silhouette / 2);
    }

    private static int sample(int[] values, int x, int y) {
        int clampedX = Math.max(0, Math.min(CAPTURE_SIZE - 1, x));
        int clampedY = Math.max(0, Math.min(CAPTURE_SIZE - 1, y));
        return values[clampedY * CAPTURE_SIZE + clampedX];
    }

    private static int lerp(int start, int end, float amount) {
        return Math.max(0, Math.min(255, Math.round(start + (end - start) * amount)));
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

    private record GraphiteKey(ResourceLocation itemId, int scale, int yaw, int roll, int colorMask) {
        private static GraphiteKey from(BookItemSketch sketch) {
            return new GraphiteKey(sketch.itemId(), Math.max(8, Math.min(320, Math.round(sketch.scale()))), Math.floorMod(Math.round(sketch.yaw()), 360), Math.floorMod(Math.round(sketch.roll()), 360), sketch.colorMask());
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

        private int centerX() {
            return this.centerX;
        }

        private int centerY() {
            return this.centerY;
        }

        private long lastUsed() {
            return this.lastUsed;
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
