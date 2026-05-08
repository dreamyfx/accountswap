package dev.dreamyfx.accountswap;

import dev.dreamyfx.accountswap.storage.AccountStorage;
import dev.dreamyfx.accountswap.ui.AccountSwapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountSwapMod implements ClientModInitializer {

    public static final String MOD_ID = "accountswap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyMapping OPEN_KEY;

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.accountswap.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "key.categories.accountswap"
        ));

        AccountStorage.get().load();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.consumeClick()) {
                mc.setScreen(new AccountSwapScreen(mc.screen));
            }
        });

        LOGGER.info("AccountSwap loaded. Press P to open.");
    }
}
