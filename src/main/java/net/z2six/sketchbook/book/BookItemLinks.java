package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BookItemLinks(Map<Integer, List<BookItemLink>> pages) {
    private static final Codec<Integer> PAGE_KEY_CODEC = Codec.STRING.comapFlatMap(BookItemLinks::parsePageIndex, Object::toString);
    private static final Codec<Map<Integer, List<BookItemLink>>> PAGES_CODEC = ExtraCodecs.sizeLimitedMap(
        Codec.unboundedMap(PAGE_KEY_CODEC, Codec.list(BookItemLink.CODEC)),
        100
    );
    public static final BookItemLinks EMPTY = new BookItemLinks(Map.of());
    public static final Codec<BookItemLinks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PAGES_CODEC.optionalFieldOf("pages", Map.of()).forGetter(BookItemLinks::pages)
    ).apply(instance, BookItemLinks::new));

    public BookItemLinks {
        LinkedHashMap<Integer, List<BookItemLink>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<BookItemLink>> entry : pages.entrySet()) {
            int pageIndex = entry.getKey();
            if (pageIndex < 0 || pageIndex >= 100) {
                throw new IllegalArgumentException("Invalid page index " + pageIndex);
            }
            List<BookItemLink> links = entry.getValue().stream()
                .filter(link -> link.start() >= 0 && link.end() <= 1024 && link.end() > link.start())
                .toList();
            if (!links.isEmpty()) {
                copy.put(pageIndex, List.copyOf(links));
            }
        }
        pages = Map.copyOf(copy);
    }

    public List<BookItemLink> get(int pageIndex) {
        return this.pages.getOrDefault(pageIndex, List.of());
    }

    public boolean isEmpty() {
        return this.pages.isEmpty();
    }

    public BookItemLinks withPage(int pageIndex, List<BookItemLink> links) {
        LinkedHashMap<Integer, List<BookItemLink>> copy = new LinkedHashMap<>(this.pages);
        if (links.isEmpty()) {
            copy.remove(pageIndex);
        } else {
            copy.put(pageIndex, List.copyOf(links));
        }
        return new BookItemLinks(copy);
    }

    public BookItemLinks withReplacement(int pageIndex, int start, int end, int insertedLength, ResourceLocation itemId) {
        int delta = insertedLength - (end - start);
        List<BookItemLink> updated = new ArrayList<>();
        for (BookItemLink link : this.get(pageIndex)) {
            if (link.end() <= start) {
                updated.add(link);
            } else if (link.start() >= end) {
                updated.add(new BookItemLink(link.start() + delta, link.end() + delta, link.itemId()));
            }
        }
        updated.add(new BookItemLink(start, start + insertedLength, itemId));
        updated.sort(java.util.Comparator.comparingInt(BookItemLink::start));
        return this.withPage(pageIndex, updated);
    }

    private static DataResult<Integer> parsePageIndex(String value) {
        try {
            int pageIndex = Integer.parseInt(value);
            if (pageIndex < 0 || pageIndex >= 100) {
                return DataResult.error(() -> "Page index out of range: " + value);
            }
            return DataResult.success(pageIndex);
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Invalid page index: " + value);
        }
    }
}
