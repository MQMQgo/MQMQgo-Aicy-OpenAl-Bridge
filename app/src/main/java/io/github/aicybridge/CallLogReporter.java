package io.github.aicybridge;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Best-effort, bounded asynchronous reporting. Logging must never affect Aicy requests. */
public final class CallLogReporter implements io.github.aicybridge.hook.HttpCallObserver {
    private static final Uri URI = Uri.parse("content://io.github.aicybridge.settings");
    private static final ThreadPoolExecutor WORKER = worker();
    private final Context context;
    private final String host, model;

    public CallLogReporter(Context context, String url, String model, String apiKey) {
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
        this.host = CallLogPrivacy.host(url, apiKey);
        this.model = CallLogPrivacy.text(model, apiKey, 128);
    }

    public Trace begin(String source, String stage) {
        Trace trace = new Trace();
        Bundle values = new Bundle();
        values.putString("id", trace.id); values.putString("source", source); values.putString("stage", stage);
        values.putString("host", host); values.putString("model", model);
        values.putLong("started_at", System.currentTimeMillis());
        enqueue(() -> trace.accepted.set(send("call_log_start", values)));
        return trace;
    }

    @Override public Trace begin() { return begin("aicy", "http"); }

    public void diagnostic(String source, String stage, Throwable error) {
        try { begin(source, stage).failure(error, false); } catch (Throwable ignored) { }
    }
    @Override public void unavailable() {
        try { begin("module", "client_init").end("failed", 0, "logging_unavailable"); } catch (Throwable ignored) { }
    }

    public final class Trace implements io.github.aicybridge.hook.HttpCallObserver.Trace {
        private final String id = UUID.randomUUID().toString();
        private final long started = System.nanoTime();
        private final AtomicBoolean accepted = new AtomicBoolean();
        private final AtomicBoolean ended = new AtomicBoolean();

        public void response(int status) {
            try { end("response", status, status == 0 ? "status_unavailable" : status < 200 || status >= 300 ? "http" : ""); }
            catch (Throwable ignored) { }
        }
        public void success() { success(0); }
        public void success(int status) { try { end("succeeded", status, ""); } catch (Throwable ignored) { } }
        public void failure(Throwable error, boolean cancelled) {
            try {
                String code = CallLogPrivacy.error(error, cancelled);
                int status = error instanceof io.github.aicybridge.core.OpenAiException
                        ? ((io.github.aicybridge.core.OpenAiException) error).statusCode : 0;
                end("cancelled".equals(code) ? "cancelled" : "failed", status, code);
            } catch (Throwable ignored) { }
        }
        private void end(String state, int status, String error) {
            if (!ended.compareAndSet(false, true)) return;
            Bundle values = new Bundle();
            values.putString("id", id); values.putString("state", state); values.putString("error", error);
            values.putInt("http_status", status);
            values.putLong("elapsed_ms", Math.max(0, (System.nanoTime() - started) / 1_000_000));
            enqueue(() -> { if (accepted.get()) send("call_log_finish", values); });
        }
    }

    private boolean send(String method, Bundle values) {
        try {
            Bundle response = context.getContentResolver().call(URI, method, null, values);
            return response != null && response.getBoolean("ok", false);
        } catch (Throwable ignored) { return false; }
    }
    private static void enqueue(Runnable task) { try { WORKER.execute(task); } catch (Throwable ignored) { } }
    private static ThreadPoolExecutor worker() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(128), task -> {
                    Thread thread = new Thread(task, "AicyBridge-CallLogs"); thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.DiscardPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
