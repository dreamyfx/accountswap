package dev.dreamyfx.accountswap.account.types;

import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountType;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;

public class CrackedAccount extends Account<CrackedAccount> {

    public CrackedAccount(String username) {
        super(AccountType.Cracked, username);
    }

    @Override
    public boolean fetchInfo() {
        cache.username = name;
        cache.uuid = UUIDUtil.createOfflinePlayerUUID(name).toString().replace("-", "");
        return true;
    }

    @Override
    public boolean login() {
        setSession(new User(
                name,
                UUIDUtil.createOfflinePlayerUUID(name),
                "",
                Optional.empty(),
                Optional.empty(),
                User.Type.LEGACY
        ));
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CrackedAccount a)) return false;
        return a.name.equalsIgnoreCase(this.name);
    }
}
