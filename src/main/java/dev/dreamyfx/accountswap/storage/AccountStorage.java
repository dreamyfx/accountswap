package dev.dreamyfx.accountswap.storage;

import com.google.gson.*;
import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.account.AccountType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AccountStorage {

    private static AccountStorage instance;
    private final Path configDir;
    private final Path dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private AccountStorage() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("accountswap");
        dataFile = configDir.resolve("accounts.json");
        EncryptionUtil.init(configDir);
    }

    public static AccountStorage getInstance() {
        if (instance == null) instance = new AccountStorage();
        return instance;
    }

    public Path getConfigDir() { return configDir; }

    public void load() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(dataFile)) return;

            String json = Files.readString(dataFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            List<Account> accounts = new ArrayList<>();
            if (root.has("accounts")) {
                for (JsonElement el : root.getAsJsonArray("accounts")) {
                    JsonObject obj = el.getAsJsonObject();
                    Account a = new Account();
                    a.setUsername(obj.get("username").getAsString());
                    a.setUuid(obj.get("uuid").getAsString());
                    a.setType(AccountType.valueOf(obj.get("type").getAsString()));
                    a.setActive(obj.has("active") && obj.get("active").getAsBoolean());
                    a.setTokenExpiry(obj.has("tokenExpiry") ? obj.get("tokenExpiry").getAsLong() : 0L);

                    if (obj.has("accessToken")) {
                        a.setAccessToken(EncryptionUtil.decrypt(obj.get("accessToken").getAsString()));
                    }
                    if (obj.has("refreshToken")) {
                        a.setRefreshToken(EncryptionUtil.decrypt(obj.get("refreshToken").getAsString()));
                    }
                    accounts.add(a);
                }
            }

            AccountManager.getInstance().setAccounts(accounts);
            AccountSwapMod.LOGGER.info("Loaded {} accounts", accounts.size());
        } catch (Exception e) {
            AccountSwapMod.LOGGER.error("Failed to load accounts", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(configDir);
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();

            for (Account a : AccountManager.getInstance().getAccounts()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("username", a.getUsername());
                obj.addProperty("uuid", a.getUuid());
                obj.addProperty("type", a.getType().name());
                obj.addProperty("active", a.isActive());
                obj.addProperty("tokenExpiry", a.getTokenExpiry());
                obj.addProperty("accessToken", EncryptionUtil.encrypt(a.getAccessToken()));
                obj.addProperty("refreshToken", EncryptionUtil.encrypt(a.getRefreshToken()));
                arr.add(obj);
            }

            root.add("accounts", arr);
            Files.writeString(dataFile, gson.toJson(root));
        } catch (Exception e) {
            AccountSwapMod.LOGGER.error("Failed to save accounts", e);
        }
    }

    public String getClientId() {
        try {
            Path cfgFile = configDir.resolve("config.json");
            if (!Files.exists(cfgFile)) return null;
            JsonObject obj = JsonParser.parseString(Files.readString(cfgFile)).getAsJsonObject();
            return obj.has("clientId") ? obj.get("clientId").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void saveClientId(String clientId) {
        try {
            Files.createDirectories(configDir);
            Path cfgFile = configDir.resolve("config.json");
            JsonObject obj = new JsonObject();
            obj.addProperty("clientId", clientId);
            Files.writeString(cfgFile, gson.toJson(obj));
        } catch (Exception e) {
            AccountSwapMod.LOGGER.error("Failed to save config", e);
        }
    }
}
