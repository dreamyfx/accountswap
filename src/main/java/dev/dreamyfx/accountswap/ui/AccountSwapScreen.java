package dev.dreamyfx.accountswap.ui;

import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.account.AccountType;
import dev.dreamyfx.accountswap.animation.AnimationUtil;
import dev.dreamyfx.accountswap.skin.SkinManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class AccountSwapScreen extends Screen {

    private static final int LEFT_W = 198, RIGHT_W = 212, PANEL_H = 290;
    private static final int ENTRY_H = 38, ENTRY_GAP = 2, LIST_TOP = 56;

    private final Screen parent;

    private int panelLeft, panelTop;
    private float openAnim = 0f;
    private boolean closing = false;

    private float scrollOff = 0f, targetScroll = 0f;

    private Account<?> selected;
    private EditBox searchBox;
    private ContextMenu contextMenu;

    private String statusMsg = "";
    private float statusAlpha = 0f;
    private long statusTime = 0;

    private ModernButton loginBtn, removeBtn, refreshBtn;

    public AccountSwapScreen(Screen parent) {
        super(Component.translatable("screen.accountswap.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelLeft = (width  - LEFT_W - RIGHT_W - 12) / 2;
        panelTop  = (height - PANEL_H) / 2;

        searchBox = new EditBox(font, panelLeft + 8, panelTop + 29, LEFT_W - 40, 18, Component.literal(""));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("accountswap.search"));
        searchBox.setBordered(false);
        addRenderableWidget(searchBox);

        addRenderableWidget(new ModernButton(
                panelLeft + LEFT_W - 26, panelTop + 27, 20, 20,
                Component.literal("+"), this::openAdd));

        int rp = panelLeft + LEFT_W + 12;
        loginBtn   = new ModernButton(rp + 4,   panelTop + PANEL_H - 60, 96, 22, Component.translatable("accountswap.login"),   this::loginSelected, ModernButton.Style.PRIMARY);
        removeBtn  = new ModernButton(rp + 108,  panelTop + PANEL_H - 60, 96, 22, Component.translatable("accountswap.remove"),  this::removeSelected, ModernButton.Style.DANGER);
        refreshBtn = new ModernButton(rp + 4,   panelTop + PANEL_H - 34, 200, 22, Component.translatable("accountswap.refresh"), this::refreshSelected);

        addRenderableWidget(loginBtn);
        addRenderableWidget(removeBtn);
        addRenderableWidget(refreshBtn);

        syncButtons();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // Open/close animation
        float spd = delta * 0.13f;
        if (!closing) openAnim = Math.min(1f, openAnim + spd);
        else {
            openAnim = Math.max(0f, openAnim - spd * 1.4f);
            if (openAnim <= 0f) { minecraft.setScreen(parent); return; }
        }

        scrollOff += (targetScroll - scrollOff) * Math.min(1f, delta * 0.18f);

        if (statusAlpha > 0 && System.currentTimeMillis() - statusTime > 2500)
            statusAlpha = Math.max(0f, statusAlpha - delta * 0.05f);

        float scale = AnimationUtil.easeOutBack(openAnim);
        int overlayA = (int)(AnimationUtil.easeOutCubic(openAnim) * 0xB0);
        g.fill(0, 0, width, height, overlayA << 24);

        g.pose().pushPose();
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-width / 2f, -height / 2f, 0);

        drawLeftPanel(g, mx, my);
        drawRightPanel(g, mx, my, delta);

        if (statusAlpha > 0 && !statusMsg.isEmpty()) {
            int sc = (int)(statusAlpha * 0xFF);
            g.drawString(font, statusMsg, panelLeft + LEFT_W / 2 - font.width(statusMsg) / 2, panelTop - 18, (sc << 24) | 0x88FF88, true);
        }

        g.pose().popPose();

        super.render(g, mx, my, delta);

        if (contextMenu != null && contextMenu.isVisible()) contextMenu.render(g, mx, my);
    }

    // ── LEFT PANEL ────────────────────────────────────────────────────────────

    private void drawLeftPanel(GuiGraphics g, int mx, int my) {
        int x = panelLeft, y = panelTop, w = LEFT_W, h = PANEL_H;
        panel(g, x, y, w, h);
        g.drawString(font, "Accounts", x + 10, y + 10, 0xFFDDDDDD, true);

        // Search bg
        g.fill(x + 7, y + 27, x + w - 33, y + 49, 0x60000000);
        g.fill(x + 7, y + 27, x + w - 33, y + 28, 0x50AAAAAA);

        // Divider
        g.fill(x + 6, y + 52, x + w - 6, y + 53, 0x30FFFFFF);

        // Account list (scissored)
        int lx = x + 4, ly0 = y + LIST_TOP, lh = h - LIST_TOP - 10;
        g.enableScissor(lx, ly0, lx + w - 8, ly0 + lh);

        String q = searchBox != null ? searchBox.getValue() : "";
        List<Account<?>> list = AccountManager.get().search(q);
        int ey = ly0 - (int) scrollOff;
        for (Account<?> acc : list) {
            if (ey + ENTRY_H > ly0 && ey < ly0 + lh)
                drawEntry(g, acc, lx, ey, w - 8, ENTRY_H, mx, my);
            ey += ENTRY_H + ENTRY_GAP;
        }

        g.disableScissor();

        // Scroll bar
        int total = list.size() * (ENTRY_H + ENTRY_GAP);
        if (total > lh) {
            int barH = Math.max(18, lh * lh / total);
            float frac = Math.min(1f, scrollOff / (total - lh));
            int barY = ly0 + (int)((lh - barH) * frac);
            g.fill(x + w - 5, barY, x + w - 3, barY + barH, 0x60AAAAAA);
        }
    }

    private void drawEntry(GuiGraphics g, Account<?> acc, int x, int y, int w, int h, int mx, int my) {
        boolean hov = inside(mx, my, x, y, w, h);
        boolean sel = acc == selected;

        int bg = sel ? (hov ? 0xC02244AA : 0xA01a3380) : (hov ? 0x80282C30 : 0x50181C20);
        rounded(g, x, y, w, h, bg);
        if (sel) g.fill(x, y + 3, x + 2, y + h - 3, 0xFF4488EE);

        // Head
        ResourceLocation head = SkinManager.get().getHead(acc.getCache().uuid);
        if (head != null) {
            g.blit(head, x + 7, y + (h - 24) / 2, 0, 0, 24, 24, 24, 24);
        } else {
            g.fill(x + 7, y + (h - 24) / 2, x + 31, y + (h + 24) / 2, 0xFF666666);
        }

        g.drawString(font, acc.getUsername(), x + 36, y + 8,  sel ? 0xFFFFFFFF : 0xFFCCCCCC, true);
        g.drawString(font, acc.getType().displayName, x + 36, y + 20, sel ? 0xFF88BBFF : 0xFF666666, false);

        String uuid = acc.getCache().uuid;
        if (!uuid.isBlank()) {
            String short_ = uuid.length() > 8 ? uuid.substring(0, 8) + "…" : uuid;
            g.drawString(font, short_, x + 36, y + 29, 0xFF444444, false);
        }
    }

    // ── RIGHT PANEL ───────────────────────────────────────────────────────────

    private void drawRightPanel(GuiGraphics g, int mx, int my, float delta) {
        int x = panelLeft + LEFT_W + 12, y = panelTop, w = RIGHT_W, h = PANEL_H;
        panel(g, x, y, w, h);

        if (selected == null) {
            int cy = y + h / 2 - 4;
            g.drawString(font, "Select an account", x + w/2 - font.width("Select an account")/2, cy, 0xFF555555, false);
            return;
        }

        // Username title
        String name = selected.getUsername();
        g.drawString(font, name, x + w/2 - font.width(name)/2, y + 10, 0xFFEEEEEE, true);

        // Type badge
        String badge = selected.getType().displayName;
        int badgeW = font.width(badge) + 8;
        int badgeX = x + w/2 - badgeW/2;
        int badgeColor = selected.getType() == AccountType.Microsoft ? 0xFF1a6fbe : 0xFF555555;
        rounded(g, badgeX, y + 22, badgeW, 12, badgeColor);
        g.drawString(font, badge, badgeX + 4, y + 24, 0xFFFFFFFF, false);

        // 3D model area
        int mx1 = x + 8, my1 = y + 38, mx2 = x + w - 8, my2 = y + h - 74;
        g.fill(mx1, my1, mx2, my2, 0x28000000);
        g.fill(mx1, my1, mx2, my1 + 1, 0x20FFFFFF);
        g.fill(mx1, my1, mx1 + 1, my2, 0x20FFFFFF);
        g.fill(mx2 - 1, my1, mx2, my2, 0x20FFFFFF);
        g.fill(mx1, my2 - 1, mx2, my2, 0x20FFFFFF);

        draw3DModel(g, (mx1 + mx2) / 2, my1, my2);

        // UUID
        String uuid = selected.getCache().uuid;
        String uuidDisplay = uuid.length() > 8 ? "UUID: " + uuid.substring(0, 8) + "…" : "UUID: " + uuid;
        g.drawString(font, uuidDisplay, x + w/2 - font.width(uuidDisplay)/2, y + h - 72, 0xFF555555, false);

        // Status
        boolean isMC = minecraft.getUser() != null && selected.getUsername().equals(minecraft.getUser().getName());
        String statusStr = isMC ? "§aActive" : (selected.getType() == AccountType.Microsoft ? "§eReady" : "§7Ready");
        Component statusComp = Component.literal(statusStr);
        g.drawString(font, statusComp, x + w/2 - font.width(statusComp)/2, y + h - 60, 0xFFFFFFFF, false);
    }

    private void draw3DModel(GuiGraphics g, int cx, int y1, int y2) {
        if (minecraft.player == null) {
            g.drawString(font, "( in-game only )", cx - font.width("( in-game only )") / 2, (y1 + y2) / 2 - 4, 0xFF444444, false);
            return;
        }
        long t = System.currentTimeMillis();
        float angle = (float)((t % 8000L) / 8000.0 * Math.PI * 2.0);
        float fakeX = cx + (float)Math.sin(angle) * 30f;
        float fakeY = (y1 + y2) / 2f - 15f;
        int size = (int)((y2 - y1) * 0.50f);
        InventoryScreen.renderEntityInInventory(g, cx - 28, y1 + 4, cx + 28, y2 - 4, size, 0f, fakeX, fakeY, minecraft.player);
    }

    // ── PANEL / DRAW HELPERS ──────────────────────────────────────────────────

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        rounded(g, x, y, w, h, 0xC0101418);
        g.fill(x + 4, y + 1, x + w - 4, y + 2, 0x18FFFFFF);
        g.fill(x + 2, y, x + w - 2, y + 1, 0x50667799);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, 0x20667799);
        g.fill(x, y + 2, x + 1, y + h - 2, 0x30667799);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, 0x30667799);
    }

    private void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + 2, y + h - 2, color);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ── INPUT ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (contextMenu != null && contextMenu.isVisible()) {
            if (contextMenu.mouseClicked((int)mx, (int)my)) return true;
            contextMenu.hide();
        }

        String q = searchBox != null ? searchBox.getValue() : "";
        List<Account<?>> list = AccountManager.get().search(q);
        int lx = panelLeft + 4, ly0 = panelTop + LIST_TOP, lh = PANEL_H - LIST_TOP - 10;
        int ey = ly0 - (int)scrollOff;

        for (Account<?> acc : list) {
            if (inside((int)mx, (int)my, lx, ey, LEFT_W - 8, ENTRY_H) && ey >= ly0 && ey + ENTRY_H <= ly0 + lh) {
                selected = acc;
                syncButtons();
                if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) showCtx(acc, (int)mx, (int)my);
                return true;
            }
            ey += ENTRY_H + ENTRY_GAP;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (inside((int)mx, (int)my, panelLeft, panelTop + LIST_TOP, LEFT_W, PANEL_H - LIST_TOP)) {
            String q = searchBox != null ? searchBox.getValue() : "";
            int total = AccountManager.get().search(q).size() * (ENTRY_H + ENTRY_GAP);
            int lh = PANEL_H - LIST_TOP - 10;
            targetScroll = (float)Math.max(0, Math.min(Math.max(0, total - lh), targetScroll - v * 12));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (contextMenu != null && contextMenu.isVisible()) { contextMenu.hide(); return true; }
            closing = true; return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── ACTIONS ───────────────────────────────────────────────────────────────

    private void loginSelected() {
        if (selected == null) return;
        AccountManager.get().loginAsync(selected,
                () -> showStatus("Logged in as " + selected.getUsername() + "!"),
                err -> showStatus("Login failed: " + err)
        );
        syncButtons();
    }

    private void removeSelected() {
        if (selected == null) return;
        AccountManager.get().remove(selected);
        selected = null;
        scrollOff = 0; targetScroll = 0;
        syncButtons();
    }

    private void refreshSelected() {
        if (selected == null || selected.getType() != AccountType.Microsoft) return;
        showStatus("Refreshing...");
        AccountManager.get().loginAsync(selected,
                () -> showStatus("Token refreshed!"),
                err -> showStatus("Refresh failed: " + err)
        );
    }

    private void openAdd() {
        minecraft.setScreen(new AddAccountScreen(this));
    }

    private void showCtx(Account<?> acc, int x, int y) {
        contextMenu = new ContextMenu(x, y, List.of(
                new ContextMenu.Entry("Login",         () -> { selected = acc; loginSelected(); }),
                new ContextMenu.Entry("Refresh Token", () -> { selected = acc; refreshSelected(); }),
                new ContextMenu.Entry("Remove",        () -> { selected = acc; removeSelected(); })
        ), font);
    }

    private void showStatus(String msg) {
        statusMsg = msg; statusAlpha = 1f; statusTime = System.currentTimeMillis();
    }

    private void syncButtons() {
        boolean has = selected != null;
        boolean ms  = has && selected.getType() == AccountType.Microsoft;
        if (loginBtn   != null) loginBtn.active   = has;
        if (removeBtn  != null) removeBtn.active  = has;
        if (refreshBtn != null) refreshBtn.active = ms;
    }

    @Override public boolean isPauseScreen() { return false; }
}
