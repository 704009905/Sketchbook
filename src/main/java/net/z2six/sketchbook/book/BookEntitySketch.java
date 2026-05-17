package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BookEntitySketch(EntityStudy study, float x, float y, float scale, float rotation, int detailMask, int colorMask) {
    public static final Codec<BookEntitySketch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        EntityStudy.CODEC.fieldOf("study").forGetter(BookEntitySketch::study),
        Codec.FLOAT.optionalFieldOf("x", 57.0F).forGetter(BookEntitySketch::x),
        Codec.FLOAT.optionalFieldOf("y", 64.0F).forGetter(BookEntitySketch::y),
        Codec.FLOAT.optionalFieldOf("scale", 30.0F).forGetter(BookEntitySketch::scale),
        Codec.FLOAT.optionalFieldOf("rotation", 180.0F).forGetter(BookEntitySketch::rotation),
        Codec.INT.optionalFieldOf("details", 0).forGetter(BookEntitySketch::detailMask),
        SketchColorMask.CODEC.optionalFieldOf("color_mask", SketchColorMask.NONE).forGetter(BookEntitySketch::colorMask)
    ).apply(instance, BookEntitySketch::new));

    public BookEntitySketch {
        scale = normalizeScale(scale);
        detailMask = EntityDetail.normalizeMask(detailMask);
        colorMask = SketchColorMask.normalize(colorMask);
    }

    public static BookEntitySketch create(EntityStudy study) {
        return new BookEntitySketch(study, 57.0F, 64.0F, 30.0F, 180.0F, 0, SketchColorMask.NONE);
    }

    public BookEntitySketch withTransform(float x, float y, float scale, float rotation) {
        return new BookEntitySketch(this.study, x, y, scale, rotation, this.detailMask, this.colorMask);
    }

    public BookEntitySketch withDetailMask(int detailMask) {
        return new BookEntitySketch(this.study, this.x, this.y, this.scale, this.rotation, detailMask, this.colorMask);
    }

    public BookEntitySketch withColorMask(int colorMask) {
        return new BookEntitySketch(this.study, this.x, this.y, this.scale, this.rotation, this.detailMask, colorMask);
    }

    private static float normalizeScale(float scale) {
        return Float.isFinite(scale) ? Math.max(8.0F, scale) : 30.0F;
    }
}
