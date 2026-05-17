package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record BookItemLink(int start, int end, ResourceLocation itemId) {
    public static final Codec<BookItemLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(0, 1024).fieldOf("start").forGetter(BookItemLink::start),
        Codec.intRange(0, 1024).fieldOf("end").forGetter(BookItemLink::end),
        ResourceLocation.CODEC.fieldOf("item").forGetter(BookItemLink::itemId)
    ).apply(instance, BookItemLink::new));

    public BookItemLink {
        if (end <= start) {
            throw new IllegalArgumentException("Invalid item link range " + start + ".." + end);
        }
    }
}
