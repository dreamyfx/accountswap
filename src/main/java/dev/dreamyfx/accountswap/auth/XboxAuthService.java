package dev.dreamyfx.accountswap.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamyfx.accountswap.util.HttpUtil;

public class XboxAuthService {

    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

    public record XboxTokens(String xblToken, String xstsToken, String uhs) {}

    public XboxTokens authenticate(String msaAccessToken) throws Exception {
        String xblToken = authenticateXBL(msaAccessToken);
        return authenticateXSTS(xblToken);
    }

    private String authenticateXBL(String msaToken) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msaToken);

        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        String response = HttpUtil.postJson(XBL_URL, body.toString());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (!json.has("Token")) {
            throw new Exception("XBL auth failed: no Token in response");
        }
        return json.get("Token").getAsString();
    }

    private XboxTokens authenticateXSTS(String xblToken) throws Exception {
        JsonObject userTokens = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add(xblToken);

        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        props.add("UserTokens", arr);

        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        String response = HttpUtil.postJson(XSTS_URL, body.toString());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (json.has("XErr")) {
            long err = json.get("XErr").getAsLong();
            throw new Exception(xboxError(err));
        }

        if (!json.has("Token")) {
            throw new Exception("XSTS auth failed: no Token");
        }

        String xstsToken = json.get("Token").getAsString();
        String uhs = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui")
                .get(0).getAsJsonObject()
                .get("uhs").getAsString();

        return new XboxTokens(xblToken, xstsToken, uhs);
    }

    private String xboxError(long code) {
        return switch ((int) code) {
            case 0x8015DC09 -> "Microsoft account not linked to Xbox";
            case 0x8015DC0A -> "Xbox requires age verification (child account)";
            case 0x8015DC0B -> "Account requires parental consent";
            default -> "Xbox auth error: " + Long.toHexString(code);
        };
    }
}
