package dev.dreamyfx.accountswap.ui;

import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.account.types.CrackedAccount;
import dev.dreamyfx.accountswap.account.types.MicrosoftAccount;
import dev.dreamyfx.accountswap.auth.MicrosoftLogin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AddAccountScreen extends Screen {

    private enum State { CHOOSE, OFFLINE_INPUT, WAITING, SUCCESS, ERROR }

    private final Screen parent;
    private State state = State.CHOOSE;

    private static final int W = 360, H = 210;

    private EditBox nameField;
    private String statusLine = "Opening browser...";
    private String resultMsg  = "";
    private boolean authStarted = false;

    public AddAccountScreen(Screen parent) {
        super(Component.translatable("accountswap.add"));
        this.parent = parent;
    }

    @Override
    protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();

        int bx = bx(), by = by();

        switch (state) {
            case CHOOSE -> {
                addRenderableWidget(new ModernButton(bx + 30, by + 60, 140, 28,
                        Component.translatable("accountswap.add.microsoft"),
                        this::startMicrosoft, ModernButton.Style.PRIMARY));
                addRenderableWidget(new ModernButton(bx + 190, by + 60, 140, 28,
                        Component.translatable("accountswap.add.offline"),
                        () -> { state = State.OFFLINE_INPUT; rebuild(); }));
                addRenderableWidget(cancel(bx, by));
            }
            case OFFLINE_INPUT -> {
                nameField = new EditBox(font, bx + 30, by + 78, W - 60, 20, Component.literal(""));
                nameField.setMaxLength(16);
                nameField.setHint(Component.literal("Username (2-16 chars)"));
                nameField.setBordered(false);
                addRenderableWidget(nameField);

                addRenderableWidget(new ModernButton(bx + W/2 - 105, by + H - 38, 100, 22,
                        Component.translatable("accountswap.add.confirm"), this::addCracked, ModernButton.Style.PRIMARY));
                addRenderableWidget(new ModernButton(bx + W/2 + 5, by + H - 38, 100, 22,
                        Component.translatable("accountswap.add.cancel"), () -> { state = State.CHOOSE; rebuild(); }));
            }
            case WAITING -> {
                addRenderableWidget(new ModernButton(bx + W/2 - 50, by + H - 38, 100, 22,
                        Component.translatable("accountswap.add.cancel"), () -> { MicrosoftLogin.stopServer(); back(); }));
            }
            case SUCCESS, ERROR -> {
                addRenderableWidget(new ModernButton(bx + W/2 - 50, by + H - 38, 100, 22,
                        Component.literal("Done"), this::back));
            }
        }
    }

    private void startMicrosoft() {
        if (authStarted) return;
        authStarted = true;
        state = State.WAITING;
        statusLine = "Opening browser...";
        rebuild();

        // startLogin opens browser and starts local server
        // callback fires when user finishes or cancels
        MicrosoftLogin.startLogin(refreshToken -> {
            if (refreshToken == null) {
                minecraft.execute(() -> { resultMsg = "Sign-in cancelled or failed."; state = State.ERROR; rebuild(); });
                return;
            }
            minecraft.execute(() -> statusLine = "Authenticating...");

            // Run auth chain off main thread
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                MicrosoftAccount acc = new MicrosoftAccount(refreshToken);
                boolean ok = acc.fetchInfo();
                minecraft.execute(() -> {
                    if (ok) {
                        AccountManager.get().add(acc);
                        resultMsg = "Added: " + acc.getUsername();
                        state = State.SUCCESS;
                    } else {
                        resultMsg = "Authentication failed.";
                        state = State.ERROR;
                    }
                    rebuild();
                });
            });
        });
    }

    private void addCracked() {
        String name = nameField != null ? nameField.getValue().trim() : "";
        if (name.length() < 2) return;
        CrackedAccount acc = new CrackedAccount(name);
        acc.fetchInfo();
        AccountManager.get().add(acc);
        back();
    }

    private void back() { minecraft.setScreen(parent); }

    private ModernButton cancel(int bx, int by) {
        return new ModernButton(bx + W/2 - 50, by + H - 38, 100, 22,
                Component.translatable("accountswap.add.cancel"), this::back);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x90000000);
        int bx = bx(), by = by();
        box(g, bx, by, W, H);

        g.drawString(font, getMessage(), bx + W/2 - font.width(getMessage())/2, by + 12, 0xFFDDDDDD, true);
        g.fill(bx + 10, by + 26, bx + W - 10, by + 27, 0x30FFFFFF);

        switch (state) {
            case CHOOSE -> {
                g.drawString(font, "Microsoft — sign in with your browser", bx + W/2 - font.width("Microsoft — sign in with your browser")/2, by + 46, 0xFF555555, false);
            }
            case OFFLINE_INPUT -> {
                g.drawString(font, "Offline / Cracked account", bx + W/2 - font.width("Offline / Cracked account")/2, by + 46, 0xFF888888, false);
                g.fill(bx + 28, by + 75, bx + W - 28, by + 101, 0x50000000);
                g.fill(bx + 28, by + 75, bx + W - 28, by + 76, 0x50AAAAAA);
            }
            case WAITING -> {
                spinner(g, bx + W/2, by + 65);
                g.drawString(font, statusLine, bx + W/2 - font.width(statusLine)/2, by + 82, 0xFF888888, false);
                String sub = "Sign in to your browser, then return here.";
                g.drawString(font, sub, bx + W/2 - font.width(sub)/2, by + 96, 0xFF555555, false);
            }
            case SUCCESS ->
                g.drawString(font, "§a✔ " + resultMsg, bx + W/2 - font.width("✔ " + resultMsg)/2 - 4, by + H/2 - 10, 0xFFFFFFFF, true);
            case ERROR -> {
                g.drawString(font, "§c✘ Failed", bx + W/2 - font.width("✘ Failed")/2 - 4, by + H/2 - 22, 0xFFFFFFFF, true);
                g.drawString(font, resultMsg, bx + W/2 - font.width(resultMsg)/2, by + H/2 - 6, 0xFFAA4444, false);
            }
        }

        super.render(g, mx, my, delta);
    }

    private void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y, x + w - 2, y + h, 0xD0101418);
        g.fill(x, y + 2, x + 2, y + h - 2, 0xD0101418);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, 0xD0101418);
        g.fill(x + 2, y, x + w - 2, y + 1, 0x60667799);
        g.fill(x, y + 2, x + 1, y + h - 2, 0x40667799);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, 0x40667799);
    }

    private void spinner(GuiGraphics g, int cx, int cy) {
        String[] f = {"|", "/", "—", "\\", "|", "/", "—", "\\"};
        String ch = f[(int)((System.currentTimeMillis() % 800) / 100)];
        g.drawString(font, ch, cx - font.width(ch)/2, cy, 0xFF88AAFF, false);
    }

    private int bx() { return (width  - W) / 2; }
    private int by() { return (height - H) / 2; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { MicrosoftLogin.stopServer(); back(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER && state == State.OFFLINE_INPUT) { addCracked(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
