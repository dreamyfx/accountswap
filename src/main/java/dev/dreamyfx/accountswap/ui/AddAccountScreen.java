package dev.dreamyfx.accountswap.ui;

import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.account.AccountType;
import dev.dreamyfx.accountswap.auth.AuthResult;
import dev.dreamyfx.accountswap.auth.MicrosoftAuthFlow;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class AddAccountScreen extends Screen {

    private enum State { CHOOSE, OFFLINE_INPUT, WAITING_BROWSER, SUCCESS, ERROR }

    private final Screen parent;
    private State state = State.CHOOSE;

    private static final int BOX_W = 360, BOX_H = 210;

    // Offline
    private TextFieldWidget usernameField;

    // Browser flow
    private final AtomicReference<MicrosoftAuthFlow> activeFlow = new AtomicReference<>();
    private String statusLine = "Opening your browser...";

    // Result
    private String successMsg = "";
    private String errorMsg   = "";

    public AddAccountScreen(Screen parent) {
        super(Text.translatable("accountswap.add"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();
        int bx = boxX(), by = boxY();

        switch (state) {
            case CHOOSE -> {
                addDrawableChild(new ModernButton(bx + 30, by + 65, 140, 28,
                        Text.translatable("accountswap.add.microsoft"),
                        btn -> startMicrosoft(), ModernButton.Style.PRIMARY));

                addDrawableChild(new ModernButton(bx + 190, by + 65, 140, 28,
                        Text.translatable("accountswap.add.offline"),
                        btn -> { state = State.OFFLINE_INPUT; rebuildWidgets(); }));

                addDrawableChild(cancelBtn(bx, by));
            }

            case OFFLINE_INPUT -> {
                usernameField = new TextFieldWidget(textRenderer,
                        bx + 30, by + 78, BOX_W - 60, 20,
                        Text.translatable("accountswap.add.username"));
                usernameField.setMaxLength(16);
                usernameField.setPlaceholder(Text.literal("Enter username..."));
                usernameField.setDrawsBackground(false);
                addDrawableChild(usernameField);

                addDrawableChild(new ModernButton(bx + BOX_W / 2 - 105, by + BOX_H - 38, 100, 22,
                        Text.translatable("accountswap.add.confirm"),
                        btn -> addOffline(), ModernButton.Style.PRIMARY));
                addDrawableChild(new ModernButton(bx + BOX_W / 2 + 5, by + BOX_H - 38, 100, 22,
                        Text.translatable("accountswap.add.cancel"),
                        btn -> { state = State.CHOOSE; rebuildWidgets(); }));
            }

            case WAITING_BROWSER -> {
                addDrawableChild(new ModernButton(bx + BOX_W / 2 - 50, by + BOX_H - 38, 100, 22,
                        Text.translatable("accountswap.add.cancel"),
                        btn -> {
                            MicrosoftAuthFlow f = activeFlow.get();
                            if (f != null) f.cancel();
                            back();
                        }));
            }

            case SUCCESS, ERROR -> {
                addDrawableChild(new ModernButton(bx + BOX_W / 2 - 50, by + BOX_H - 38, 100, 22,
                        Text.literal("Done"), btn -> back()));
            }
        }
    }

    private void startMicrosoft() {
        state = State.WAITING_BROWSER;
        statusLine = "Opening your browser...";
        rebuildWidgets();

        MicrosoftAuthFlow flow = new MicrosoftAuthFlow();
        activeFlow.set(flow);

        CompletableFuture.runAsync(() -> {
            try {
                MicrosoftAuthFlow.BrowserAuthData data = flow.startBrowserFlow();

                // Open system browser
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(URI.create(data.authUrl()));
                    } else {
                        // Fallback: copy to clipboard
                        client.execute(() ->
                                client.keyboard.setClipboard(data.authUrl())
                        );
                    }
                } catch (Exception e) {
                    client.execute(() -> statusLine = "Could not open browser. URL copied to clipboard.");
                    client.execute(() -> client.keyboard.setClipboard(data.authUrl()));
                }

                client.execute(() -> statusLine = "Sign in to your Microsoft account, then return here.");

                AuthResult result = flow.waitForCallback(data.port(), s ->
                        client.execute(() -> statusLine = s)
                );

                client.execute(() -> {
                    if (result.isSuccess()) {
                        Account acc = new Account(result.getUsername(), result.getUuid(), AccountType.MICROSOFT);
                        acc.setAccessToken(result.getAccessToken());
                        acc.setRefreshToken(result.getRefreshToken());
                        acc.setTokenExpiry(result.getExpiry());
                        AccountManager.getInstance().addAccount(acc);
                        successMsg = "Added: " + result.getUsername();
                        state = State.SUCCESS;
                    } else {
                        errorMsg = result.getError();
                        state = State.ERROR;
                    }
                    rebuildWidgets();
                });

            } catch (Exception e) {
                client.execute(() -> {
                    errorMsg = e.getMessage();
                    state = State.ERROR;
                    rebuildWidgets();
                });
            }
        });
    }

    private void addOffline() {
        if (usernameField == null) return;
        String name = usernameField.getText().trim();
        if (name.length() < 2) return;
        AccountManager.getInstance().addAccount(Account.offline(name));
        back();
    }

    private void back() {
        MicrosoftAuthFlow f = activeFlow.get();
        if (f != null) f.cancel();
        client.setScreen(parent);
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x90000000);

        int bx = boxX(), by = boxY();
        drawBox(ctx, bx, by, BOX_W, BOX_H);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("accountswap.add"), bx + BOX_W / 2, by + 12, 0xFFDDDDDD);
        ctx.fill(bx + 10, by + 26, bx + BOX_W - 10, by + 27, 0x30FFFFFF);

        switch (state) {
            case CHOOSE -> {
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Choose account type:"), bx + BOX_W / 2, by + 46, 0xFF888888);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Microsoft  —  real account, browser sign-in"),
                        bx + BOX_W / 2, by + 56, 0xFF555555);
            }
            case OFFLINE_INPUT -> {
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Offline / Cracked"), bx + BOX_W / 2, by + 44, 0xFF888888);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Enter a username (2–16 characters):"),
                        bx + BOX_W / 2, by + 56, 0xFF555555);
                // Field bg
                ctx.fill(bx + 28, by + 75, bx + BOX_W - 28, by + 101, 0x50000000);
                ctx.fill(bx + 28, by + 75, bx + BOX_W - 28, by + 76, 0x50AAAAAA);
            }
            case WAITING_BROWSER -> {
                drawSpinner(ctx, bx + BOX_W / 2, by + 65);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(statusLine), bx + BOX_W / 2, by + 85, 0xFF888888);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Your browser should have opened."),
                        bx + BOX_W / 2, by + 100, 0xFF555555);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Sign in, then this screen will update automatically."),
                        bx + BOX_W / 2, by + 112, 0xFF555555);
            }
            case SUCCESS ->
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("§a✔ " + successMsg), bx + BOX_W / 2, by + BOX_H / 2 - 10, 0xFFFFFFFF);
            case ERROR -> {
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("§c✘ Failed"), bx + BOX_W / 2, by + BOX_H / 2 - 22, 0xFFFFFFFF);
                int ly = by + BOX_H / 2 - 6;
                for (var line : textRenderer.wrapLines(Text.literal(errorMsg), BOX_W - 40)) {
                    ctx.drawCenteredTextWithShadow(textRenderer, line, bx + BOX_W / 2, ly, 0xFFAA4444);
                    ly += 10;
                }
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBox(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 2, y, x + w - 2, y + h, 0xD0101418);
        ctx.fill(x, y + 2, x + 2, y + h - 2, 0xD0101418);
        ctx.fill(x + w - 2, y + 2, x + w, y + h - 2, 0xD0101418);
        ctx.fill(x + 2, y, x + w - 2, y + 1, 0x60667799);
        ctx.fill(x, y + 2, x + 1, y + h - 2, 0x40667799);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, 0x40667799);
    }

    private void drawSpinner(DrawContext ctx, int cx, int cy) {
        String[] f = {"|", "/", "—", "\\", "|", "/", "—", "\\"};
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(f[(int)((System.currentTimeMillis() % 800) / 100)]), cx, cy, 0xFF88AAFF);
    }

    private ModernButton cancelBtn(int bx, int by) {
        return new ModernButton(bx + BOX_W / 2 - 50, by + BOX_H - 38, 100, 22,
                Text.translatable("accountswap.add.cancel"), btn -> back());
    }

    private int boxX() { return (width  - BOX_W) / 2; }
    private int boxY() { return (height - BOX_H) / 2; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER && state == State.OFFLINE_INPUT) { addOffline(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean shouldPause() { return false; }
}
