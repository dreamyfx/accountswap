package dev.dreamyfx.accountswap.account;

import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.storage.AccountStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccountManager {

    private static AccountManager instance;
    private final List<Account> accounts = new ArrayList<>();
    private Account activeAccount;

    private AccountManager() {}

    public static AccountManager getInstance() {
        if (instance == null) instance = new AccountManager();
        return instance;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public Account getActiveAccount() {
        return activeAccount;
    }

    public void addAccount(Account account) {
        accounts.removeIf(a -> a.getUuid().equals(account.getUuid()));
        accounts.add(account);
        AccountStorage.getInstance().save();
    }

    public void removeAccount(Account account) {
        if (activeAccount == account) {
            activeAccount = null;
        }
        accounts.remove(account);
        AccountStorage.getInstance().save();
    }

    public boolean switchAccount(Account account) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();

            Session.AccountType sessionType = account.getType() == AccountType.MICROSOFT
                    ? Session.AccountType.MSA
                    : Session.AccountType.LEGACY;

            UUID uuid;
            try {
                uuid = UUID.fromString(account.getUuid());
            } catch (IllegalArgumentException e) {
                uuid = UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + account.getUsername()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
            }

            Session newSession = new Session(
                    account.getUsername(),
                    uuid,
                    account.getAccessToken() != null ? account.getAccessToken() : "",
                    Optional.empty(),
                    Optional.empty(),
                    sessionType
            );

            // Inject session via accessor mixin
            ((dev.dreamyfx.accountswap.mixin.MinecraftClientAccessor) client).setSession(newSession);

            if (activeAccount != null) activeAccount.setActive(false);
            activeAccount = account;
            account.setActive(true);

            AccountStorage.getInstance().save();
            AccountSwapMod.LOGGER.info("Switched to account: {}", account.getUsername());
            return true;
        } catch (Exception e) {
            AccountSwapMod.LOGGER.error("Failed to switch account", e);
            return false;
        }
    }

    public void setAccounts(List<Account> list) {
        accounts.clear();
        accounts.addAll(list);
        for (Account a : accounts) {
            if (a.isActive()) {
                activeAccount = a;
            }
        }
    }

    public List<Account> search(String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(accounts);
        String lower = query.toLowerCase();
        return accounts.stream()
                .filter(a -> a.getUsername().toLowerCase().contains(lower))
                .toList();
    }
}
