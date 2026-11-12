package io.github.aicybridge;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Map;
import io.github.aicybridge.core.OpenAiClient;

/** Durable profile data is independent of the enable switch and temporary credential availability. */
public final class ConfigStore {
    private static final String KEY_ALIAS = "aicybridge.api-key.v1";
    private static final Object WRITE_LOCK = new Object();
    private final SharedPreferences preferences;
    private final SecretCodec secrets;

    public ConfigStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE),
                new AndroidSecretCodec(KEY_ALIAS));
    }

    ConfigStore(SharedPreferences preferences, SecretCodec secrets) {
        this.preferences = preferences;
        this.secrets = secrets;
    }

    public static final class Settings {
        public final boolean enabled;
        public final String baseUrl;
        public final String apiKey;
        public final String model;
        public final boolean keyReadable;
        public final boolean hasSavedConfig;
        public final boolean hasApiKey;
        public final boolean savedEnabled;
        public final long savedAt;

        Settings(boolean enabled, String baseUrl, String apiKey, String model, boolean readable,
                 boolean configured, boolean hasKey, boolean savedEnabled, long savedAt) {
            this.enabled = enabled;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.keyReadable = readable;
            this.hasSavedConfig = configured;
            this.hasApiKey = hasKey;
            this.savedEnabled = savedEnabled;
            this.savedAt = savedAt;
        }
    }

    public Settings read() {
        Map<String, ?> snapshot = preferences.getAll();
        String baseUrl = string(snapshot, "base_url", "https://api.openai.com/v1");
        String model = string(snapshot, "model", "");
        String encrypted = string(snapshot, "encrypted_api_key", "");
        // Legacy profiles predate this marker. Never use enabled or decrypted key presence as existence.
        boolean configured = Boolean.TRUE.equals(snapshot.get("has_saved_config"))
                || snapshot.containsKey("base_url") || snapshot.containsKey("model");
        boolean savedEnabled = Boolean.TRUE.equals(snapshot.get("enabled"));
        Object timestamp = snapshot.get("config_saved_at");
        long savedAt = timestamp instanceof Long ? (Long) timestamp : 0L;
        try {
            return new Settings(savedEnabled, baseUrl, secrets.decrypt(encrypted), model, true,
                    configured, !encrypted.isEmpty(), savedEnabled, savedAt);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException | java.security.ProviderException unavailable) {
            // Preserve the complete record. A transient Keystore failure must not erase or overwrite it.
            return new Settings(false, baseUrl, "", model, false, configured,
                    !encrypted.isEmpty(), savedEnabled, savedAt);
        }
    }

    /** Explicitly replace the credential, retained for compatibility with the original store API. */
    public void save(boolean enabled, String baseUrl, String apiKey, String model)
            throws GeneralSecurityException, IOException {
        saveProfile(enabled, baseUrl, apiKey, model, false);
    }

    /** Empty editor text may retain an existing encrypted key without decrypting/re-encrypting it. */
    public void saveProfile(boolean enabled, String baseUrl, String enteredKey, String model, boolean keepKey)
            throws GeneralSecurityException, IOException {
        new OpenAiClient.Config(baseUrl, enteredKey, model);
        synchronized (WRITE_LOCK) {
            Map<String, ?> before = preferences.getAll();
            String existing = string(before, "encrypted_api_key", "");
            String encrypted;
            if (keepKey && !existing.isEmpty()) {
                if (!sameOrigin(string(before, "base_url", ""), baseUrl)) {
                    throw new IllegalArgumentException("A different API origin requires an explicit credential");
                }
                // Enabling requires a usable key; editing a disabled profile can retain unreadable ciphertext.
                if (enabled) secrets.decrypt(existing);
                encrypted = existing;
            } else {
                encrypted = secrets.encrypt(enteredKey);
            }
            if (!preferences.edit().putBoolean("enabled", enabled)
                    .putBoolean("has_saved_config", true)
                    .putLong("config_saved_at", System.currentTimeMillis())
                    .putString("base_url", baseUrl).putString("encrypted_api_key", encrypted)
                    .putString("model", model).commit()) {
                throw new IOException("Settings could not be committed");
            }
        }
    }

    public void setEnabled(boolean enabled) throws GeneralSecurityException, IOException {
        synchronized (WRITE_LOCK) {
            if (enabled) {
                Settings saved = read();
                if (!saved.hasSavedConfig) throw new IllegalStateException("Save a profile first");
                if (!saved.keyReadable) throw new GeneralSecurityException("Credential temporarily unavailable");
                new OpenAiClient.Config(saved.baseUrl, saved.apiKey, saved.model);
            }
            if (!preferences.edit().putBoolean("enabled", enabled).commit()) {
                throw new IOException("Enable state could not be committed");
            }
        }
    }

    public boolean disable() {
        synchronized (WRITE_LOCK) {
            return preferences.edit().putBoolean("enabled", false).commit();
        }
    }

    public String statusCode() { return preferences.getString("hook_status", "waiting"); }
    public long statusTime() { return preferences.getLong("hook_status_time", 0L); }

    public boolean reportStatus(String code) {
        if (!StatusText.isKnown(code)) return false;
        synchronized (WRITE_LOCK) {
            return preferences.edit().putString("hook_status", code)
                    .putLong("hook_status_time", System.currentTimeMillis()).commit();
        }
    }

    private static String string(Map<String, ?> values, String name, String fallback) {
        Object value = values.get(name);
        return value instanceof String ? (String) value : fallback;
    }

    private static boolean sameOrigin(String a, String b) {
        try {
            URI left = new URI(a), right = new URI(b);
            return "https".equalsIgnoreCase(left.getScheme()) && "https".equalsIgnoreCase(right.getScheme())
                    && left.getHost() != null && left.getHost().equalsIgnoreCase(right.getHost())
                    && (left.getPort() < 0 ? 443 : left.getPort()) == (right.getPort() < 0 ? 443 : right.getPort());
        } catch (java.net.URISyntaxException invalid) { return false; }
    }
}
