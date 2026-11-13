package io.github.aicybridge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Bounded private history, independent of the saved API profile. */
public final class CallLogStore extends SQLiteOpenHelper {
    public static final int MAX_ENTRIES = 200;
    private static final Object DATABASE_OPEN_LOCK = new Object();
    private final android.content.SharedPreferences preferences;

    public CallLogStore(Context context) { this(context, "call-logs.db"); }
    CallLogStore(Context context, String database) {
        super(context, database, null, 1);
        preferences = context.getSharedPreferences("call_logging", Context.MODE_PRIVATE);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS calls (_id INTEGER PRIMARY KEY AUTOINCREMENT, call_id TEXT NOT NULL UNIQUE, "
                + "started_at INTEGER NOT NULL, source TEXT NOT NULL, stage TEXT NOT NULL, host TEXT NOT NULL, "
                + "model TEXT NOT NULL, state TEXT NOT NULL, http_status INTEGER NOT NULL DEFAULT 0, "
                + "elapsed_ms INTEGER NOT NULL DEFAULT -1, error TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS log_meta (name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO log_meta (name, value) VALUES ('cleared_before', 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported call log database version");
    }

    // Provider writes and UI reads use different helpers in the same process. Their first open
    // must not both observe user_version=0 before either helper finishes creating the schema.
    @Override public SQLiteDatabase getWritableDatabase() {
        synchronized (DATABASE_OPEN_LOCK) { return super.getWritableDatabase(); }
    }
    @Override public SQLiteDatabase getReadableDatabase() {
        synchronized (DATABASE_OPEN_LOCK) { return super.getReadableDatabase(); }
    }

    public boolean isEnabled() { return preferences.getBoolean("enabled", true); }
    public boolean setEnabled(boolean enabled) { return preferences.edit().putBoolean("enabled", enabled).commit(); }

    public boolean start(Bundle input) {
        if (!isEnabled() || input == null) return false;
        String id = input.getString("id", "");
        String source = input.getString("source", "");
        String stage = input.getString("stage", "");
        if (!validId(id) || !Arrays.asList("aicy", "test", "module").contains(source)
                || !Arrays.asList("http", "test", "models", "client_init", "request_body", "hook_init").contains(stage)) return false;
        long time = input.getLong("started_at", 0);
        if (time <= 0) return false;
        ContentValues values = new ContentValues();
        values.put("call_id", id);
        long recordedTime = Math.min(time, System.currentTimeMillis());
        values.put("started_at", recordedTime);
        values.put("source", source);
        values.put("stage", stage);
        values.put("host", CallLogPrivacy.text(input.getString("host", ""), "", 253));
        values.put("model", CallLogPrivacy.text(input.getString("model", ""), "", 128));
        values.put("state", "started");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long clearedBefore = android.database.DatabaseUtils.longForQuery(db,
                    "SELECT value FROM log_meta WHERE name = 'cleared_before'", null);
            if (recordedTime <= clearedBefore) {
                db.setTransactionSuccessful();
                return false;
            }
            boolean inserted = db.insertWithOnConflict("calls", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
            db.execSQL("DELETE FROM calls WHERE _id NOT IN (SELECT _id FROM calls ORDER BY started_at DESC, _id DESC LIMIT " + MAX_ENTRIES + ")");
            db.setTransactionSuccessful();
            return inserted;
        } finally { db.endTransaction(); }
    }

    public boolean finish(Bundle input) {
        if (input == null || !validId(input.getString("id", ""))) return false;
        String state = input.getString("state", "");
        String error = input.getString("error", "");
        int status = input.getInt("http_status", 0);
        if (!Arrays.asList("response", "succeeded", "failed", "cancelled").contains(state)
                || !CallLogPrivacy.knownError(error) || status != 0 && (status < 100 || status > 599)) return false;
        ContentValues values = new ContentValues();
        values.put("state", state);
        values.put("error", error);
        values.put("http_status", status);
        values.put("elapsed_ms", Math.max(0, Math.min(input.getLong("elapsed_ms", 0), 86_400_000)));
        // Disabling logging stops new entries; already recorded requests can still finish.
        return getWritableDatabase().update("calls", values, "call_id = ? AND state = 'started'",
                new String[]{input.getString("id")}) > 0;
    }

    public List<Entry> read() {
        ArrayList<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("calls",
                new String[]{"call_id", "started_at", "source", "stage", "host", "model", "state", "http_status", "elapsed_ms", "error"},
                null, null, null, null, "started_at DESC, _id DESC", String.valueOf(MAX_ENTRIES))) {
            while (c.moveToNext()) result.add(new Entry(c));
        }
        return result;
    }

    public void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE log_meta SET value = ? WHERE name = 'cleared_before'", new Object[]{System.currentTimeMillis()});
            db.delete("calls", null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static boolean validId(String value) {
        try { return value.equals(UUID.fromString(value).toString()); }
        catch (RuntimeException invalid) { return false; }
    }

    public static final class Entry {
        public final String id, source, stage, host, model, state, error;
        public final long startedAt, elapsedMs;
        public final int httpStatus;
        Entry(Cursor c) {
            id = c.getString(0); startedAt = c.getLong(1); source = c.getString(2); stage = c.getString(3);
            host = c.getString(4); model = c.getString(5); state = c.getString(6); httpStatus = c.getInt(7);
            elapsedMs = c.getLong(8); error = c.getString(9);
        }
        public boolean isFailure() { return "failed".equals(state) || httpStatus != 0 && (httpStatus < 200 || httpStatus >= 300); }
        public boolean isSuccess() { return "succeeded".equals(state) || "response".equals(state) && httpStatus >= 200 && httpStatus < 300; }
        public String sourceLabel() { return "test".equals(source) ? "连接测试" : "module".equals(source) ? "模块" : "Aicy"; }
        public String stageLabel() {
            switch (stage) {
                case "models": return "模型列表";
                case "client_init": return "客户端创建";
                case "request_body": return "请求准备";
                case "hook_init": return "模块初始化";
                default: return "API 请求";
            }
        }
        public String stateLabel() {
            switch (state) {
                case "response": return httpStatus == 0 ? "已收到响应（状态未知）" : httpStatus >= 200 && httpStatus < 300 ? "已收到响应" : "HTTP 响应异常";
                case "succeeded": return "models".equals(stage) ? "获取成功" : "test".equals(source) ? "测试成功" : "请求成功";
                case "failed": return "调用失败";
                case "cancelled": return "已取消";
                default: return "等待响应";
            }
        }
    }
}
