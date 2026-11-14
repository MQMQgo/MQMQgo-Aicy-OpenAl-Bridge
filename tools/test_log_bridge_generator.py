#!/usr/bin/env python3
"""Execute the generated concrete bridge on a JVM using only our own test contracts."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from generate_log_bridge import class_file


class LogBridgeGeneratorTest(unittest.TestCase):
    def test_response_and_checked_exception_keep_their_identity(self):
        with tempfile.TemporaryDirectory(prefix='aicy-bridge-test-') as directory:
            root = Path(directory)
            (root / 'bridge.class').write_bytes(class_file('bridgefixture/GeneratedInterceptor',
                    'BridgeHarness$Interceptor', 'BridgeHarness$Chain', 'BridgeHarness$Response'))
            (root / 'BridgeHarness.java').write_text('''
import java.nio.file.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
public class BridgeHarness extends ClassLoader {
    public interface Interceptor { Response intercept(Chain chain); }
    public static final class Chain { }
    public static final class Response { }
    public static void main(String[] args) throws Throwable {
        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        Class<?> type = new BridgeHarness().defineClass("bridgefixture.GeneratedInterceptor", bytes, 0, bytes.length);
        Chain chain = new Chain(); Response expected = new Response();
        InvocationHandler success = (self, method, values) -> {
            if (method != null || values.length != 1 || values[0] != chain) throw new AssertionError();
            return expected;
        };
        Interceptor interceptor = (Interceptor) type.getConstructor(InvocationHandler.class).newInstance(success);
        if (interceptor.intercept(chain) != expected) throw new AssertionError("Response identity changed");
        IOException original = new IOException("Test native failure");
        InvocationHandler failure = (self, method, values) -> { throw original; };
        interceptor = (Interceptor) type.getConstructor(InvocationHandler.class).newInstance(failure);
        try { interceptor.intercept(chain); throw new AssertionError("Failure was swallowed"); }
        catch (Throwable actual) { if (actual != original) throw new AssertionError("Native exception was wrapped", actual); }
        System.out.println("Generated bridge preserves the response and original checked IOException");
    }
}
''')
            java_home = os.environ.get('JAVA_HOME')
            binary = lambda name: str(Path(java_home, 'bin', name)) if java_home else name
            subprocess.run([binary('javac'), str(root / 'BridgeHarness.java')], check=True)
            subprocess.run([binary('java'), '-cp', str(root), 'BridgeHarness', str(root / 'bridge.class')], check=True)


if __name__ == '__main__':
    unittest.main()
