package dev.dreamyfx.accountswap.auth;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.util.HttpUtil;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MicrosoftLogin {

    // Microsoft's own Xbox Live native app — no Azure registration required
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE     = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final int    PORT      = 9675;

    private static volatile HttpServer server;
    private static volatile Consumer<String> pendingCallback;

    // ── Step 1: Open browser, return refresh token via callback ─────────────

    public static String startLogin(Consumer<String> callback) {
        pendingCallback = callback;
        startServer();

        String url = "https://login.live.com/oauth20_authorize.srf"
                + "?client_id=" + CLIENT_ID
                + "&response_type=code"
                + "&redirect_uri=http://127.0.0.1:" + PORT
                + "&scope=" + encode(SCOPE)
                + "&cobrandid=8058f65d-ce06-4c30-9559-473c9275a65d"
                + "&prompt=select_account";

        Util.getPlatform().openUri(url);
        return url;
    }

    // ── Step 2: Exchange refresh token for full LoginData ───────────────────

    public static LoginData login(String refreshToken) {
        // Refresh MSA tokens
        TokenResponse msRes = HttpUtil.postFormGson(
                "https://login.live.com/oauth20_token.srf",
                "client_id=" + CLIENT_ID
                        + "&refresh_token=" + refreshToken
                        + "&grant_type=refresh_token"
                        + "&redirect_uri=http://127.0.0.1:" + PORT,
                TokenResponse.class
        );
        if (msRes == null) return new LoginData();
        return loginWithMsaToken(msRes.access_token, msRes.refresh_token);
    }

    // ── Internal: full auth chain from MSA access token ─────────────────────

    private static LoginData loginWithMsaToken(String msaToken, String newRefresh) {
        // XBL
        XblResponse xblRes = HttpUtil.postJsonGson(
                "https://user.auth.xboxlive.com/user/authenticate",
                "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                        + "\"RpsTicket\":\"" + msaToken + "\"},"
                        + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}",
                XblResponse.class
        );
        if (xblRes == null) return new LoginData();

        // XSTS
        XblResponse xstsRes = HttpUtil.postJsonGson(
                "https://xsts.auth.xboxlive.com/xsts/authorize",
                "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblRes.Token + "\"]},"
                        + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}",
                XblResponse.class
        );
        if (xstsRes == null) return new LoginData();

        String uhs = xstsRes.DisplayClaims.xui[0].uhs;

        // Minecraft login
        McLoginResponse mcRes = HttpUtil.postJsonGson(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsRes.Token + "\"}",
                McLoginResponse.class
        );
        if (mcRes == null) return new LoginData();

        // Ownership check
        OwnershipResponse ownerRes = HttpUtil.getJsonGson(
                "https://api.minecraftservices.com/entitlements/mcstore",
                mcRes.access_token,
                OwnershipResponse.class
        );
        if (ownerRes == null || !ownerRes.ownsMinecraft()) return new LoginData();

        // Profile
        ProfileResponse profile = HttpUtil.getJsonGson(
                "https://api.minecraftservices.com/minecraft/profile",
                mcRes.access_token,
                ProfileResponse.class
        );
        if (profile == null) return new LoginData();

        return new LoginData(mcRes.access_token, newRefresh, profile.id, profile.name);
    }

    // ── Local HTTP server ────────────────────────────────────────────────────

    private static void startServer() {
        if (server != null) return;
        try {
            int port = isPortFree(PORT) ? PORT : findFreePort();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/", MicrosoftLogin::handleRequest);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            AccountSwapMod.LOGGER.info("AccountSwap auth server started on port {}", port);
        } catch (IOException e) {
            AccountSwapMod.LOGGER.error("Failed to start auth server", e);
            stopServer();
        }
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) return;

        String query = exchange.getRequestURI().getRawQuery();
        String code = queryParam(query, "code");

        if (code != null) {
            handleCode(code);
            respond(exchange, "You may now close this page and return to Minecraft.");
        } else {
            respond(exchange, "Authentication failed. You can close this page.");
            if (pendingCallback != null) pendingCallback.accept(null);
        }

        stopServer();
    }

    private static void handleCode(String code) {
        TokenResponse res = HttpUtil.postFormGson(
                "https://login.live.com/oauth20_token.srf",
                "client_id=" + CLIENT_ID
                        + "&code=" + code
                        + "&grant_type=authorization_code"
                        + "&redirect_uri=http://127.0.0.1:" + PORT,
                TokenResponse.class
        );
        if (pendingCallback != null) {
            pendingCallback.accept(res != null ? res.refresh_token : null);
        }
    }

    public static void stopServer() {
        if (server == null) return;
        server.stop(0);
        server = null;
        pendingCallback = null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void respond(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        try (var out = ex.getResponseBody()) { out.write(bytes); }
    }

    private static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            if (part.startsWith(key + "=")) {
                return urlDecode(part.substring(key.length() + 1));
            }
        }
        return null;
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) { return s; }
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) { return s; }
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket s = new ServerSocket(port)) { return true; }
        catch (IOException e) { return false; }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    // ── Result ───────────────────────────────────────────────────────────────

    public static class LoginData {
        public final String mcToken;
        public final String newRefreshToken;
        public final String uuid;
        public final String username;

        public LoginData() { this(null, null, null, null); }

        public LoginData(String mcToken, String newRefreshToken, String uuid, String username) {
            this.mcToken = mcToken;
            this.newRefreshToken = newRefreshToken;
            this.uuid = uuid;
            this.username = username;
        }

        public boolean isGood() { return mcToken != null; }
    }

    // ── Internal response POJOs ───────────────────────────────────────────────

    private static class TokenResponse {
        String access_token;
        String refresh_token;
    }

    private static class XblResponse {
        String Token;
        DisplayClaims DisplayClaims;
        static class DisplayClaims {
            Claim[] xui;
            static class Claim { String uhs; }
        }
    }

    private static class McLoginResponse {
        String access_token;
    }

    private static class OwnershipResponse {
        OwnershipItem[] items;
        boolean ownsMinecraft() {
            if (items == null) return false;
            boolean product = false, game = false;
            for (OwnershipItem i : items) {
                if ("product_minecraft".equals(i.name)) product = true;
                if ("game_minecraft".equals(i.name)) game = true;
            }
            return product && game;
        }
        static class OwnershipItem { String name; }
    }

    private static class ProfileResponse {
        String id;
        String name;
    }
}
