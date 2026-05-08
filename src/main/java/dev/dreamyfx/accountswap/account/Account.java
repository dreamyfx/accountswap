package dev.dreamyfx.accountswap.account;

import java.util.UUID;

public class Account {

    private String username;
    private String uuid;
    private AccountType type;
    private String accessToken;
    private String refreshToken;
    private long tokenExpiry;
    private boolean active;

    public Account() {}

    public Account(String username, String uuid, AccountType type) {
        this.username = username;
        this.uuid = uuid;
        this.type = type;
        this.accessToken = "";
        this.refreshToken = "";
        this.tokenExpiry = 0;
        this.active = false;
    }

    public static Account offline(String username) {
        UUID offlineUUID = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        Account a = new Account(username, offlineUUID.toString(), AccountType.OFFLINE);
        a.accessToken = "-";
        return a;
    }

    public boolean isTokenExpired() {
        if (type == AccountType.OFFLINE) return false;
        return System.currentTimeMillis() > tokenExpiry;
    }

    public boolean isValid() {
        if (type == AccountType.OFFLINE) return true;
        return accessToken != null && !accessToken.isEmpty() && !isTokenExpired();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public long getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(long tokenExpiry) { this.tokenExpiry = tokenExpiry; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDisplayUUID() {
        if (uuid == null || uuid.length() != 36) return uuid;
        return uuid.substring(0, 8) + "...";
    }

    @Override
    public String toString() {
        return "Account{" + username + ", " + type + ", active=" + active + "}";
    }
}
