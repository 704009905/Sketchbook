package net.z2six.sketchbook.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ItemSearchOverlay {
    private static final int MIN_WIDTH = 126;
    private static final int MAX_WIDTH = 230;
    private static final int SEARCH_HEIGHT = 18;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_ROWS = 7;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final long SEARCH_DELAY_MS = 1000L;

    private boolean visible;
    private int left;
    private int top;
    private int screenWidth;
    private int screenHeight;
    private int pageIndex;
    private String query = "";
    private List<ResourceLocation> results = List.of();
    private int scrollOffset;
    private long searchDeadline;
    private boolean searchDirty;
    private boolean draggingScrollbar;
    private boolean sketchableOnly = true;
    private Consumer<ResourceLocation> onSelect = itemId -> { };

    public void open(int pageIndex, int mouseX, int mouseY, int screenWidth, int screenHeight, Consumer<ResourceLocation> onSelect) {
        this.open(pageIndex, mouseX, mouseY, screenWidth, screenHeight, true, onSelect);
    }

    public void open(int pageIndex, int mouseX, int mouseY, int screenWidth, int screenHeight, boolean sketchableOnly, Consumer<ResourceLocation> onSelect) {
        this.visible = true;
        this.pageIndex = pageIndex;
        this.query = "";
        this.results = List.of();
        this.scrollOffset = 0;
        this.searchDeadline = 0L;
        this.searchDirty = false;
        this.draggingScrollbar = false;
        this.sketchableOnly = sketchableOnly;
        this.onSelect = onSelect;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        int width = this.width(null);
        int height = this.height();
        this.left = Mth.clamp(mouseX, 4, screenWidth - width - 4);
        this.top = Mth.clamp(mouseY, 4, screenHeight - height - 4);
    }

    public void clear() {
        this.visible = false;
        this.draggingScrollbar = false;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public int pageIndex() {
        return this.pageIndex;
    }

    public boolean contains(double mouseX, double mouseY) {
        int width = this.width(null);
        return this.visible && mouseX >= this.left && mouseX < this.left + width && mouseY >= this.top && mouseY < this.top + this.height();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        this.tickSearch();
        int width = this.width(font);
        int height = this.height();
        this.left = Mth.clamp(this.left, 4, this.screenWidth - width - 4);
        this.top = Mth.clamp(this.top, 4, this.screenHeight - height - 4);
        int visibleRows = this.visibleRows();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 450.0F);
        graphics.fill(this.left, this.top, this.left + width, this.top + height, 0xF4F0E6CF);
        graphics.renderOutline(this.left, this.top, width, height, 0x7F302718);
        graphics.fill(this.left + 5, this.top + 5, this.left + width - 5, this.top + 5 + SEARCH_HEIGHT, 0x55FFF8E8);
        graphics.renderOutline(this.left + 5, this.top + 5, width - 10, SEARCH_HEIGHT, 0x806B563A);
        int textColor = this.query.isEmpty() ? 0x8F8170 : 0x3A342E;
        Component text = this.query.isEmpty() ? Component.translatable("menu.sketchbook.item_search") : Component.literal(this.query);
        graphics.drawString(font, text, this.left + 9, this.top + 10, textColor, false);
        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = this.left + 9 + (this.query.isEmpty() ? 0 : font.width(this.query));
            graphics.fill(caretX, this.top + 9, caretX + 1, this.top + 19, 0xFF3A342E);
        }

        int listTop = this.top + SEARCH_HEIGHT + 7;
        if (this.query.isEmpty()) {
            graphics.drawString(font, Component.translatable("menu.sketchbook.item_search_empty"), this.left + 8, listTop + 4, 0x8F8170, false);
        } else if (this.searchDirty) {
            graphics.drawString(font, Component.literal(this.searchingText()), this.left + 8, listTop + 4, 0x8F8170, false);
        }
        for (int row = 0; row < visibleRows; row++) {
            int index = this.scrollOffset + row;
            if (index >= this.results.size()) {
                break;
            }
            int rowTop = listTop + row * ROW_HEIGHT;
            int contentRight = this.left + width - (this.results.size() > visibleRows ? SCROLLBAR_WIDTH + 4 : 4);
            boolean hovered = mouseX >= this.left + 4 && mouseX < contentRight && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(this.left + 4, rowTop, contentRight, rowTop + ROW_HEIGHT, 0x30A58F6A);
            }
            graphics.drawString(font, this.displayName(this.results.get(index)), this.left + 8, rowTop + 4, 0x3A342E, false);
        }

        if (this.results.size() > visibleRows) {
            int trackLeft = this.left + width - SCROLLBAR_WIDTH - 3;
            int trackTop = listTop;
            int trackHeight = visibleRows * ROW_HEIGHT;
            graphics.fill(trackLeft, trackTop, trackLeft + SCROLLBAR_WIDTH, trackTop + trackHeight, 0x302B241C);
            int maxOffset = this.results.size() - visibleRows;
            int thumbHeight = Math.max(10, Math.round((visibleRows / (float)this.results.size()) * trackHeight));
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbOffset = Math.round((this.scrollOffset / (float)maxOffset) * thumbTravel);
            graphics.fill(trackLeft + 1, trackTop + thumbOffset, trackLeft + SCROLLBAR_WIDTH - 1, trackTop + thumbOffset + thumbHeight, 0x9073604A);
        }
        graphics.pose().popPose();
    }

    public boolean charTyped(char codePoint) {
        if (!this.visible || Character.isISOControl(codePoint)) {
            return false;
        }
        this.query += codePoint;
        this.markDirty();
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!this.visible) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.clear();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.query.isEmpty()) {
            this.query = this.query.substring(0, this.query.length() - 1);
            this.markDirty();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && !this.results.isEmpty()) {
            this.select(this.results.get(this.scrollOffset));
            return true;
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.contains(mouseX, mouseY)) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int listTop = this.top + SEARCH_HEIGHT + 7;
        int visibleRows = this.visibleRows();
        int width = this.width(null);
        if (this.results.size() > visibleRows && mouseX >= this.left + width - SCROLLBAR_WIDTH - 3) {
            this.draggingScrollbar = true;
            this.updateScrollbar(mouseY);
            return true;
        }
        int row = (int)((mouseY - listTop) / ROW_HEIGHT);
        int index = this.scrollOffset + row;
        if (row >= 0 && row < visibleRows && index >= 0 && index < this.results.size()) {
            this.select(this.results.get(index));
        }
        return true;
    }

    public boolean mouseDragged(double mouseY) {
        if (!this.visible || !this.draggingScrollbar) {
            return false;
        }
        this.updateScrollbar(mouseY);
        return true;
    }

    public boolean mouseReleased() {
        boolean wasDragging = this.draggingScrollbar;
        this.draggingScrollbar = false;
        return wasDragging;
    }

    public boolean scroll(double scrollY) {
        int visibleRows = this.visibleRows();
        if (!this.visible || this.results.size() <= visibleRows || scrollY == 0.0D) {
            return false;
        }
        int maxOffset = this.results.size() - visibleRows;
        this.scrollOffset = Mth.clamp(this.scrollOffset + (scrollY < 0.0D ? 1 : -1), 0, maxOffset);
        return true;
    }

    private void select(ResourceLocation itemId) {
        this.onSelect.accept(itemId);
        this.clear();
    }

    private void markDirty() {
        this.searchDirty = true;
        this.searchDeadline = System.currentTimeMillis() + SEARCH_DELAY_MS;
        this.results = List.of();
        this.scrollOffset = 0;
    }

    private void tickSearch() {
        if (!this.searchDirty || System.currentTimeMillis() < this.searchDeadline) {
            return;
        }
        this.searchDirty = false;
        String needle = this.query.toLowerCase(Locale.ROOT).trim();
        if (needle.isBlank()) {
            this.results = List.of();
            return;
        }
        this.results = BuiltInRegistries.ITEM.keySet().stream()
            .filter(id -> this.matches(id, needle))
            .limit(100)
            .toList();
    }

    private boolean matches(ResourceLocation id, String needle) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != Items.AIR && (!this.sketchableOnly || isSketchable(item)) && (id.toString().contains(needle) || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(needle));
    }

    private static boolean isSketchable(Item item) {
        return item != Items.AIR && !(item instanceof BlockItem);
    }

    private void updateScrollbar(double mouseY) {
        int visibleRows = this.visibleRows();
        int maxOffset = Math.max(0, this.results.size() - visibleRows);
        if (maxOffset <= 0) {
            this.scrollOffset = 0;
            return;
        }
        int trackTop = this.top + SEARCH_HEIGHT + 7;
        int trackHeight = visibleRows * ROW_HEIGHT;
        float position = Mth.clamp((float)((mouseY - trackTop) / trackHeight), 0.0F, 1.0F);
        this.scrollOffset = Mth.clamp(Math.round(position * maxOffset), 0, maxOffset);
    }

    private int width(Font font) {
        int contentWidth = font == null ? MIN_WIDTH : font.width(this.query.isEmpty() ? Component.translatable("menu.sketchbook.item_search") : Component.literal(this.query)) + 24;
        if (font != null) {
            for (ResourceLocation result : this.results) {
                contentWidth = Math.max(contentWidth, font.width(this.displayName(result)) + 18 + (this.results.size() > this.visibleRows() ? SCROLLBAR_WIDTH + 4 : 0));
            }
            if (this.query.isEmpty()) {
                contentWidth = Math.max(contentWidth, font.width(Component.translatable("menu.sketchbook.item_search_empty")) + 18);
            } else if (this.searchDirty) {
                contentWidth = Math.max(contentWidth, font.width(this.searchingText()) + 18);
            }
        }
        return Mth.clamp(contentWidth, MIN_WIDTH, MAX_WIDTH);
    }

    private int height() {
        return SEARCH_HEIGHT + this.visibleRows() * ROW_HEIGHT + 8;
    }

    private int visibleRows() {
        if (this.query.isEmpty()) {
            return 1;
        }
        return Math.max(1, Math.min(MAX_VISIBLE_ROWS, this.results.size()));
    }

    private Component displayName(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item.getDescription();
    }

    private String searchingText() {
        int dots = (int)(System.currentTimeMillis() / 300L % 3L) + 1;
        return "Searching " + ".".repeat(dots);
    }
}
