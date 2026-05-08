package dev.dreamyfx.accountswap.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.dreamyfx.accountswap.util.HttpUtil;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MicrosoftAuthFlow {

    // Microsoft's own Xbox Live client — no Azure app registration required
    static final String CLIENT_ID    = "00000000402b5328";
    static final String AUTHORIZE    = "https://login.live.com/oauth20_authorize.srf";
    static final String TOKEN_URL    = "https://login.live.com/oauth20_token.srf";
    static final String SCOPE        = "service::user.auth.xboxlive.com::MBI_SSL";

    private volatile boolean cancelled = false;

    public record BrowserAuthData(String authUrl, int port) {}

    public BrowserAuthData startBrowserFlow() throws Exception {
        int port = freePort();
        String redirect = "http://localhost:" + port;

        String authUrl = AUTHORIZE
                + "?client_id=" + CLIENT_ID
                + "&response_type=code"
                + "&scope=" + enc(SCOPE)
                + "&redirect_uri=" + enc(redirect)
                + "&cobrandid=8058f65d-ce06-4c30-9559-473c9275a65d"
                + "&prompt=select_account";

        return new BrowserAuthData(authUrl, port);
    }

    // Starts local HTTP server, blocks until browser posts code back (5 min timeout)
    public AuthResult waitForCallback(int port, Consumer<String> status) throws Exception {
        String redirect = "http://localhost:" + port;
        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String html;
            if (query != null && query.contains("code=")) {
                codeFuture.complete(param(query, "code"));
                html = successPage();
            } else {
                String err = query != null ? param(query, "error_description") : "cancelled";
                codeFuture.completeExceptionally(new Exception(err.isBlank() ? "Sign-in failed" : err));
                html = errorPage(err);
            }
            byte[] b = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, b.length);
            exchange.getResponseBody().write(b);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            String code = codeFuture.get(5, TimeUnit.MINUTES);
            if (cancelled) return AuthResult.failure("Cancelled.");

            if (status != null) status.accept("Exchanging code for tokens...");

            String body = "client_id=" + CLIENT_ID
                    + "&code=" + code
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=" + enc(redirect)
                    + "&scope=" + enc(SCOPE);

            String resp = HttpUtil.postForm(TOKEN_URL, body);
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

            if (json.has("error")) {
                return AuthResult.failure("Token exchange: " + json.get("error").getAsString());
            }

            return runChain(
                    json.get("access_token").getAsString(),
                    json.has("refresh_token") ? json.get("refresh_token").getAsString() : "",
                    json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L,
                    status
            );

        } catch (java.util.concurrent.TimeoutException e) {
            return AuthResult.failure("Timed out waiting for sign-in (5 min).");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            return AuthResult.failure(cause != null ? cause.getMessage() : e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    public AuthResult refresh(String refreshToken) {
        try {
            String body = "client_id=" + CLIENT_ID
                    + "&refresh_token=" + refreshToken
                    + "&grant_type=refresh_token"
                    + "&scope=" + enc(SCOPE);

            String resp = HttpUtil.postForm(TOKEN_URL, body);
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

            if (json.has("error")) {
                return AuthResult.failure("Refresh failed: " + json.get("error").getAsString());
            }

            return runChain(
                    json.get("access_token").getAsString(),
                    json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken,
                    json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L,
                    null
            );
        } catch (Exception e) {
            return AuthResult.failure("Refresh error: " + e.getMessage());
        }
    }

    private AuthResult runChain(String msaToken, String refreshToken, long expiresIn, Consumer<String> status) throws Exception {
        long expiry = System.currentTimeMillis() + expiresIn * 1000L;

        if (status != null) status.accept("Authenticating with Xbox Live...");
        XboxAuthService xbox = new XboxAuthService();
        XboxAuthService.XboxTokens xboxTokens = xbox.authenticate(msaToken);

        if (status != null) status.accept("Fetching Minecraft profile...");
        MinecraftAuthService mc = new MinecraftAuthService();
        MinecraftAuthService.MinecraftTokens mcTokens = mc.authenticate(xboxTokens);

        return AuthResult.success(mcTokens.username(), mcTokens.uuid(), mcTokens.accessToken(), refreshToken, expiry);
    }

    public void cancel() { cancelled = true; }

    private int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String param(String query, String name) {
        for (String part : query.split("&")) {
            if (part.startsWith(name + "=")) {
                try { return URLDecoder.decode(part.substring(name.length() + 1), StandardCharsets.UTF_8); }
                catch (Exception e) { return part.substring(name.length() + 1); }
            }
        }
        return "";
    }

    private String successPage() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>AccountSwap</title>"
                + "<style>body{margin:0;background:#0e1117;color:#fff;font-family:'Segoe UI',sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;text-align:center}"
                + "h1{font-size:2em;margin-bottom:.5em}p{color:#aaa}</style></head>"
                + "<body><div><h1>&#10003; Signed in!</h1>"
                + "<p>You can close this tab and return to Minecraft.</p></div></body></html>";
    }

    private String errorPage(String err) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>AccountSwap</title>"
                + "<style>body{margin:0;background:#0e1117;color:#fff;font-family:'Segoe UI',sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;text-align:center}"
                + "h1{font-size:2em;color:#e05555;margin-bottom:.5em}p{color:#aaa}</style></head>"
                + "<body><div><h1>&#10007; Sign-in failed</h1><p>" + err + "</p></div></body></html>";
    }
}
