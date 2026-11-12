#!/usr/bin/env python3
"""Check API102 packaging and ensure framework/vendor implementations are not bundled."""
import argparse
import base64
import hashlib
import struct
import zipfile
import zlib
from inspect_dex import Dex


def verify_logging_bridge(data):
    assert struct.unpack_from('<I', data, 8)[0] == zlib.adler32(data[12:]) & 0xffffffff
    assert data[12:32] == hashlib.sha1(data[32:]).digest()
    dex = Dex(data)
    own = 'Lio/github/aicybridge/nativebridge/HttpLogInterceptor;'
    classes = list(dex.classes(['L']))
    assert len(classes) == 1 and classes[0]['class'] == own, 'Embedded bridge must contain only our concrete interceptor'
    assert {(x['name'], x['descriptor']) for x in classes[0]['methods']} == {
        ('<init>', '(Ljava/lang/reflect/InvocationHandler;)V'), ('intercept', '(Lbi/M;)Lbi/h0;')}
    assert {x[0] for x in dex.methods} <= {own, 'Ljava/lang/Object;', 'Ljava/lang/reflect/InvocationHandler;'}
    offset = dex.u32(100)
    interfaces = dex.u32(offset + 12)
    assert dex.u32(interfaces) == 1
    assert dex.types[struct.unpack_from('<H', data, interfaces + 4)[0]] == 'Lbi/N;'
    position = dex.u32(offset + 24)
    counts = []
    for _ in range(4):
        value, position = dex.uleb(position); counts.append(value)
    for count in counts[:2]:
        for _ in range(count):
            _, position = dex.uleb(position); _, position = dex.uleb(position)
    for count in counts[2:]:
        method = 0
        for _ in range(count):
            delta, position = dex.uleb(position); method += delta
            _, position = dex.uleb(position)
            code, position = dex.uleb(position)
            assert struct.unpack_from('<H', data, code + 6)[0] == 0, 'Bridge must not wrap native exceptions'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as archive:
        base = "META-INF/xposed/"
        assert archive.read(base + "java_init.list").decode().strip() == "io.github.aicybridge.hook.BridgeModule"
        assert archive.read(base + "scope.list").decode().strip() == "com.meizu.voiceassistant"
        properties = dict(line.split("=", 1) for line in archive.read(base + "module.prop").decode().splitlines()
                          if line and not line.startswith("#"))
        assert properties["minApiVersion"] == "102"
        assert properties["targetApiVersion"] == "102"
        assert properties["staticScope"] == "true"
        assert properties["autoHotReload"] == "false"
        classes = set()
        embedded = set()
        for name in archive.namelist():
            if name.startswith("classes") and name.endswith(".dex") and "/" not in name:
                dex = Dex(archive.read(name))
                classes.update(c["class"] for c in dex.classes(["L"]))
                embedded.update(x for x in dex.strings if x.startswith('ZGV4CjA'))
        assert "Lio/github/aicybridge/hook/BridgeModule;" in classes
        assert "Lio/github/aicybridge/hook/AicyHooks;" in classes
        assert not any(c.startswith("Lio/github/libxposed/") for c in classes), "Framework API must be compileOnly"
        assert not any(c.startswith("Lcom/flyme/") for c in classes), "Vendor implementations must not be bundled"
        assert not any(c.startswith("Lbi/") for c in classes), "Native OkHttp contracts must come from the target"
        if 'Lio/github/aicybridge/hook/NativeLogBridge;' in classes:
            assert len(embedded) == 1, 'Exactly one logging bridge is expected'
            verify_logging_bridge(base64.b64decode(next(iter(embedded)), validate=True))
    print("Verified API102 metadata, scope, entry classes and compileOnly packaging")
    print("APK classes:", len(classes))
    if embedded: print('Verified embedded logging bridge: one module-owned class, native exception forwarding')


if __name__ == "__main__":
    main()
