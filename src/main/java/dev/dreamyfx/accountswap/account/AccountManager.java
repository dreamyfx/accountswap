package dev.dreamyfx.accountswap.account;

import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.storage.AccountStorage;

import java.util.ArrayList;
import java.util.List;

public class AccountManager {

    private static final AccountManager INSTANCE = new AccountManager();
    private final List<Account<?>> accounts = new ArrayList<>();

    private AccountManager() {}

    public static AccountManager get() { return INSTANCE; }

    public List<Account<?>> getAccounts() { return accounts; }

    public void add(Account<?> account) {
        accounts.removeIf(a -> a.equals(account));
        accounts.add(account);
        AccountStorage.get().save();
    }

    public void remove(Account<?> account) {
        if (accounts.remove(account)) {
            AccountStorage.get().save();
        }
    }

    public boolean exists(Account<?> account) {
        return accounts.contains(account);
    }

    public int size() { return accounts.size(); }

    /** Runs fetchInfo() + login() on a background thread, calls callback on the MC thread. */
    public void loginAsync(Account<?> account, Runnable onSuccess, java.util.function.Consumer<String> onFail) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                boolean ok = account.fetchInfo();
                if (!ok) {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> onFail.accept("Authentication failed."));
                    return;
                }
                boolean loggedIn = account.login();
                if (!loggedIn) {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> onFail.accept("Login failed."));
                    return;
                }
                AccountStorage.get().save();
                net.minecraft.client.Minecraft.getInstance().execute(onSuccess);
            } catch (Exception e) {
                AccountSwapMod.LOGGER.error("loginAsync error", e);
                net.minecraft.client.Minecraft.getInstance().execute(() -> onFail.accept(e.getMessage()));
            }
        });
    }

    public List<Account<?>> search(String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(accounts);
        String lower = query.toLowerCase();
        return accounts.stream()
                .filter(a -> a.getUsername().toLowerCase().contains(lower))
                .toList();
    }

    public void setAll(List<Account<?>> list) {
        accounts.clear();
        accounts.addAll(list);
    }
}
