package io.github.aicybridge.hook;

import io.github.aicybridge.core.OpenAiClient;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/** Observe the real execution path; no injected interceptor, body access, or response wrapping. */
final class NativeRequestLogging {
    interface Original { Object proceed() throws Throwable; }
    final Method execution;
    private final Method request, header, cancelled;
    private final Field url, status;
    private final URI endpoint;
    private final String apiKey;
    private final HttpCallObserver observer;
    private final AtomicBoolean reportedUnavailable = new AtomicBoolean();

    NativeRequestLogging(Method execution, Method request, Field url, Method header, Method cancelled, Field status,
                         URI endpoint, String apiKey, HttpCallObserver observer) {
        this.execution = execution; this.request = request; this.url = url; this.header = header;
        this.cancelled = cancelled; this.status = status; this.endpoint = endpoint; this.apiKey = apiKey; this.observer = observer;
    }

    static NativeRequestLogging create(ClassLoader loader, String prefix, String apiKey, HttpCallObserver observer) throws Exception {
        Class<?> call = Class.forName("okhttp3.internal.connection.RealCall", false, loader);
        Class<?> request = Class.forName("bi.Y", false, loader);
        Class<?> response = Class.forName("bi.h0", false, loader);
        Method execution = call.getDeclaredMethod("getResponseWithInterceptorChain$okhttp");
        Method original = call.getDeclaredMethod("getOriginalRequest");
        Method header = request.getDeclaredMethod("b", String.class);
        Method cancelled = call.getDeclaredMethod("isCanceled");
        Field url = request.getDeclaredField("a"), status = response.getDeclaredField("d");
        if (execution.getReturnType() != response || original.getReturnType() != request
                || !url.getType().getName().equals("bi.L") || status.getType() != int.class
                || header.getReturnType() != String.class || cancelled.getReturnType() != boolean.class) {
            throw new NoSuchMethodException("Native request logging ABI changed");
        }
        for (Method method : new Method[]{execution, original, header, cancelled}) method.setAccessible(true);
        url.setAccessible(true); status.setAccessible(true);
        return new NativeRequestLogging(execution, original, url, header, cancelled, status,
                OpenAiClient.chatCompletionsUrl(prefix).toURI(), apiKey, observer);
    }

    Object observe(Object call, Original original) throws Throwable {
        if (!matches(call)) return original.proceed();
        HttpCallObserver.Trace trace = null;
        try { trace = observer.begin(); } catch (Throwable ignored) { }
        Object response;
        try { response = original.proceed(); }
        catch (Throwable failure) {
            boolean wasCancelled = false;
            try { wasCancelled = Boolean.TRUE.equals(invoke(cancelled, call)); } catch (Throwable ignored) { }
            try { if (trace != null) trace.failure(failure, wasCancelled); } catch (Throwable ignored) { }
            throw failure;
        }
        int code = 0;
        try { int value = status.getInt(response); if (value >= 100 && value <= 599) code = value; } catch (Throwable ignored) { }
        // Even unavailable metadata must finish the row; zero never claims HTTP success.
        try { if (trace != null) trace.response(code); } catch (Throwable ignored) { }
        return response;
    }

    private boolean matches(Object call) {
        try {
            Object outgoing = invoke(request, call);
            URI actual = new URI(url.get(outgoing).toString());
            if (!"https".equalsIgnoreCase(actual.getScheme()) || actual.getRawUserInfo() != null
                    || !endpoint.getHost().equalsIgnoreCase(actual.getHost()) || port(endpoint) != port(actual)
                    || !endpoint.normalize().getRawPath().equals(actual.normalize().getRawPath())) return false;
            String auth = (String) invoke(header, outgoing, "Authorization");
            if (apiKey.isEmpty()) return auth == null || auth.isEmpty() || "Bearer".equalsIgnoreCase(auth.trim());
            return auth != null && auth.length() >= 7 && auth.regionMatches(true, 0, "Bearer ", 0, 7)
                    && apiKey.equals(auth.substring(7));
        } catch (Throwable unavailable) {
            if (reportedUnavailable.compareAndSet(false, true)) {
                try { observer.unavailable(); } catch (Throwable ignored) { }
            }
            return false;
        }
    }

    private static int port(URI value) { return value.getPort() < 0 ? 443 : value.getPort(); }
    private static Object invoke(Method method, Object receiver, Object... args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
    }
}
