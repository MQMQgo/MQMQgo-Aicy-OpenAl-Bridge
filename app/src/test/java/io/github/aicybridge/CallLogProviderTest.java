package io.github.aicybridge;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class CallLogProviderTest {
    private SettingsProvider provider;
    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("call-logs.db");
        context.getSharedPreferences("call_logging", Context.MODE_PRIVATE).edit().clear().commit();
        provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        ShadowBinder.setCallingUid(Process.myUid());
    }
    @After public void close() { ShadowBinder.setCallingUid(Process.myUid()); provider.shutdown(); }

    @Test public void selfCanAppendMetadataButProviderDoesNotExposeHistoryReads() {
        Bundle start = CallLogStoreTest.start("provider-test-model");
        assertTrue(provider.call("call_log_start", null, start).getBoolean("ok"));
        assertTrue(provider.call("call_log_finish", null, CallLogStoreTest.end(start, "response", 200, "")).getBoolean("ok"));
        try { provider.call("get_call_logs", null, null); fail(); }
        catch (IllegalArgumentException expected) { }
        try { provider.query(SettingsProvider.URI, null, null, null, null); fail(); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void untrustedUidCannotAppendOrCompleteCalls() {
        ShadowBinder.setCallingUid(Process.myUid() + 1000);
        Bundle start = CallLogStoreTest.start("untrusted");
        try { provider.call("call_log_start", null, start); fail(); }
        catch (SecurityException expected) { }
        try { provider.call("call_log_finish", null, CallLogStoreTest.end(start, "response", 200, "")); fail(); }
        catch (SecurityException expected) { }
    }
}
