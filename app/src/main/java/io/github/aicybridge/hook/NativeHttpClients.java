package io.github.aicybridge.hook;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/** Creates an isolated native Ktor/OkHttp client for the verified Aicy APK. */
public final class NativeHttpClients {
    private NativeHttpClients() { }

    /** Resolve the complete version-specific ABI without allocating an HTTP engine. */
    public static void verify(ClassLoader loader) throws ReflectiveOperationException {
        new Layout(loader);
    }

    /** Best-effort cleanup when a constructor fails; never mask the original failure. */
    public static void close(Object client) {
        if (client == null) return;
        try {
            if (client instanceof AutoCloseable) ((AutoCloseable) client).close();
            else client.getClass().getMethod("close").invoke(client);
        } catch (Exception ignored) {
            // No exception text: a transport failure can contain request or credential values.
        }
    }

    /**
     * Uses the target's own Ktor factory with a fresh configuration, without Flyme auth,
     * shared engines, cookies, or app request plugins. Redirects are disabled in both layers.
     * The caller owns the returned client and must retain or close it deliberately.
     */
    public static Object create(ClassLoader loader) throws Throwable {
        Layout abi = new Layout(loader);
        Object unit = Class.forName("kotlin.Unit", true, loader).getField("INSTANCE").get(null);
        Object configureBuilder = callback(loader, abi.function, unit, value -> {
            if (!abi.builder.isInstance(value)) throw new IllegalArgumentException("Unexpected OkHttp builder");
            abi.okHttpRedirects.setBoolean(value, false);
            abi.okHttpSslRedirects.setBoolean(value, false);
            if (abi.okHttpRedirects.getBoolean(value) || abi.okHttpSslRedirects.getBoolean(value)) {
                throw new IllegalStateException("Native OkHttp redirects remain enabled");
            }
        });
        Object configureTransport = callback(loader, abi.function, unit, value -> {
            if (!abi.engineConfig.isInstance(value)) throw new IllegalArgumentException("Unexpected native engine config");
            // Set only the native transport callback; never copy the original auth factory callback.
            abi.configureOkHttp.set(value, configureBuilder);
        });
        Object configureClient = callback(loader, abi.function, unit, value -> {
            if (!abi.config.isInstance(value)) throw new IllegalArgumentException("Unexpected native client config");
            abi.followRedirects.setBoolean(value, false);
            abi.configureEngine.set(value, configureTransport);
        });

        try {
            return abi.create.invoke(null, abi.factoryInstance.get(null), configureClient);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static final class Layout {
        final Class<?> function, config, engineConfig, builder;
        final Field followRedirects, configureEngine, configureOkHttp;
        final Field okHttpRedirects, okHttpSslRedirects, factoryInstance;
        final Method create;

        Layout(ClassLoader loader) throws ReflectiveOperationException {
            if (loader == null) throw new IllegalArgumentException("Target class loader is missing");
            function = Class.forName("kotlin.jvm.functions.Function1", false, loader);
            config = Class.forName("io.ktor.client.c", false, loader);
            engineConfig = Class.forName("io.ktor.client.engine.okhttp.c", false, loader);
            builder = Class.forName("bi.U", false, loader);
            Class<?> engineFactory = Class.forName("io.ktor.client.engine.okhttp.a", false, loader);
            Class<?> factories = Class.forName("io.ktor.client.f", false, loader);
            Class<?> client = Class.forName("io.ktor.client.b", false, loader);
            Class.forName("kotlin.Unit", false, loader).getField("INSTANCE");
            followRedirects = field(config, "e", boolean.class);
            configureEngine = field(config, "d", function);
            configureOkHttp = field(engineConfig, "a", function);
            okHttpRedirects = field(builder, "i", boolean.class);
            okHttpSslRedirects = field(builder, "j", boolean.class);
            factoryInstance = field(engineFactory, "a", engineFactory);
            create = factories.getDeclaredMethod("a", engineFactory, function);
            if (!Modifier.isStatic(factoryInstance.getModifiers()) || !Modifier.isStatic(create.getModifiers())
                    || create.getReturnType() != client) {
                throw new NoSuchMethodException("Unexpected native Ktor factory shape");
            }
            client.getMethod("close");
            create.setAccessible(true);
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> expected) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != expected) throw new NoSuchFieldException("Unexpected native field shape");
        field.setAccessible(true);
        return field;
    }

    private interface Configure {
        void apply(Object value) throws Throwable;
    }

    private static Object callback(ClassLoader loader, Class<?> function, Object unit, Configure configure) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString": return "AicyBridgeNativeTransportConfig";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return args != null && args.length == 1 && proxy == args[0];
                    default: throw new UnsupportedOperationException("Unexpected Object method");
                }
            }
            if (!"invoke".equals(method.getName()) || args == null || args.length != 1) {
                throw new UnsupportedOperationException("Unexpected native configuration callback");
            }
            configure.apply(args[0]);
            return unit;
        };
        return Proxy.newProxyInstance(loader, new Class<?>[]{function}, handler);
    }
}
