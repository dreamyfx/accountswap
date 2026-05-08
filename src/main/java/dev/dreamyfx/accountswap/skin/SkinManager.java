package dev.dreamyfx.accountswap.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.storage.AccountStorage;
import dev.dreamyfx.accountswap.util.HttpUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SkinManager {

    private static final SkinManager INSTANCE = new SkinManager();
    private final Map<String, ResourceLocation> headMap   = new ConcurrentHashMap<>();
    private final Map<String, Boolean>          loading   = new ConcurrentHashMap<>();

    private SkinManager() {}

    public static SkinManager get() { return INSTANCE; }

    /** Returns a registered head texture, or null (triggers async load). */
    public ResourceLocation getHead(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        String clean = uuid.replace("-", "");
        if (headMap.containsKey(clean)) return headMap.get(clean);
        if (!loading.getOrDefault(clean, false)) {
            loading.put(clean, true);
            loadAsync(clean);
        }
        return null;
    }

    public void invalidate(String uuid) {
        if (uuid == null) return;
        String clean = uuid.replace("-", "");
        headMap.remove(clean);
        loading.remove(clean);
        try {
            Path p = AccountStorage.get().getConfigDir().resolve("skin_cache").resolve(clean + ".png");
            Files.deleteIfExists(p);
        } catch (Exception ignored) {}
    }

    private void loadAsync(String uuid) {
        CompletableFuture.runAsync(() -> {
            try {
                Path cacheDir = AccountStorage.get().getConfigDir().resolve("skin_cache");
                Files.createDirectories(cacheDir);
                Path cached = cacheDir.resolve(uuid + ".png");

                byte[] png;
                if (Files.exists(cached) && Files.size(cached) > 0) {
                    png = Files.readAllBytes(cached);
                } else {
                    String url = fetchSkinUrl(uuid);
                    if (url == null) { loading.remove(uuid); return; }
                    png = HttpUtil.getBytes(url);
                    Files.write(cached, png);
                }

                final byte[] finalPng = png;
                Minecraft.getInstance().execute(() -> {
                    try (com.mojang.blaze3d.platform.NativeImage full =
                                 com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(finalPng))) {

                        // Extract 8×8 face + hat overlay from the 64×64 skin
                        com.mojang.blaze3d.platform.NativeImage face =
                                new com.mojang.blaze3d.platform.NativeImage(
                                        com.mojang.blaze3d.platform.NativeImage.Format.RGBA, 8, 8, false);

                        for (int px = 0; px < 8; px++) {
                            for (int py = 0; py < 8; py++) {
                                int base = full.getPixelRGBA(8 + px, 8 + py);
                                int hat  = full.getPixelRGBA(40 + px, 8 + py);
                                face.setPixelRGBA(px, py, ((hat >> 24 & 0xFF) > 0) ? hat : base);
                            }
                        }

                        DynamicTexture tex = new DynamicTexture(face);
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("accountswap", "head/" + uuid);
                        Minecraft.getInstance().getTextureManager().register(id, tex);
                        headMap.put(uuid, id);
                    } catch (Exception e) {
                        AccountSwapMod.LOGGER.warn("Failed to register head for {}", uuid, e);
                    } finally {
                        loading.remove(uuid);
                    }
                });

            } catch (Exception e) {
                AccountSwapMod.LOGGER.warn("Skin load failed for {}: {}", uuid, e.getMessage());
                loading.remove(uuid);
            }
        });
    }

    private String fetchSkinUrl(String uuid) {
        try {
            String resp = HttpUtil.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
            JsonObject profile = JsonParser.parseString(resp).getAsJsonObject();
            for (var el : profile.getAsJsonArray("properties")) {
                JsonObject prop = el.getAsJsonObject();
                if ("textures".equals(prop.get("name").getAsString())) {
                    String decoded = new String(Base64.getDecoder().decode(prop.get("value").getAsString()));
                    JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
                    return textures.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                }
            }
        } catch (Exception e) {
            AccountSwapMod.LOGGER.debug("Could not fetch skin url for {}: {}", uuid, e.getMessage());
        }
        return null;
    }
}
