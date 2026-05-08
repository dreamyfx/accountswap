package dev.dreamyfx.accountswap.account;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.mixin.MinecraftClientAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.util.Util;

import java.util.concurrent.CompletableFuture;

public abstract class Account<T extends Account<?>> {

    protected AccountType type;
    // For Microsoft: stores the refresh token. For Cracked: stores the username.
    protected String name;
    protected final AccountCache cache;

    protected Account(AccountType type, String name) {
        this.type = type;
        this.name = name;
        this.cache = new AccountCache();
    }

    /** Fetches profile info (username, uuid) from remote. Blocking. */
    public abstract boolean fetchInfo();

    /** Applies the session to Minecraft. Call after fetchInfo() succeeds. */
    public abstract boolean login();

    public String getUsername() {
        return cache.username.isEmpty() ? name : cache.username;
    }

    public String getUuid() {
        return cache.uuid;
    }

    public AccountType getType() {
        return type;
    }

    public AccountCache getCache() {
        return cache;
    }

    public String getRawName() {
        return name;
    }

    /** Full session injection — sets User plus all associated services. */
    public static void setSession(User newUser) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftClientAccessor acc = (MinecraftClientAccessor) mc;

        acc.setUser(newUser);

        try {
            YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(mc.getProxy());
            UserApiService api = auth.createUserApiService(newUser.getAccessToken());

            acc.setUserApiService(api);

            try { acc.setPlayerSocialManager(new PlayerSocialManager(mc, api)); }
            catch (Exception e) { AccountSwapMod.LOGGER.warn("PlayerSocialManager update failed", e); }

            try { acc.setProfileKeyPairManager(ProfileKeyPairManager.create(api, newUser, mc.gameDirectory.toPath())); }
            catch (Exception e) { AccountSwapMod.LOGGER.warn("ProfileKeyPairManager update failed", e); }

            try { acc.setReportingContext(ReportingContext.create(ReportEnvironment.local(), api)); }
            catch (Exception e) { AccountSwapMod.LOGGER.warn("ReportingContext update failed", e); }

            try {
                acc.setProfileFuture(CompletableFuture.supplyAsync(
                        () -> mc.services().sessionService().fetchProfile(mc.getUser().getProfileId(), true),
                        Util.ioPool()
                ));
            } catch (Exception e) { AccountSwapMod.LOGGER.warn("ProfileFuture update failed", e); }

        } catch (Exception e) {
            AccountSwapMod.LOGGER.error("Session injection partially failed", e);
        }

        AccountSwapMod.LOGGER.info("Switched session to: {}", newUser.getName());
    }
}
