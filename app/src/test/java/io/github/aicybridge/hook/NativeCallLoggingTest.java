package io.github.aicybridge.hook;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCallLoggingTest {
    public static final class Request { }
    public static final class Response { public int code = 200; private int hiddenCode = 200; }
    public static final class Call { boolean cancelled; public boolean isCanceled() { return cancelled; } }
    public static final class Chain {
        final Request request = new Request(); final Response response = new Response();
        final Call call = new Call();
        IOException failure; int calls;
        public Request request() { return request; }
        public Call call() { return call; }
        public Response proceed(Request value) throws IOException {
            assertSame(request, value); calls++;
            if (failure != null) throw failure;
            return response;
        }
    }
    private static final class Observer implements HttpCallObserver, HttpCallObserver.Trace {
        int began, status = -1; Throwable failure; boolean cancelled;
        boolean breakBegin, breakEnd;
        public Trace begin() { began++; if (breakBegin) throw new IllegalStateException(); return this; }
        public void response(int value) { if (breakEnd) throw new IllegalStateException(); status = value; }
        public void failure(Throwable error, boolean value) { if (breakEnd) throw new IllegalStateException(); failure = error; cancelled = value; }
        public void unavailable() { }
    }
    static InvocationHandler handler(HttpCallObserver observer) throws Exception {
        return handler(observer, Response.class.getField("code"));
    }
    static InvocationHandler handler(HttpCallObserver observer, Field status) throws Exception {
        return new NativeCallLogging(Chain.class.getMethod("request"), Chain.class.getMethod("proceed", Request.class),
                Chain.class.getMethod("call"), Call.class.getMethod("isCanceled"), status, observer);
    }
    @Test public void originalRequestAndResponseArePassedThroughExactlyOnce() throws Throwable {
        Chain chain = new Chain(); Observer observer = new Observer(); chain.response.code = 429;
        assertSame(chain.response, handler(observer).invoke(this, null, new Object[]{chain}));
        assertEquals(1, chain.calls); assertEquals(1, observer.began); assertEquals(429, observer.status);
    }
    @Test public void nativeCheckedIOExceptionAndCancellationKeepTheirOriginalIdentity() throws Throwable {
        Chain chain = new Chain(); Observer observer = new Observer();
        chain.failure = new IOException("private native error");
        chain.call.cancelled = true;
        try { handler(observer).invoke(this, null, new Object[]{chain}); fail(); }
        catch (IOException original) { assertSame(chain.failure, original); }
        assertSame(chain.failure, observer.failure); assertTrue(observer.cancelled); assertEquals(1, chain.calls);
    }
    @Test public void loggingFailuresNeverBreakAValidNativeResponse() throws Throwable {
        for (boolean breakBegin : new boolean[]{true, false}) {
            Chain chain = new Chain(); Observer observer = new Observer();
            observer.breakBegin = breakBegin; observer.breakEnd = !breakBegin;
            assertSame(chain.response, handler(observer).invoke(this, null, new Object[]{chain}));
            assertEquals(1, chain.calls);
        }
    }
    @Test public void loggingFailuresNeverReplaceTheOriginalNativeException() throws Throwable {
        Chain chain = new Chain(); Observer observer = new Observer(); observer.breakEnd = true;
        chain.failure = new IOException("native failure");
        try { handler(observer).invoke(this, null, new Object[]{chain}); fail(); }
        catch (IOException original) { assertSame(chain.failure, original); }
        assertEquals(1, chain.calls);
    }
    @Test public void normalHttp200IsRecordedWithoutChangingTheResponse() throws Throwable {
        Chain chain = new Chain(); Observer observer = new Observer();
        assertSame(chain.response, handler(observer).invoke(this, null, new Object[]{chain}));
        assertEquals(1, chain.calls); assertEquals(1, observer.began); assertEquals(200, observer.status);
    }
    @Test public void unavailableStatusStillEndsObservationAsUnknown() throws Throwable {
        Chain chain = new Chain(); Observer observer = new Observer();
        InvocationHandler logging = handler(observer, Response.class.getDeclaredField("hiddenCode"));
        assertSame(chain.response, logging.invoke(this, null, new Object[]{chain}));
        assertEquals(0, observer.status);
        chain.response.code = 999;
        handler(observer).invoke(this, null, new Object[]{chain}); assertEquals(0, observer.status);
    }
}
