package io.github.aicybridge.hook;

import android.content.Context;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.json.JSONObject;
import io.github.aicybridge.core.ChatRequestSanitizer;
import io.github.aicybridge.core.OpenAiClient;
import io.github.aicybridge.CallLogReporter;
import io.github.libxposed.api.XposedInterface.ExceptionMode;

/** Exact descriptors verified against the supplied 13.1.1 APK; no vendor code is bundled. */
public final class AicyHooks {
    private static final String OPENAI = "com.flyme.agentos.llm.impl.openai.";
    private static final int MAX_SERIALIZED_BYTES = 32 * 1024 * 1024;
    private AicyHooks() { }

    public static void install(BridgeModule module, Context context, ClassLoader loader) throws Throwable {
        Bundle settings = BridgeModule.readConfig(context);
        if (!settings.getBoolean("enabled", false)) {
            BridgeModule.reportStatus(context, "bridge_disabled");
            return;
        }
        // Aicy caches native bindings; all settings deliberately take effect after process restart.
        OpenAiClient.Config validated = new OpenAiClient.Config(settings.getString("base_url", ""),
                settings.getString("api_key", ""), settings.getString("model", ""));
        String full = validated.endpoint.toString();
        String prefix = full.substring(0, full.length() - "/chat/completions".length());
        new Adapter(module, context, loader, prefix, settings.getString("api_key", ""), validated.model).install();
    }

    private static final class Adapter {
        final BridgeModule module;
        final Context context;
        final ClassLoader loader;
        final String endpoint;
        final String key;
        final String model;
        final DelegateScope delegateScope = new DelegateScope();
        final CallLogReporter callLogs;
        final Map<Object, Object> ownedStreaming = Collections.synchronizedMap(new WeakHashMap<>());
        volatile boolean active;

        Adapter(BridgeModule module, Context context, ClassLoader loader, String endpoint, String key, String model) {
            this.module = module; this.context = context; this.loader = loader;
            this.endpoint = endpoint; this.key = key; this.model = model;
            this.callLogs = new CallLogReporter(context, endpoint, model, key);
        }

        void install() throws Throwable {
            Class<?> config = type(OPENAI + "l");
            Class<?> client = type(OPENAI + "k");
            Class<?> stream = type(OPENAI + "f");
            Class<?> timeouts = type("com.flyme.agentos.core.network.m");
            Class<?> profile = type("C6.L");
            Class<?> kind = type("com.flyme.agentos.core.ModelClientKind");
            Class<?> function = type("com.flyme.agentos.core.ModelFunction");
            Class<?> snapshot = type("W6.f");
            Class<?> validation = type("W6.u");
            Class<?> capabilities = type("Y6.b0");
            Class<?> router = type("com.flyme.agentos.llm.impl.routing.o");
            Class<?> codec = type("D9.c");
            Class<?> request = type("Y6.i");
            Class<?> support = type(OPENAI + "n");
            Class<?> delegateFactory = type("com.flyme.agentos.llm.impl.routing.LlmBindings$LlmClientBinding$delegateSlot$2");
            Constructor<?> profileCtor = constructor(profile, String.class, kind, String.class, String.class,
                    String.class, String.class, Integer.class, Set.class, Map.class);
            Constructor<?> validationCtor = constructor(validation, boolean.class, String.class, Set.class, String.class);
            Constructor<?> snapshotCtor = constructor(snapshot, profile, timeouts, type("C6.O"), type("C6.I"), validation, capabilities);
            Constructor<?> configCtor = constructor(config, String.class, String.class, String.class, capabilities,
                    String.class, timeouts, type(OPENAI + "y"));
            Constructor<?> streamCtor = constructor(stream, timeouts, type("kotlin.jvm.functions.Function1"));
            Constructor<?> clientCtor = constructor(client, config, type("io.ktor.client.b"), stream,
                    type("com.flyme.agentos.agent.core.telemetry.b"), type("Z6.a"));
            Method bindingSnapshot = method(router, "c", function, String.class);
            Method createDelegate = methodReturning(delegateFactory, "invoke", type(OPENAI + "g"));
            Method createDelegateBridge = methodReturning(delegateFactory, "invoke", Object.class);
            Method writeRequest = method(codec, "Q", OutputStream.class, request, boolean.class);
            Method headers = method(support, "a", type("Qg.c"), Map.class);
            Method close = method(client, "close");
            Field streamFactory = field(stream, "b");
            Field streamHttpClient = field(stream, "d");
            Field clientStream = field(client, "g");
            Field codecConfig = field(codec, "b");
            Field httpConfig = field(support, "a");
            // Resolve all reflected members before registering any active mutation.
            for (String name : new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i"}) field(profile, name);
            for (String name : new String[]{"a", "b", "c", "d", "e", "f"}) field(snapshot, name);
            for (String name : new String[]{"a", "b", "c", "d", "e", "f", "g", "h"}) field(config, name);
            NativeHttpClients.verify(loader);

            try {
                NativeRequestLogging requests = NativeRequestLogging.create(loader, endpoint, key, callLogs);
                module.hook(requests.execution).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.native.request-log").intercept(chain -> {
                    if (!active) return chain.proceed();
                    return requests.observe(chain.getThisObject(), () -> chain.proceed());
                });
            } catch (Throwable unavailable) { callLogs.unavailable(); }

            module.hook(method(config, "toString")).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.redact.config").intercept(chain -> {
                if (active && ownedConfig(chain.getThisObject())) return "OpenAiCompatibleConfig(<private API settings>)";
                return chain.proceed();
            });
            module.hook(method(profile, "toString")).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.redact.profile").intercept(chain -> {
                Object value = chain.getThisObject();
                if (active && endpoint.equals(get(value, "d")) && key.equals(get(value, "f"))) {
                    return "ModelProfileConfig(<private API settings>)";
                }
                return chain.proceed();
            });
            module.hook(clientCtor).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.native.client").intercept(chain -> {
                if (!active || !delegateScope.isActive()) return chain.proceed();
                Object old = chain.getArg(0);
                Object oldStream = chain.getArg(2);
                if (oldStream == null) throw new IllegalStateException("Aicy OpenAI streaming factory is unavailable");
                Object pureClient = null;
                try {
                    Object ownConfig = configCtor.newInstance(key, model, endpoint, get(old, "d"), "openai-compatible",
                            get(old, "g"), get(old, "h"));
                    pureClient = NativeHttpClients.create(loader);
                    Object ownStream = streamCtor.newInstance(get(old, "g"), streamFactory.get(oldStream));
                    Object result = chain.proceed(new Object[]{ownConfig, pureClient, ownStream, null, chain.getArg(4)});
                    Object receiver = chain.getThisObject();
                    Object installedStream = clientStream.get(receiver);
                    if (installedStream == null) throw new IllegalStateException("Aicy did not create a streaming client");
                    // Retain the native reference count and close behavior, with an independent transport.
                    streamHttpClient.set(installedStream, pureClient);
                    ownedStreaming.put(receiver, installedStream);
                    return result;
                } catch (Throwable error) {
                    NativeHttpClients.close(pureClient);
                    callLogs.diagnostic("aicy", "client_init", error);
                    BridgeModule.reportStatus(context, "request_failed");
                    throw error;
                }
            });
            module.hook(close).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.native.close").intercept(chain -> {
                Object result;
                try { result = chain.proceed(); }
                finally {
                    Object streamValue = ownedStreaming.remove(chain.getThisObject());
                    if (streamValue != null) method(streamValue.getClass(), "close").invoke(streamValue);
                }
                return result;
            });
            module.hook(headers).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.native.headers").intercept(chain -> {
                if (active && ownedConfig(httpConfig.get(chain.getThisObject()))) {
                    // Retain bearer/content type while omitting Flyme attribution headers.
                    return chain.proceed(new Object[]{chain.getArg(0), Collections.emptyMap()});
                }
                return chain.proceed();
            });
            module.hook(writeRequest).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.openai.body").intercept(chain -> {
                if (!active || !ownedConfig(codecConfig.get(chain.getThisObject()))) return chain.proceed();
                OutputStream destination = (OutputStream) chain.getArg(0);
                BoundedBuffer buffered = new BoundedBuffer();
                try {
                    Object result = chain.proceed(new Object[]{buffered, chain.getArg(1), chain.getArg(2)});
                    JSONObject json = new JSONObject(new String(buffered.toByteArray(), StandardCharsets.UTF_8));
                    JSONObject normalized = ChatRequestSanitizer.sanitize(json, model, (Boolean) chain.getArg(2));
                    destination.write(normalized.toString().getBytes(StandardCharsets.UTF_8));
                    BridgeModule.reportStatus(context, "bridge_enabled");
                    return result;
                } catch (Throwable error) {
                    callLogs.diagnostic("aicy", "request_body", error);
                    BridgeModule.reportStatus(context, "request_failed");
                    throw error;
                }
            });
            // Hook both Kotlin entry points, including the Function0 bridge used by the lazy slot.
            // Nested entry is intentional; the scope restores its previous state in finally.
            for (Method entry : new Method[]{createDelegate, createDelegateBridge}) {
                String id = entry == createDelegate ? "aicybridge.llm.delegate-scope.typed" : "aicybridge.llm.delegate-scope.bridge";
                module.hook(entry).setExceptionMode(ExceptionMode.PASSTHROUGH).setId(id).intercept(chain -> {
                    if (!active) return chain.proceed();
                    return delegateScope.during(chain::proceed);
                });
            }
            module.hook(bindingSnapshot).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.llm.profile").intercept(chain -> {
                Object result = chain.proceed();
                if (!active || !"com.flyme.agentos.llm.impl.routing.m".equals(chain.getThisObject().getClass().getName())) {
                    return result;
                }
                // Startup media tools index their limits by the native profile's model name.
                // Return that exact snapshot for metadata readers; replace it only inside the
                // synchronous delegate factory that turns a profile into an actual LLM client.
                return delegateScope.profile(result, () -> {
                    Object original = get(result, "a");
                    Object ownProfile = profileCtor.newInstance(get(original, "a"), get(original, "b"), "openai-compatible",
                            endpoint, model, key, get(original, "g"), get(original, "h"), get(original, "i"));
                    Object valid = validationCtor.newInstance(true, get(original, "a"), Collections.emptySet(), null);
                    return snapshotCtor.newInstance(ownProfile, get(result, "b"), get(result, "c"), get(result, "d"), valid, get(result, "f"));
                });
            });
            LocalToolSearch.install(module, loader);
            active = true;
            BridgeModule.reportStatus(context, "bridge_enabled");
        }

        boolean ownedConfig(Object value) throws ReflectiveOperationException {
            return value != null && value.getClass().getName().equals(OPENAI + "l")
                    && endpoint.equals(get(value, "c")) && key.equals(get(value, "a"));
        }

        Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
    }

    private static Constructor<?> constructor(Class<?> owner, Class<?>... arguments) throws NoSuchMethodException {
        Constructor<?> result = owner.getDeclaredConstructor(arguments); result.setAccessible(true); return result;
    }
    private static Method method(Class<?> owner, String name, Class<?>... arguments) throws NoSuchMethodException {
        Method result = owner.getDeclaredMethod(name, arguments); result.setAccessible(true); return result;
    }
    private static Method methodReturning(Class<?> owner, String name, Class<?> returnType) throws NoSuchMethodException {
        for (Method result : owner.getDeclaredMethods()) {
            if (name.equals(result.getName()) && result.getParameterTypes().length == 0 && result.getReturnType() == returnType) {
                result.setAccessible(true);
                return result;
            }
        }
        throw new NoSuchMethodException("Verified native delegate factory is unavailable");
    }
    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field result = owner.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static Object get(Object owner, String name) throws ReflectiveOperationException {
        return field(owner.getClass(), name).get(owner);
    }
    private static final class BoundedBuffer extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) {
            checkLimit(1); super.write(value);
        }
        @Override public synchronized void write(byte[] value, int offset, int length) {
            if (offset < 0 || length < 0 || offset > value.length - length) throw new IndexOutOfBoundsException();
            checkLimit(length); super.write(value, offset, length);
        }
        private void checkLimit(int added) {
            if ((long) count + added > MAX_SERIALIZED_BYTES) throw new IllegalStateException("Chat request exceeds 32 MiB");
        }
    }
}
