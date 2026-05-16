package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record BookItemSketch(ResourceLocation itemId, float x, float y, float scale, float yaw, float roll, int colorMask) {
    public static final Codec<BookItemSketch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("item").forGetter(BookItemSketch::itemId),
        Codec.FLOAT.optionalFieldOf("x", 57.0F).forGetter(BookItemSketch::x),
        Codec.FLOAT.optionalFieldOf("y", 64.0F).forGetter(BookItemSketch::y),
        Codec.FLOAT.optionalFieldOf("scale", 42.0F).forGetter(BookItemSketch::scale),
        Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(BookItemSketch::yaw),
        Codec.FLOAT.optionalFieldOf("roll", 0.0F).forGetter(BookItemSketch::roll),
        SketchColorMask.CODEC.optionalFieldOf("color_mask", SketchColorMask.NONE).forGetter(BookItemSketch::colorMask)
    ).apply(instance, BookItemSketch::new));

    public BookItemSketch {
        colorMask = SketchColorMask.normalize(colorMask);
    }

    public static BookItemSketch create(ResourceLocation itemId) {
        return new BookItemSketch(itemId, 57.0F, 64.0F, 42.0F, 0.0F, 0.0F, SketchColorMask.NONE);
    }

    public BookItemSketch withTransform(float x, float y, float scale, float yaw, float roll) {
        return new BookItemSketch(this.itemId, x, y, scale, yaw, roll, this.colorMask);
    }

    public BookItemSketch withColorMask(int colorMask) {
        return new BookItemSketch(this.itemId, this.x, this.y, this.scale, this.yaw, this.roll, colorMask);
    }
}
