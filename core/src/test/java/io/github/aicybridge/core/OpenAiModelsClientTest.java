package io.github.aicybridge.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenAiModelsClientTest {
    private static class Connection extends HttpURLConnection {
        final int status; final byte[] body; boolean disconnected;
        Connection(int status, String body) throws Exception {
            super(new URL("https://example.test/v1/models"));
            this.status = status; this.body = body.getBytes(StandardCharsets.UTF_8);
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(body); }
    }
    private OpenAiModelsClient client(Connection connection) throws Exception {
        return new OpenAiModelsClient("https://example.test/v1/chat/completions", "test-key", 1234, 4321, url -> {
            assertEquals("https://example.test/v1/models", url.toString()); return connection;
        });
    }
    @Test public void derivesModelsEndpointWithoutRequiringAModelOrAcceptingUnsafeUrls() throws Exception {
        assertEquals("https://example.test/v1/models", OpenAiClient.modelsUrl("https://example.test/v1/").toString());
        assertEquals("https://example.test/gateway%2Ftenant/models", OpenAiClient.modelsUrl("https://example.test/gateway%2Ftenant/chat/completions").toString());
        for (String url : new String[]{"http://example.test/v1", "https://key@example.test/v1", "https://example.test/v1?api_key=x", "https://example.test/v1#x"}) {
            try { new OpenAiModelsClient(url, "", 100, 100); fail(url); } catch (ProtocolException expected) { }
        }
        try { new OpenAiModelsClient("https://example.test/v1", "key\nheader", 100, 100); fail(); }
        catch (ProtocolException expected) { }
    }
    @Test public void fetchesWithGetAuthAndNoRedirectsAndReturnsUniqueSortedIds() throws Exception {
        Connection connection = new Connection(200, "{\"data\":[{\"id\":\"z-model\"},{\"id\":\"Alpha\"},{\"id\":\"z-model\"},{\"id\":42},{\"id\":\"\"},{\"id\":\"bad\\nmodel\"}]}");
        OpenAiModelsClient.Call call = client(connection).newCall();
        java.util.List<String> ids = call.execute();
        assertEquals(Arrays.asList("Alpha", "z-model"), ids);
        assertEquals("GET", connection.getRequestMethod()); assertFalse(connection.getDoOutput());
        assertEquals("Bearer test-key", connection.getRequestProperty("Authorization"));
        assertEquals("application/json", connection.getRequestProperty("Accept"));
        assertFalse(connection.getInstanceFollowRedirects()); assertFalse(connection.getUseCaches());
        assertEquals(1234, connection.getConnectTimeout()); assertEquals(4321, connection.getReadTimeout());
        assertEquals(200, call.getResponseCode()); assertTrue(connection.disconnected);
        try { ids.add("other"); fail(); } catch (UnsupportedOperationException expected) { }
        try { call.execute(); fail(); } catch (IllegalStateException expected) { }
    }
    @Test public void emptyListIsValidButMissingOrMalformedDataIsRejected() throws Exception {
        assertTrue(client(new Connection(200, "{\"data\":[]}")).newCall().execute().isEmpty());
        for (String body : new String[]{"not json", "{\"models\":[]}", "{\"data\":{}}"}) {
            Connection connection = new Connection(200, body);
            try { client(connection).newCall().execute(); fail(); } catch (ProtocolException expected) { }
            assertTrue(connection.disconnected);
        }
    }
    @Test public void httpErrorsAndRedirectsNeverBecomeSuccessfulCatalogues() throws Exception {
        for (int status : new int[]{302, 401, 404, 429, 500}) {
            Connection connection = new Connection(status, "private provider error");
            try { client(connection).newCall().execute(); fail(); }
            catch (OpenAiException expected) { assertEquals(status, expected.statusCode); assertFalse(expected.getMessage().contains("private provider error")); }
            assertFalse(connection.getInstanceFollowRedirects()); assertTrue(connection.disconnected);
        }
    }
    @Test public void cancellationBeforeStartDoesNotOpenATransport() throws Exception {
        OpenAiModelsClient.Call call = new OpenAiModelsClient("https://example.test/v1", "", 100, 100, url -> {
            fail("Cancelled call opened a connection"); return null;
        }).newCall();
        call.cancel();
        try { call.execute(); fail(); } catch (InterruptedIOException expected) { assertTrue(call.isCancelled()); }
    }
    @Test public void cancellationDuringReadDisconnectsAndDiscardsTheResult() throws Exception {
        AtomicReference<OpenAiModelsClient.Call> reference = new AtomicReference<>();
        Connection connection = new Connection(200, "") {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    @Override public int read() throws IOException { reference.get().cancel(); throw new IOException("cancelled transport"); }
                };
            }
        };
        OpenAiModelsClient.Call call = client(connection).newCall(); reference.set(call);
        try { call.execute(); fail(); } catch (InterruptedIOException expected) { assertTrue(connection.disconnected); }
    }
    @Test public void catalogueResponseSizeIsBoundedBeforeJsonParsing() throws Exception {
        Connection connection = new Connection(200, "") {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    int remaining = 4 * 1024 * 1024 + 1;
                    @Override public int read() { return remaining-- > 0 ? 'x' : -1; }
                    @Override public int read(byte[] bytes, int offset, int length) {
                        if (remaining <= 0) return -1;
                        int count = Math.min(length, remaining); Arrays.fill(bytes, offset, offset + count, (byte)'x'); remaining -= count; return count;
                    }
                };
            }
        };
        try { client(connection).newCall().execute(); fail(); }
        catch (ProtocolException expected) { assertTrue(expected.getMessage().contains("size limit")); }
        assertTrue(connection.disconnected);
    }
}
