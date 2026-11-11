package io.github.aicybridge.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class OpenAiRequest {
    private OpenAiRequest() { }

    /** Deep-copies the request, preserving tools, tool_choice, multimodal content and extensions. */
    public static JSONObject copyForChat(JSONObject source, String model, boolean stream)
            throws ProtocolException {
        if (source == null) throw new ProtocolException("Request is missing");
        if (model == null || model.trim().isEmpty()) throw new ProtocolException("Model is empty");
        Object messages = source.opt("messages");
        if (!(messages instanceof JSONArray) || ((JSONArray) messages).length() == 0) {
            throw new ProtocolException("Request messages must be a non-empty array");
        }
        try {
            JSONObject copy = new JSONObject(source.toString());
            copy.put("model", model);
            copy.put("stream", stream);
            return copy;
        } catch (JSONException e) {
            throw new ProtocolException("Cannot encode chat request", e);
        }
    }
}
