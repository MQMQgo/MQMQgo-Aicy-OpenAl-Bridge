package io.github.aicybridge;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A fixed metadata schema: no URLs, headers, response text, exception messages or payloads. */
public final class CallLogPrivacy {
    private CallLogPrivacy() { }
    private static final Set<String> ERRORS = new HashSet<>(Arrays.asList(
            "", "http", "dns", "timeout", "tls", "connection", "cancelled", "protocol",
            "credential", "compatibility", "io", "internal", "logging_unavailable"));

    public static boolean knownError(String code) { return ERRORS.contains(code); }

    public static String text(String value, String secret, int limit) {
        String safe = value == null ? "" : value;
        if (secret != null && !secret.isEmpty()) safe = safe.replace(secret, "[redacted]");
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < safe.length() && out.length() < limit;) {
            int c = safe.codePointAt(offset);
            offset += Character.charCount(c);
            if (!Character.isISOControl(c) && Character.getType(c) != Character.FORMAT && c != 0x2028 && c != 0x2029
                    && out.length() + Character.charCount(c) <= limit) out.appendCodePoint(c);
        }
        return out.toString();
    }

    public static String host(String url, String secret) {
        try { return text(new URI(url).getHost(), secret, 253); }
        catch (Exception invalid) { return ""; }
    }

    public static String error(Throwable failure, boolean cancelled) {
        if (cancelled) return "cancelled";
        for (int i = 0; i < 8 && failure instanceof InvocationTargetException && failure.getCause() != null; i++) {
            failure = failure.getCause();
        }
        if (failure instanceof java.util.concurrent.CancellationException) return "cancelled";
        if (failure instanceof java.net.UnknownHostException) return "dns";
        if (failure instanceof java.net.SocketTimeoutException) return "timeout";
        if (failure instanceof javax.net.ssl.SSLException) return "tls";
        if (failure instanceof java.net.ConnectException) return "connection";
        if (failure instanceof io.github.aicybridge.core.OpenAiException) return "http";
        if (failure instanceof io.github.aicybridge.core.ProtocolException
                || failure instanceof java.net.ProtocolException) return "protocol";
        if (failure instanceof java.security.GeneralSecurityException || failure instanceof SecurityException) return "credential";
        if (failure instanceof ReflectiveOperationException || failure instanceof LinkageError) return "compatibility";
        if (failure instanceof java.io.InterruptedIOException) return "timeout";
        if (failure instanceof java.io.IOException) return "io";
        return "internal";
    }

    public static String describeError(String error) {
        switch (error) {
            case "http": return "服务商返回错误状态";
            case "dns": return "无法解析服务商域名";
            case "timeout": return "连接或读取超时";
            case "tls": return "HTTPS 证书或握手失败";
            case "connection": return "无法连接服务商";
            case "cancelled": return "请求已取消";
            case "protocol": return "请求配置或响应格式无效";
            case "credential": return "凭据或授权不可用";
            case "compatibility": return "目标接口不兼容";
            case "io": return "网络读写失败";
            case "internal": return "调用失败";
            case "logging_unavailable": return "网络日志接口不可用，模型请求继续运行";
            default: return "";
        }
    }
}
