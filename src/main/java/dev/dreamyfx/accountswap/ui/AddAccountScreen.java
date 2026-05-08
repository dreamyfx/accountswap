package dev.dreamyfx.accountswap.ui;

import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.account.AccountType;
import dev.dreamyfx.accountswap.auth.AuthResult;
import dev.dreamyfx.accountswap.auth.DeviceCodeResponse;
import dev.dreamyfx.accountswap.auth.MicrosoftAuthFlow;
import dev.dreamyfx.accountswap.storage.AccountStorage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class AddAccountScreen extends Screen {

    private enum State { CHOOSE, OFFLINE_INPUT, DEVICE_CODE, POLLING, SUCCESS, ERROR }

    private final Screen parent;
    private State state = State.CHOOSE;

    private int boxW = 360, boxH = 220;
    private int boxX, boxY;

    // Offline
    private TextFieldWidget usernameField;

    // Device code
    private String userCode = "";
    private String verificationUri = "";
    private String pollStatus = "Waiting for sign-in...";
    private AtomicReference<MicrosoftAuthFlow> activeFlow = new AtomicReference<>();
    private boolean codeCopied = false;
    private long codeCopiedTime = 0;

    // Result
    private String errorMsg = "";
    private String successMsg = "";

    // Animation
    private float fadeAnim = 0f;

    public AddAccountScreen(Screen parent) {
        super(Text.translatable("accountswap.add"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boxX = (width - boxW) / 2;
        boxY = (height - boxH) / 2;
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();
        boxX = (width - boxW) / 2;
        boxY = (height - boxH) / 2;

        switch (state) {
            case CHOOSE -> {
                addDrawableChild(new ModernButton(boxX + 30, boxY + 70, 140, 28,
                        Text.translatable("accountswap.add.microsoft"),
                        btn -> startMicrosoft(), ModernButton.Style.PRIMARY));

                addDrawableChild(new ModernButton(boxX + 190, boxY + 70, 140, 28,
                        Text.translatable("accountswap.add.offline"),
                        btn -> {
                            state = State.OFFLINE_INPUT;
                            rebuildWidgets();
                        }));

                addDrawableChild(new ModernButton(boxX + boxW / 2 - 50, boxY + boxH - 38, 100, 22,
                        Text.translatable("accountswap.add.cancel"),
                        btn -> back()));
            }

            case OFFLINE_INPUT -> {
                usernameField = new TextFieldWidget(textRenderer,
                        boxX + 30, boxY + 80, boxW - 60, 20,
                        Text.translatable("accountswap.add.username"));
                usernameField.setMaxLength(32);
                usernameField.setPlaceholder(Text.literal("Enter username..."));
                usernameField.setDrawsBackground(false);
                addDrawableChild(usernameField);

                addDrawableChild(new ModernButton(boxX + boxW / 2 - 105, boxY + boxH - 38, 100, 22,
                        Text.translatable("accountswap.add.confirm"),
                        btn -> addOfflineAccount(), ModernButton.Style.PRIMARY));

                addDrawableChild(new ModernButton(boxX + boxW / 2 + 5, boxY + boxH - 38, 100, 22,
                        Text.translatable("accountswap.add.cancel"),
                        btn -> { state = State.CHOOSE; rebuildWidgets(); }));
            }

            case DEVICE_CODE -> {
                addDrawableChild(new ModernButton(boxX + boxW / 2 - 60, boxY + 120, 120, 22,
                        Text.literal(codeCopied ? "Copied!" : "Copy Code"),
                        btn -> {
                            client.keyboard.setClipboard(userCode);
                            codeCopied = true;
                            codeCopiedTime = System.currentTimeMillis();
                        }, ModernButton.Style.GHOST));

                addDrawableChild(new ModernButton(boxX + boxW / 2 - 50, boxY + boxH - 38, 100, 22,
                        Text.translatable("accountswap.add.cancel"),
                        btn -> {
                            MicrosoftAuthFlow f = activeFlow.get();
                            if (f != null) f.cancel();
                            back();
                        }));
            }

            case POLLING -> {
                addDrawableChild(new ModernButton(boxX + boxW / 2 - 50, boxY + boxH - 38, 100, 22,
                        Text.translatable("accountswap.add.cancel"),
                        btn -> {
                            MicrosoftAuthFlow f = activeFlow.get();
                            if (f != null) f.cancel();
                            back();
                        }));
            }

            case SUCCESS, ERROR -> {
                addDrawableChild(new ModernButton(boxX + boxW / 2 - 50, boxY + boxH - 38, 100, 22,
                        Text.literal("Done"), btn -> back()));
            }
        }
    }

    private void startMicrosoft() {
        String clientId = AccountStorage.getInstance().getClientId();
        if (clientId == null || clientId.isBlank()) {
            client.setScreen(new ConfigScreen(this));
            return;
        }

        MicrosoftAuthFlow flow = new MicrosoftAuthFlow(clientId);
        activeFlow.set(flow);
        pollStatus = "Starting device flow...";
        state = State.POLLING;
        rebuildWidgets();

        CompletableFuture.runAsync(() -> {
            try {
                DeviceCodeResponse dcr = flow.startDeviceFlow();
                client.execute(() -> {
                    userCode = dcr.userCode;
                    verificationUri = dcr.verificationUri;
                    state = State.DEVICE_CODE;
                    rebuildWidgets();

                    // Open browser
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(URI.create(dcr.verificationUri));
                        }
                    } catch (Exception ignored) {}
                });

                AuthResult result = flow.pollForToken(dcr, status ->
                        client.execute(() -> pollStatus = status));

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

    private void addOfflineAccount() {
        String name = usernameField != null ? usernameField.getText().trim() : "";
        if (name.isEmpty() || name.length() < 2) return;
        Account acc = Account.offline(name);
        AccountManager.getInstance().addAccount(acc);
        back();
    }

    private void back() {
        MicrosoftAuthFlow f = activeFlow.get();
        if (f != null) f.cancel();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        fadeAnim = Math.min(1f, fadeAnim + delta * 0.15f);

        // Dark backdrop
        context.fill(0, 0, width, height, 0x90000000);

        // Box
        drawBox(context, boxX, boxY, boxW, boxH);

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("accountswap.add"), boxX + boxW / 2, boxY + 14, 0xFFDDDDDD);

        // Divider
        context.fill(boxX + 10, boxY + 28, boxX + boxW - 10, boxY + 29, 0x30FFFFFF);

        // Content based on state
        switch (state) {
            case CHOOSE -> {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Choose account type:"),
                        boxX + boxW / 2, boxY + 50, 0xFFAAAAAA);
            }
            case OFFLINE_INPUT -> {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Enter a username:"),
                        boxX + boxW / 2, boxY + 58, 0xFFAAAAAA);
                // Search field bg
                context.fill(boxX + 28, boxY + 78, boxX + boxW - 28, boxY + 102, 0x50000000);
                context.fill(boxX + 28, boxY + 78, boxX + boxW - 28, boxY + 79, 0x50AAAAAA);
            }
            case DEVICE_CODE -> {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Open in browser:"),
                        boxX + boxW / 2, boxY + 42, 0xFFAAAAAA);
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(verificationUri),
                        boxX + boxW / 2, boxY + 56, 0xFF88AAFF);

                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Enter code:"),
                        boxX + boxW / 2, boxY + 76, 0xFFAAAAAA);

                // Big code display
                drawCodeBox(context, userCode, boxX + boxW / 2, boxY + 90);

                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Waiting for sign-in..."),
                        boxX + boxW / 2, boxY + 150, 0xFF666666);
            }
            case POLLING -> {
                drawSpinner(context, boxX + boxW / 2, boxY + boxH / 2 - 20);
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(pollStatus),
                        boxX + boxW / 2, boxY + boxH / 2 + 10, 0xFFAAAAAA);
            }
            case SUCCESS -> {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("§a✔ " + successMsg),
                        boxX + boxW / 2, boxY + boxH / 2 - 10, 0xFFFFFFFF);
            }
            case ERROR -> {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("§c✘ Authentication Failed"),
                        boxX + boxW / 2, boxY + boxH / 2 - 20, 0xFFFFFFFF);
                // Word-wrap error
                int lineY = boxY + boxH / 2;
                for (var line : textRenderer.wrapLines(Text.literal(errorMsg), boxW - 40)) {
                    context.drawCenteredTextWithShadow(textRenderer, line,
                            boxX + boxW / 2, lineY, 0xFFAA4444);
                    lineY += 10;
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBox(DrawContext ctx, int x, int y, int w, int h) {
        // Background
        ctx.fill(x + 2, y, x + w - 2, y + h, 0xD0101418);
        ctx.fill(x, y + 2, x + 2, y + h - 2, 0xD0101418);
        ctx.fill(x + w - 2, y + 2, x + w, y + h - 2, 0xD0101418);

        // Border
        ctx.fill(x + 2, y, x + w - 2, y + 1, 0x60667799);
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, 0x30667799);
        ctx.fill(x, y + 2, x + 1, y + h - 2, 0x40667799);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, 0x40667799);
    }

    private void drawCodeBox(DrawContext ctx, String code, int cx, int y) {
        int cw = textRenderer.getWidth(code) * 2 + 24;
        int x = cx - cw / 2;
        ctx.fill(x, y, x + cw, y + 20, 0x80001122);
        ctx.fill(x, y, x + cw, y + 1, 0x8088AAFF);
        ctx.fill(x, y + 19, x + cw, y + 20, 0x8088AAFF);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y + 6, 0);
        ctx.getMatrices().scale(2f, 2f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(code), 0, 0, 0xFFEECC44);
        ctx.getMatrices().pop();
    }

    private void drawSpinner(DrawContext ctx, int cx, int cy) {
        long t = System.currentTimeMillis() % 800;
        int frame = (int)(t / 100);
        String[] frames = {"|", "/", "—", "\\", "|", "/", "—", "\\"};
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(frames[frame]), cx, cy, 0xFF88AAFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER && state == State.OFFLINE_INPUT) {
            addOfflineAccount(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
