package io.github.aicybridge.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenAiClientTest {
    private static final class FakeConnection extends HttpURLConnection {
        final int status;
        final String contentType;
        final byte[] response;
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        boolean disconnected;
        FakeConnection(int status, String contentType, String body) throws Exception {
            super(new URL("https://example.com/v1/chat/completions"));
            this.status = status; this.contentType = contentType;
            response = body.getBytes(StandardCharsets.UTF_8);
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public OutputStream getOutputStream() { return sent; }
        @Override public int getResponseCode() { return status; }
        @Override public String getContentType() { return contentType; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(response); }
        @Override public InputStream getErrorStream() { return status >= 300 ? getInputStream() : null; }
    }

    private OpenAiClient client(FakeConnection connection) throws Exception {
        return new OpenAiClient(new OpenAiClient.Config("https://example.com/v1", "test-key", "custom-model"), url -> connection);
    }

    private JSONObject request() { return new JSONObject("{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}]}"); }

    @Test public void executesPostAndDisablesRedirectsWithoutMutatingRequest() throws Exception {
        FakeConnection connection = new FakeConnection(200, "application/json", "{\"choices\":[]}");
        OpenAiClient.Call call = client(connection).newCall(request());
        assertEquals(0, call.execute().getJSONArray("choices").length());
        assertEquals(200, call.getResponseCode());
        JSONObject sent = new JSONObject(new String(connection.sent.toByteArray(), StandardCharsets.UTF_8));
        assertEquals("custom-model", sent.getString("model"));
        assertFalse(sent.getBoolean("stream"));
        assertEquals("POST", connection.getRequestMethod());
        assertEquals("Bearer test-key", connection.getRequestProperty("Authorization"));
        assertFalse(connection.getInstanceFollowRedirects());
        assertTrue(connection.disconnected);
        try { call.execute(); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("once")); }
    }

    @Test public void surfacesHttpErrorBody() throws Exception {
        FakeConnection connection = new FakeConnection(429, "application/json", "{\"error\":{\"message\":\"rate limit\",\"code\":\"rate\"}}");
        try { client(connection).newCall(request()).execute(); fail(); }
        catch (OpenAiException e) {
            assertEquals(429, e.statusCode);
            assertEquals("rate limit", e.getMessage());
            assertEquals("rate", e.errorCode);
        }
        assertTrue(connection.disconnected);
    }

    @Test public void surfacesErrorEventAndDetectsMissingDone() throws Exception {
        FakeConnection error = new FakeConnection(200, "text/event-stream", "event:error\ndata:{\"error\":{\"message\":\"stream failed\"}}\n\n");
        try { client(error).newCall(request()).stream((chunk, snapshot) -> fail()); fail(); }
        catch (OpenAiException e) { assertEquals("stream failed", e.getMessage()); }
        FakeConnection truncated = new FakeConnection(200, "text/event-stream", "data:{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"a\"},\"finish_reason\":\"stop\"}]}\n\n");
        try { client(truncated).newCall(request()).stream((chunk, snapshot) -> { }); fail(); }
        catch (ProtocolException e) { assertTrue(e.getMessage().contains("[DONE]")); }
        assertTrue(truncated.disconnected);
    }

    @Test public void cancelBeforeStartDoesNotOpenConnection() throws Exception {
        OpenAiClient configured = new OpenAiClient(new OpenAiClient.Config("https://example.com/v1", "", "model"), url -> {
            fail("Cancelled call must not open a connection"); return null;
        });
        OpenAiClient.Call call = configured.newCall(request());
        call.cancel();
        try { call.execute(); fail(); }
        catch (InterruptedIOException expected) { assertTrue(call.isCancelled()); }
    }

    @Test public void cancellationDuringStreamDisconnectsAndDoesNotReturnPartialSuccess() throws Exception {
        String wire = "data:{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"partial\"}}]}\n\ndata:[DONE]\n\n";
        FakeConnection connection = new FakeConnection(200, "text/event-stream", wire);
        AtomicReference<OpenAiClient.Call> reference = new AtomicReference<>(client(connection).newCall(request()));
        try {
            reference.get().stream((chunk, snapshot) -> reference.get().cancel());
            fail();
        } catch (InterruptedIOException expected) { assertTrue(connection.disconnected); }
    }

    @Test public void successfulStreamReturnsCompletedSnapshot() throws Exception {
        String wire = "data:{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"你好\"},\"finish_reason\":\"stop\"}]}\n\ndata:[DONE]\n\n";
        FakeConnection connection = new FakeConnection(200, "text/event-stream; charset=utf-8", wire);
        ChatCompletionAccumulator.Snapshot snapshot = client(connection).newCall(request()).stream((chunk, state) -> assertFalse(state.done));
        assertTrue(snapshot.done);
        assertEquals("你好", snapshot.choices.get(0).content);
        assertTrue(connection.disconnected);
    }
}
