#!/usr/bin/env python3
"""Check API102 packaging and ensure framework/vendor implementations are not bundled."""
import argparse
import zipfile
from inspect_dex import Dex


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
        for name in archive.namelist():
            if name.startswith("classes") and name.endswith(".dex") and "/" not in name:
                classes.update(c["class"] for c in Dex(archive.read(name)).classes(["L"]))
        assert "Lio/github/aicybridge/hook/BridgeModule;" in classes
        assert "Lio/github/aicybridge/hook/AicyHooks;" in classes
        assert not any(c.startswith("Lio/github/libxposed/") for c in classes), "Framework API must be compileOnly"
        assert not any(c.startswith("Lcom/flyme/") for c in classes), "Vendor implementations must not be bundled"
    print("Verified API102 metadata, scope, entry classes and compileOnly packaging")
    print("APK classes:", len(classes))


if __name__ == "__main__":
    main()
