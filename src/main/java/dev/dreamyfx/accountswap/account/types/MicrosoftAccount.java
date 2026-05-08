package dev.dreamyfx.accountswap.account.types;

import com.mojang.util.UndashedUuid;
import dev.dreamyfx.accountswap.account.Account;
import dev.dreamyfx.accountswap.account.AccountType;
import dev.dreamyfx.accountswap.auth.MicrosoftLogin;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class MicrosoftAccount extends Account<MicrosoftAccount> {

    // name field holds the refresh token
    private @Nullable String mcToken;

    public MicrosoftAccount(String refreshToken) {
        super(AccountType.Microsoft, refreshToken);
    }

    @Override
    public boolean fetchInfo() {
        mcToken = auth();
        return mcToken != null;
    }

    @Override
    public boolean login() {
        if (mcToken == null) return false;
        setSession(new User(
                cache.username,
                UndashedUuid.fromStringLenient(cache.uuid),
                mcToken,
                Optional.empty(),
                Optional.empty(),
                User.Type.MSA
        ));
        return true;
    }

    private @Nullable String auth() {
        MicrosoftLogin.LoginData data = MicrosoftLogin.login(name);
        if (!data.isGood()) return null;
        // Update stored refresh token
        name = data.newRefreshToken;
        cache.username = data.username;
        cache.uuid = data.uuid;
        return data.mcToken;
    }

    public String getRefreshToken() { return name; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MicrosoftAccount a)) return false;
        return a.name.equals(this.name);
    }
}
