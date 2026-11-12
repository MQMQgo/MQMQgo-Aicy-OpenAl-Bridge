package io.github.aicybridge;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallLogPrivacyTest {
    @Test public void endpointLoggingRetainsOnlyHostAndNeverUserInfoPathsOrQueries() {
        assertEquals("example.test", CallLogPrivacy.host("https://username:private-key@example.test/private-key/v1?key=private-key#private-key", "private-key"));
        assertEquals("", CallLogPrivacy.host("not a valid endpoint private-key", "private-key"));
    }
    @Test public void knownKeyIsRedactedBeforeLengthLimitingAndControlCharactersAreRemoved() {
        String safe = CallLogPrivacy.text("model-private-key\r\n\t-tail", "private-key", 128);
        assertEquals("model-[redacted]-tail", safe);
        assertFalse(CallLogPrivacy.text("prefix-private-key", "private-key", 10).contains("private"));
        assertEquals(128, CallLogPrivacy.text(new String(new char[300]).replace('\0', 'a'), "", 128).length());
    }
    @Test public void errorMessagesNeverBecomeLogFields() {
        IOException privateFailure = new IOException("Authorization: private-key; user prompt and server response");
        assertEquals("io", CallLogPrivacy.error(privateFailure, false));
        assertFalse(CallLogPrivacy.describeError("io").contains("private-key"));
        assertFalse(CallLogPrivacy.knownError(privateFailure.getMessage()));
    }
    @Test public void cancellationAndReflectedNetworkFailuresKeepTheirFixedCategories() {
        assertEquals("cancelled", CallLogPrivacy.error(new IOException("anything"), true));
        assertEquals("timeout", CallLogPrivacy.error(new java.net.SocketTimeoutException(), false));
        assertEquals("dns", CallLogPrivacy.error(new InvocationTargetException(new java.net.UnknownHostException("private-value")), false));
        assertEquals("tls", CallLogPrivacy.error(new javax.net.ssl.SSLException("private-value"), false));
    }
}
