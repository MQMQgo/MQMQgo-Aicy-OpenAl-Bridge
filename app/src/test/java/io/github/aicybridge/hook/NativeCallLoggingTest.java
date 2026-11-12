package io.github.aicybridge.hook;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCallLoggingTest {
    public static final class Request { }
    public static final class Response { public int code = 200; }
    public static final class Call { public boolean isCanceled() { return true; } }
    public static final class Chain {
        final Request request = new Request(); final Response response = new Response();
        IOException failure; int calls;
        public Request request() { return request; }
        public Call call() { return new Call(); }
        public Response proceed(Request value) throws IOException {
            assertSame(request, value); calls++;
            if (failure != null) throw failure;
            return response;
        }
    }
    private static final class Observer implements HttpCallObserver, HttpCallObserver.Trace {
        int began, status; Throwable failure; boolean cancelled;
        boolean breakBegin, breakEnd;
        public Trace begin() { began++; if (breakBegin) throw new IllegalStateException(); return this; }
        public void response(int value) { if (breakEnd) throw new IllegalStateException(); status = value; }
        public void failure(Throwable error, boolean value) { if (breakEnd) throw new IllegalStateException(); failure = error; cancelled = value; }
        public void unavailable() { }
    }
    private InvocationHandler handler(Observer observer) throws Exception {
        return new NativeCallLogging(Chain.class.getMethod("request"), Chain.class.getMethod("proceed", Request.class),
                Chain.class.getMethod("call"), Call.class.getMethod("isCanceled"), Response.class.getField("code"), observer);
    }
    @Test public void originalRequestAndResponseArePassedThroughExactlyOnce() throws Throwable {
        Chain chain = new Chain(); Observer observer = new Observer(); chain.response.code = 429;
        assertSame(chain.response, handler(observer).invoke(this, null, new Object[]{chain}));
        assertEquals(1, chain.calls); assertEquals(1, observer.began); assertEquals(429, observer.status);
    }
    @Test public void nativeCheckedIOExceptionAndCancellationKeepTheirOriginalIdentity() throws Throwable {
        Chain chain = new Chain(); Observer observer = new Observer();
        chain.failure = new IOException("private native error");
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
}
