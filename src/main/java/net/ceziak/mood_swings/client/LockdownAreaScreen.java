package net.ceziak.mood_swings.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.doctor4t.wathe.util.ShopEntry;
import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.ceziak.mood_swings.network.ActivateLockdownPayload;
import net.ceziak.mood_swings.network.LockdownAreaOption;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Lockdown area selector rendered in Wathe's native killer-shop visual language.
 *
 * <p>The selector intentionally avoids a large custom panel. Each area is
 * presented as a Wathe tool-shop slot with the normal gold/brown framing,
 * price tag, item icon, and shop-style hover sheen.</p>
 */
public final class LockdownAreaScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final Type OPTION_LIST_TYPE = new TypeToken<List<LockdownAreaOption>>() { }.getType();

    /** Wathe's private StoreItemWidget highlight colour. */
    private static final int SHOP_HIGHLIGHT = 0x90FFBF49;
    private static final int BACKDROP = 0x8A160902;
    private static final int TAG_BG = 0xF4160902;
    private static final int TAG_INNER = 0xFF2C1204;
    private static final int GOLD = 0xFFC5A244;
    private static final int GOLD_DARK = 0xFF815A15;
    private static final int GOLD_HOVER = 0xFFFFBF49;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFB99C71;

    private static final int SLOT_SIZE = 30;
    private static final int ITEM_OFFSET = 7;
    private static final int CELL_WIDTH = 56;
    private static final int NAME_MAX_WIDTH = 52;
    private static final int ROW_GAP = 56;
    private static final String COIN_GLYPH = "\uE781";

    private final List<LockdownAreaOption> areas;
    private final List<AreaSlot> visibleSlots = new ArrayList<>();

    private int page;
    private int pageSize = 1;
    private int rowY;
    private int previousX;
    private int nextX;
    private int navigationY;

    private LockdownAreaScreen(List<LockdownAreaOption> areas) {
        super(Text.translatable("screen.mood_swings.lockdown.title"));
        this.areas = areas == null ? List.of() : List.copyOf(areas);
    }

    public static LockdownAreaScreen fromJson(String json) {
        try {
            List<LockdownAreaOption> parsed = GSON.fromJson(json, OPTION_LIST_TYPE);
            return new LockdownAreaScreen(parsed == null ? List.of() : parsed);
        } catch (RuntimeException ignored) {
            return new LockdownAreaScreen(new ArrayList<>());
        }
    }

    @Override
    protected void init() {
        clearChildren();
        visibleSlots.clear();

        // Keep the same compact horizontal rhythm as Wathe's 38 px shop slots,
        // but leave enough room for readable area names beneath identical icons.
        pageSize = Math.max(1, Math.min(13, (width - 28) / CELL_WIDTH));
        rowY = Math.max(54, Math.min(height - 86, height / 2 - 42));
        navigationY = rowY + 42;

        page = Math.max(0, Math.min(page, totalPages() - 1));
        rebuildVisibleSlots();
    }

    private void rebuildVisibleSlots() {
        visibleSlots.clear();
        int start = page * pageSize;
        int end = Math.min(areas.size(), start + pageSize);
        int count = Math.max(0, end - start);
        int totalWidth = count * CELL_WIDTH;
        int startX = (width - totalWidth) / 2;

        for (int index = start; index < end; index++) {
            int local = index - start;
            int cellX = startX + local * CELL_WIDTH;
            int slotX = cellX + (CELL_WIDTH - SLOT_SIZE) / 2;
            visibleSlots.add(new AreaSlot(slotX, rowY, cellX, areas.get(index)));
        }

        int navSpread = Math.max(56, totalWidth / 2 + 34);
        previousX = width / 2 - navSpread;
        nextX = width / 2 + navSpread - 18;
    }

    private int totalPages() {
        return Math.max(1, (areas.size() + pageSize - 1) / pageSize);
    }

    private void changePage(int direction) {
        int next = Math.max(0, Math.min(page + direction, totalPages() - 1));
        if (next != page) {
            page = next;
            rebuildVisibleSlots();
        }
    }

    private void select(LockdownAreaOption area) {
        if (ClientPlayNetworking.canSend(ActivateLockdownPayload.ID)) {
            ClientPlayNetworking.send(new ActivateLockdownPayload(area.id()));
        }
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        if (areas.isEmpty()) {
            drawEmptyState(context);
            return;
        }

        drawHeader(context);
        for (AreaSlot slot : visibleSlots) {
            drawAreaSlot(context, slot, mouseX, mouseY);
        }
        drawFooter(context, mouseX, mouseY);
    }

    private void drawHeader(DrawContext context) {
        Text header = Text.translatable("screen.mood_swings.lockdown.shop_header");
        drawTagCentered(context, header, width / 2, rowY - 43, TEXT, true);

        Text subtitle = Text.translatable("screen.mood_swings.lockdown.shop_subtitle");
        context.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, rowY - 27, TEXT_MUTED);
    }

    private void drawAreaSlot(DrawContext context, AreaSlot slot, int mouseX, int mouseY) {
        boolean hovered = slot.contains(mouseX, mouseY);

        // Price plaque, matching Wathe's labels above killer-shop items.
        Text price = Text.literal(LockdownSystem.PRICE + COIN_GLYPH);
        drawTagCentered(context, price, slot.slotX + SLOT_SIZE / 2, slot.slotY - 18, TEXT, hovered);

        // Use Wathe's actual TOOL shop frame instead of recreating it.
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), slot.slotX, slot.slotY, SLOT_SIZE, SLOT_SIZE);
        context.drawItem(LockdownSystem.LOCKDOWN_ICON.getDefaultStack(), slot.slotX + ITEM_OFFSET, slot.slotY + ITEM_OFFSET);

        if (hovered) {
            drawWatheHighlight(context, slot.slotX + ITEM_OFFSET, slot.slotY + ITEM_OFFSET);
        }

        String fitted = fit(slot.area.displayName(), NAME_MAX_WIDTH);
        int nameColor = hovered ? GOLD_HOVER : TEXT;
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(fitted),
                slot.cellX + CELL_WIDTH / 2,
                slot.slotY + 34,
                nameColor
        );

        if (hovered) {
            Text fullName = Text.literal(safeName(slot.area.displayName()));
            Text action = Text.translatable("screen.mood_swings.lockdown.hover_action", LockdownSystem.DURATION_SECONDS);
            int tooltipY = Math.min(height - 32, slot.slotY + ROW_GAP + 8);
            drawTagCentered(context, fullName, width / 2, tooltipY, TEXT, true);
            context.drawCenteredTextWithShadow(textRenderer, action, width / 2, tooltipY + 15, TEXT_MUTED);
        }
    }

    /** Reproduces Wathe's StoreItemWidget hover sheen over the 16x16 item. */
    private static void drawWatheHighlight(DrawContext context, int x, int y) {
        context.fill(x, y, x + 16, y + 14, SHOP_HIGHLIGHT);
        context.fill(x, y + 14, x + 15, y + 15, SHOP_HIGHLIGHT);
        context.fill(x, y + 15, x + 14, y + 16, SHOP_HIGHLIGHT);
    }

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        Text details = Text.translatable(
                "screen.mood_swings.lockdown.shop_details",
                LockdownSystem.DURATION_SECONDS,
                LockdownSystem.COOLDOWN_SECONDS / 60
        );
        context.drawCenteredTextWithShadow(textRenderer, details, width / 2, navigationY + 14, TEXT_MUTED);

        if (totalPages() <= 1) {
            return;
        }

        boolean previousEnabled = page > 0;
        boolean nextEnabled = page < totalPages() - 1;
        boolean previousHovered = previousEnabled && contains(mouseX, mouseY, previousX, navigationY, 18, 14);
        boolean nextHovered = nextEnabled && contains(mouseX, mouseY, nextX, navigationY, 18, 14);

        drawNavigationTag(context, previousX, navigationY, "<", previousEnabled, previousHovered);
        drawNavigationTag(context, nextX, navigationY, ">", nextEnabled, nextHovered);

        Text pageText = Text.translatable("screen.mood_swings.lockdown.page_short", page + 1, totalPages());
        context.drawCenteredTextWithShadow(textRenderer, pageText, width / 2, navigationY + 3, TEXT_MUTED);
    }

    private void drawNavigationTag(DrawContext context, int x, int y, String glyph, boolean enabled, boolean hovered) {
        int border = enabled ? (hovered ? GOLD_HOVER : GOLD) : 0xFF4D3820;
        int text = enabled ? (hovered ? GOLD_HOVER : TEXT) : 0xFF6D6254;
        context.fill(x - 1, y + 1, x + 19, y + 15, BACKDROP);
        context.fill(x, y, x + 18, y + 14, border);
        context.fill(x + 1, y + 1, x + 17, y + 13, GOLD_DARK);
        context.fill(x + 2, y + 2, x + 16, y + 12, TAG_BG);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(glyph), x + 9, y + 3, text);
    }

    private void drawEmptyState(DrawContext context) {
        int centerY = height / 2 - 12;
        drawTagCentered(
                context,
                Text.translatable("screen.mood_swings.lockdown.empty_title"),
                width / 2,
                centerY,
                TEXT,
                false
        );
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.mood_swings.lockdown.empty"),
                width / 2,
                centerY + 19,
                TEXT_MUTED
        );
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.mood_swings.lockdown.empty_hint"),
                width / 2,
                centerY + 31,
                TEXT_MUTED
        );
    }

    private void drawTagCentered(DrawContext context, Text text, int centerX, int y, int textColor, boolean brightBorder) {
        int width = textRenderer.getWidth(text) + 10;
        int x = centerX - width / 2;
        int border = brightBorder ? GOLD_HOVER : GOLD;

        context.fill(x - 2, y + 2, x + width + 2, y + 15, BACKDROP);
        context.fill(x, y, x + width, y + 13, border);
        context.fill(x + 1, y + 1, x + width - 1, y + 12, GOLD_DARK);
        context.fill(x + 2, y + 2, x + width - 2, y + 11, TAG_BG);
        context.fill(x + 3, y + 3, x + width - 3, y + 4, TAG_INNER);
        context.drawCenteredTextWithShadow(textRenderer, text, centerX, y + 3, textColor);
    }

    private String fit(String value, int maxWidth) {
        String safe = safeName(value);
        if (textRenderer.getWidth(safe) <= maxWidth) {
            return safe;
        }
        String ellipsis = "...";
        String trimmed = textRenderer.trimToWidth(safe, Math.max(1, maxWidth - textRenderer.getWidth(ellipsis)));
        return trimmed + ellipsis;
    }

    private static String safeName(String value) {
        return value == null || value.isBlank() ? "Unnamed Area" : value;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (AreaSlot slot : visibleSlots) {
                if (slot.contains(mouseX, mouseY)) {
                    select(slot.area);
                    return true;
                }
            }

            if (page > 0 && contains(mouseX, mouseY, previousX, navigationY, 18, 14)) {
                changePage(-1);
                return true;
            }
            if (page < totalPages() - 1 && contains(mouseX, mouseY, nextX, navigationY, 18, 14)) {
                changePage(1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT && page > 0) {
            changePage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT && page < totalPages() - 1) {
            changePage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record AreaSlot(int slotX, int slotY, int cellX, LockdownAreaOption area) {
        private boolean contains(double mouseX, double mouseY) {
            return LockdownAreaScreen.contains(mouseX, mouseY, slotX, slotY, SLOT_SIZE, SLOT_SIZE);
        }
    }
}
