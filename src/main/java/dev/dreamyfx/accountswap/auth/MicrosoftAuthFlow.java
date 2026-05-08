package dev.dreamyfx.accountswap.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.util.HttpUtil;

import java.util.function.Consumer;

public class MicrosoftAuthFlow {

    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    private final String clientId;
    private volatile boolean cancelled = false;

    public MicrosoftAuthFlow(String clientId) {
        this.clientId = clientId;
    }

    public DeviceCodeResponse startDeviceFlow() throws Exception {
        String body = "client_id=" + clientId + "&scope=XboxLive.signin%20offline_access";
        String response = HttpUtil.postForm(DEVICE_CODE_URL, body);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (json.has("error")) {
            String err = json.get("error").getAsString();
            String desc = json.has("error_description") ? json.get("error_description").getAsString() : "";
            throw new Exception("Device code error: " + err + " - " + desc);
        }

        DeviceCodeResponse dcr = new DeviceCodeResponse();
        dcr.deviceCode = json.get("device_code").getAsString();
        dcr.userCode = json.get("user_code").getAsString();
        dcr.verificationUri = json.get("verification_uri").getAsString();
        dcr.expiresIn = json.get("expires_in").getAsInt();
        dcr.interval = json.has("interval") ? json.get("interval").getAsInt() : 5;
        dcr.message = json.has("message") ? json.get("message").getAsString() : "";

        return dcr;
    }

    public AuthResult pollForToken(DeviceCodeResponse dcr, Consumer<String> statusCallback) throws Exception {
        long deadline = System.currentTimeMillis() + (dcr.expiresIn * 1000L);
        int intervalMs = dcr.interval * 1000;

        while (System.currentTimeMillis() < deadline && !cancelled) {
            Thread.sleep(intervalMs);
            if (cancelled) break;

            String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    + "&client_id=" + clientId
                    + "&device_code=" + dcr.deviceCode;

            try {
                String response = HttpUtil.postForm(TOKEN_URL, body);
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();

                if (json.has("error")) {
                    String err = json.get("error").getAsString();
                    if ("authorization_pending".equals(err)) {
                        if (statusCallback != null) statusCallback.accept("Waiting for sign-in...");
                        continue;
                    }
                    if ("slow_down".equals(err)) {
                        intervalMs += 5000;
                        continue;
                    }
                    if ("authorization_declined".equals(err)) {
                        return AuthResult.failure("Sign-in was declined.");
                    }
                    if ("expired_token".equals(err)) {
                        return AuthResult.failure("Sign-in timed out. Please try again.");
                    }
                    return AuthResult.failure("Auth error: " + err);
                }

                if (json.has("access_token")) {
                    String msaToken = json.get("access_token").getAsString();
                    String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : "";
                    long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
                    long expiry = System.currentTimeMillis() + (expiresIn * 1000L);

                    if (statusCallback != null) statusCallback.accept("Authenticating with Xbox...");
                    XboxAuthService xbox = new XboxAuthService();
                    XboxAuthService.XboxTokens xboxTokens = xbox.authenticate(msaToken);

                    if (statusCallback != null) statusCallback.accept("Getting Minecraft profile...");
                    MinecraftAuthService mc = new MinecraftAuthService();
                    MinecraftAuthService.MinecraftTokens mcTokens = mc.authenticate(xboxTokens);

                    return AuthResult.success(
                            mcTokens.username(),
                            mcTokens.uuid(),
                            mcTokens.accessToken(),
                            refreshToken,
                            expiry
                    );
                }
            } catch (Exception e) {
                AccountSwapMod.LOGGER.warn("Poll attempt failed: {}", e.getMessage());
            }
        }

        if (cancelled) return AuthResult.failure("Cancelled.");
        return AuthResult.failure("Sign-in timed out.");
    }

    public AuthResult refreshToken(String refreshToken) {
        try {
            String body = "grant_type=refresh_token"
                    + "&client_id=" + clientId
                    + "&refresh_token=" + refreshToken;

            String response = HttpUtil.postForm(TOKEN_URL, body);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.has("error")) {
                return AuthResult.failure("Refresh failed: " + json.get("error").getAsString());
            }

            String msaToken = json.get("access_token").getAsString();
            String newRefresh = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
            long expiry = System.currentTimeMillis() + (json.get("expires_in").getAsLong() * 1000L);

            XboxAuthService xbox = new XboxAuthService();
            XboxAuthService.XboxTokens xboxTokens = xbox.authenticate(msaToken);

            MinecraftAuthService mc = new MinecraftAuthService();
            MinecraftAuthService.MinecraftTokens mcTokens = mc.authenticate(xboxTokens);

            return AuthResult.success(mcTokens.username(), mcTokens.uuid(), mcTokens.accessToken(), newRefresh, expiry);
        } catch (Exception e) {
            return AuthResult.failure("Refresh error: " + e.getMessage());
        }
    }

    public void cancel() {
        cancelled = true;
    }
}
