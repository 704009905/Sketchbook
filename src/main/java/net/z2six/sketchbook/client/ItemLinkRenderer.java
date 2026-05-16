package net.z2six.sketchbook.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemLinkRenderer {
    private static final Pattern LINK = Pattern.compile("#\\$([a-z0-9_.-]+:[a-z0-9_./-]+)\\$#");
    private static final Pattern TRAILING_LINK_SPACE = Pattern.compile("(.*#\\$[a-z0-9_.-]+:[a-z0-9_./-]+\\$)# $");
    private static final int LINK_COLOR = 0x55CFFF;

    private ItemLinkRenderer() {
    }

    public static void renderLinks(GuiGraphics graphics, Font font, List<String> pages, int pageIndex, int pageLeft, int pageTop, int mouseX, int mouseY) {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        String[] lines = pages.get(pageIndex).split("\n", -1);
        int globalIndex = 0;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            Matcher matcher = LINK.matcher(line);
            while (matcher.find()) {
                Optional<ItemStack> stack = stack(matcher.group(1));
                if (stack.isEmpty()) {
                    continue;
                }
                String text = matcher.group();
                int x = pageLeft + font.width(line.substring(0, matcher.start()));
                int y = pageTop + lineIndex * 9;
                graphics.drawString(font, text, x, y, LINK_COLOR, false);
                int width = font.width(text);
                if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 9) {
                    hovered = stack.get();
                }
            }
            globalIndex += line.length() + 1;
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    public static boolean breakCompletedLinkOnSpace(List<String> pages, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return false;
        }
        String text = pages.get(pageIndex);
        Matcher matcher = TRAILING_LINK_SPACE.matcher(text);
        if (!matcher.matches()) {
            return false;
        }
        pages.set(pageIndex, matcher.group(1) + " ");
        return true;
    }

    private static Optional<ItemStack> stack(String idText) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? Optional.empty() : Optional.of(new ItemStack(item));
    }
}
