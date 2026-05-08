package dev.dreamyfx.accountswap.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamyfx.accountswap.util.HttpUtil;

public class MinecraftAuthService {

    private static final String MC_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    public record MinecraftTokens(String accessToken, String uuid, String username) {}

    public MinecraftTokens authenticate(XboxAuthService.XboxTokens xbox) throws Exception {
        String mcToken = loginWithXbox(xbox.uhs(), xbox.xstsToken());
        return fetchProfile(mcToken);
    }

    private String loginWithXbox(String uhs, String xstsToken) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        String response = HttpUtil.postJson(MC_AUTH_URL, body.toString());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (!json.has("access_token")) {
            throw new Exception("Minecraft auth failed: no access_token. Response: " + response);
        }

        return json.get("access_token").getAsString();
    }

    private MinecraftTokens fetchProfile(String mcToken) throws Exception {
        String response = HttpUtil.getWithBearer(MC_PROFILE_URL, mcToken);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (json.has("error")) {
            String err = json.get("error").getAsString();
            if ("NOT_FOUND".equals(err)) {
                throw new Exception("This Microsoft account does not own Minecraft.");
            }
            throw new Exception("Profile error: " + err);
        }

        String uuid = json.get("id").getAsString();
        String name = json.get("name").getAsString();

        String formattedUUID = uuid.replaceAll(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5"
        );

        return new MinecraftTokens(mcToken, formattedUUID, name);
    }
}
