"""Stable Horde (ücretsiz, gönüllü GPU ağı) ile görsel üretimi.

/opt/bvenv/bin/python tools/ai/horde.py --model "Pixel Illustrious" --w 832 --h 1216 --n 2 --out /home/user/zx_ai/h1 "prompt" ["negatif"]
İsteğe bağlı: --lora 120096 (civitai kimliği), --img ref.png --strength 0.55 (img2img), --seed 7
Anahtar: HORDE_KEY ortam değişkeni; yoksa anonim (0000000000, daha yavaş sıra).
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.request

API = "https://stablehorde.net/api/v2"
KEY = os.environ.get("HORDE_KEY", "0000000000")


def call(method, path, body=None):
    req = urllib.request.Request(API + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"apikey": KEY, "Content-Type": "application/json", "Client-Agent": "zenvex:1:eaquel"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read())


def generate(prompt, neg="", model="Pixel Illustrious", w=832, h=1216, n=1, steps=28, cfg=6.0, seed=None, lora=None,
             img=None, strength=0.55, sampler="k_euler_a", timeout=1800):
    params = {"width": w, "height": h, "steps": steps, "cfg_scale": cfg, "n": n, "sampler_name": sampler, "karras": True}
    if seed is not None:
        params["seed"] = str(seed)
    if lora:
        params["loras"] = [{"name": str(lora), "model": 1.0, "clip": 1.0}]
    body = {"prompt": prompt + (" ### " + neg if neg else ""), "params": params, "models": [model], "nsfw": False,
            "censor_nsfw": True, "r2": True, "trusted_workers": False, "shared": False}
    if img:
        body["source_image"] = base64.b64encode(open(img, "rb").read()).decode()
        body["source_processing"] = "img2img"
        params["denoising_strength"] = strength
    j = call("POST", "/generate/async", body)
    jid = j["id"]
    t0 = time.time()
    while time.time() - t0 < timeout:
        s = call("GET", f"/generate/check/{jid}")
        print(f"\r sıra {s.get('queue_position')} bekleme ~{s.get('wait_time')} sn  bitti {s.get('finished')}/{n}   ", end="", flush=True)
        if s.get("done"):
            break
        if not s.get("is_possible", True):
            raise SystemExit("\nbu istek şu an mümkün değil (model/çözünürlük)")
        time.sleep(6)
    print()
    r = call("GET", f"/generate/status/{jid}")
    out = []
    for g in r.get("generations", []):
        data = g["img"]
        if data.startswith("http"):
            raw = urllib.request.urlopen(data, timeout=120).read()
        else:
            raw = base64.b64decode(data)
        out.append((raw, g.get("seed"), g.get("worker_name")))
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("prompt")
    ap.add_argument("neg", nargs="?", default="")
    ap.add_argument("--model", default="Pixel Illustrious")
    ap.add_argument("--w", type=int, default=832)
    ap.add_argument("--h", type=int, default=1216)
    ap.add_argument("--n", type=int, default=1)
    ap.add_argument("--steps", type=int, default=28)
    ap.add_argument("--cfg", type=float, default=6.0)
    ap.add_argument("--seed", type=int)
    ap.add_argument("--lora")
    ap.add_argument("--img")
    ap.add_argument("--strength", type=float, default=0.55)
    ap.add_argument("--out", default="/home/user/zx_ai/out")
    a = ap.parse_args()
    res = generate(a.prompt, a.neg, a.model, a.w, a.h, a.n, a.steps, a.cfg, a.seed, a.lora, a.img, a.strength)
    for i, (raw, seed, worker) in enumerate(res):
        p = f"{a.out}_{i}.webp"
        open(p, "wb").write(raw)
        print(p, seed, worker)
    sys.exit(0 if res else 1)
