package io.github.aicybridge.hook;

/** Custom profiles belong only to synchronous native LLM delegate creation, never app metadata. */
final class DelegateScope {
    private final ThreadLocal<Boolean> creating = new ThreadLocal<>();

    boolean isActive() { return Boolean.TRUE.equals(creating.get()); }

    <T> T during(Call<T> call) throws Throwable {
        Boolean previous = creating.get();
        creating.set(true);
        try {
            return call.run();
        } finally {
            if (previous == null) creating.remove();
            else creating.set(previous);
        }
    }

    <T> T profile(T original, Call<T> custom) throws Throwable {
        return isActive() ? custom.run() : original;
    }

    interface Call<T> { T run() throws Throwable; }
}
