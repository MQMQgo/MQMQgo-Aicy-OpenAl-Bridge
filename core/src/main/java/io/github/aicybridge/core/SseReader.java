package io.github.aicybridge.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

/** Bounded EventSource parser. An event must end with an empty line; unfinished EOF is discarded. */
public final class SseReader implements Closeable {
    public static final int DEFAULT_MAX_LINE_CHARS = 1024 * 1024;
    public static final int DEFAULT_MAX_EVENT_CHARS = 2 * 1024 * 1024;
    public static final long DEFAULT_MAX_TOTAL_CHARS = 32L * 1024 * 1024;

    public static final class Event {
        public final String type;
        public final String data;
        public final String id;
        Event(String type, String data, String id) {
            this.type = type; this.data = data; this.id = id;
        }
        public boolean isDone() { return "[DONE]".equals(data.trim()); }
    }

    private final PushbackReader reader;
    private final int maxLineChars;
    private final int maxEventChars;
    private final long maxTotalChars;
    private long totalChars;
    private boolean firstCharacter = true;
    private boolean eof;
    private String lastEventId = "";

    public SseReader(Reader reader) {
        this(reader, DEFAULT_MAX_LINE_CHARS, DEFAULT_MAX_EVENT_CHARS, DEFAULT_MAX_TOTAL_CHARS);
    }

    public SseReader(Reader reader, int maxLineChars, int maxEventChars, long maxTotalChars) {
        if (reader == null) throw new IllegalArgumentException("reader is missing");
        if (maxLineChars < 1 || maxEventChars < 1 || maxTotalChars < 1) {
            throw new IllegalArgumentException("SSE limits must be positive");
        }
        this.reader = new PushbackReader(reader, 1);
        this.maxLineChars = maxLineChars;
        this.maxEventChars = maxEventChars;
        this.maxTotalChars = maxTotalChars;
    }

    public Event next() throws IOException {
        if (eof) return null;
        StringBuilder data = new StringBuilder();
        String type = "message";
        boolean hasData = false;
        int eventChars = 0;
        for (;;) {
            String line = readLine();
            if (line == null) return null;
            if (line.isEmpty()) {
                if (hasData) {
                    data.setLength(data.length() - 1);
                    return new Event(type, data.toString(), lastEventId);
                }
                type = "message";
                eventChars = 0;
                continue;
            }
            eventChars += line.length();
            if (eventChars > maxEventChars) throw new ProtocolException("SSE event exceeds size limit");
            if (line.charAt(0) == ':') continue;
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            if ("data".equals(field)) {
                if ((long) data.length() + value.length() + 1 > maxEventChars) {
                    throw new ProtocolException("SSE data exceeds size limit");
                }
                data.append(value).append('\n');
                hasData = true;
            } else if ("event".equals(field)) {
                type = value.isEmpty() ? "message" : value;
            } else if ("id".equals(field) && value.indexOf('\0') < 0) {
                lastEventId = value;
            }
            // retry and unknown fields intentionally have no effect on this non-reconnecting client.
        }
    }

    private int readCharacter() throws IOException {
        int c = reader.read();
        if (c >= 0 && ++totalChars > maxTotalChars) {
            throw new ProtocolException("SSE stream exceeds size limit");
        }
        if (firstCharacter) {
            firstCharacter = false;
            if (c == 0xFEFF) return readCharacter();
        }
        return c;
    }

    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        for (;;) {
            int c = readCharacter();
            if (c < 0) {
                eof = true;
                return line.length() == 0 ? null : line.toString();
            }
            if (c == '\n') return line.toString();
            if (c == '\r') {
                int next = readCharacter();
                if (next >= 0 && next != '\n') {
                    reader.unread(next);
                    totalChars--; // The lookahead will be counted on its next read.
                }
                return line.toString();
            }
            if (line.length() == maxLineChars) throw new ProtocolException("SSE line exceeds size limit");
            line.append((char) c);
        }
    }

    @Override public void close() throws IOException { reader.close(); }
}
