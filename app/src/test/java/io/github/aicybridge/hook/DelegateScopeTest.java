package io.github.aicybridge.hook;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class DelegateScopeTest {
    private final DelegateScope scope = new DelegateScope();
    private final Profile nativeProfile = new Profile("native-vision");
    private final Profile customProfile = new Profile("custom-api-model");

    @Test public void startupMediaLookupKeepsNativeProfileEvenWhenCustomModelIsUnknown() throws Throwable {
        Map<String, Integer> mediaLimits = Map.of("native-vision", 16);
        assertNull(mediaLimits.get(customProfile.model)); // Same missing-entry condition as the device crash.
        AtomicInteger replacements = new AtomicInteger();
        Profile startup = scope.profile(nativeProfile, () -> {
            replacements.incrementAndGet();
            return customProfile;
        });
        assertSame(nativeProfile, startup);
        assertEquals(Integer.valueOf(16), mediaLimits.get(startup.model));
        assertEquals(0, replacements.get());
    }

    @Test public void nativeDelegateUsesCustomModelButLaterMetadataStillUsesOriginal() throws Throwable {
        Profile client = scope.during(() -> scope.profile(nativeProfile, () -> customProfile));
        assertSame(customProfile, client);
        assertSame(nativeProfile, scope.profile(nativeProfile, () -> customProfile));
        assertFalse(scope.isActive());
    }

    @Test public void failedDelegateCreationRestoresMetadataAndPreservesTheOriginalException() throws Throwable {
        IllegalStateException expected = new IllegalStateException("Simulated native client failure");
        try {
            scope.during(() -> { throw expected; });
            fail("Expected the native failure to propagate");
        } catch (IllegalStateException actual) {
            assertSame(expected, actual);
        }
        assertFalse(scope.isActive());
        assertSame(nativeProfile, scope.profile(nativeProfile, () -> customProfile));
    }

    @Test public void nestedDelegateCallsRetainTheirOuterScope() throws Throwable {
        scope.during(() -> {
            assertTrue(scope.isActive());
            scope.during(() -> { assertTrue(scope.isActive()); return null; });
            assertTrue(scope.isActive());
            return null;
        });
        assertFalse(scope.isActive());
    }

    @Test public void concurrentMetadataReaderDoesNotInheritARequestThreadScope() throws Throwable {
        scope.during(() -> {
            assertFalse(CompletableFuture.supplyAsync(scope::isActive).get(5, TimeUnit.SECONDS));
            assertTrue(scope.isActive());
            return null;
        });
        assertFalse(scope.isActive());
    }

    private static final class Profile {
        final String model;
        Profile(String model) { this.model = model; }
    }
}
