package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BookEntitySketch(EntityStudy study, float x, float y, float scale, float rotation, int detailMask) {
    public static final Codec<BookEntitySketch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        EntityStudy.CODEC.fieldOf("study").forGetter(BookEntitySketch::study),
        Codec.FLOAT.optionalFieldOf("x", 57.0F).forGetter(BookEntitySketch::x),
        Codec.FLOAT.optionalFieldOf("y", 64.0F).forGetter(BookEntitySketch::y),
        Codec.FLOAT.optionalFieldOf("scale", 30.0F).forGetter(BookEntitySketch::scale),
        Codec.FLOAT.optionalFieldOf("rotation", 180.0F).forGetter(BookEntitySketch::rotation),
        Codec.INT.optionalFieldOf("details", 0).forGetter(BookEntitySketch::detailMask)
    ).apply(instance, BookEntitySketch::new));

    public BookEntitySketch {
        detailMask = EntityDetail.normalizeMask(detailMask);
    }

    public static BookEntitySketch create(EntityStudy study) {
        return new BookEntitySketch(study, 57.0F, 64.0F, 30.0F, 180.0F, 0);
    }

    public BookEntitySketch withTransform(float x, float y, float scale, float rotation) {
        return new BookEntitySketch(this.study, x, y, scale, rotation, this.detailMask);
    }

    public BookEntitySketch withDetailMask(int detailMask) {
        return new BookEntitySketch(this.study, this.x, this.y, this.scale, this.rotation, detailMask);
    }
}
