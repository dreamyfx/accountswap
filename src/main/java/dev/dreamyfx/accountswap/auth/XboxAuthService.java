package dev.dreamyfx.accountswap.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamyfx.accountswap.util.HttpUtil;

public class XboxAuthService {

    private static final String XBL_URL  = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

    public record XboxTokens(String xblToken, String xstsToken, String uhs) {}

    public XboxTokens authenticate(String msaAccessToken) throws Exception {
        String xblToken = getXBLToken(msaAccessToken);
        return getXSTSToken(xblToken);
    }

    private String getXBLToken(String msaToken) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        // Raw MSA token — no "d=" or "t=" prefix for the login.live.com flow
        props.addProperty("RpsTicket", msaToken);

        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        String resp = HttpUtil.postJson(XBL_URL, body.toString());
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

        if (!json.has("Token")) throw new Exception("XBL auth failed — no Token. Response: " + resp);
        return json.get("Token").getAsString();
    }

    private XboxTokens getXSTSToken(String xblToken) throws Exception {
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);

        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        props.add("UserTokens", tokens);

        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        String resp = HttpUtil.postJson(XSTS_URL, body.toString());
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

        if (json.has("XErr")) {
            throw new Exception(xboxError(json.get("XErr").getAsLong()));
        }
        if (!json.has("Token")) throw new Exception("XSTS auth failed — no Token. Response: " + resp);

        String xstsToken = json.get("Token").getAsString();
        String uhs = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        return new XboxTokens(xblToken, xstsToken, uhs);
    }

    private String xboxError(long code) {
        return switch (String.valueOf(code)) {
            case "2148916227" -> "Your account has been banned by Xbox.";
            case "2148916233" -> "This Microsoft account has no Xbox profile. Sign up at minecraft.net first.";
            case "2148916235" -> "Xbox Live is not available in your country/region.";
            case "2148916236" -> "Adult verification required. Sign in at xbox.com first.";
            case "2148916237" -> "Adult verification required (South Korea). Sign in at xbox.com first.";
            case "2148916238" -> "Child account — add it to a family group at xbox.com first.";
            default -> "Xbox auth error (XErr=" + code + ")";
        };
    }
}
