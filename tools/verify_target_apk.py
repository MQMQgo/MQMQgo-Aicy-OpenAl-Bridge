#!/usr/bin/env python3
"""Verify the original APK hash and the exact reflected ABI used by this module."""
import argparse
import hashlib
import zipfile
from inspect_dex import Dex

APK_SHA256 = "eea6d390e6b1cbc3fb151354d354fe14516d44c6edd06fef9ca7571bc89d69af"
OPENAI = "Lcom/flyme/agentos/llm/impl/openai/"
AGENT = "Lcom/flyme/agentos/agent/"
TIMEOUT = "Lcom/flyme/agentos/core/network/m;"
FUNCTION = "Lkotlin/jvm/functions/Function1;"

# Only symbols and public interface descriptors are recorded, never vendor implementation code.
METHODS = {
    OPENAI + "k;": {
        ("<init>", "(" + OPENAI + "l;Lio/ktor/client/b;" + OPENAI
         + "f;Lcom/flyme/agentos/agent/core/telemetry/b;LZ6/a;)V"), ("close", "()V")},
    OPENAI + "l;": {("<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;LY6/b0;Ljava/lang/String;"
                         + TIMEOUT + OPENAI + "y;)V"), ("toString", "()Ljava/lang/String;")},
    OPENAI + "f;": {("<init>", "(" + TIMEOUT + FUNCTION + ")V"), ("close", "()V")},
    OPENAI + "n;": {("a", "(LQg/c;Ljava/util/Map;)V")},
    "LC6/L;": {("<init>", "(Ljava/lang/String;Lcom/flyme/agentos/core/ModelClientKind;Ljava/lang/String;"
                "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/util/Set;Ljava/util/Map;)V"),
                ("toString", "()Ljava/lang/String;")},
    "LW6/f;": {("<init>", "(LC6/L;" + TIMEOUT + "LC6/O;LC6/I;LW6/u;LY6/b0;)V")},
    "LW6/u;": {("<init>", "(ZLjava/lang/String;Ljava/util/Set;Ljava/lang/String;)V")},
    "LD9/c;": {("Q", "(Ljava/io/OutputStream;LY6/i;Z)V")},
    "Lcom/flyme/agentos/llm/impl/routing/o;": {
        ("c", "(Lcom/flyme/agentos/core/ModelFunction;Ljava/lang/String;)LW6/f;")},
    "Lio/ktor/client/f;": {("a", "(Lio/ktor/client/engine/okhttp/a;" + FUNCTION + ")Lio/ktor/client/b;")},
    "Lio/ktor/client/b;": {("close", "()V")},
    AGENT + "domain/tool/recall/y;": {("<init>", "(ZZZZZZZLjava/lang/String;IIIFIIFIFIIILjava/lang/String;FI"
        + AGENT + "domain/tool/recall/ExceptionStrategy;ZLjava/lang/String;I)V")},
    AGENT + "domain/tool/recall/p;": {("i", "(" + AGENT + "core/tool/J;Ljava/util/ArrayList;)Ljava/util/Set;")},
    AGENT + "core/tool/J;": {("b", "()Ljava/lang/String;")},
    AGENT + "core/tool/S;": {("getName", "()Ljava/lang/String;"), ("getDescription", "()Ljava/lang/String;"),
                              ("u", "()Ljava/util/List;"), ("o", "()Ljava/util/List;")},
}
FIELDS = {
    OPENAI + "k;": {("g", OPENAI + "f;")},
    OPENAI + "l;": {(x, t) for x, t in zip("abcdefgh", ["Ljava/lang/String;"] * 3 + ["LY6/b0;",
        "Ljava/lang/String;", "Ljava/util/Map;", TIMEOUT, OPENAI + "y;"])},
    OPENAI + "f;": {("b", "Lkotlin/jvm/internal/FunctionReferenceImpl;"), ("d", "Lio/ktor/client/b;")},
    OPENAI + "n;": {("a", OPENAI + "l;")},
    "LD9/c;": {("b", "Ljava/lang/Object;")},
    "LC6/L;": {(x, t) for x, t in zip("abcdefghi", ["Ljava/lang/String;",
        "Lcom/flyme/agentos/core/ModelClientKind;", "Ljava/lang/String;", "Ljava/lang/String;",
        "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/Integer;", "Ljava/util/Set;", "Ljava/util/Map;"])},
    "LW6/f;": {(x, t) for x, t in zip("abcdef", ["LC6/L;", TIMEOUT, "LC6/O;", "LC6/I;", "LW6/u;", "LY6/b0;"])},
    "Lio/ktor/client/c;": {("e", "Z"), ("d", FUNCTION)},
    "Lio/ktor/client/engine/okhttp/c;": {("a", FUNCTION)},
    "Lio/ktor/client/engine/okhttp/a;": {("a", "Lio/ktor/client/engine/okhttp/a;")},
    "Lbi/U;": {("i", "Z"), ("j", "Z")},
    "Lkotlin/Unit;": {("INSTANCE", "Lkotlin/Unit;")},
    AGENT + "core/tool/J;": {("j", "I")},
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    args = parser.parse_args()
    with open(args.apk, "rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if digest != APK_SHA256:
        raise SystemExit("APK hash mismatch; this version has not been adapted")
    requested = set(METHODS) | set(FIELDS)
    found = {}
    with zipfile.ZipFile(args.apk) as archive:
        for name in archive.namelist():
            if name.startswith("classes") and name.endswith(".dex") and "/" not in name:
                for entry in Dex(archive.read(name)).classes(requested):
                    if entry["class"] in requested:
                        found[entry["class"]] = entry
    errors = []
    for name in sorted(requested):
        entry = found.get(name)
        if entry is None:
            errors.append("Missing class " + name)
            continue
        actual_methods = {(m["name"], m["descriptor"]) for m in entry["methods"]}
        actual_fields = {(f["name"], f["type"]) for f in entry["fields"]}
        for member in sorted(METHODS.get(name, set()) - actual_methods):
            errors.append("Missing method " + name + " " + repr(member))
        for member in sorted(FIELDS.get(name, set()) - actual_fields):
            errors.append("Missing field " + name + " " + repr(member))
    if errors:
        raise SystemExit("\n".join(errors))
    print("Verified APK SHA-256 and exact descriptors for", len(requested), "native classes")
    print("Static ABI verification only; device execution has not been tested")


if __name__ == "__main__":
    main()
