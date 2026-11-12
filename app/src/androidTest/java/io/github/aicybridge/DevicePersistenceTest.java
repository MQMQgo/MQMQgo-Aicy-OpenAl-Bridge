package io.github.aicybridge;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses isolated test preferences and a separate real AndroidKeyStore alias, never the user's profile. */
@RunWith(AndroidJUnit4.class)
public class DevicePersistenceTest {
    private ConfigStore store() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("device_persistence_regression", Context.MODE_PRIVATE);
        return new ConfigStore(prefs, new AndroidSecretCodec("aicybridge.test.persistence"));
    }
    @Test public void savedProfileRestoresAfterProcessRestart() throws Exception {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("restore_only"))) {
            store().save(true, "https://example.test/v1", "device-test-key", "device-test-model");
        }
        assertRestored();
    }
    /** Repeat the preceding test with restore_only=true after force-stopping the package. */
    private void assertRestored() {
        ConfigStore.Settings value = store().read();
        assertTrue(value.hasSavedConfig);
        assertTrue(value.keyReadable);
        assertTrue(value.enabled);
        assertEquals("https://example.test/v1", value.baseUrl);
        assertEquals("device-test-key", value.apiKey);
        assertEquals("device-test-model", value.model);
    }
    @Test public void disableRetainsProfile() throws Exception {
        ConfigStore saved = store();
        saved.save(true, "https://example.test/v1", "device-test-key", "device-test-model");
        saved.setEnabled(false);
        ConfigStore.Settings value = store().read();
        assertFalse(value.enabled);
        assertTrue(value.hasSavedConfig);
        assertTrue(value.hasApiKey);
        assertEquals("device-test-key", value.apiKey);
        saved.saveProfile(false, value.baseUrl, "", "updated-model", true);
        assertEquals("device-test-key", store().read().apiKey);
        assertEquals("updated-model", store().read().model);
    }
}
