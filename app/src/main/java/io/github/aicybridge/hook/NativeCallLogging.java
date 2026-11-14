package io.github.aicybridge.hook;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Transparent application interceptor, attached only to the module's isolated native transport. */
final class NativeCallLogging implements InvocationHandler {
    private final Method request, proceed, call, cancelled;
    private final Field status;
    private final HttpCallObserver observer;

    NativeCallLogging(Method request, Method proceed, Method call, Method cancelled, Field status, HttpCallObserver observer) {
        this.request = request; this.proceed = proceed; this.call = call; this.cancelled = cancelled;
        this.status = status; this.observer = observer;
    }

    static void attach(ClassLoader loader, Object builder, HttpCallObserver observer) throws ReflectiveOperationException {
        Class<?> interceptor = Class.forName("bi.N", false, loader);
        Class<?> chain = Class.forName("bi.M", false, loader);
        Class<?> request = Class.forName("bi.Y", false, loader);
        Class<?> response = Class.forName("bi.h0", false, loader);
        Class<?> call = Class.forName("bi.m", false, loader);
        Method intercept = interceptor.getDeclaredMethod("intercept", chain);
        Method proceed = chain.getDeclaredMethod("proceed", request);
        Field code = response.getDeclaredField("d"); code.setAccessible(true);
        if (!interceptor.isInterface() || intercept.getReturnType() != response
                || proceed.getReturnType() != response || code.getType() != int.class) {
            throw new NoSuchMethodException("Native logging ABI changed");
        }
        Method outgoing = chain.getDeclaredMethod("request"), currentCall = chain.getDeclaredMethod("call"), cancelled = call.getDeclaredMethod("isCanceled");
        for (Method method : new Method[]{outgoing, proceed, currentCall, cancelled}) method.setAccessible(true);
        InvocationHandler handler = new NativeCallLogging(outgoing, proceed, currentCall, cancelled, code, observer);
        Object proxy = NativeLogBridge.type(loader).getConstructor(InvocationHandler.class).newInstance(handler);
        if (!interceptor.isInstance(proxy)) throw new IllegalStateException("Native log bridge type mismatch");
        Method add = builder.getClass().getDeclaredMethod("a", interceptor); add.setAccessible(true);
        add.invoke(builder, proxy);
    }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method != null && method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "toString": return "AicyBridgeCallLogger";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return args != null && args.length == 1 && proxy == args[0];
                default: return null;
            }
        }
        if (method != null && !"intercept".equals(method.getName()) || args == null || args.length != 1) {
            throw new UnsupportedOperationException("Unexpected native interceptor method");
        }
        Object chain = args[0];
        Object outgoing = invokeNative(request, chain);
        HttpCallObserver.Trace trace = null;
        try { trace = observer.begin(); } catch (Throwable ignored) { }
        Object incoming;
        try {
            incoming = invokeNative(proceed, chain, outgoing);
        } catch (Throwable original) {
            boolean wasCancelled = false;
            try { wasCancelled = Boolean.TRUE.equals(invokeNative(cancelled, invokeNative(call, chain))); }
            catch (Throwable ignored) { }
            try { if (trace != null) trace.failure(original, wasCancelled); } catch (Throwable ignored) { }
            throw original;
        }
        int code = 0;
        try { int value = status.getInt(incoming); if (value >= 100 && value <= 599) code = value; }
        catch (Throwable ignored) { }
        try { if (trace != null) trace.response(code); }
        catch (Throwable ignored) { }
        return incoming;
    }

    private static Object invokeNative(Method method, Object receiver, Object... args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
    }
}
