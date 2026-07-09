#!/usr/bin/env python3
"""Validate release jars and write a SHA256 manifest."""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def read_zip_text(jar: Path, member: str) -> str:
    with zipfile.ZipFile(jar) as zf:
        try:
            return zf.read(member).decode("utf-8")
        except KeyError:
            raise SystemExit(f"artifact: {jar} lacks {member}")


def validate_fabric(jar: Path, version: str) -> dict:
    text = read_zip_text(jar, "fabric.mod.json")
    data = json.loads(text)
    actual = str(data.get("version", ""))
    if actual != version:
        raise SystemExit(f"artifact: Fabric metadata version {actual!r} != {version!r}")
    if data.get("id") != "alaindustrial":
        raise SystemExit(f"artifact: Fabric mod id is {data.get('id')!r}, expected 'alaindustrial'")
    return {"loader": "fabric", "metadata_version": actual}


def validate_neoforge(jar: Path, version: str) -> dict:
    text = read_zip_text(jar, "META-INF/neoforge.mods.toml")
    if 'modId="alaindustrial"' not in text and "modId = \"alaindustrial\"" not in text:
        raise SystemExit("artifact: NeoForge metadata does not declare modId alaindustrial")
    if version not in text:
        raise SystemExit(f"artifact: NeoForge metadata does not contain version {version}")
    return {"loader": "neoforge", "metadata_version": version}


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--version", required=True)
    p.add_argument("--fabric", required=True, type=Path)
    p.add_argument("--neoforge", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    args = p.parse_args()

    entries = []
    for loader, jar, validator in [
        ("fabric", args.fabric, validate_fabric),
        ("neoforge", args.neoforge, validate_neoforge),
    ]:
        if not jar.is_file() or jar.stat().st_size <= 0:
            raise SystemExit(f"artifact: missing or empty {loader} jar: {jar}")
        if not jar.name.endswith(f"-{args.version}.jar"):
            raise SystemExit(f"artifact: {loader} jar filename does not end with -{args.version}.jar: {jar.name}")
        meta = validator(jar, args.version)
        artifact_path = f"{loader}/build/libs/{jar.name}"
        entries.append({
            "loader": loader,
            "file": artifact_path,
            "name": jar.name,
            "size": jar.stat().st_size,
            "sha256": sha256(jar),
            **meta,
        })

    manifest = {
        "project": "Ala Industrial",
        "modid": "alaindustrial",
        "version": args.version,
        "minecraft": "26.2",
        "artifacts": entries,
    }
    args.output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"OK artifacts validated; manifest written to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
