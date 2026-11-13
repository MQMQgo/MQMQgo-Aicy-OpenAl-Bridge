package io.github.aicybridge.hook;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRequestLoggingTest {
    public static final class Request {
        public String url = "https://example.test/v1/chat/completions";
        public String authorization = "Bearer test-key";
        int headerReads;
        public String header(String name) { assertEquals("Authorization", name); headerReads++; return authorization; }
    }
    public static final class Response { public int code = 200; private int hiddenCode = 200; }
    public static final class Call {
        public Request outgoing = new Request(); public Response incoming = new Response();
        boolean cancelled, metadataBroken;
        public Request getOriginalRequest() { if (metadataBroken) throw new IllegalStateException(); return outgoing; }
        public boolean isCanceled() { return cancelled; }
    }
    static final class Observer implements HttpCallObserver, HttpCallObserver.Trace {
        int began, status = -1, unavailable; Throwable failure; boolean cancelled, breakBegin, breakEnd;
        public Trace begin() { began++; if (breakBegin) throw new IllegalStateException(); return this; }
        public void response(int value) { if (breakEnd) throw new IllegalStateException(); status = value; }
        public void failure(Throwable value, boolean cancelled) { if (breakEnd) throw new IllegalStateException(); failure = value; this.cancelled = cancelled; }
        public void unavailable() { unavailable++; }
    }
    static NativeRequestLogging logger(HttpCallObserver observer) throws Exception {
        return logger(observer, "test-key", Response.class.getField("code"));
    }
    static NativeRequestLogging logger(HttpCallObserver observer, String key, Field status) throws Exception {
        return new NativeRequestLogging(null, Call.class.getMethod("getOriginalRequest"), Request.class.getField("url"),
                Request.class.getMethod("header", String.class), Call.class.getMethod("isCanceled"), status,
                new URI("https://example.test/v1/chat/completions"), key, observer);
    }
    @Test public void successfulResponseAndOriginalIdentityAreRecordedExactlyOnce() throws Throwable {
        Observer observer = new Observer(); Call call = new Call(); int[] proceeded = {0};
        assertSame(call.incoming, logger(observer).observe(call, () -> { proceeded[0]++; return call.incoming; }));
        assertEquals(1, proceeded[0]); assertEquals(1, observer.began); assertEquals(200, observer.status);
    }
    @Test public void otherDestinationsPathsPortsAndCredentialsAreNotObserved() throws Throwable {
        for (String url : new String[]{"https://other.test/v1/chat/completions", "https://example.test/v1/models",
                "http://example.test/v1/chat/completions", "https://example.test:444/v1/chat/completions"}) {
            Observer observer = new Observer(); Call call = new Call(); call.outgoing.url = url;
            assertSame(call.incoming, logger(observer).observe(call, () -> call.incoming));
            assertEquals(0, observer.began); assertEquals(0, call.outgoing.headerReads);
        }
        Observer observer = new Observer(); Call call = new Call(); call.outgoing.authorization = "Bearer other-key";
        logger(observer).observe(call, () -> call.incoming); assertEquals(0, observer.began);
    }
    @Test public void httpFailureAndNativeCheckedExceptionKeepTheirMeaning() throws Throwable {
        Observer observer = new Observer(); Call call = new Call(); call.incoming.code = 429;
        logger(observer).observe(call, () -> call.incoming); assertEquals(429, observer.status);
        IOException original = new IOException("private native error"); call.cancelled = true;
        try { logger(observer).observe(call, () -> { throw original; }); fail(); }
        catch (IOException result) { assertSame(original, result); }
        assertSame(original, observer.failure); assertTrue(observer.cancelled);
    }
    @Test public void observerFailuresDoNotChangeResponseOrNetworkException() throws Throwable {
        for (boolean failBegin : new boolean[]{true, false}) {
            Observer observer = new Observer(); observer.breakBegin = failBegin; observer.breakEnd = !failBegin;
            Call call = new Call(); NativeRequestLogging logger = logger(observer);
            assertSame(call.incoming, logger.observe(call, () -> call.incoming));
            IOException original = new IOException("original failure");
            try { logger.observe(call, () -> { throw original; }); fail(); }
            catch (IOException result) { assertSame(original, result); }
        }
    }
    @Test public void unavailableStatusStillFinishesObservationWithoutInventingSuccess() throws Throwable {
        Observer observer = new Observer(); Call call = new Call();
        logger(observer, "test-key", Response.class.getDeclaredField("hiddenCode")).observe(call, () -> call.incoming);
        assertEquals(0, observer.status);
        call.incoming.code = 999;
        logger(observer).observe(call, () -> call.incoming); assertEquals(0, observer.status);
    }
    @Test public void metadataFailuresReportOnceAndKeepTheOriginalCallWorking() throws Throwable {
        Observer observer = new Observer(); Call call = new Call(); call.metadataBroken = true;
        NativeRequestLogging logger = logger(observer); int[] proceeded = {0};
        for (int i = 0; i < 2; i++) assertSame(call.incoming, logger.observe(call, () -> { proceeded[0]++; return call.incoming; }));
        assertEquals(2, proceeded[0]); assertEquals(0, observer.began); assertEquals(1, observer.unavailable);
    }
    @Test public void keylessProviderAndDefaultHttpsPortStillMatch() throws Throwable {
        Observer observer = new Observer(); Call call = new Call();
        call.outgoing.url = "https://EXAMPLE.test:443/v1/chat/completions"; call.outgoing.authorization = null;
        logger(observer, "", Response.class.getField("code")).observe(call, () -> call.incoming);
        assertEquals(200, observer.status); assertEquals(1, observer.began);
    }
}
