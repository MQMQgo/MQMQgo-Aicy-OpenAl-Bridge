package io.github.aicybridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import java.security.MessageDigest;
import java.util.Locale;

/** Read-only bridge for the module and the verified Aicy package. */
public final class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.aicybridge.settings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    private ConfigStore store;
    private CallLogStore callLogs;

    @Override public boolean onCreate() {
        if (getContext() == null) return false;
        store = new ConfigStore(getContext());
        callLogs = new CallLogStore(getContext());
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        enforceCaller();
        if ("call_log_start".equals(method) || "call_log_finish".equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean("ok", "call_log_start".equals(method) ? callLogs.start(extras) : callLogs.finish(extras));
            return result;
        }
        if ("get_config".equals(method)) {
            ConfigStore.Settings settings = store.read();
            Bundle result = new Bundle();
            result.putBoolean("enabled", settings.enabled);
            result.putString("base_url", settings.baseUrl);
            result.putString("api_key", settings.enabled ? settings.apiKey : "");
            result.putString("model", settings.model);
            return result;
        }
        if ("report_status".equals(method)) {
            String code = extras == null ? null : extras.getString("status");
            if (!StatusText.isKnown(code)) throw new IllegalArgumentException("Unknown diagnostic code");
            Bundle result = new Bundle();
            result.putBoolean("ok", store.reportStatus(code));
            return result;
        }
        throw new IllegalArgumentException("Unknown settings operation");
    }

    @Override public void shutdown() {
        if (callLogs != null) callLogs.close();
        super.shutdown();
    }

    private void enforceCaller() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return;
        String expectedPackage = BuildConfig.TARGET_PACKAGE;
        String expectedCertificate = BuildConfig.TARGET_CERT_SHA256;
        if (expectedPackage.isEmpty() || expectedCertificate.isEmpty()) deny();
        if (!expectedPackage.equals(getCallingPackage())) deny();
        PackageManager pm = getContext().getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        boolean belongs = false;
        if (packages != null) for (String name : packages) if (expectedPackage.equals(name)) belongs = true;
        if (!belongs) deny();
        try {
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = pm.getPackageInfo(expectedPackage, flags);
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                if (info.signingInfo == null) { deny(); return; }
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                signatures = info.signatures;
            }
            // Require every current signer to match the pinned identity; a repackaged target is refused.
            if (signatures == null || signatures.length != 1) { deny(); return; }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder actual = new StringBuilder(64);
            for (byte value : digest) actual.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            if (!actual.toString().equalsIgnoreCase(expectedCertificate)) deny();
        } catch (PackageManager.NameNotFoundException | java.security.GeneralSecurityException e) {
            deny();
        }
    }

    private static void deny() { throw new SecurityException("Settings access denied"); }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException("Use call");
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
}
