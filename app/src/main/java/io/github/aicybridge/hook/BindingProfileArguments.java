package io.github.aicybridge.hook;

import java.lang.reflect.Field;

/** Route credentials may change; native model identity must remain valid for metadata readers. */
final class BindingProfileArguments {
    private BindingProfileArguments() { }

    static Object[] forEndpoint(Object original, String endpoint, String apiKey) throws ReflectiveOperationException {
        return new Object[]{read(original, "a"), read(original, "b"), "openai-compatible", endpoint,
                read(original, "e"), apiKey, read(original, "g"), read(original, "h"), read(original, "i")};
    }

    private static Object read(Object value, String name) throws ReflectiveOperationException {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }
}
