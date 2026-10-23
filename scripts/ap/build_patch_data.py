"""
Builds the data the Archipelago world needs to patch the game in pure Python (worlds/alundra/patch_data).

It runs the Java randomizer on an Archipelago patch where every location holds a placeholder, then stores the difference
between every game file it edits and the original one. What depends on the seed (the item of each location and the
slot and seed names) is written by the world on top of these differences, so the values found here for it don't matter.

Usage: python scripts/ap/build_patch_data.py <Alundra disc .bin> [path/to/Archipelago/worlds/alundra]
Needs the `bsdiff4` package (shipped with Archipelago) and a built target/alundra-randomizer.jar.
"""
import importlib.util
import json
import os
import re
import runpy
import struct
import subprocess
import sys
import tempfile
import zlib

import bsdiff4

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
JAR = os.path.join(ROOT, "target", "alundra-randomizer.jar")

# Files the randomizer patches may edit
FILES = ["ALUN_CD.EXE", "SLUS_005.53", "CLOSING.EXE", "END.EXE", "DATA/BALANCE.BIN", "DATA/ETC_USA.R",
         "DATA/DATAS.BIN"]
# Differences closer than this many bytes are stored as a single run
RUN_MERGE_GAP = 16
RUNS_MAGIC = b"ARUN"


def load_module(name: str, path: str):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def make_runs(original: bytes, patched: bytes) -> bytes:
    runs, start, last = [], None, None
    # Compare by chunks first: the files can be over 100 MB and almost identical
    size, chunk = len(original), 1 << 16
    for base in range(0, size, chunk):
        if original[base:base + chunk] == patched[base:base + chunk]:
            continue
        for i in range(base, min(base + chunk, size)):
            if original[i] != patched[i]:
                if start is None:
                    start = last = i
                elif i - last <= RUN_MERGE_GAP:
                    last = i
                else:
                    runs.append((start, last + 1))
                    start = last = i
    if start is not None:
        runs.append((start, last + 1))
    out = bytearray(RUNS_MAGIC + struct.pack("<I", len(runs)))
    for begin, end in runs:
        out += struct.pack("<II", begin, end - begin) + patched[begin:end]
    return bytes(out)


def main() -> None:
    disc = sys.argv[1]
    world = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "..", "archipelago-alundra", "worlds", "alundra"))
    out_dir = os.path.join(world, "patch_data")
    os.makedirs(out_dir, exist_ok=True)

    world_data = runpy.run_path(os.path.join(world, "data.py"))
    locations = list(world_data["LOCATIONS"])
    disc_module = load_module("alundra_disc", os.path.join(world, "disc.py"))

    with tempfile.TemporaryDirectory() as temp:
        template_json = os.path.join(temp, "template.json")
        with open(template_json, "w") as file:
            json.dump({"game": "Alundra", "format": 1, "seed_name": "", "player": 1, "player_name": "",
                       "locations": [{"name": name, "item": None} for name in locations]}, file)
        template_bin = os.path.join(temp, "template.bin")
        subprocess.run(["java", "-jar", JAR, f"--archipelago={template_json}", f"--input={disc}",
                        f"--outputrom={template_bin}"], check=True)

        manifest = {"datas_file": "DATA/DATAS.BIN", "exe_file": "ALUN_CD.EXE", "files": {}}
        with disc_module.CdImage(disc) as original_image, disc_module.CdImage(template_bin) as template_image:
            original_iso = disc_module.Iso9660(original_image)
            template_iso = disc_module.Iso9660(template_image)
            for name in FILES:
                original, patched = original_iso.read_file(name), template_iso.read_file(name)
                if len(original) == len(patched):
                    kind, patch = "runs", make_runs(original, patched)
                else:
                    kind, patch = "bsdiff", bsdiff4.diff(original, patched)
                patch_name = name.replace("/", "_") + ".patch"
                with open(os.path.join(out_dir, patch_name), "wb") as file:
                    file.write(patch)
                manifest["files"][name] = {"format": kind, "patch": patch_name, "original_size": len(original),
                                           "original_crc": zlib.crc32(original)}
                print(f"{name}: {kind}, {len(patch)} bytes")
                if name == "ALUN_CD.EXE":
                    manifest["mailbox_offset"] = patched.index(b"ALAP")
                    manifest["text_table_offset"] = patched.index(b"ALTB") + 4

    sources = json.loads(re.sub(r"^\s*//.*$", "", open(os.path.join(ROOT, "src/main/resources/data/item_source.json"),
                                                         encoding="utf-8").read(), flags=re.M))
    seed_sources = {}
    for source in sources:
        if source["name"] not in world_data["LOCATIONS"]:
            continue
        if "priceAddress" in source or "merrickItemAddress" in source:
            raise SystemExit(f"{source['name']} has a price or a shop slot, which the Python patcher doesn't handle")
        as_list = lambda value: value if isinstance(value, list) else [value]
        seed_sources[source["name"]] = {
            "addresses": [int(a, 16) for a in as_list(source["address"])],
            "sprites": [int(a, 16) for a in as_list(source.get("spriteAddress", []))],
        }
    with open(os.path.join(out_dir, "seed_sources.json"), "w") as file:
        json.dump(seed_sources, file, separators=(",", ":"))
    with open(os.path.join(out_dir, "manifest.json"), "w") as file:
        json.dump(manifest, file, indent=1)
    print("Wrote", out_dir)


if __name__ == "__main__":
    main()
