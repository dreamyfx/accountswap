package dev.dreamyfx.accountswap.ui;

import dev.dreamyfx.accountswap.storage.AccountStorage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ConfigScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget clientIdField;
    private static final int BOX_W = 380, BOX_H = 200;

    public ConfigScreen(Screen parent) {
        super(Text.translatable("accountswap.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bx = (width - BOX_W) / 2;
        int by = (height - BOX_H) / 2;

        clientIdField = new TextFieldWidget(textRenderer,
                bx + 20, by + 80, BOX_W - 40, 20,
                Text.translatable("accountswap.config.client_id"));
        clientIdField.setMaxLength(128);
        clientIdField.setDrawsBackground(false);
        String saved = AccountStorage.getInstance().getClientId();
        if (saved != null) clientIdField.setText(saved);
        addDrawableChild(clientIdField);

        addDrawableChild(new ModernButton(bx + BOX_W / 2 - 105, by + BOX_H - 40, 100, 22,
                Text.literal("Save"), btn -> save(), ModernButton.Style.PRIMARY));
        addDrawableChild(new ModernButton(bx + BOX_W / 2 + 5, by + BOX_H - 40, 100, 22,
                Text.literal("Cancel"), btn -> client.setScreen(parent)));
    }

    private void save() {
        String id = clientIdField.getText().trim();
        if (!id.isEmpty()) {
            AccountStorage.getInstance().saveClientId(id);
        }
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);

        int bx = (width - BOX_W) / 2, by = (height - BOX_H) / 2;

        // Box
        context.fill(bx + 2, by, bx + BOX_W - 2, by + BOX_H, 0xD0101418);
        context.fill(bx, by + 2, bx + 2, by + BOX_H - 2, 0xD0101418);
        context.fill(bx + BOX_W - 2, by + 2, bx + BOX_W, by + BOX_H - 2, 0xD0101418);
        context.fill(bx + 2, by, bx + BOX_W - 2, by + 1, 0x60667799);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("accountswap.config.title"),
                bx + BOX_W / 2, by + 12, 0xFFDDDDDD);

        context.fill(bx + 10, by + 26, bx + BOX_W - 10, by + 27, 0x30FFFFFF);

        context.drawTextWithShadow(textRenderer,
                Text.translatable("accountswap.config.client_id"),
                bx + 20, by + 46, 0xFFAAAAAA);

        context.drawTextWithShadow(textRenderer,
                Text.literal("Register a free app at portal.azure.com"),
                bx + 20, by + 58, 0xFF555555);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Scope required: XboxLive.signin offline_access"),
                bx + 20, by + 68, 0xFF555555);

        // Field bg
        context.fill(bx + 18, by + 78, bx + BOX_W - 18, by + 102, 0x50000000);
        context.fill(bx + 18, by + 78, bx + BOX_W - 18, by + 79, 0x50AAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(parent); return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER) { save(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
