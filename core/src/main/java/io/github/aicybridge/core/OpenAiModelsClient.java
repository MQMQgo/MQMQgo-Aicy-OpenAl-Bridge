package io.github.aicybridge.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cancellable, bounded discovery of the configured provider's OpenAI model catalogue. */
public final class OpenAiModelsClient {
    private final URL endpoint;
    private final String apiKey;
    private final int connectTimeout, readTimeout;
    private final OpenAiClient.ConnectionFactory connections;

    public OpenAiModelsClient(String baseUrl, String apiKey, int connectTimeout, int readTimeout) throws ProtocolException {
        this(baseUrl, apiKey, connectTimeout, readTimeout, url -> {
            java.net.URLConnection connection = url.openConnection();
            if (!(connection instanceof HttpsURLConnection)) throw new ProtocolException("HTTPS is required");
            return (HttpsURLConnection) connection;
        });
    }

    OpenAiModelsClient(String baseUrl, String apiKey, int connectTimeout, int readTimeout,
                       OpenAiClient.ConnectionFactory connections) throws ProtocolException {
        this.endpoint = OpenAiClient.modelsUrl(baseUrl);
        if (apiKey != null && (apiKey.indexOf('\r') >= 0 || apiKey.indexOf('\n') >= 0)) {
            throw new ProtocolException("API key contains a line break");
        }
        if (connectTimeout <= 0 || readTimeout <= 0 || connections == null) throw new ProtocolException("Invalid transport configuration");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.connectTimeout = connectTimeout; this.readTimeout = readTimeout; this.connections = connections;
    }

    public Call newCall() { return new Call(); }

    public final class Call {
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile boolean cancelled;
        private volatile HttpURLConnection active;
        private volatile int responseCode;

        public void cancel() {
            cancelled = true;
            HttpURLConnection connection = active;
            if (connection != null) connection.disconnect();
        }
        public boolean isCancelled() { return cancelled; }
        public int getResponseCode() { return responseCode; }

        public List<String> execute() throws IOException {
            if (!started.compareAndSet(false, true)) throw new IllegalStateException("Call can only be executed once");
            checkCancelled();
            try {
                HttpURLConnection connection = connections.open(endpoint);
                active = connection;
                checkCancelled();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(connectTimeout); connection.setReadTimeout(readTimeout);
                connection.setRequestMethod("GET"); connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                if (!apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                responseCode = connection.getResponseCode();
                checkCancelled();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new OpenAiException(responseCode, "Model catalogue request failed", null, null);
                }
                String body;
                try (InputStream input = connection.getInputStream()) { body = OpenAiClient.readBody(input, 4 * 1024 * 1024); }
                checkCancelled();
                JSONObject result = OpenAiClient.parseJson(body, "Invalid model catalogue JSON");
                if (result.has("error") && !result.isNull("error")) throw OpenAiException.fromJson(responseCode, result);
                JSONArray data = result.optJSONArray("data");
                if (data == null || data.length() > 5000) throw new ProtocolException("Invalid model catalogue data");
                LinkedHashSet<String> unique = new LinkedHashSet<>();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    Object raw = item == null ? null : item.opt("id");
                    if (!(raw instanceof String)) continue;
                    String id = ((String) raw).trim();
                    if (id.isEmpty() || id.length() > 512 || hasControl(id)) continue;
                    unique.add(id);
                }
                ArrayList<String> ids = new ArrayList<>(unique);
                ids.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(java.util.Comparator.naturalOrder()));
                checkCancelled();
                return Collections.unmodifiableList(ids);
            } catch (IOException error) {
                checkCancelled();
                throw error;
            } finally {
                HttpURLConnection connection = active; active = null;
                if (connection != null) connection.disconnect();
            }
        }

        private void checkCancelled() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Model catalogue request cancelled");
        }
    }

    private static boolean hasControl(String id) {
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == '\u2028' || c == '\u2029') return true;
        }
        return false;
    }
}
