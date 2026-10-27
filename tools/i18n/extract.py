#!/usr/bin/env python3
"""Çevrilecek metinleri toplar (V15 çok dil).

Kaynak dil Türkçe; anahtar metnin kendisidir. Değişken içeren metinler şablona çevrilir:
  Kotlin  "Seviye $lv Gerekli"      → "Seviye {0} Gerekli"
  Rust    format!("{n}× {} satıldı") → "{0}× {1} satıldı"
  SQL     raise exception 'Yetersiz altın (% gerekli)' → "Yetersiz altın ({0} gerekli)"
Uygulama çalışırken ekrana gelen metin önce birebir, olmazsa şablonlarla eşleştirilir (I18n.kt); bu yüzden kodda
çağrı yerlerini değiştirmek gerekmez.

Çıktı: android/app/src/main/assets/i18n/catalog.json (sıralı liste). Diğer dil dosyaları (en.json …) korunur;
yeni metinler tools/i18n/missing_<dil>.json dosyasına yazılır.
Çalıştırma: python3 tools/i18n/extract.py
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "android/app/src/main/assets/i18n")
LANGS = ["en", "es", "pt", "ru", "de", "fr", "it", "id", "ar"]
TR = "çğıöşüÇĞİÖŞÜ"


# çevrilmeyenler: dil adları (her dil kendi adıyla görünür), protokol başlıkları, iç adlar
SKIP = {
    "Öğretici kapatıldı",
    "Türkçe", "English", "Español", "Português (Brasil)", "Русский", "Deutsch", "Français", "Italiano",
    "Bahasa Indonesia", "العربية", "Authorization", "Bearer {0}", "%.2f Mr", "Zenvex Online", "Zenvex",
    "Material", "Consumable", "Ammo", "Weapon", "Helmet", "Jacket", "Boots", "Bag", "Cloak", "Ring", "Necklace",
    "Pants", "Chest", "Currency", "Tool",
}


def translatable(s: str) -> bool:
    if s.strip() in SKIP:
        return False
    # hile/test komutları ve biçim kalıpları (oyuncuya gösterilmez)
    if re.match(r"^(spawn|item|tp|vehicle|points|level|dog|shot failed)\b", s.strip()) or s.strip() == "dd.MM HH:mm" or s.startswith("bot {0},"):
        return False
    if s.startswith("HTTP/1.1") or "zenvex_server" in s:
        return False
    core = re.sub(r"\{\d+\}", "", s).strip()
    letters = re.findall(r"[A-Za-zÇĞİÖŞÜçğıöşü]", core)
    if len(letters) < 2:
        return False
    if any(c in TR for c in core):
        return True
    if re.fullmatch(r"[a-z0-9_./:\-#%]+", core):  # kimlik, yol, anahtar
        return False
    if re.search(r"https?://|\.png|\.webp|\.json|zx_|^[a-z]+_[a-z_]+$", core):
        return False
    if " " in core and re.search(r"[A-Za-z]{2}", core):
        return True
    return bool(re.fullmatch(r"[A-ZÇĞİÖŞÜ][a-zçğıöşü]{2,}[.!?]?", core))


# ---------------------------------------------------------------- Kotlin
def kotlin_strings(src: str):
    """Kotlin dizgelerini şablonlarıyla döndürür (iç içe ${ "…" } ifadeleri dahil)."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        if c == "'":
            # karakter sabiti
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            i = j + 1
            continue
        if c == '"':
            text, k, i = [], 0, i + 1
            while i < n and src[i] != '"':
                ch = src[i]
                if ch == "\\":
                    nxt = src[i + 1]
                    text.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$", "'": "'"}.get(nxt, nxt))
                    i += 2
                    continue
                if ch == "$" and i + 1 < n and src[i + 1] == "{":
                    depth, j = 1, i + 2
                    while j < n and depth:
                        if src[j] == "{":
                            depth += 1
                        elif src[j] == "}":
                            depth -= 1
                        elif src[j] == '"':
                            # iç dizge
                            j += 1
                            while j < n and src[j] != '"':
                                j += 2 if src[j] == "\\" else 1
                        j += 1
                    text.append("{%d}" % k)
                    k += 1
                    i = j
                    continue
                if ch == "$" and i + 1 < n and (src[i + 1].isalpha() or src[i + 1] == "_"):
                    j = i + 1
                    while j < n and (src[j].isalnum() or src[j] == "_"):
                        j += 1
                    text.append("{%d}" % k)
                    k += 1
                    i = j
                    continue
                text.append(ch)
                i += 1
            i += 1
            out.append("".join(text))
            continue
        i += 1
    return out


# ---------------------------------------------------------------- Rust
def rust_strings(src: str):
    src = src.split("#[cfg(test)]")[0]
    out = []
    for m in re.finditer(r'(?<![A-Za-z0-9_])(r#)?"((?:[^"\\]|\\.)*)"', src):
        if m.group(1):
            continue
        s = m.group(2)
        s = s.replace("\\n", "\n").replace('\\"', '"').replace("\\\\", "\\")
        k = [0]

        def ph(mm):
            if mm.group(0) in ("{{", "}}"):
                return mm.group(0)[0]
            r = "{%d}" % k[0]
            k[0] += 1
            return r

        s = re.sub(r"\{\{|\}\}|\{[^{}]*\}", ph, s)
        out.append(s)
    # satır sonu yorumları içindeki dizgeler de toplanabilir; çevirisi zararsız
    return out


# ---------------------------------------------------------------- SQL (sunucu hata metinleri)
def sql_strings(src: str):
    out = []
    for m in re.finditer(r"raise exception\s+'((?:[^']|'')*)'", src, re.I):
        s = m.group(1).replace("''", "'")
        if s.startswith("HILE|"):
            s = s[5:]
        k = [0]

        def ph(mm):
            r = "{%d}" % k[0]
            k[0] += 1
            return r

        out.append(re.sub(r"%", ph, s))
    return out


def main():
    found = set()
    for f in glob.glob(os.path.join(ROOT, "android/app/src/main/kotlin/**/*.kt"), recursive=True):
        found.update(kotlin_strings(open(f, encoding="utf-8").read()))
    for f in glob.glob(os.path.join(ROOT, "rust/zenvex_core/src/**/*.rs"), recursive=True) + \
            glob.glob(os.path.join(ROOT, "rust/zenvex_server/src/**/*.rs"), recursive=True):
        found.update(rust_strings(open(f, encoding="utf-8").read()))
    for f in glob.glob(os.path.join(ROOT, "supabase/migrations/*.sql")):
        found.update(sql_strings(open(f, encoding="utf-8").read()))
    for f in glob.glob(os.path.join(ROOT, "supabase/functions/*/index.ts")):
        for m in re.finditer(r'error:\s*"([^"]+)"', open(f, encoding="utf-8").read()):
            found.add(m.group(1))
    # çalışma anında baştaki/sondaki boşluklar ayrı tutulur; anahtar kırpılmış metindir
    keys = sorted({s.strip() for s in found if translatable(s)} - {""})
    os.makedirs(OUT, exist_ok=True)
    json.dump(keys, open(os.path.join(OUT, "catalog.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    for lang in LANGS:
        p = os.path.join(OUT, f"{lang}.json")
        have = json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}
        missing = [k for k in keys if k not in have]
        json.dump(missing, open(os.path.join(ROOT, f"tools/i18n/missing_{lang}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
        print(f"{lang}: {len(have)} çevrili, {len(missing)} eksik")
    print(f"toplam {len(keys)} metin")


if __name__ == "__main__":
    sys.exit(main())
