package dev.dreamyfx.accountswap.account;

public enum AccountType {
    MICROSOFT("Microsoft"),
    OFFLINE("Offline");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
