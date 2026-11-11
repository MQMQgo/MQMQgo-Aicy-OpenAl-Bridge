#!/usr/bin/env python3
"""Make a small, lossless analysis bundle from an APK without installing it."""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path


def extract(apk: Path, output: Path) -> dict:
    if apk.resolve() == output.resolve():
        raise ValueError("Output must differ from the original APK")
    digest = hashlib.sha256()
    with apk.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)

    with zipfile.ZipFile(apk) as source:
        infos = source.infolist()
        names = [entry.filename for entry in infos]
        dex = [name for name in names if re.fullmatch(r"classes\d*\.dex", name)]
        if "AndroidManifest.xml" not in names or not dex:
            raise ValueError("Expected a base APK containing AndroidManifest.xml and classes*.dex")
        selected = {"AndroidManifest.xml", *dex}
        if "resources.arsc" in names:
            selected.add("resources.arsc")
        for entry in infos:
            if entry.filename.startswith("assets/") and entry.file_size <= 1024 * 1024:
                if Path(entry.filename).suffix.lower() in {".js", ".json", ".html", ".wasm"}:
                    selected.add(entry.filename)
        report = {
            "format": 1,
            "apk_filename": apk.name,
            "apk_bytes": apk.stat().st_size,
            "apk_sha256": digest.hexdigest(),
            "purpose": "Static analysis only; this archive is not an installable APK",
            "selected": sorted(selected),
            "original_entries": [
                {"path": entry.filename, "bytes": entry.file_size}
                for entry in infos if not entry.is_dir()
            ],
        }
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as target:
            target.writestr("analysis-manifest.json", json.dumps(report, ensure_ascii=False, indent=2))
            for name in sorted(selected):
                target.writestr(name, source.read(name))
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="Original Aicy base APK")
    parser.add_argument("-o", "--output", type=Path, default=Path("aicy-analysis.zip"))
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists; choose a new --output path")
    try:
        report = extract(args.apk, args.output)
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as exc:
        print(f"Could not create analysis bundle: {exc}", file=sys.stderr)
        return 1
    print(f"Saved {args.output} ({args.output.stat().st_size / 1024 / 1024:.1f} MiB)")
    print(f"Included {len(report['selected'])} code/resource files; original APK unchanged.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
