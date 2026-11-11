package io.github.aicybridge.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolTest {
    private static String fixture(String name) throws IOException {
        try (InputStream input = ProtocolTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(input);
            StringBuilder value = new StringBuilder();
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) value.append(buffer, 0, count);
            return value.toString();
        }
    }

    @Test public void requestPreservesStructuredFieldsAndDoesNotMutateSource() throws Exception {
        JSONObject original = new JSONObject(fixture("multimodal-request.json"));
        String unchanged = original.toString();
        JSONObject copy = OpenAiRequest.copyForChat(original, "my-model", true);
        assertEquals("my-model", copy.getString("model"));
        assertTrue(copy.getBoolean("stream"));
        assertEquals(original.getJSONArray("messages").toString(), copy.getJSONArray("messages").toString());
        assertEquals(original.getJSONArray("tools").toString(), copy.getJSONArray("tools").toString());
        assertEquals(original.getJSONObject("tool_choice").toString(), copy.getJSONObject("tool_choice").toString());
        copy.getJSONArray("messages").getJSONObject(0).put("role", "system");
        assertEquals(unchanged, original.toString());
    }

    @Test public void parserHandlesBomCrLfCommentsMultilineDataAndEventIds() throws Exception {
        String wire = "\uFEFF: ping\r\nid: 7\r\nevent: message\r\nretry: 12\r\ndata: {\r\ndata:  \"choices\": []}\r\n\r\ndata:[DONE]\r\n\r\n";
        try (SseReader reader = new SseReader(new StringReader(wire))) {
            SseReader.Event event = reader.next();
            assertEquals("message", event.type);
            assertEquals("7", event.id);
            assertEquals("{\n \"choices\": []}", event.data);
            assertEquals(0, new JSONObject(event.data).getJSONArray("choices").length());
            SseReader.Event end = reader.next();
            assertTrue(end.isDone());
            assertEquals("7", end.id);
            assertNull(reader.next());
        }
    }

    @Test public void parserSupportsLoneCrAndDiscardsUnterminatedEvent() throws Exception {
        try (SseReader reader = new SseReader(new StringReader("data: one\r\rdata: two"))) {
            assertEquals("one", reader.next().data);
            assertNull(reader.next());
        }
    }

    @Test public void parserEnforcesLineEventAndTotalLimits() throws Exception {
        assertLimit(new SseReader(new StringReader("data: abc\n\n"), 7, 100, 100), "line");
        assertLimit(new SseReader(new StringReader("data:a\ndata:b\n\n"), 100, 10, 100), "event");
        assertLimit(new SseReader(new StringReader(":aaaa\n:bbbb\n\n"), 100, 100, 8), "stream");
    }

    private void assertLimit(SseReader reader, String word) throws Exception {
        try (SseReader closeable = reader) {
            try { closeable.next(); fail("Expected size limit"); }
            catch (ProtocolException e) { assertTrue(e.getMessage(), e.getMessage().contains(word)); }
        }
    }

    @Test public void aggregatesInterleavedToolsAndTextWithoutParsingPartialArguments() throws Exception {
        ChatCompletionAccumulator accumulator = new ChatCompletionAccumulator();
        ChatCompletionAccumulator.Snapshot first = null;
        try (SseReader reader = new SseReader(new StringReader(fixture("tool-stream.sse")))) {
            SseReader.Event event;
            while ((event = reader.next()) != null) {
                if (event.isDone()) { accumulator.complete(); break; }
                ChatCompletionAccumulator.Snapshot current = accumulator.acceptData(event.data);
                if (first == null) first = current;
            }
        }
        assertNotNull(first);
        assertEquals("好", first.choices.get(0).content); // Old snapshots are immutable.
        ChatCompletionAccumulator.Snapshot result = accumulator.snapshot();
        assertTrue(result.done);
        ChatCompletionAccumulator.Choice choice = result.choices.get(0);
        assertEquals("好的", choice.content);
        assertEquals("tool_calls", choice.finishReason);
        assertEquals(2, choice.toolCalls.size());
        assertEquals(0, choice.toolCalls.get(0).index);
        assertEquals("id_0", choice.toolCalls.get(0).id);
        assertEquals("tap", choice.toolCalls.get(0).name);
        assertEquals(12, new JSONObject(choice.toolCalls.get(0).arguments).getInt("x"));
        assertEquals("call_1", choice.toolCalls.get(1).id);
        assertEquals("read_screen", choice.toolCalls.get(1).name);
        assertEquals("你好", new JSONObject(choice.toolCalls.get(1).arguments).getString("text"));
    }

    @Test public void rejectsMalformedChunkAndPrematureCompletion() throws Exception {
        ChatCompletionAccumulator accumulator = new ChatCompletionAccumulator();
        try { accumulator.acceptData("{broken"); fail(); }
        catch (ProtocolException expected) { assertTrue(expected.getMessage().contains("JSON")); }
        accumulator.acceptData("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"partial\"}}]}");
        try { accumulator.complete(); fail(); }
        catch (ProtocolException expected) { assertTrue(expected.getMessage().contains("finish_reason")); }
    }

    @Test public void errorChunksKeepProviderErrorAndSizeLimitsAreEnforced() throws Exception {
        try {
            new ChatCompletionAccumulator().acceptData("{\"error\":{\"message\":\"quota exhausted\",\"type\":\"quota\",\"code\":\"limit\"}}");
            fail();
        } catch (OpenAiException e) {
            assertEquals("quota exhausted", e.getMessage());
            assertEquals("quota", e.errorType);
            assertEquals("limit", e.errorCode);
        }
        try {
            new ChatCompletionAccumulator(2).acceptData("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"abc\"}}]}");
            fail();
        } catch (ProtocolException e) { assertTrue(e.getMessage().contains("limit")); }
    }

    @Test public void normalizesEndpointAndRejectsCredentialsRedirectStyleUrlsAndHttp() throws Exception {
        assertEquals("https://example.com/v1/chat/completions", OpenAiClient.chatCompletionsUrl("https://example.com/v1///").toString());
        assertEquals("https://example.com/v1/chat/completions", OpenAiClient.chatCompletionsUrl("https://example.com/v1/chat/completions/").toString());
        for (String url : new String[]{"http://example.com/v1", "https://key@example.com/v1", "https://example.com/v1?q=key", "https://example.com/v1#key"}) {
            try { OpenAiClient.chatCompletionsUrl(url); fail(url); }
            catch (ProtocolException expected) { assertNotNull(expected.getMessage()); }
        }
    }
}
