package io.github.aicybridge;

import android.content.Context;
import android.os.Bundle;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class CallLogStoreTest {
    private Context context;
    private CallLogStore store;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("call-log-regression.db");
        context.getSharedPreferences("call_logging", Context.MODE_PRIVATE).edit().clear().commit();
        store = new CallLogStore(context, "call-log-regression.db");
    }
    @After public void close() { store.close(); }

    @Test public void completedMetadataPersistsAfterClosingAndReopeningTheStore() {
        Bundle started = start("safe-model");
        started.putString("api_key", "private-key-must-not-be-stored");
        started.putString("prompt", "private-prompt-must-not-be-stored");
        assertTrue(store.start(started));
        assertTrue(store.finish(end(started, "response", 200, "")));
        store.close();
        store = new CallLogStore(context, "call-log-regression.db");
        CallLogStore.Entry entry = store.read().get(0);
        assertEquals("safe-model", entry.model);
        assertEquals("example.test", entry.host);
        assertEquals("response", entry.state);
        assertEquals(200, entry.httpStatus);
        assertEquals(123, entry.elapsedMs);
        try (android.database.Cursor c = store.getReadableDatabase().rawQuery("SELECT * FROM calls", null)) {
            assertTrue(c.moveToFirst());
            for (int i = 0; i < c.getColumnCount(); i++) {
                String value = c.getString(i);
                assertFalse(value != null && value.contains("must-not-be-stored"));
            }
        }
    }

    @Test public void historyIsBoundedAndKeepsTheMostRecentEntries() {
        for (int i = 0; i < CallLogStore.MAX_ENTRIES + 5; i++) {
            Bundle value = start("model-" + i);
            value.putLong("started_at", 1_700_000_000_000L + i);
            assertTrue(store.start(value));
        }
        List<CallLogStore.Entry> entries = store.read();
        assertEquals(CallLogStore.MAX_ENTRIES, entries.size());
        assertEquals("model-204", entries.get(0).model);
        assertEquals("model-5", entries.get(entries.size() - 1).model);
    }

    @Test public void disablingStopsNewCallsButAllowsExistingCallsToFinish() {
        Bundle original = start("in-flight");
        assertTrue(store.start(original));
        assertTrue(store.setEnabled(false));
        assertFalse(store.start(start("disabled-call")));
        assertTrue(store.finish(end(original, "response", 200, "")));
        assertEquals(1, store.read().size());
        assertEquals("response", store.read().get(0).state);
        store.close(); store = new CallLogStore(context, "call-log-regression.db");
        assertFalse(store.isEnabled());
    }

    @Test public void lateFinishesDoNotRecreateClearedRowsOrOverwriteFinalResults() {
        Bundle first = start("first");
        assertTrue(store.start(first));
        assertFalse(store.start(first));
        assertTrue(store.finish(end(first, "response", 200, "")));
        assertFalse(store.finish(end(first, "failed", 500, "http")));
        assertEquals(200, store.read().get(0).httpStatus);
        store.clear();
        assertFalse(store.finish(end(first, "response", 200, "")));
        Bundle delayedStart = start("queued-before-clear");
        delayedStart.putLong("started_at", first.getLong("started_at"));
        assertFalse(store.start(delayedStart));
        assertTrue(store.read().isEmpty());
    }

    @Test public void invalidEventsAndFreeFormErrorTextAreRejected() {
        Bundle bad = start("invalid"); bad.putString("id", "not-a-uuid"); assertFalse(store.start(bad));
        bad = start("invalid"); bad.putString("source", "arbitrary-source"); assertFalse(store.start(bad));
        Bundle original = start("valid"); assertTrue(store.start(original));
        assertFalse(store.finish(end(original, "failed", 401, "raw-server-message-with-key")));
        assertFalse(store.finish(end(original, "failed", 999, "http")));
        assertEquals("started", store.read().get(0).state);
    }

    @Test public void outOfOrderResponsesUpdateOnlyTheirOwnRequestAndRedirectsAreFailures() {
        Bundle a = start("a"), b = start("b");
        store.start(a); store.start(b);
        store.finish(end(b, "response", 302, "http"));
        store.finish(end(a, "response", 200, ""));
        for (CallLogStore.Entry entry : store.read()) {
            if ("a".equals(entry.model)) { assertEquals(200, entry.httpStatus); assertFalse(entry.isFailure()); }
            else { assertEquals(302, entry.httpStatus); assertTrue(entry.isFailure()); }
        }
    }

    public static Bundle start(String model) {
        Bundle values = new Bundle();
        values.putString("id", UUID.randomUUID().toString());
        values.putLong("started_at", System.currentTimeMillis());
        values.putString("source", "aicy"); values.putString("stage", "http");
        values.putString("host", "example.test"); values.putString("model", model);
        return values;
    }
    public static Bundle end(Bundle start, String state, int status, String error) {
        Bundle values = new Bundle(); values.putString("id", start.getString("id"));
        values.putString("state", state); values.putString("error", error);
        values.putInt("http_status", status); values.putLong("elapsed_ms", 123);
        return values;
    }
}
