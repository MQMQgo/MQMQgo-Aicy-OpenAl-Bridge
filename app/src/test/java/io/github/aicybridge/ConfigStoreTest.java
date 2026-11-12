package io.github.aicybridge;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.GeneralSecurityException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class ConfigStoreTest {
    private SharedPreferences prefs;
    private TestSecrets secrets;
    private ConfigStore store;

    @Before public void setup() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("profile_regression", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        secrets = new TestSecrets();
        store = new ConfigStore(prefs, secrets);
    }

    @Test public void savedProfileRestoresInANewStoreAndSurvivesDisabling() throws Exception {
        store.save(true, "https://example.test/v1", "test-key", "model-one");
        ConfigStore restored = new ConfigStore(prefs, new TestSecrets());
        assertProfile(restored.read(), true, "model-one");
        restored.setEnabled(false);
        ConfigStore.Settings disabled = new ConfigStore(prefs, new TestSecrets()).read();
        assertProfile(disabled, false, "model-one");
        assertTrue(disabled.hasSavedConfig);
        assertTrue(disabled.hasApiKey);
        restored.setEnabled(true);
        assertProfile(restored.read(), true, "model-one");
    }

    @Test public void originalVersionPreferencesRestoreWithoutSchemaMarker() {
        prefs.edit().putBoolean("enabled", false).putString("base_url", "https://example.test/v1")
                .putString("model", "model-one").putString("encrypted_api_key", "encoded:test-key").commit();
        assertFalse(prefs.contains("has_saved_config"));
        assertProfile(new ConfigStore(prefs, secrets).read(), false, "model-one");
        assertTrue(store.read().hasSavedConfig);
    }

    @Test public void temporaryKeyFailureRetainsChannelAndRecoversWithoutResaving() throws Exception {
        store.save(true, "https://example.test/v1", "test-key", "model-one");
        Map<String, ?> before = prefs.getAll();
        secrets.failRead = true;
        ConfigStore.Settings unavailable = new ConfigStore(prefs, secrets).read();
        assertFalse(unavailable.enabled);
        assertFalse(unavailable.keyReadable);
        assertTrue(unavailable.savedEnabled);
        assertTrue(unavailable.hasSavedConfig);
        assertTrue(unavailable.hasApiKey);
        assertEquals("https://example.test/v1", unavailable.baseUrl);
        assertEquals("model-one", unavailable.model);
        assertEquals(before, prefs.getAll());
        secrets.failRead = false;
        assertProfile(new ConfigStore(prefs, secrets).read(), true, "model-one");
    }

    @Test public void emptyEditorKeyRetainsCiphertextWithoutEncryptingAgain() throws Exception {
        store.save(true, "https://example.test/v1", "test-key", "model-one");
        String original = prefs.getString("encrypted_api_key", "");
        secrets.failWrite = true;
        store.saveProfile(true, "https://example.test/v1", "", "model-two", true);
        assertEquals(original, prefs.getString("encrypted_api_key", ""));
        assertProfile(new ConfigStore(prefs, secrets).read(), true, "model-two");
    }

    @Test public void keystoreProviderFailureKeepsTheSavedRecordVisibleAndUntouched() throws Exception {
        store.save(true, "https://example.test/v1", "test-key", "model-one");
        Map<String, ?> before = prefs.getAll();
        SecretCodec unavailable = new SecretCodec() {
            public String encrypt(String value) { throw new java.security.ProviderException(); }
            public String decrypt(String value) { throw new java.security.ProviderException(); }
        };
        ConfigStore.Settings value = new ConfigStore(prefs, unavailable).read();
        assertFalse(value.enabled);
        assertFalse(value.keyReadable);
        assertTrue(value.hasSavedConfig);
        assertTrue(value.hasApiKey);
        assertEquals("https://example.test/v1", value.baseUrl);
        assertEquals("model-one", value.model);
        assertEquals(before, prefs.getAll());
        assertProfile(new ConfigStore(prefs, secrets).read(), true, "model-one");
    }

    @Test public void encryptionFailureDoesNotOverwriteExistingSavedProfile() throws Exception {
        store.save(true, "https://example.test/v1", "test-key", "model-one");
        Map<String, ?> before = prefs.getAll();
        secrets.failWrite = true;
        try { store.save(true, "https://other.test/v1", "replacement-key", "new-model"); fail(); }
        catch (GeneralSecurityException expected) { }
        assertEquals(before, prefs.getAll());
        assertProfile(store.read(), true, "model-one");
    }

    @Test public void changedProviderCannotSilentlyReusePreviousCredential() throws Exception {
        store.save(true, "https://example.test/v1", "test-key", "model-one");
        Map<String, ?> before = prefs.getAll();
        try { store.saveProfile(true, "https://other.test/v1", "", "model-two", true); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(before, prefs.getAll());
    }

    @Test public void disabledProfileCanKeepAnUnreadableKeyWithoutDestroyingIt() throws Exception {
        store.save(true, "https://example.test/v1", "test-key", "model-one");
        String cipher = prefs.getString("encrypted_api_key", "");
        secrets.failRead = true;
        assertTrue(store.disable());
        store.saveProfile(false, "https://example.test/v1", "", "model-two", true);
        assertTrue(store.read().hasSavedConfig);
        assertFalse(store.read().enabled);
        assertEquals(cipher, prefs.getString("encrypted_api_key", ""));
        try { store.setEnabled(true); fail(); }
        catch (GeneralSecurityException expected) { }
        secrets.failRead = false;
        assertProfile(new ConfigStore(prefs, secrets).read(), false, "model-two");
    }

    private static void assertProfile(ConfigStore.Settings value, boolean enabled, String model) {
        assertEquals(enabled, value.enabled);
        assertEquals("https://example.test/v1", value.baseUrl);
        assertEquals("test-key", value.apiKey);
        assertEquals(model, value.model);
        assertTrue(value.hasSavedConfig);
        assertTrue(value.keyReadable);
    }

    private static final class TestSecrets implements SecretCodec {
        boolean failRead, failWrite;
        public String encrypt(String value) throws GeneralSecurityException {
            if (failWrite) throw new GeneralSecurityException("Simulated unavailable Keystore");
            return value.isEmpty() ? "" : "encoded:" + value;
        }
        public String decrypt(String value) throws GeneralSecurityException {
            if (failRead) throw new GeneralSecurityException("Simulated unavailable Keystore");
            if (value.isEmpty()) return "";
            if (!value.startsWith("encoded:")) throw new GeneralSecurityException();
            return value.substring(8);
        }
    }
}
