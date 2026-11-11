package io.github.aicybridge.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Removes Aicy gateway extensions after its native message/tool/media serialization. */
public final class ChatRequestSanitizer {
    private static final Set<String> STANDARD_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "model", "messages", "tools", "tool_choice", "parallel_tool_calls", "stream", "stream_options",
            "temperature", "top_p", "n", "max_tokens", "max_completion_tokens", "frequency_penalty",
            "presence_penalty", "stop", "seed", "logit_bias", "logprobs", "top_logprobs", "response_format")));

    private ChatRequestSanitizer() { }

    public static JSONObject sanitize(JSONObject serialized, String model, boolean stream) throws ProtocolException {
        JSONObject copied = OpenAiRequest.copyForChat(serialized, model, stream);
        JSONObject result = new JSONObject();
        try {
            Iterator<String> keys = copied.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (STANDARD_FIELDS.contains(key)) result.put(key, copied.get(key));
            }
            return result;
        } catch (JSONException error) {
            throw new ProtocolException("Cannot normalize chat request", error);
        }
    }
}
