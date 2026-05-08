package dev.dreamyfx.accountswap.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamyfx.accountswap.AccountSwapMod;
import dev.dreamyfx.accountswap.storage.AccountStorage;
import dev.dreamyfx.accountswap.util.HttpUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SkinManager {

    private static SkinManager instance;

    private final Map<String, Identifier> headCache = new ConcurrentHashMap<>();
    private final Map<String, Identifier> skinCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<>();

    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Identifier STEVE_TEXTURE = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    private SkinManager() {}

    public static SkinManager getInstance() {
        if (instance == null) instance = new SkinManager();
        return instance;
    }

    public Identifier getHeadTexture(String uuid) {
        if (uuid == null) return null;
        String cleanUUID = uuid.replace("-", "");
        if (headCache.containsKey(cleanUUID)) return headCache.get(cleanUUID);
        if (!loading.getOrDefault(cleanUUID, false)) {
            loading.put(cleanUUID, true);
            loadSkinAsync(cleanUUID);
        }
        return null;
    }

    public Identifier getSkinTexture(String uuid) {
        if (uuid == null) return STEVE_TEXTURE;
        String cleanUUID = uuid.replace("-", "");
        return skinCache.getOrDefault(cleanUUID, STEVE_TEXTURE);
    }

    private void loadSkinAsync(String uuid) {
        CompletableFuture.runAsync(() -> {
            try {
                Path cacheDir = AccountStorage.getInstance().getConfigDir().resolve("skin_cache");
                Files.createDirectories(cacheDir);
                Path cachedFile = cacheDir.resolve(uuid + ".png");

                byte[] skinBytes;
                if (Files.exists(cachedFile) && Files.size(cachedFile) > 0) {
                    skinBytes = Files.readAllBytes(cachedFile);
                } else {
                    String skinUrl = fetchSkinUrl(uuid);
                    if (skinUrl == null) {
                        loading.remove(uuid);
                        return;
                    }
                    skinBytes = HttpUtil.getBytes(skinUrl);
                    Files.write(cachedFile, skinBytes);
                }

                final byte[] finalBytes = skinBytes;
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        NativeImage fullSkin = NativeImage.read(new java.io.ByteArrayInputStream(finalBytes));

                        // Extract 8x8 face from skin (offset 8,8 in 64x64 skin)
                        NativeImage face = new NativeImage(NativeImage.Format.RGBA, 8, 8, false);
                        for (int x = 0; x < 8; x++) {
                            for (int y = 0; y < 8; y++) {
                                face.setColorArgb(x, y, fullSkin.getColorArgb(8 + x, 8 + y));
                            }
                        }

                        // Apply hat layer (overlay at 40,8)
                        for (int x = 0; x < 8; x++) {
                            for (int y = 0; y < 8; y++) {
                                int hat = fullSkin.getColorArgb(40 + x, 8 + y);
                                if ((hat >> 24 & 0xFF) > 0) {
                                    face.setColorArgb(x, y, hat);
                                }
                            }
                        }

                        NativeImageBackedTexture headTex = new NativeImageBackedTexture(face);
                        Identifier headId = MinecraftClient.getInstance().getTextureManager()
                                .registerDynamicTexture("accountswap/head_" + uuid, headTex);

                        NativeImage skinImg = NativeImage.read(new java.io.ByteArrayInputStream(finalBytes));
                        NativeImageBackedTexture skinTex = new NativeImageBackedTexture(skinImg);
                        Identifier skinId = MinecraftClient.getInstance().getTextureManager()
                                .registerDynamicTexture("accountswap/skin_" + uuid, skinTex);

                        headCache.put(uuid, headId);
                        skinCache.put(uuid, skinId);
                        fullSkin.close();
                    } catch (Exception e) {
                        AccountSwapMod.LOGGER.warn("Failed to register skin texture for {}", uuid, e);
                    } finally {
                        loading.remove(uuid);
                    }
                });
            } catch (Exception e) {
                AccountSwapMod.LOGGER.warn("Failed to load skin for {}", uuid, e);
                loading.remove(uuid);
            }
        });
    }

    private String fetchSkinUrl(String uuid) {
        try {
            String response = HttpUtil.get(PROFILE_URL + uuid);
            JsonObject profile = JsonParser.parseString(response).getAsJsonObject();
            if (!profile.has("properties")) return null;

            for (var el : profile.getAsJsonArray("properties")) {
                JsonObject prop = el.getAsJsonObject();
                if ("textures".equals(prop.get("name").getAsString())) {
                    String encoded = prop.get("value").getAsString();
                    String decoded = new String(Base64.getDecoder().decode(encoded));
                    JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
                    JsonObject skin = textures.getAsJsonObject("textures").getAsJsonObject("SKIN");
                    return skin.get("url").getAsString();
                }
            }
        } catch (Exception e) {
            AccountSwapMod.LOGGER.debug("Could not fetch skin URL for {}: {}", uuid, e.getMessage());
        }
        return null;
    }

    public void invalidate(String uuid) {
        if (uuid == null) return;
        String clean = uuid.replace("-", "");
        headCache.remove(clean);
        skinCache.remove(clean);
        loading.remove(clean);
        try {
            Path cachedFile = AccountStorage.getInstance().getConfigDir()
                    .resolve("skin_cache").resolve(clean + ".png");
            Files.deleteIfExists(cachedFile);
        } catch (Exception ignored) {}
    }
}
