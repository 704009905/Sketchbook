package net.z2six.sketchbook.client;

import net.minecraft.util.Mth;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SketchColorMask;
import net.z2six.sketchbook.item.PencilColor;

final class SketchRenderColorizer {
    private SketchRenderColorizer() {
    }

    static int colorize(int sourceRed, int sourceGreen, int sourceBlue, float tone, int colorMask) {
        int normalizedColorMask = SketchColorMask.normalize(colorMask);
        if (normalizedColorMask == SketchColorMask.NONE) {
            return nativeRgb(lerp(PageSketch.INK_RED, PageSketch.PAPER_RED, tone), lerp(PageSketch.INK_GREEN, PageSketch.PAPER_GREEN, tone), lerp(PageSketch.INK_BLUE, PageSketch.PAPER_BLUE, tone));
        }

        int paperRed = PageSketch.PAPER_RED;
        int paperGreen = PageSketch.PAPER_GREEN;
        int paperBlue = PageSketch.PAPER_BLUE;
        int inkRed = PageSketch.INK_RED;
        int inkGreen = PageSketch.INK_GREEN;
        int inkBlue = PageSketch.INK_BLUE;

        if (normalizedColorMask == SketchColorMask.ALL) {
            float sourceSaturation = saturation(sourceRed, sourceGreen, sourceBlue);
            int paperTintRed = lerp(PageSketch.PAPER_RED, sourceRed, 0.74F);
            int paperTintGreen = lerp(PageSketch.PAPER_GREEN, sourceGreen, 0.74F);
            int paperTintBlue = lerp(PageSketch.PAPER_BLUE, sourceBlue, 0.74F);
            int inkTintRed = lerp(PageSketch.INK_RED, sourceRed, 0.24F + sourceSaturation * 0.14F);
            int inkTintGreen = lerp(PageSketch.INK_GREEN, sourceGreen, 0.24F + sourceSaturation * 0.14F);
            int inkTintBlue = lerp(PageSketch.INK_BLUE, sourceBlue, 0.24F + sourceSaturation * 0.14F);
            paperRed = lerp(PageSketch.PAPER_RED, paperTintRed, 0.56F + sourceSaturation * 0.10F);
            paperGreen = lerp(PageSketch.PAPER_GREEN, paperTintGreen, 0.56F + sourceSaturation * 0.10F);
            paperBlue = lerp(PageSketch.PAPER_BLUE, paperTintBlue, 0.56F + sourceSaturation * 0.10F);
            inkRed = lerp(PageSketch.INK_RED, inkTintRed, 0.70F);
            inkGreen = lerp(PageSketch.INK_GREEN, inkTintGreen, 0.70F);
            inkBlue = lerp(PageSketch.INK_BLUE, inkTintBlue, 0.70F);
            tone = clamp01((float)Math.pow(tone, 1.14F));
        } else {
            float totalChromaWeight = 0.0F;
            float strongestChromaWeight = 0.0F;
            float weightedRed = 0.0F;
            float weightedGreen = 0.0F;
            float weightedBlue = 0.0F;

            for (PencilColor color : PencilColor.values()) {
                if (!SketchColorMask.isSelected(normalizedColorMask, color) || !isChromatic(color)) {
                    continue;
                }

                float chromaWeight = pigmentAmount(color, sourceRed, sourceGreen, sourceBlue);
                if (chromaWeight <= 0.0F) {
                    continue;
                }

                totalChromaWeight += chromaWeight;
                strongestChromaWeight = Math.max(strongestChromaWeight, chromaWeight);
                weightedRed += color.red() * chromaWeight;
                weightedGreen += color.green() * chromaWeight;
                weightedBlue += color.blue() * chromaWeight;
            }

            if (totalChromaWeight > 0.0F) {
                float chromaStrength = clamp01(strongestChromaWeight * 0.95F + totalChromaWeight * 0.30F);
                int mixRed = Math.round(weightedRed / totalChromaWeight);
                int mixGreen = Math.round(weightedGreen / totalChromaWeight);
                int mixBlue = Math.round(weightedBlue / totalChromaWeight);
                int sourceTintRed = lerp(mixRed, sourceRed, 0.45F);
                int sourceTintGreen = lerp(mixGreen, sourceGreen, 0.45F);
                int sourceTintBlue = lerp(mixBlue, sourceBlue, 0.45F);
                paperRed = lerp(PageSketch.PAPER_RED, sourceTintRed, chromaStrength * 0.82F);
                paperGreen = lerp(PageSketch.PAPER_GREEN, sourceTintGreen, chromaStrength * 0.82F);
                paperBlue = lerp(PageSketch.PAPER_BLUE, sourceTintBlue, chromaStrength * 0.82F);
                inkRed = lerp(PageSketch.INK_RED, sourceTintRed, chromaStrength * 0.40F);
                inkGreen = lerp(PageSketch.INK_GREEN, sourceTintGreen, chromaStrength * 0.40F);
                inkBlue = lerp(PageSketch.INK_BLUE, sourceTintBlue, chromaStrength * 0.40F);
            }

            paperRed = applyNeutralPaper(normalizedColorMask, PencilColor.WHITE, paperRed, PencilColor.WHITE.red(), sourceRed, sourceGreen, sourceBlue, 0.92F);
            paperGreen = applyNeutralPaper(normalizedColorMask, PencilColor.WHITE, paperGreen, PencilColor.WHITE.green(), sourceRed, sourceGreen, sourceBlue, 0.92F);
            paperBlue = applyNeutralPaper(normalizedColorMask, PencilColor.WHITE, paperBlue, PencilColor.WHITE.blue(), sourceRed, sourceGreen, sourceBlue, 0.92F);
            inkRed = applyNeutralInk(normalizedColorMask, PencilColor.WHITE, inkRed, PencilColor.LIGHT_GRAY.red(), sourceRed, sourceGreen, sourceBlue, 0.18F);
            inkGreen = applyNeutralInk(normalizedColorMask, PencilColor.WHITE, inkGreen, PencilColor.LIGHT_GRAY.green(), sourceRed, sourceGreen, sourceBlue, 0.18F);
            inkBlue = applyNeutralInk(normalizedColorMask, PencilColor.WHITE, inkBlue, PencilColor.LIGHT_GRAY.blue(), sourceRed, sourceGreen, sourceBlue, 0.18F);

            paperRed = applyNeutralPaper(normalizedColorMask, PencilColor.LIGHT_GRAY, paperRed, PencilColor.LIGHT_GRAY.red(), sourceRed, sourceGreen, sourceBlue, 0.48F);
            paperGreen = applyNeutralPaper(normalizedColorMask, PencilColor.LIGHT_GRAY, paperGreen, PencilColor.LIGHT_GRAY.green(), sourceRed, sourceGreen, sourceBlue, 0.48F);
            paperBlue = applyNeutralPaper(normalizedColorMask, PencilColor.LIGHT_GRAY, paperBlue, PencilColor.LIGHT_GRAY.blue(), sourceRed, sourceGreen, sourceBlue, 0.48F);
            inkRed = applyNeutralInk(normalizedColorMask, PencilColor.LIGHT_GRAY, inkRed, PencilColor.LIGHT_GRAY.red(), sourceRed, sourceGreen, sourceBlue, 0.28F);
            inkGreen = applyNeutralInk(normalizedColorMask, PencilColor.LIGHT_GRAY, inkGreen, PencilColor.LIGHT_GRAY.green(), sourceRed, sourceGreen, sourceBlue, 0.28F);
            inkBlue = applyNeutralInk(normalizedColorMask, PencilColor.LIGHT_GRAY, inkBlue, PencilColor.LIGHT_GRAY.blue(), sourceRed, sourceGreen, sourceBlue, 0.28F);

            paperRed = applyNeutralPaper(normalizedColorMask, PencilColor.GRAY, paperRed, PencilColor.GRAY.red(), sourceRed, sourceGreen, sourceBlue, 0.34F);
            paperGreen = applyNeutralPaper(normalizedColorMask, PencilColor.GRAY, paperGreen, PencilColor.GRAY.green(), sourceRed, sourceGreen, sourceBlue, 0.34F);
            paperBlue = applyNeutralPaper(normalizedColorMask, PencilColor.GRAY, paperBlue, PencilColor.GRAY.blue(), sourceRed, sourceGreen, sourceBlue, 0.34F);
            inkRed = applyNeutralInk(normalizedColorMask, PencilColor.GRAY, inkRed, PencilColor.GRAY.red(), sourceRed, sourceGreen, sourceBlue, 0.55F);
            inkGreen = applyNeutralInk(normalizedColorMask, PencilColor.GRAY, inkGreen, PencilColor.GRAY.green(), sourceRed, sourceGreen, sourceBlue, 0.55F);
            inkBlue = applyNeutralInk(normalizedColorMask, PencilColor.GRAY, inkBlue, PencilColor.GRAY.blue(), sourceRed, sourceGreen, sourceBlue, 0.55F);

            paperRed = applyNeutralPaper(normalizedColorMask, PencilColor.BLACK, paperRed, PencilColor.BLACK.red(), sourceRed, sourceGreen, sourceBlue, 0.08F);
            paperGreen = applyNeutralPaper(normalizedColorMask, PencilColor.BLACK, paperGreen, PencilColor.BLACK.green(), sourceRed, sourceGreen, sourceBlue, 0.08F);
            paperBlue = applyNeutralPaper(normalizedColorMask, PencilColor.BLACK, paperBlue, PencilColor.BLACK.blue(), sourceRed, sourceGreen, sourceBlue, 0.08F);
            inkRed = applyNeutralInk(normalizedColorMask, PencilColor.BLACK, inkRed, PencilColor.BLACK.red(), sourceRed, sourceGreen, sourceBlue, 0.88F);
            inkGreen = applyNeutralInk(normalizedColorMask, PencilColor.BLACK, inkGreen, PencilColor.BLACK.green(), sourceRed, sourceGreen, sourceBlue, 0.88F);
            inkBlue = applyNeutralInk(normalizedColorMask, PencilColor.BLACK, inkBlue, PencilColor.BLACK.blue(), sourceRed, sourceGreen, sourceBlue, 0.88F);
        }

        return nativeRgb(lerp(inkRed, paperRed, tone), lerp(inkGreen, paperGreen, tone), lerp(inkBlue, paperBlue, tone));
    }

    private static int applyNeutralPaper(int colorMask, PencilColor color, int current, int target, int sourceRed, int sourceGreen, int sourceBlue, float strength) {
        if (!SketchColorMask.isSelected(colorMask, color)) {
            return current;
        }
        float amount = pigmentAmount(color, sourceRed, sourceGreen, sourceBlue);
        return amount <= 0.0F ? current : lerp(current, target, amount * strength);
    }

    private static int applyNeutralInk(int colorMask, PencilColor color, int current, int target, int sourceRed, int sourceGreen, int sourceBlue, float strength) {
        if (!SketchColorMask.isSelected(colorMask, color)) {
            return current;
        }
        float amount = pigmentAmount(color, sourceRed, sourceGreen, sourceBlue);
        return amount <= 0.0F ? current : lerp(current, target, amount * strength);
    }

    private static float pigmentAmount(PencilColor color, int sourceRed, int sourceGreen, int sourceBlue) {
        float brightness = (sourceRed + sourceGreen + sourceBlue) / (255.0F * 3.0F);
        float saturation = saturation(sourceRed, sourceGreen, sourceBlue);
        float similarity = colorSimilarity(sourceRed, sourceGreen, sourceBlue, color.red(), color.green(), color.blue());
        return switch (color) {
            case WHITE -> emphasize(Math.max(smoothstep(0.60F, 1.0F, brightness), similarity * 0.45F) * (0.55F + (1.0F - saturation) * 0.45F), 2.0F);
            case LIGHT_GRAY -> emphasize(Math.max(similarity * 0.55F, (1.0F - saturation) * (1.0F - Math.min(1.0F, Math.abs(brightness - 0.74F) / 0.24F))), 1.8F);
            case GRAY -> emphasize(Math.max(similarity * 0.55F, (1.0F - saturation) * (1.0F - Math.min(1.0F, Math.abs(brightness - 0.50F) / 0.28F))), 1.8F);
            case BLACK -> emphasize(Math.max(similarity * 0.60F, smoothstep(0.18F, 0.78F, 1.0F - brightness)), 1.6F);
            case GRAPHITE -> 0.0F;
            default -> emphasize(similarity, 4.0F) * (0.25F + saturation * 0.75F);
        };
    }

    private static boolean isChromatic(PencilColor color) {
        return switch (color) {
            case GRAPHITE, WHITE, LIGHT_GRAY, GRAY, BLACK -> false;
            default -> true;
        };
    }

    private static float emphasize(float amount, float exponent) {
        return (float)Math.pow(clamp01(amount), exponent);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float normalized = clamp01((value - edge0) / Math.max(0.0001F, edge1 - edge0));
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float colorSimilarity(int sourceRed, int sourceGreen, int sourceBlue, int targetRed, int targetGreen, int targetBlue) {
        float redDelta = (sourceRed - targetRed) / 255.0F;
        float greenDelta = (sourceGreen - targetGreen) / 255.0F;
        float blueDelta = (sourceBlue - targetBlue) / 255.0F;
        float distance = Mth.sqrt(redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta) / Mth.sqrt(3.0F);
        return Math.max(0.0F, 1.0F - distance);
    }

    private static float saturation(int red, int green, int blue) {
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return max == 0 ? 0.0F : (max - min) / (float)max;
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static int lerp(int start, int end, float amount) {
        return Mth.clamp(Math.round(start + (end - start) * amount), 0, 255);
    }

    private static int nativeRgb(int red, int green, int blue) {
        return blue << 16 | green << 8 | red;
    }
}
