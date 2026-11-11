package io.github.aicybridge.core;

import java.io.IOException;

/** A malformed, incomplete, unsupported or oversized API response. Never includes raw payloads. */
public class ProtocolException extends IOException {
    public ProtocolException(String message) { super(message); }
    public ProtocolException(String message, Throwable cause) { super(message, cause); }
}
