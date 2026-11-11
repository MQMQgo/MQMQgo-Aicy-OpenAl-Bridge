package io.github.aicybridge.core;

import java.io.IOException;
import org.json.JSONObject;

/** Provider-reported failure. The status is zero for an error received within an SSE stream. */
public final class OpenAiException extends IOException {
    public final int statusCode;
    public final String errorType;
    public final String errorCode;

    public OpenAiException(int statusCode, String message, String errorType, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorCode = errorCode;
    }

    static OpenAiException fromJson(int statusCode, JSONObject root) {
        Object error = root.opt("error");
        JSONObject details = error instanceof JSONObject ? (JSONObject) error : root;
        String fallback = statusCode == 0 ? "Provider reported a streaming error" : "HTTP " + statusCode;
        Object rawMessage = details.opt("message");
        String message = rawMessage instanceof String ? (String) rawMessage : fallback;
        // Bound provider messages. Callers should display failures, not log full exceptions/payloads.
        if (message.length() > 512) message = message.substring(0, 512) + "…";
        return new OpenAiException(statusCode, message, string(details.opt("type")), string(details.opt("code")));
    }

    private static String string(Object value) {
        return value == null || value == JSONObject.NULL ? null : String.valueOf(value);
    }
}
