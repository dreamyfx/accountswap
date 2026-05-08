package dev.dreamyfx.accountswap.ui;

import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.animation.AnimationUtil;
import dev.dreamyfx.accountswap.auth.AuthResult;
import dev.dreamyfx.accountswap.auth.MicrosoftAuthFlow;
import dev.dreamyfx.accountswap.skin.SkinManager;
import dev.dreamyfx.accountswap.storage.AccountStorage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class AccountSwapScreen extends Screen {

    private static final int LEFT_W = 198;
    private static final int RIGHT_W = 212;
    private static final int PANEL_H = 290;
    private static final int ENTRY_H = 38;
    private static final int ENTRY_SPACING = 2;
    private static final int LIST_TOP_PAD = 56;

    private final Screen parent;

    // Panel positions (computed in init)
    private int panelLeft, panelTop;

    // Animation
    private float openAnim = 0f;
    private boolean closing = false;

    // Account list scroll
    private float scrollOffset = 0f;
    private float targetScroll = 0f;

    // Selected account
    private Account selected;

    // Search widget
    private TextFieldWidget searchField;

    // Context menu
    private ContextMenu contextMenu;

    // Status message (auth feedback)
    private String statusMessage = "";
    private float statusAlpha = 0f;
    private long statusTime = 0;

    // Buttons
    private ModernButton loginBtn, removeBtn, refreshBtn, addBtn;

    public AccountSwapScreen(Screen parent) {
        super(Text.translatable("screen.accountswap.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelLeft = (width - LEFT_W - RIGHT_W - 12) / 2;
        panelTop = (height - PANEL_H) / 2;

        int searchX = panelLeft + 8;
        int searchY = panelTop + 30;

        searchField = new TextFieldWidget(textRenderer, searchX, searchY, LEFT_W - 40, 18,
                Text.translatable("accountswap.search"));
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.translatable("accountswap.search"));
        searchField.setDrawsBackground(false);
        addDrawableChild(searchField);

        // Add (+) button top-right of left panel
        addDrawableChild(new ModernButton(
                panelLeft + LEFT_W - 26, panelTop + 28, 20, 20,
                Text.literal("+"), btn -> openAddAccount()
        ));

        int rp = panelLeft + LEFT_W + 12;

        loginBtn = new ModernButton(rp + 4, panelTop + PANEL_H - 60, 96, 22,
                Text.translatable("accountswap.login"), btn -> loginSelected(), ModernButton.Style.PRIMARY);
        removeBtn = new ModernButton(rp + 108, panelTop + PANEL_H - 60, 96, 22,
                Text.translatable("accountswap.remove"), btn -> removeSelected(), ModernButton.Style.DANGER);
        refreshBtn = new ModernButton(rp + 4, panelTop + PANEL_H - 34, 200, 22,
                Text.translatable("accountswap.refresh"), btn -> refreshSelected());
        addBtn = new ModernButton(rp + 4, panelTop + PANEL_H - 34, 200, 22,
                Text.translatable("accountswap.add"), btn -> openAddAccount());

        addDrawableChild(loginBtn);
        addDrawableChild(removeBtn);
        addDrawableChild(refreshBtn);

        updateButtonState();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Animate open/close
        float speed = delta * 0.14f;
        if (!closing) {
            openAnim = Math.min(1f, openAnim + speed);
        } else {
            openAnim = Math.max(0f, openAnim - speed * 1.3f);
            if (openAnim <= 0f) {
                client.setScreen(parent);
                return;
            }
        }

        // Smooth scroll
        scrollOffset += (targetScroll - scrollOffset) * Math.min(1f, delta * 0.18f);

        // Status message fade
        if (statusAlpha > 0) {
            long elapsed = System.currentTimeMillis() - statusTime;
            if (elapsed > 2500) statusAlpha = Math.max(0f, statusAlpha - delta * 0.05f);
        }

        float scale = AnimationUtil.easeOutBack(openAnim);
        float alpha = AnimationUtil.easeOutCubic(openAnim);

        // Dark backdrop
        int overlayAlpha = (int) (alpha * 0xB0);
        context.fill(0, 0, width, height, (overlayAlpha << 24));

        // Apply scale from center
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-width / 2f, -height / 2f, 0);

        drawLeftPanel(context, mouseX, mouseY, delta);
        drawRightPanel(context, mouseX, mouseY, delta);

        // Status message
        if (statusAlpha > 0 && !statusMessage.isEmpty()) {
            int sc = (int)(statusAlpha * 0xFF);
            int textColor = (sc << 24) | 0x88FF88;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMessage),
                    panelLeft + LEFT_W / 2, panelTop - 18, textColor);
        }

        context.getMatrices().pop();

        super.render(context, mouseX, mouseY, delta);

        if (contextMenu != null && contextMenu.isVisible()) {
            contextMenu.render(context, mouseX, mouseY, delta);
        }
    }

    // ── LEFT PANEL ───────────────────────────────────────────────────────────

    private void drawLeftPanel(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = panelLeft, y = panelTop, w = LEFT_W, h = PANEL_H;

        drawPanel(context, x, y, w, h);

        // Title
        context.drawTextWithShadow(textRenderer, Text.literal("Accounts"), x + 10, y + 10, 0xFFDDDDDD);

        // Search bg
        int sfx = x + 8, sfy = y + 28;
        context.fill(sfx, sfy, sfx + w - 40, sfy + 20, 0x60000000);
        drawBorderLine(context, sfx, sfy, w - 40, 20, 0x40AAAAAA);

        // Divider line
        context.fill(x + 6, y + 52, x + w - 6, y + 53, 0x30FFFFFF);

        // Account list with scissor
        int listX = x + 4;
        int listY0 = y + LIST_TOP_PAD;
        int listH = h - LIST_TOP_PAD - 10;
        context.enableScissor(listX, listY0, listX + w - 8, listY0 + listH);

        String query = searchField != null ? searchField.getText() : "";
        List<Account> accounts = AccountManager.getInstance().search(query);
        int entryY = listY0 - (int) scrollOffset;

        for (Account acc : accounts) {
            if (entryY + ENTRY_H > listY0 && entryY < listY0 + listH) {
                drawAccountEntry(context, acc, listX, entryY, w - 8, ENTRY_H, mouseX, mouseY);
            }
            entryY += ENTRY_H + ENTRY_SPACING;
        }

        context.disableScissor();

        // Scroll bar
        int totalEntries = accounts.size();
        int totalH = totalEntries * (ENTRY_H + ENTRY_SPACING);
        if (totalH > listH) {
            float barFrac = (float) listH / totalH;
            int barH = Math.max(20, (int) (listH * barFrac));
            float scrollFrac = scrollOffset / (totalH - listH);
            int barY = listY0 + (int) ((listH - barH) * scrollFrac);
            context.fill(x + w - 5, barY, x + w - 3, barY + barH, 0x60AAAAAA);
        }
    }

    private void drawAccountEntry(DrawContext ctx, Account acc, int x, int y, int w, int h,
                                   int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);
        boolean isSelected = acc == selected;
        boolean isActive = acc.isActive();

        // Background
        int bg;
        if (isSelected) {
            bg = hovered ? 0xC02244AA : 0xA01a3380;
        } else {
            bg = hovered ? 0x80282C30 : 0x50181C20;
        }
        fillRounded(ctx, x, y, w, h, bg);

        // Selection glow on left edge
        if (isSelected) {
            ctx.fill(x, y + 3, x + 2, y + h - 3, 0xFF4488EE);
        }

        // Active dot
        if (isActive) {
            ctx.fill(x + w - 10, y + h / 2 - 2, x + w - 6, y + h / 2 + 2, 0xFF44EE88);
        }

        // Head texture
        Identifier head = SkinManager.getInstance().getHeadTexture(acc.getUuid());
        int headX = x + 7, headY = y + (h - 24) / 2;
        if (head != null) {
            ctx.drawTexture(head, headX, headY, 0, 0, 24, 24, 24, 24);
        } else {
            ctx.fill(headX, headY, headX + 24, headY + 24, 0xFF888888);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), headX + 12, headY + 8, 0xFFFFFFFF);
        }

        // Username
        ctx.drawTextWithShadow(textRenderer, Text.literal(acc.getUsername()),
                x + 38, y + 8, isSelected ? 0xFFFFFFFF : 0xFFCCCCCC);

        // Type label
        String typeLabel = acc.getType().getDisplayName();
        ctx.drawTextWithShadow(textRenderer, Text.literal(typeLabel),
                x + 38, y + 20, isSelected ? 0xFF88BBFF : 0xFF777777);

        // UUID (short)
        if (acc.getUuid() != null && !acc.getUuid().isEmpty()) {
            String shortUUID = acc.getUuid().length() > 8 ? acc.getUuid().substring(0, 8) + "…" : acc.getUuid();
            ctx.drawTextWithShadow(textRenderer, Text.literal(shortUUID),
                    x + 38, y + 28, 0xFF555555);
        }
    }

    // ── RIGHT PANEL ──────────────────────────────────────────────────────────

    private void drawRightPanel(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = panelLeft + LEFT_W + 12;
        int y = panelTop;
        int w = RIGHT_W;
        int h = PANEL_H;

        drawPanel(context, x, y, w, h);

        if (selected == null) {
            // Empty state
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Select an account"), x + w / 2, y + h / 2 - 8, 0xFF555555);
            return;
        }

        // Title / username
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(selected.getUsername()), x + w / 2, y + 10, 0xFFEEEEEE);

        // Type badge
        String type = selected.getType().getDisplayName();
        int badgeColor = selected.getType() == dev.dreamyfx.accountswap.account.AccountType.MICROSOFT
                ? 0xFF1a6fbe : 0xFF666666;
        int badgeW = textRenderer.getWidth(type) + 8;
        int badgeX = x + w / 2 - badgeW / 2;
        fillRounded(context, badgeX, y + 22, badgeW, 12, badgeColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(type), x + w / 2, y + 24, 0xFFFFFFFF);

        // 3D model area
        int modelAreaX1 = x + 10, modelAreaY1 = y + 40;
        int modelAreaX2 = x + w - 10, modelAreaY2 = y + h - 70;

        // model background
        context.fill(modelAreaX1, modelAreaY1, modelAreaX2, modelAreaY2, 0x30000000);
        drawBorderLine(context, modelAreaX1, modelAreaY1, modelAreaX2 - modelAreaX1, modelAreaY2 - modelAreaY1, 0x20FFFFFF);

        draw3DModel(context, x + w / 2, modelAreaY1, modelAreaY2, mouseX, mouseY);

        // UUID
        String uuidText = "UUID: " + (selected.getUuid() != null && selected.getUuid().length() > 8
                ? selected.getUuid().substring(0, 8) + "…" : selected.getUuid());
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(uuidText),
                x + w / 2, y + h - 68, 0xFF666666);

        // Status
        String status = selected.isActive() ? "§aActive" : (selected.isValid() ? "§eReady" : "§cExpired");
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status),
                x + w / 2, y + h - 58, 0xFFFFFFFF);
    }

    private void draw3DModel(DrawContext context, int centerX, int y1, int y2, int mouseX, int mouseY) {
        if (client.player == null) {
            // No player in world — show a simple placeholder avatar
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("☻"),
                    centerX, (y1 + y2) / 2 - 4, 0xFF888888);
            return;
        }

        // Auto-rotate: compute fake mouse position circling around center
        long time = System.currentTimeMillis();
        float angle = (float)((time % 8000L) / 8000.0 * Math.PI * 2.0);
        float fakeMouseX = centerX + (float) Math.sin(angle) * 35f;
        float fakeMouseY = (y1 + y2) / 2f - 15f;

        int size = (int)((y2 - y1) * 0.52f);
        InventoryScreen.drawEntity(context,
                centerX - 30, y1 + 5,
                centerX + 30, y2 - 5,
                size,
                0f,
                fakeMouseX, fakeMouseY,
                client.player
        );
    }

    // ── PANEL / DRAWING HELPERS ───────────────────────────────────────────────

    private void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        // Background
        fillRounded(ctx, x, y, w, h, 0xC0101418);

        // Subtle inner highlight on top
        ctx.fill(x + 4, y + 1, x + w - 4, y + 2, 0x18FFFFFF);

        // Border
        ctx.fill(x + 2, y, x + w - 2, y + 1, 0x50667799);
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, 0x20667799);
        ctx.fill(x, y + 2, x + 1, y + h - 2, 0x30667799);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, 0x30667799);
    }

    private void fillRounded(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 2, y, x + w - 2, y + h, color);
        ctx.fill(x, y + 2, x + 2, y + h - 2, color);
        ctx.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
    }

    private void drawBorderLine(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ── INPUT HANDLING ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Close context menu on any click
        if (contextMenu != null && contextMenu.isVisible()) {
            if (contextMenu.mouseClicked((int) mouseX, (int) mouseY, button)) return true;
            contextMenu.hide();
        }

        // Click on account list
        String query = searchField != null ? searchField.getText() : "";
        List<Account> accounts = AccountManager.getInstance().search(query);

        int listX = panelLeft + 4;
        int listY0 = panelTop + LIST_TOP_PAD;
        int listH = PANEL_H - LIST_TOP_PAD - 10;

        int entryY = listY0 - (int) scrollOffset;
        for (Account acc : accounts) {
            if (isInside((int) mouseX, (int) mouseY, listX, entryY, LEFT_W - 8, ENTRY_H)) {
                if (entryY >= listY0 && entryY + ENTRY_H <= listY0 + listH) {
                    selected = acc;
                    updateButtonState();
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        showContextMenu(acc, (int) mouseX, (int) mouseY);
                    }
                    return true;
                }
            }
            entryY += ENTRY_H + ENTRY_SPACING;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // Only scroll if mouse is over the list
        if (isInside((int) mouseX, (int) mouseY, panelLeft, panelTop + LIST_TOP_PAD, LEFT_W, PANEL_H - LIST_TOP_PAD)) {
            String query = searchField != null ? searchField.getText() : "";
            int total = AccountManager.getInstance().search(query).size() * (ENTRY_H + ENTRY_SPACING);
            int listH = PANEL_H - LIST_TOP_PAD - 10;
            float maxScroll = Math.max(0, total - listH);
            targetScroll = (float) Math.max(0, Math.min(maxScroll, targetScroll - vertical * 12));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (contextMenu != null && contextMenu.isVisible()) {
                contextMenu.hide();
                return true;
            }
            closing = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── ACTIONS ───────────────────────────────────────────────────────────────

    private void loginSelected() {
        if (selected == null) return;
        boolean ok = AccountManager.getInstance().switchAccount(selected);
        if (ok) {
            showStatus("Logged in as " + selected.getUsername() + "!");
        } else {
            showStatus("Failed to switch account.");
        }
        updateButtonState();
    }

    private void removeSelected() {
        if (selected == null) return;
        AccountManager.getInstance().removeAccount(selected);
        selected = null;
        updateButtonState();
        scrollOffset = 0;
        targetScroll = 0;
    }

    private void refreshSelected() {
        if (selected == null || selected.getType() != dev.dreamyfx.accountswap.account.AccountType.MICROSOFT) return;
        String clientId = AccountStorage.getInstance().getClientId();
        if (clientId == null || clientId.isBlank()) {
            client.setScreen(new ConfigScreen(this));
            return;
        }

        showStatus("Refreshing token...");
        String refreshToken = selected.getRefreshToken();
        final Account target = selected;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            MicrosoftAuthFlow flow = new MicrosoftAuthFlow(clientId);
            AuthResult result = flow.refreshToken(refreshToken);
            client.execute(() -> {
                if (result.isSuccess()) {
                    target.setAccessToken(result.getAccessToken());
                    target.setRefreshToken(result.getRefreshToken());
                    target.setTokenExpiry(result.getExpiry());
                    target.setUsername(result.getUsername());
                    AccountStorage.getInstance().save();
                    showStatus("Token refreshed!");
                } else {
                    showStatus("Refresh failed: " + result.getError());
                }
            });
        });
    }

    private void openAddAccount() {
        client.setScreen(new AddAccountScreen(this));
    }

    private void showContextMenu(Account acc, int x, int y) {
        contextMenu = new ContextMenu(x, y, List.of(
                new ContextMenu.Entry("Login", () -> { selected = acc; loginSelected(); }),
                new ContextMenu.Entry("Refresh Token", () -> { selected = acc; refreshSelected(); }),
                new ContextMenu.Entry("Remove", () -> { selected = acc; removeSelected(); })
        ), textRenderer);
    }

    private void showStatus(String msg) {
        statusMessage = msg;
        statusAlpha = 1f;
        statusTime = System.currentTimeMillis();
    }

    private void updateButtonState() {
        boolean hasSelected = selected != null;
        boolean isMicrosoft = hasSelected && selected.getType() == dev.dreamyfx.accountswap.account.AccountType.MICROSOFT;
        if (loginBtn != null) loginBtn.active = hasSelected;
        if (removeBtn != null) removeBtn.active = hasSelected;
        if (refreshBtn != null) refreshBtn.active = isMicrosoft;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldBlurBackground() {
        return client.world != null;
    }
}
