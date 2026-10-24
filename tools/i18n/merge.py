#!/usr/bin/env python3
"""Çeviri satırlarını dil dosyasına ekler.

Girdi: her satırı "<sıra> <çeviri>" olan metin (sıra: catalog.json içindeki konum; satır sonu "\\n" olarak yazılır).
Kullanım: python3 tools/i18n/merge.py <dil> <dosya> [<dosya> ...]
Yer tutucular ({0}, {1} …) çeviride de aynen bulunmalı; eksik/fazla olan satır reddedilir.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "android/app/src/main/assets/i18n")
PH = re.compile(r"\{\d+\}")


def main():
    lang, files = sys.argv[1], sys.argv[2:]
    keys = json.load(open(os.path.join(OUT, "catalog.json"), encoding="utf-8"))
    p = os.path.join(OUT, f"{lang}.json")
    have = json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}
    bad = added = 0
    for f in files:
        for n, line in enumerate(open(f, encoding="utf-8"), 1):
            line = line.rstrip("\n")
            if not line.strip():
                continue
            idx, _, text = line.partition(" ")
            k = keys[int(idx)]
            v = text.replace("\\n", "\n").strip()
            if sorted(PH.findall(k)) != sorted(PH.findall(v)):
                print(f"{f}:{n}: yer tutucu uyuşmuyor: {k!r} → {v!r}")
                bad += 1
                continue
            have[k] = v
            added += 1
    have = {k: have[k] for k in sorted(have) if k in set(keys)}
    json.dump(have, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=0, sort_keys=True)
    print(f"{lang}: +{added}, hatalı {bad}, toplam {len(have)}/{len(keys)}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
