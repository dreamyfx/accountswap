package dev.dreamyfx.accountswap.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ModernButton extends ButtonWidget {

    private float hoverAnim = 0f;

    public enum Style { DEFAULT, PRIMARY, DANGER, GHOST }

    private final Style style;

    public ModernButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        this(x, y, width, height, message, onPress, Style.DEFAULT);
    }

    public ModernButton(int x, int y, int width, int height, Text message, PressAction onPress, Style style) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.style = style;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered();
        hoverAnim += delta * (hovered ? 0.2f : -0.2f);
        hoverAnim = Math.max(0f, Math.min(1f, hoverAnim));

        int bg = switch (style) {
            case PRIMARY -> blendColor(0xB0155a9a, 0xD01a6fbe, hoverAnim);
            case DANGER  -> blendColor(0xB09a1515, 0xD0be1a1a, hoverAnim);
            case GHOST   -> blendColor(0x30202020, 0x50303030, hoverAnim);
            default      -> blendColor(0xA0202428, 0xC0303438, hoverAnim);
        };

        // Background
        fillRounded(context, getX(), getY(), getWidth(), getHeight(), bg);

        // Top highlight line
        int highlight = style == Style.PRIMARY ? 0x40aaddff : 0x30ffffff;
        context.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + 1, highlight);

        // Bottom shadow line
        context.fill(getX() + 2, getY() + getHeight() - 1, getX() + getWidth() - 2, getY() + getHeight(), 0x20000000);

        // Border
        int border = isActive() ? (hovered ? 0x80aaaaaa : 0x50666666) : 0x30444444;
        drawBorder(context, getX(), getY(), getWidth(), getHeight(), border);

        // Text
        int textColor = isActive() ? (hovered ? 0xFFFFFFFF : 0xFFCCCCCC) : 0xFF666666;
        int textX = getX() + getWidth() / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        context.drawCenteredTextWithShadow(
                net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                getMessage(), textX, textY, textColor
        );
    }

    private void fillRounded(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 2, y, x + w - 2, y + h, color);
        ctx.fill(x, y + 2, x + 2, y + h - 2, color);
        ctx.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 2, y, x + w - 2, y + 1, color);
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
        ctx.fill(x, y + 2, x + 1, y + h - 2, color);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
    }

    private int blendColor(int from, int to, float t) {
        int fa = (from >> 24) & 0xFF, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
