package dev.dreamyfx.accountswap;

import dev.dreamyfx.accountswap.account.AccountManager;
import dev.dreamyfx.accountswap.storage.AccountStorage;
import dev.dreamyfx.accountswap.ui.AccountSwapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountSwapMod implements ClientModInitializer {

    public static final String MOD_ID = "accountswap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding OPEN_KEY;

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.accountswap.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.accountswap"
        ));

        AccountStorage.getInstance().load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                client.setScreen(new AccountSwapScreen(client.currentScreen));
            }
        });

        LOGGER.info("AccountSwap loaded. Press P to open.");
    }
}
