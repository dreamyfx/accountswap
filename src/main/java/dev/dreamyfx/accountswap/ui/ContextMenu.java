package dev.dreamyfx.accountswap.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ContextMenu {

    public record Entry(String label, Runnable action) {}

    private int x, y;
    private final List<Entry> entries;
    private final Font font;
    private boolean visible = true;

    private static final int W = 120, EH = 18;

    public ContextMenu(int x, int y, List<Entry> entries, Font font) {
        this.x = x; this.y = y;
        this.entries = entries;
        this.font = font;
    }

    public boolean isVisible() { return visible; }
    public void hide() { visible = false; }

    public void render(GuiGraphics g, int mx, int my) {
        if (!visible) return;
        int h = entries.size() * EH + 4;

        g.fill(x, y, x + W, y + h, 0xE0141820);
        g.fill(x, y, x + W, y + 1, 0x60667799);
        g.fill(x, y + h - 1, x + W, y + h, 0x40667799);
        g.fill(x, y, x + 1, y + h, 0x50667799);
        g.fill(x + W - 1, y, x + W, y + h, 0x50667799);

        for (int i = 0; i < entries.size(); i++) {
            int ey = y + 2 + i * EH;
            boolean hovered = mx >= x && mx < x + W && my >= ey && my < ey + EH;
            if (hovered) g.fill(x + 1, ey, x + W - 1, ey + EH, 0x50334466);
            g.drawString(font, entries.get(i).label(), x + 8, ey + 5, hovered ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }
    }

    public boolean mouseClicked(int mx, int my) {
        if (!visible) return false;
        int h = entries.size() * EH + 4;
        if (mx < x || mx > x + W || my < y || my > y + h) { visible = false; return false; }
        for (int i = 0; i < entries.size(); i++) {
            int ey = y + 2 + i * EH;
            if (my >= ey && my < ey + EH) { entries.get(i).action().run(); visible = false; return true; }
        }
        return true;
    }
}
