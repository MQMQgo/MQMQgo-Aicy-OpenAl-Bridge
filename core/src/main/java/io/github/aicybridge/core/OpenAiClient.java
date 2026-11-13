package io.github.aicybridge.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Synchronous HTTPS client. Run calls on a worker thread and retain Call to cancel them. */
public final class OpenAiClient {
    public interface StreamListener {
        /** The raw chunk contains incremental deltas; snapshot contains immutable accumulated values. */
        void onChunk(JSONObject chunk, ChatCompletionAccumulator.Snapshot snapshot) throws IOException;
    }

    public static final class Config {
        public final URL endpoint;
        public final String model;
        public final int connectTimeoutMillis;
        public final int readTimeoutMillis;
        private final String apiKey;

        /** baseUrl is the API prefix (e.g. https://host/v1) or the full chat/completions URL. */
        public Config(String baseUrl, String apiKey, String model) throws ProtocolException {
            this(baseUrl, apiKey, model, 20000, 120000);
        }

        public Config(String baseUrl, String apiKey, String model, int connectTimeoutMillis,
                      int readTimeoutMillis) throws ProtocolException {
            endpoint = chatCompletionsUrl(baseUrl);
            if (model == null || model.trim().isEmpty()) throw new ProtocolException("Model is empty");
            if (apiKey != null && (apiKey.indexOf('\r') >= 0 || apiKey.indexOf('\n') >= 0)) {
                throw new ProtocolException("API key contains a line break");
            }
            if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
                throw new ProtocolException("Timeouts must be positive");
            }
            this.apiKey = apiKey == null ? "" : apiKey;
            this.model = model;
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.readTimeoutMillis = readTimeoutMillis;
        }
    }

    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }

    private static final int MAX_REQUEST_BYTES = 16 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private final Config config;
    private final ConnectionFactory connections;

    public OpenAiClient(Config config) {
        this(config, url -> {
            java.net.URLConnection connection = url.openConnection();
            if (!(connection instanceof HttpsURLConnection)) throw new ProtocolException("HTTPS is required");
            return (HttpsURLConnection) connection;
        });
    }

    OpenAiClient(Config config, ConnectionFactory connections) {
        if (config == null || connections == null) throw new IllegalArgumentException("Client configuration is missing");
        this.config = config;
        this.connections = connections;
    }

    public Call newCall(JSONObject request) throws ProtocolException {
        // Freeze caller-owned JSON at call creation so later UI edits cannot alter an active request.
        return new Call(OpenAiRequest.copyForChat(request, config.model, false));
    }

    public final class Call {
        private final JSONObject request;
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile boolean cancelled;
        private volatile HttpURLConnection active;
        private volatile int responseCode;

        private Call(JSONObject request) { this.request = request; }

        public void cancel() {
            cancelled = true;
            HttpURLConnection connection = active;
            if (connection != null) connection.disconnect();
        }

        public boolean isCancelled() { return cancelled; }
        public int getResponseCode() { return responseCode; }

        public JSONObject execute() throws IOException {
            start();
            try {
                HttpURLConnection connection = connect(false);
                try (InputStream input = successBody(connection)) {
                    String body = readBody(input, MAX_RESPONSE_BYTES);
                    checkCancelled();
                    JSONObject result = parseJson(body, "Invalid JSON completion response");
                    if (result.has("error") && !result.isNull("error")) throw OpenAiException.fromJson(200, result);
                    if (!(result.opt("choices") instanceof org.json.JSONArray)) {
                        throw new ProtocolException("Completion response choices must be an array");
                    }
                    return result;
                }
            } catch (IOException e) {
                checkCancelled();
                throw e;
            } finally { disconnect(); }
        }

        public ChatCompletionAccumulator.Snapshot stream(StreamListener listener) throws IOException {
            if (listener == null) throw new IllegalArgumentException("Stream listener is missing");
            start();
            try {
                HttpURLConnection connection = connect(true);
                InputStream input = successBody(connection);
                String contentType = connection.getContentType();
                if (contentType == null || !contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim()
                        .equals("text/event-stream")) {
                    try (InputStream closeable = input) {
                        String body = readBody(closeable, 64 * 1024);
                        JSONObject error = parseJson(body, "Expected text/event-stream response");
                        if (error.has("error")) throw OpenAiException.fromJson(200, error);
                        throw new ProtocolException("Expected text/event-stream response");
                    }
                }
                ChatCompletionAccumulator accumulator = new ChatCompletionAccumulator();
                try (SseReader events = new SseReader(utf8Reader(input))) {
                    for (;;) {
                        checkCancelled();
                        SseReader.Event event = events.next();
                        checkCancelled();
                        if (event == null) throw new ProtocolException("SSE stream ended before [DONE]");
                        if ("error".equals(event.type)) {
                            throw OpenAiException.fromJson(0, parseJson(event.data, "Invalid streaming error body"));
                        }
                        if (event.isDone()) return accumulator.complete();
                        if (!"message".equals(event.type)) {
                            throw new ProtocolException("Unsupported SSE event type: " + event.type);
                        }
                        JSONObject chunk = parseJson(event.data, "Invalid JSON in SSE data");
                        ChatCompletionAccumulator.Snapshot snapshot = accumulator.accept(chunk);
                        listener.onChunk(chunk, snapshot);
                        checkCancelled();
                    }
                }
            } catch (IOException e) {
                checkCancelled();
                throw e;
            } finally { disconnect(); }
        }

        private void start() throws IOException {
            if (!started.compareAndSet(false, true)) throw new IllegalStateException("Call can only be executed once");
            checkCancelled();
        }

        private HttpURLConnection connect(boolean stream) throws IOException {
            checkCancelled();
            byte[] body = OpenAiRequest.copyForChat(request, config.model, stream).toString()
                    .getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_REQUEST_BYTES) throw new ProtocolException("Request exceeds size limit");
            HttpURLConnection connection = connections.open(config.endpoint);
            active = connection;
            checkCancelled();
            connection.setInstanceFollowRedirects(false); // Never forward the bearer key to a redirect target.
            connection.setConnectTimeout(config.connectTimeoutMillis);
            connection.setReadTimeout(config.readTimeoutMillis);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
            if (!config.apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                checkCancelled();
                output.write(body);
            }
            checkCancelled();
            return connection;
        }

        private InputStream successBody(HttpURLConnection connection) throws IOException {
            int status = connection.getResponseCode();
            responseCode = status;
            checkCancelled();
            if (status < 200 || status >= 300) {
                InputStream error = connection.getErrorStream();
                if (error == null) throw new OpenAiException(status, "HTTP " + status, null, null);
                try (InputStream input = error) {
                    String body = readBody(input, 64 * 1024);
                    try { throw OpenAiException.fromJson(status, new JSONObject(body)); }
                    catch (JSONException e) { throw new OpenAiException(status, "HTTP " + status + " with a non-JSON error body", null, null); }
                }
            }
            return connection.getInputStream();
        }

        private void checkCancelled() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Request cancelled");
        }

        private void disconnect() {
            HttpURLConnection connection = active;
            active = null;
            if (connection != null) connection.disconnect();
        }
    }

    public static URL chatCompletionsUrl(String baseUrl) throws ProtocolException {
        if (baseUrl == null || baseUrl.trim().isEmpty()) throw new ProtocolException("Base URL is empty");
        try {
            URI uri = new URI(baseUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new ProtocolException("Base URL must use HTTPS and include a host");
            }
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new ProtocolException("Base URL must not contain credentials, a query or a fragment");
            }
            String path = uri.getRawPath();
            if (path == null) path = "";
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (!path.endsWith("/chat/completions")) path += "/chat/completions";
            return new URI(uri.getScheme() + "://" + uri.getRawAuthority() + path).toURL();
        } catch (URISyntaxException | java.net.MalformedURLException e) {
            throw new ProtocolException("Invalid base URL", e);
        }
    }

    private static InputStreamReader utf8Reader(InputStream input) {
        return new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
    }

    public static URL modelsUrl(String baseUrl) throws ProtocolException {
        String chat = chatCompletionsUrl(baseUrl).toExternalForm();
        try { return new URL(chat.substring(0, chat.length() - "chat/completions".length()) + "models"); }
        catch (java.net.MalformedURLException invalid) { throw new ProtocolException("Invalid models URL", invalid); }
    }

    static String readBody(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if ((long) output.size() + count > maxBytes) throw new ProtocolException("Response body exceeds size limit");
            output.write(buffer, 0, count);
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString();
    }

    static JSONObject parseJson(String body, String message) throws ProtocolException {
        try { return new JSONObject(body); }
        catch (JSONException e) { throw new ProtocolException(message, e); }
    }
}
