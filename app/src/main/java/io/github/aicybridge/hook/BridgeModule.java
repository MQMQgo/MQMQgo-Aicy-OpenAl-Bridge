package io.github.aicybridge.hook;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Modern libxposed API 102 entry, restricted to the verified Aicy release. */
public final class BridgeModule extends XposedModule {
    public static final String TARGET_PACKAGE = "com.meizu.voiceassistant";
    public static final long TARGET_VERSION_CODE = 13001001L;
    public static final String TARGET_VERSION_NAME = "13.1.1";
    private static final Uri PROVIDER_URI = Uri.parse("content://io.github.aicybridge.settings");
    private static final String TAG = "AicyBridge";

    private final AtomicBoolean attachInstalled = new AtomicBoolean();
    private final AtomicBoolean adapterInstalled = new AtomicBoolean();
    private String processName;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())
                || !TARGET_PACKAGE.equals(processName)
                || !param.isFirstPackage()
                || getApiVersion() < 102
                || !attachInstalled.compareAndSet(false, true)) {
            return;
        }
        try {
            final ClassLoader loader = param.getClassLoader();
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).setId("aicybridge.application.attach").intercept(chain -> {
                Object result = chain.proceed();
                Context context = (Context) chain.getArg(0);
                if (context != null && TARGET_PACKAGE.equals(context.getPackageName())
                        && adapterInstalled.compareAndSet(false, true)) {
                    installForVerifiedRelease(context, loader);
                }
                return result;
            });
        } catch (Throwable error) {
            // Provider and app exceptions can include private values; log only the exception type.
            log(Log.ERROR, TAG, "Module entry failed: " + error.getClass().getSimpleName());
        }
    }

    private void installForVerifiedRelease(Context context, ClassLoader loader) {
        try {
            PackageInfo target = context.getPackageManager().getPackageInfo(TARGET_PACKAGE, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? target.getLongVersionCode() : target.versionCode;
            if (code != TARGET_VERSION_CODE || !TARGET_VERSION_NAME.equals(target.versionName)) {
                reportStatus(context, "unsupported_version");
                log(Log.WARN, TAG, "Unsupported Aicy release; hooks were not installed");
                return;
            }
            AicyHooks.install(this, context, loader);
        } catch (Throwable error) {
            reportStatus(context, "hook_error");
            log(Log.ERROR, TAG, "Adapter installation failed: " + error.getClass().getSimpleName());
        }
    }

    /** The provider performs caller UID, package and signing certificate checks. */
    public static Bundle readConfig(Context context) {
        try {
            Bundle result = context.getContentResolver().call(PROVIDER_URI, "get_config", null, null);
            return result == null ? new Bundle() : result;
        } catch (RuntimeException unavailable) {
            return new Bundle(); // Missing, stopped or untrusted module config keeps replacement off.
        }
    }

    /** Send only fixed status codes accepted by the settings provider, never user content. */
    public static void reportStatus(Context context, String status) {
        try {
            Bundle extras = new Bundle();
            extras.putString("status", status);
            context.getContentResolver().call(PROVIDER_URI, "report_status", null, extras);
        } catch (RuntimeException unavailable) {
            // Diagnostics must not prevent Aicy from starting.
        }
    }
}
