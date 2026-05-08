package dev.dreamyfx.accountswap.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {

    @Mutable @Accessor("user")
    void setUser(User user);

    @Accessor("user")
    User getUser();

    @Mutable @Accessor("userApiService")
    void setUserApiService(UserApiService service);

    @Mutable @Accessor("playerSocialManager")
    void setPlayerSocialManager(PlayerSocialManager manager);

    @Mutable @Accessor("profileKeyPairManager")
    void setProfileKeyPairManager(ProfileKeyPairManager manager);

    @Mutable @Accessor("reportingContext")
    void setReportingContext(ReportingContext context);

    @Mutable @Accessor("profileFuture")
    void setProfileFuture(CompletableFuture<?> future);
}
