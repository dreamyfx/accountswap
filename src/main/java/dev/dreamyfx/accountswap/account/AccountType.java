package dev.dreamyfx.accountswap.account;

public enum AccountType {
    Microsoft("Microsoft"),
    Cracked("Offline");

    public final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }
}
