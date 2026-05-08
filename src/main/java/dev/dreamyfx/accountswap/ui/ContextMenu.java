package dev.dreamyfx.accountswap.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public class ContextMenu {

    public record Entry(String label, Runnable action) {}

    private final int x, y;
    private final List<Entry> entries;
    private final TextRenderer textRenderer;
    private boolean visible = true;

    private static final int ENTRY_H = 18;
    private static final int MENU_W = 120;

    public ContextMenu(int x, int y, List<Entry> entries, TextRenderer textRenderer) {
        this.x = x;
        this.y = y;
        this.entries = entries;
        this.textRenderer = textRenderer;
    }

    public boolean isVisible() { return visible; }
    public void hide() { visible = false; }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        int menuH = entries.size() * ENTRY_H + 4;

        // Background
        ctx.fill(x, y, x + MENU_W, y + menuH, 0xE0141820);
        // Border
        ctx.fill(x, y, x + MENU_W, y + 1, 0x60667799);
        ctx.fill(x, y + menuH - 1, x + MENU_W, y + menuH, 0x40667799);
        ctx.fill(x, y, x + 1, y + menuH, 0x50667799);
        ctx.fill(x + MENU_W - 1, y, x + MENU_W, y + menuH, 0x50667799);

        for (int i = 0; i < entries.size(); i++) {
            int ey = y + 2 + i * ENTRY_H;
            boolean hovered = mouseX >= x && mouseX < x + MENU_W && mouseY >= ey && mouseY < ey + ENTRY_H;
            if (hovered) {
                ctx.fill(x + 1, ey, x + MENU_W - 1, ey + ENTRY_H, 0x50334466);
            }
            ctx.drawTextWithShadow(textRenderer, Text.literal(entries.get(i).label()),
                    x + 8, ey + 5, hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible) return false;
        int menuH = entries.size() * ENTRY_H + 4;
        if (mouseX < x || mouseX > x + MENU_W || mouseY < y || mouseY > y + menuH) {
            visible = false;
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            int ey = y + 2 + i * ENTRY_H;
            if (mouseY >= ey && mouseY < ey + ENTRY_H) {
                entries.get(i).action().run();
                visible = false;
                return true;
            }
        }
        return true;
    }
}
