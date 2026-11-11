#!/usr/bin/env python3
"""Read DEX member descriptors without loading or executing an Android package."""
import argparse
import json
import struct
import zipfile


class Dex:
    def __init__(self, data):
        if not data.startswith(b"dex\n"):
            raise ValueError("Unsupported DEX magic")
        self.data = data
        self.strings = []
        count, offset = self.u32(56), self.u32(60)
        for index in range(count):
            position = self.u32(offset + index * 4)
            _, position = self.uleb(position)
            end = data.index(b"\0", position)
            self.strings.append(data[position:end].decode("utf-8", errors="replace"))
        count, offset = self.u32(64), self.u32(68)
        self.types = [self.strings[self.u32(offset + i * 4)] for i in range(count)]
        count, offset = self.u32(72), self.u32(76)
        self.protos = []
        for index in range(count):
            _, result, params = struct.unpack_from("<III", data, offset + index * 12)
            args = [] if not params else [
                self.types[struct.unpack_from("<H", data, params + 4 + i * 2)[0]]
                for i in range(self.u32(params))
            ]
            self.protos.append("(" + "".join(args) + ")" + self.types[result])
        count, offset = self.u32(80), self.u32(84)
        self.fields = []
        for index in range(count):
            owner, kind, name = struct.unpack_from("<HHI", data, offset + index * 8)
            self.fields.append((self.types[owner], self.strings[name], self.types[kind]))
        count, offset = self.u32(88), self.u32(92)
        self.methods = []
        for index in range(count):
            owner, proto, name = struct.unpack_from("<HHI", data, offset + index * 8)
            self.methods.append((self.types[owner], self.strings[name], self.protos[proto]))

    def u32(self, offset):
        return struct.unpack_from("<I", self.data, offset)[0]

    def uleb(self, offset):
        result = 0
        for shift in range(0, 35, 7):
            value = self.data[offset]
            offset += 1
            result |= (value & 127) << shift
            if value < 128:
                return result, offset
        raise ValueError("Invalid ULEB128")

    def classes(self, prefixes):
        count, offset = self.u32(96), self.u32(100)
        for index in range(count):
            entry = offset + index * 32
            name = self.types[self.u32(entry)]
            if not any(name.startswith(prefix) for prefix in prefixes):
                continue
            position = self.u32(entry + 24)
            result = {"class": name, "fields": [], "methods": []}
            if position:
                counts = []
                for _ in range(4):
                    value, position = self.uleb(position)
                    counts.append(value)
                for size in counts[:2]:
                    field_id = 0
                    for _ in range(size):
                        delta, position = self.uleb(position)
                        access, position = self.uleb(position)
                        field_id += delta
                        _, field, kind = self.fields[field_id]
                        result["fields"].append({"name": field, "type": kind, "access": access})
                for size in counts[2:]:
                    method_id = 0
                    for _ in range(size):
                        delta, position = self.uleb(position)
                        access, position = self.uleb(position)
                        _, position = self.uleb(position)
                        method_id += delta
                        _, method, proto = self.methods[method_id]
                        result["methods"].append({"name": method, "descriptor": proto, "access": access})
            yield result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    parser.add_argument("prefix", nargs="+", help="Java class or package names")
    args = parser.parse_args()
    prefixes = ["L" + value.replace(".", "/") for value in args.prefix]
    with zipfile.ZipFile(args.apk) as archive:
        for name in archive.namelist():
            if name.startswith("classes") and name.endswith(".dex") and "/" not in name:
                for value in Dex(archive.read(name)).classes(prefixes):
                    value["dex"] = name
                    print(json.dumps(value, ensure_ascii=False))


if __name__ == "__main__":
    main()
