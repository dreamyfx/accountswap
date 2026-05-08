package dev.dreamyfx.accountswap.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ModernButton extends AbstractButton {

    public enum Style { DEFAULT, PRIMARY, DANGER, GHOST }

    private final Runnable action;
    private final Style style;
    private float hoverAnim = 0f;

    public ModernButton(int x, int y, int w, int h, Component label, Runnable action) {
        this(x, y, w, h, label, action, Style.DEFAULT);
    }

    public ModernButton(int x, int y, int w, int h, Component label, Runnable action, Style style) {
        super(x, y, w, h, label);
        this.action = action;
        this.style  = style;
    }

    @Override
    public void onPress() {
        if (active) action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        hoverAnim += delta * (isHovered() ? 0.2f : -0.2f);
        hoverAnim = Math.max(0f, Math.min(1f, hoverAnim));

        int bg = switch (style) {
            case PRIMARY -> blend(0xB0155a9a, 0xD01a6fbe, hoverAnim);
            case DANGER  -> blend(0xB09a1515, 0xD0be1a1a, hoverAnim);
            case GHOST   -> blend(0x30202020, 0x50303030, hoverAnim);
            default      -> blend(0xA0202428, 0xC0303438, hoverAnim);
        };

        fillRounded(g, getX(), getY(), getWidth(), getHeight(), bg);

        int hi = style == Style.PRIMARY ? 0x40aaddff : 0x30ffffff;
        g.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + 1, hi);
        g.fill(getX() + 2, getY() + getHeight() - 1, getX() + getWidth() - 2, getY() + getHeight(), 0x20000000);

        int border = active ? (isHovered() ? 0x80aaaaaa : 0x50666666) : 0x30444444;
        border(g, getX(), getY(), getWidth(), getHeight(), border);

        int col = active ? (isHovered() ? 0xFFFFFFFF : 0xFFCCCCCC) : 0xFF666666;
        int tx = getX() + getWidth() / 2;
        int ty = getY() + (getHeight() - 8) / 2;
        g.drawString(net.minecraft.client.Minecraft.getInstance().font, getMessage(), tx - net.minecraft.client.Minecraft.getInstance().font.width(getMessage()) / 2, ty, col, true);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    private void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + 2, y + h - 2, color);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + 1, y + h - 2, color);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
    }

    private int blend(int a, int b, float t) {
        int a4 = (a >> 24) & 0xFF, r4 = (a >> 16) & 0xFF, g4 = (a >> 8) & 0xFF, b4 = a & 0xFF;
        int a5 = (b >> 24) & 0xFF, r5 = (b >> 16) & 0xFF, g5 = (b >> 8) & 0xFF, b5 = b & 0xFF;
        return ((int)(a4 + (a5-a4)*t) << 24) | ((int)(r4 + (r5-r4)*t) << 16)
             | ((int)(g4 + (g5-g4)*t) << 8)  |  (int)(b4 + (b5-b4)*t);
    }
}
