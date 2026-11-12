package io.github.aicybridge.hook;

/** Optional metadata observation; implementations must not inspect request or response bodies. */
public interface HttpCallObserver {
    Trace begin();
    void unavailable();
    interface Trace {
        void response(int status);
        void failure(Throwable error, boolean cancelled);
    }
}
