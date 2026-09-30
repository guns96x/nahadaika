#!/usr/bin/env python3
"""Готує файли оновлення для GitHub Release: APK, патч від попередньої версії та update.json.

Патч — bsdiff, у якого блоки перепаковано з bzip2 у zlib (формат NHP1),
щоб застосунок міг накласти його стандартними засобами Android (див. update/BsPatch.kt).
"""
import argparse
import bz2
import hashlib
import json
import os
import shutil
import struct
import zlib


def offtin(b: bytes) -> int:
    y = int.from_bytes(b[:7] + bytes([b[7] & 0x7F]), "little")
    return -y if b[7] & 0x80 else y


def make_patch(old: bytes, new: bytes) -> bytes:
    import bsdiff4

    p = bsdiff4.diff(old, new)
    assert p[:8] == b"BSDIFF40", "очікувався формат BSDIFF40"
    ctrl_len, diff_len, new_size = offtin(p[8:16]), offtin(p[16:24]), offtin(p[24:32])
    blocks = (p[32:32 + ctrl_len], p[32 + ctrl_len:32 + ctrl_len + diff_len], p[32 + ctrl_len + diff_len:])
    ctrl, diff, extra = (zlib.compress(bz2.decompress(x), 9) for x in blocks)
    return b"NHP1" + struct.pack("<QQQ", new_size, len(ctrl), len(diff)) + ctrl + diff + extra


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True, help="новий APK")
    ap.add_argument("--code", type=int, required=True, help="versionCode нового APK")
    ap.add_argument("--name", required=True, help="versionName нового APK")
    ap.add_argument("--notes", default="")
    ap.add_argument("--prev-dir", default="", help="тека з update.json і APK попереднього релізу")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    os.makedirs(a.out, exist_ok=True)
    new = open(a.apk, "rb").read()
    apk_name = os.path.basename(a.apk)
    if os.path.abspath(os.path.dirname(a.apk)) != os.path.abspath(a.out):
        shutil.copy(a.apk, os.path.join(a.out, apk_name))
    info = {
        "versionCode": a.code,
        "versionName": a.name,
        "notes": a.notes.strip(),
        "apk": {"name": apk_name, "size": len(new), "sha256": sha256(new)},
        "patches": [],
    }

    prev_json = os.path.join(a.prev_dir, "update.json") if a.prev_dir else ""
    if prev_json and os.path.exists(prev_json):
        prev = json.load(open(prev_json))
        prev_apk = os.path.join(a.prev_dir, prev["apk"]["name"])
        if os.path.exists(prev_apk) and prev["versionCode"] < a.code:
            old = open(prev_apk, "rb").read()
            patch = make_patch(old, new)
            name = f"Nahadaika-{prev['versionCode']}-to-{a.code}.patch"
            open(os.path.join(a.out, name), "wb").write(patch)
            info["patches"].append({
                "from": prev["versionCode"], "fromSha256": sha256(old),
                "name": name, "size": len(patch), "sha256": sha256(patch),
            })
            print(f"патч {name}: {len(patch) // 1024} КБ замість {len(new) // 1024} КБ")

    json.dump(info, open(os.path.join(a.out, "update.json"), "w"), ensure_ascii=False, indent=2)
    print(json.dumps(info, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
