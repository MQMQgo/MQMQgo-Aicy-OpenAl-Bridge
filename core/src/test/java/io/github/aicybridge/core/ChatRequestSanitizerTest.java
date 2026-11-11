package io.github.aicybridge.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatRequestSanitizerTest {
    @Test public void removesVendorIdentityAndBillingFieldsWhileKeepingToolRoundTrip() throws Exception {
        JSONObject source = new JSONObject("{\"model\":\"vendor-model\",\"stream\":false,"
                + "\"tier_id\":\"paid\",\"request_id\":\"id\",\"caller\":\"phone\","
                + "\"session_id\":\"session\",\"chat_turn\":1,\"loop_turn\":2,"
                + "\"catalog_version\":\"v\",\"catalog_md5\":\"hash\",\"app_version\":\"13.1.1\","
                + "\"message_id\":\"message\",\"enable_thinking\":true,\"repeat_penalty\":1.1,"
                + "\"messages\":[{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call_1\","
                + "\"type\":\"function\",\"function\":{\"name\":\"search_tool\",\"arguments\":\"{}\"}}]},"
                + "{\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"result\"}],"
                + "\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"search_tool\","
                + "\"parameters\":{\"type\":\"object\"}}}],\"temperature\":0.2}");
        String before = source.toString();
        JSONObject result = ChatRequestSanitizer.sanitize(source, "my-model", true);
        assertEquals("my-model", result.getString("model"));
        assertTrue(result.getBoolean("stream"));
        assertEquals(source.getJSONArray("messages").toString(), result.getJSONArray("messages").toString());
        assertEquals(source.getJSONArray("tools").toString(), result.getJSONArray("tools").toString());
        assertEquals(0.2, result.getDouble("temperature"), 0.0001);
        assertEquals(5, result.length());
        assertEquals(before, source.toString());
    }

    @Test public void preservesMultimodalMessagesAndStreamUsage() throws Exception {
        JSONObject source = new JSONObject().put("messages", new JSONArray("[{\"role\":\"user\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"describe\"},{\"type\":\"image_url\","
                + "\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}]}]"))
                .put("stream_options", new JSONObject().put("include_usage", true))
                .put("top_p", 0.8).put("top_k", 20);
        JSONObject result = ChatRequestSanitizer.sanitize(source, "vision-model", true);
        assertEquals(source.getJSONArray("messages").toString(), result.getJSONArray("messages").toString());
        assertTrue(result.getJSONObject("stream_options").getBoolean("include_usage"));
        assertFalse(result.has("top_k"));
        assertEquals(0.8, result.getDouble("top_p"), 0.0001);
    }
}
