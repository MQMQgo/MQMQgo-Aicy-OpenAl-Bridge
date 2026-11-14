package io.github.aicybridge.hook;

import android.content.Context;
import io.github.aicybridge.CallLogReporter;
import io.github.aicybridge.CallLogStore;
import io.github.aicybridge.SettingsProvider;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

/** Per-client interceptor -> real async reporter -> authenticated provider -> private database. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class NativeRequestReportingTest {
    private SettingsProvider provider;
    private CallLogStore store;
    private CallLogReporter reporter;
    @Before public void setup() {
        Context app = RuntimeEnvironment.getApplication();
        app.deleteDatabase("call-logs.db"); app.getSharedPreferences("call_logging", Context.MODE_PRIVATE).edit().clear().commit();
        provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        ShadowContentResolver.registerProviderInternal(SettingsProvider.AUTHORITY, provider);
        store = new CallLogStore(app);
        reporter = new CallLogReporter(app, "https://example.test/v1", "safe-model", "test-key");
    }
    @After public void close() { store.close(); provider.shutdown(); }
    private List<CallLogStore.Entry> await(Predicate<List<CallLogStore.Entry>> ready) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            List<CallLogStore.Entry> values = store.read();
            if (ready.test(values)) return values;
            Thread.sleep(10);
        }
        fail("Native request did not reach the call history"); return null;
    }
    @Test public void nativeHttp200ReachesTheDatabaseAsSuccessfulAicyHistory() throws Throwable {
        NativeCallLoggingTest.Chain call = new NativeCallLoggingTest.Chain();
        assertSame(call.response, NativeCallLoggingTest.handler(reporter).invoke(this, null, new Object[]{call}));
        CallLogStore.Entry entry = await(rows -> rows.size() == 1 && rows.get(0).isSuccess()).get(0);
        assertEquals("aicy", entry.source); assertEquals("http", entry.stage); assertEquals(200, entry.httpStatus);
        assertEquals("response", entry.state); assertFalse(entry.isFailure()); assertTrue(entry.elapsedMs >= 0);
        assertEquals("example.test", entry.host); assertEquals("safe-model", entry.model);
    }
    @Test public void httpAndTransportFailuresRemainDistinctFromSuccess() throws Throwable {
        NativeCallLoggingTest.Chain call = new NativeCallLoggingTest.Chain(); call.response.code = 401;
        java.lang.reflect.InvocationHandler logger = NativeCallLoggingTest.handler(reporter);
        logger.invoke(this, null, new Object[]{call});
        IOException original = new IOException("private-error-not-stored");
        call.failure = original;
        try { logger.invoke(this, null, new Object[]{call}); fail(); }
        catch (IOException result) { assertSame(original, result); }
        List<CallLogStore.Entry> entries = await(rows -> rows.size() == 2 && rows.stream().allMatch(CallLogStore.Entry::isFailure));
        assertTrue(entries.stream().anyMatch(e -> e.httpStatus == 401));
        assertTrue(entries.stream().anyMatch(e -> "io".equals(e.error)));
        assertTrue(entries.stream().noneMatch(CallLogStore.Entry::isSuccess));
    }
    @Test public void testAndCatalogueSuccessKeepTheirActualHttpStatusAndLabels() throws Exception {
        reporter.begin("test", "test").success(201);
        reporter.begin("module", "models").success(200);
        List<CallLogStore.Entry> entries = await(rows -> rows.size() == 2 && rows.stream().allMatch(CallLogStore.Entry::isSuccess));
        assertTrue(entries.stream().anyMatch(e -> e.httpStatus == 201 && "测试成功".equals(e.stateLabel())));
        assertTrue(entries.stream().anyMatch(e -> e.httpStatus == 200 && "模型列表".equals(e.stageLabel()) && "获取成功".equals(e.stateLabel())));
    }
    @Test public void missingStatusIsRecordedAsUnknownInsteadOfStayingPendingOrClaimingSuccess() throws Throwable {
        NativeCallLoggingTest.Chain call = new NativeCallLoggingTest.Chain();
        NativeCallLoggingTest.handler(reporter, NativeCallLoggingTest.Response.class.getDeclaredField("hiddenCode"))
                .invoke(this, null, new Object[]{call});
        CallLogStore.Entry entry = await(rows -> rows.size() == 1 && "response".equals(rows.get(0).state)).get(0);
        assertEquals(0, entry.httpStatus); assertFalse(entry.isSuccess()); assertFalse(entry.isFailure());
        assertEquals("status_unavailable", entry.error); assertEquals("已收到响应（状态未知）", entry.stateLabel());
    }
}
