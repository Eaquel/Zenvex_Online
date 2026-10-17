#!/usr/bin/env python3
"""Yapay zeka ile görsel üretir.

Anahtar yoksa ücretsiz anonim Pollinations ucunu kullanır (düşük kalite, "sana").
POLLINATIONS_KEY ortam değişkeni varsa daha iyi ücretsiz modeller kullanılır
(varsayılan: gpt-image-2). Anahtar: https://enter.pollinations.ai (GitHub ile ücretsiz).

Kullanım: ai_gen.py <cikti.png> <genislik> <yukseklik> <seed> "<prompt>" [model]
"""
import os, sys, time, urllib.parse, urllib.request

def generate(out, w, h, seed, prompt, model=None):
    key = os.environ.get("POLLINATIONS_KEY")
    q = urllib.parse.quote(prompt)
    if key:
        model = model or "gpt-image-2"
        url = f"https://gen.pollinations.ai/image/{q}?model={model}&width={w}&height={h}&seed={seed}&nologo=true"
        headers = {"Authorization": f"Bearer {key}"}
    else:
        url = f"https://image.pollinations.ai/prompt/{q}?width={w}&height={h}&seed={seed}&nologo=true"
        headers = {}
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=300) as r:
                data = r.read()
            if data[:3] == b"\xff\xd8\xff" or data[:4] == b"\x89PNG":
                open(out, "wb").write(data)
                return out
            raise RuntimeError(data[:200])
        except Exception as e:
            print(f"deneme {attempt+1} başarısız: {e}", file=sys.stderr)
            time.sleep(2 ** (attempt + 1))
    raise SystemExit(f"üretilemedi: {out}")

if __name__ == "__main__":
    a = sys.argv[1:]
    generate(a[0], int(a[1]), int(a[2]), int(a[3]), a[4], a[5] if len(a) > 5 else None)
