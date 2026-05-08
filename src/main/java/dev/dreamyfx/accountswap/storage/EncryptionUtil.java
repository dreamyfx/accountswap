package dev.dreamyfx.accountswap.storage;

import dev.dreamyfx.accountswap.AccountSwapMod;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;

    private static SecretKey key;
    private static Path keyPath;

    public static void init(Path configDir) {
        keyPath = configDir.resolve(".key");
        try {
            if (Files.exists(keyPath)) {
                byte[] keyBytes = Files.readAllBytes(keyPath);
                key = new SecretKeySpec(keyBytes, "AES");
            } else {
                KeyGenerator gen = KeyGenerator.getInstance("AES");
                gen.init(256, new SecureRandom());
                key = gen.generateKey();
                Files.createDirectories(configDir);
                Files.write(keyPath, key.getEncoded());
            }
        } catch (Exception e) {
            AccountSwapMod.LOGGER.warn("Encryption init failed, using fallback", e);
            try {
                KeyGenerator gen = KeyGenerator.getInstance("AES");
                gen.init(256);
                key = gen.generateKey();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static String encrypt(String plaintext) {
        if (key == null || plaintext == null) return plaintext;
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            AccountSwapMod.LOGGER.warn("Encrypt failed", e);
            return plaintext;
        }
    }

    public static String decrypt(String ciphertext) {
        if (key == null || ciphertext == null) return ciphertext;
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(data, 0, iv, 0, iv.length);
            byte[] enc = new byte[data.length - iv.length];
            System.arraycopy(data, iv.length, enc, 0, enc.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
            return new String(cipher.doFinal(enc));
        } catch (Exception e) {
            AccountSwapMod.LOGGER.warn("Decrypt failed (may be unencrypted data)", e);
            return ciphertext;
        }
    }
}
