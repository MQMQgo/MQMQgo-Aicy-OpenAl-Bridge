package io.github.aicybridge.hook;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class BindingProfileArgumentsTest {
    public static final class Profile {
        final String a = "native-media", c = "native-provider", d = "https://native.test", e = "native-vision", f = "native-key";
        final Object b = new Object();
        final Integer g = 12000;
        final Set<String> h = Set.of("text", "image");
        final Map<String, Integer> i = Map.of("images", 16);
    }
    @Test public void routeCredentialsChangeWhileNativeMetadataKeepsItsIdentityAndLimits() throws Exception {
        Profile original = new Profile();
        Object[] args = BindingProfileArguments.forEndpoint(original, "https://provider.test/v1", "user-key");
        assertEquals(9, args.length);
        assertEquals(original.a, args[0]); assertSame(original.b, args[1]);
        assertEquals("openai-compatible", args[2]); // Keep native outbound credential redaction enabled.
        assertEquals("https://provider.test/v1", args[3]); assertEquals(original.e, args[4]); assertEquals("user-key", args[5]);
        assertSame(original.g, args[6]); assertSame(original.h, args[7]); assertSame(original.i, args[8]);
        assertEquals("native-key", original.f); assertEquals("native-provider", original.c);
    }
    @Test public void mediaLimitsResolveEvenDuringDelegateCreationWithAnUnknownApiModel() throws Throwable {
        Profile original = new Profile(); DelegateScope scope = new DelegateScope();
        Map<String, Integer> limits = Map.of("native-vision", 16);
        assertNull(limits.get("gpt-6.1-sol"));
        scope.during(() -> {
            Object[] args = scope.profile(null, () -> BindingProfileArguments.forEndpoint(original, "https://provider.test/v1", "user-key"));
            assertEquals(Integer.valueOf(16), limits.get(args[4]));
            assertEquals(original.e, args[4]);
            return null;
        });
        assertFalse(scope.isActive());
        assertSame(original, scope.profile(original, () -> { fail("Startup must retain the original profile"); return null; }));
    }
}
