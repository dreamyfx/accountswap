package dev.dreamyfx.accountswap.storage;

import com.google.gson.*;
import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.account.AccountType;
import dev.dreamyfx.accountswap.account.types.CrackedAccount;
import dev.dreamyfx.accountswap.account.types.MicrosoftAccount;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AccountStorage {

    private static final AccountStorage INSTANCE = new AccountStorage();
    private final Path configDir;
    private final Path dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private AccountStorage() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("accountswap");
        dataFile  = configDir.resolve("accounts.json");
        EncryptionUtil.init(configDir);
    }

    public static AccountStorage get() { return INSTANCE; }

    public Path getConfigDir() { return configDir; }

    public void load() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(dataFile)) return;

            JsonObject root = JsonParser.parseString(Files.readString(dataFile)).getAsJsonObject();
            List<Account<?>> list = new ArrayList<>();

            for (JsonElement el : root.getAsJsonArray("accounts")) {
                JsonObject obj = el.getAsJsonObject();
                String typeStr = obj.get("type").getAsString();
                String rawName = EncryptionUtil.decrypt(obj.get("name").getAsString());

                Account<?> acc;
                try {
                    AccountType type = AccountType.valueOf(typeStr);
                    acc = switch (type) {
                        case Microsoft -> new MicrosoftAccount(rawName);
                        case Cracked   -> new CrackedAccount(rawName);
                    };
                } catch (IllegalArgumentException e) {
                    continue;
                }

                if (obj.has("cache")) {
                    acc.getCache().fromJson(obj.getAsJsonObject("cache"));
                }
                list.add(acc);
            }

            AccountManager.get().setAll(list);
            AccountSwapMod.LOGGER.info("Loaded {} accounts", list.size());
        } catch (Exception e) {
            AccountSwapMod.LOGGER.error("Failed to load accounts", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(configDir);
            JsonArray arr = new JsonArray();
            for (Account<?> a : AccountManager.get().getAccounts()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", a.getType().name());
                obj.addProperty("name", EncryptionUtil.encrypt(a.getRawName()));
                obj.add("cache", a.getCache().toJson());
                arr.add(obj);
            }
            JsonObject root = new JsonObject();
            root.add("accounts", arr);
            Files.writeString(dataFile, gson.toJson(root));
        } catch (Exception e) {
            AccountSwapMod.LOGGER.error("Failed to save accounts", e);
        }
    }
}
