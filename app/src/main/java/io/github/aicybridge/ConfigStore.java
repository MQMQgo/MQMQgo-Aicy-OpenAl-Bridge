package io.github.aicybridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Private module settings. The bearer key is encrypted using the module UID's Android Keystore. */
public final class ConfigStore {
    private static final String KEY_ALIAS = "aicybridge.api-key.v1";
    private final SharedPreferences preferences;

    public ConfigStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static final class Settings {
        public final boolean enabled;
        public final String baseUrl;
        public final String apiKey;
        public final String model;
        public final boolean keyReadable;

        Settings(boolean enabled, String baseUrl, String apiKey, String model, boolean keyReadable) {
            this.enabled = enabled;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.keyReadable = keyReadable;
        }
    }

    public Settings read() {
        // Take one atomic preference snapshot so a concurrent save cannot pair a new key with an old host.
        java.util.Map<String, ?> snapshot = preferences.getAll();
        String baseUrl = string(snapshot, "base_url", "https://api.openai.com/v1");
        String model = string(snapshot, "model", "");
        String encrypted = string(snapshot, "encrypted_api_key", "");
        try {
            return new Settings(Boolean.TRUE.equals(snapshot.get("enabled")), baseUrl,
                    decrypt(encrypted), model, true);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            // A restored preference file or unavailable Keystore must never activate stale settings.
            return new Settings(false, baseUrl, "", model, false);
        }
    }

    private static String string(java.util.Map<String, ?> values, String name, String fallback) {
        Object value = values.get(name);
        return value instanceof String ? (String) value : fallback;
    }

    public synchronized void save(boolean enabled, String baseUrl, String apiKey, String model)
            throws GeneralSecurityException, IOException {
        String encrypted = encrypt(apiKey);
        if (!preferences.edit().putBoolean("enabled", enabled)
                .putString("base_url", baseUrl).putString("encrypted_api_key", encrypted)
                .putString("model", model).commit()) {
            throw new IOException("Settings could not be committed");
        }
    }

    /** A disable action remains possible even if the user has incomplete or damaged credentials. */
    public synchronized boolean disable() {
        return preferences.edit().putBoolean("enabled", false).commit();
    }

    public String statusCode() { return preferences.getString("hook_status", "waiting"); }

    public long statusTime() { return preferences.getLong("hook_status_time", 0L); }

    public synchronized boolean reportStatus(String code) {
        if (!StatusText.isKnown(code)) return false;
        return preferences.edit().putString("hook_status", code)
                .putLong("hook_status_time", System.currentTimeMillis()).commit();
    }

    private static String encrypt(String value) throws GeneralSecurityException, IOException {
        if (value.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "v1:" + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws GeneralSecurityException, IOException {
        if (value.isEmpty()) return "";
        String[] fields = value.split(":", -1);
        if (fields.length != 3 || !"v1".equals(fields[0])) {
            throw new GeneralSecurityException("Unknown encrypted key format");
        }
        byte[] iv = Base64.decode(fields[1], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(fields[2], Base64.NO_WRAP);
        if (iv.length != 12 || encrypted.length < 16) {
            throw new GeneralSecurityException("Invalid encrypted key");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static SecretKey key(boolean create) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            java.security.Key value = store.getKey(KEY_ALIAS, null);
            if (value instanceof SecretKey) return (SecretKey) value;
            throw new GeneralSecurityException("Unexpected key type");
        }
        if (!create) throw new GeneralSecurityException("Encryption key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
