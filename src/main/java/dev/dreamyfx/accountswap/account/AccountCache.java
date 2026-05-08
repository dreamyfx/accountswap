package dev.dreamyfx.accountswap.account;

import com.google.gson.JsonObject;

public class AccountCache {
    public String username = "";
    public String uuid = "";

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("username", username);
        o.addProperty("uuid", uuid);
        return o;
    }

    public void fromJson(JsonObject o) {
        username = o.has("username") ? o.get("username").getAsString() : "";
        uuid     = o.has("uuid")     ? o.get("uuid").getAsString()     : "";
    }
}
