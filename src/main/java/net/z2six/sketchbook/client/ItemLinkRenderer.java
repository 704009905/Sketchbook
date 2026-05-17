package net.z2six.sketchbook.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.ClientTooltipFlag;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.BookItemLink;
import net.z2six.sketchbook.book.BookItemLinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ItemLinkRenderer {
    private static final int LINK_COLOR = 0x66D8FF;

    private ItemLinkRenderer() {
    }

    public static void renderLinks(GuiGraphics graphics, Font font, ItemStack book, List<String> pages, int pageIndex, int pageLeft, int pageTop, int mouseX, int mouseY) {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return;
        }
        BookItemLinks itemLinks = book.getOrDefault(Sketchbook.BOOK_ITEM_LINKS, BookItemLinks.EMPTY);
        List<BookItemLink> links = itemLinks.get(pageIndex);
        if (links.isEmpty()) {
            return;
        }

        ItemStack hovered = ItemStack.EMPTY;
        String[] lines = pages.get(pageIndex).split("\n", -1);
        int globalIndex = 0;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineStart = globalIndex;
            int lineEnd = globalIndex + line.length();
            for (BookItemLink link : links) {
                if (link.start() < lineStart || link.end() > lineEnd) {
                    continue;
                }
                Optional<ItemStack> stack = stack(link.itemId());
                if (stack.isEmpty()) {
                    continue;
                }
                String text = line.substring(link.start() - lineStart, link.end() - lineStart);
                int x = pageLeft + font.width(line.substring(0, link.start() - lineStart));
                int y = pageTop + lineIndex * 9;
                graphics.drawString(font, text, x, y, LINK_COLOR, false);
                int width = font.width(text);
                graphics.hLine(x, x + width - 1, y + 8, LINK_COLOR);
                if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 9) {
                    hovered = stack.get();
                }
            }
            globalIndex += line.length() + 1;
        }
        if (!hovered.isEmpty()) {
            renderItemTooltip(graphics, font, hovered, mouseX, mouseY);
        }
    }

    private static void renderItemTooltip(GuiGraphics graphics, Font font, ItemStack stack, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        TooltipFlag.Default flag = minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        List<Component> tooltip = new ArrayList<>(stack.getTooltipLines(
            TooltipContext.of(minecraft.level),
            minecraft.player,
            ClientTooltipFlag.of(flag.asCreative())
        ));
        int tabLine = 1;
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (!tab.hasSearchBar() && tab.contains(stack)) {
                tooltip.add(tabLine++, tab.getDisplayName().copy().withStyle(ChatFormatting.BLUE));
            }
        }
        graphics.renderTooltip(font, tooltip, stack.getTooltipImage(), stack, mouseX, mouseY);
    }

    private static Optional<ItemStack> stack(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? Optional.empty() : Optional.of(new ItemStack(item));
    }
}
