#!/usr/bin/env python3
"""Tanıtım videoları: gerçek oyun arayüzüyle, oyuncu gibi oynanan kliplerden; sesler oyunun KENDİ çaldığı seslerdir.

Kareler (cihazsız, JVM'de oyunun kendi çizicisiyle): android/app/build/promo_hud/<klip> (yatay 2340x1080) ve
promo_v/<klip> (dikey 1080x1920), her klipte sfx.json: oyunun o karede çaldığı her ses (Audio.tap).
Üretim:   cd android && PROMO=1 PROMO_HUD=1 ./gradlew :app:recordPaparazziDebug -x cargoBuild --tests '*GameShotsTest.y0*'
          (dikey için ek olarak PROMO_PORTRAIT=1)
Kurgu:    python3 tools/promo/edit.py --format wide|tall [--voice <piper.onnx>] [--out ...]
Kurgu ilkeleri (oyun reklamı): ilk karede en güçlü an (kanca), kesimlerde "vuuş", patlamada yavaş çekim,
sesler olayın anına birebir oturur (oyunun gerçek vuruş/patlama/takla sesleri), seslendirme isteğe bağlıdır
(yerel metin-okuma yapay duyulur; gerçek insan sesi önerilir, bkz. docs/23_TANITIM.md).
"""
import argparse
import json
import os
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trailer as T  # noqa: E402

FPS, SR = 30, 44100
BUILD = os.path.join(T.ROOT, "android/app/build")

# sahne: klip, süre (sn), yazı, alt yazı. Başlangıç karesi seste/olayda otomatik seçilir (en yoğun an).
WIDE = [
    dict(clip="rocket", dur=5.6, hook="explosion", big="TEK ROKET", sub="Bütün sürü gitti"),
    dict(clip="horde", dur=5.6, big="TIRPAN", sub="Sürünün ortasında dön, biç"),
    dict(clip="chase", dur=4.8, big="ARAÇLAR", sub="Çölde motosikletle kaç", start=70),
    dict(clip="titan", dur=6.6, big="TİTAN", sub="Her çeyrek saatte bir uyanır"),
    dict(clip="frost", dur=5.6, big="DONMUŞ LİMAN", sub="Buz tırpanıyla sürüyü biç"),
]
TALL = [
    dict(clip="rocket", dur=3.0, hook="explosion", big="TEK ROKET.", sub="BÜTÜN SÜRÜ."),
    dict(clip="horde", dur=2.8, big="TIRPAN", sub="Sürünün ortasında dön"),
    dict(clip="chase", dur=2.4, big="TAM GAZ", sub="Çölde motosiklet", start=70),
    dict(clip="titan", dur=3.0, big="TİTAN UYANDI", sub="Klanınla çık karşısına"),
    dict(clip="frost", dur=2.6, big="SEN DE KATIL", sub="Ücretsiz test sürümü"),
]
CTA = dict(dur=2.8, big="Zenvex Online", sub="", tag="Test sürümü açık")
VO_SCRIPT = {
    "wide": ["Tek roket. Bütün sürü.", "Tırpanla biç.", "Çölde tam gaz.", "Titan uyandı.", "Soğukta bile durma.", "Zenvex Online. Şimdi test sürümünde."],
    "tall": ["Tek roket. Bütün sürü.", "Tırpanla biç.", "Tam gaz.", "Titan uyandı. Klanını topla.", "Sen de katıl.", "Zenvex Online. Şimdi test sürümünde."],
}


def load_sfx(clip_dir):
    p = os.path.join(clip_dir, "sfx.json")
    return json.load(open(p)) if os.path.exists(p) else []


def sfx_path(name):
    for ext in ("wav", "ogg"):
        p = os.path.join(T.ASSETS, "sfx", f"{name}.{ext}")
        if os.path.exists(p):
            return p
    return None


def auto_start(clip_dir, n, nframes, hook=None):
    """Pencere başlangıcı: kanca için ilk patlamadan 25 kare önce; yoksa en çok (ağırlıklı) sesin olduğu pencere."""
    ev = load_sfx(clip_dir)
    if hook == "explosion":
        ex = [e["f"] for e in ev if e["n"] in ("explosion", "rocket")]
        ex = [f for f in ex if f > 12]
        if ex:
            # roketin çıkışı ile patlamanın ikisi de pencerede: patlama ~0,8 sn'de
            first_boom = min(f for f in [e["f"] for e in ev if e["n"] == "explosion"] if f > 12)
            return max(0, min(first_boom - 24, nframes - n))
    w = np.zeros(nframes + 1)
    for e in ev:
        if e["n"] in ("step0", "step1", "click", "coin", "pickup"):
            continue
        w[min(nframes, e["f"])] += e["v"] * (3.0 if e["n"] in ("explosion", "boss", "levelup", "thunder") else 1.0)
    c = np.convolve(w, np.ones(n), mode="valid")
    lo = 20  # öğrenme/uyarı yazılarından sonrası
    return int(lo + np.argmax(c[lo:])) if len(c) > lo else 0


def whoosh(dur=0.38, seed=7):
    n = int(dur * SR)
    x = np.random.default_rng(seed).standard_normal(n).astype(np.float32)
    y = np.zeros(n, dtype=np.float32)
    s = 0.0
    for i in range(n):
        s += (0.02 + 0.5 * (i / n) ** 1.6) * (x[i] - s)
        y[i] = s
    y *= np.sin(np.pi * np.linspace(0, 1, n)) ** 1.4
    y = y / (np.abs(y).max() + 1e-6) * 0.7
    return np.stack([y, y], 1)


def riser(dur=0.9):
    n = int(dur * SR)
    x = np.random.default_rng(3).standard_normal(n).astype(np.float32)
    y = np.zeros(n, dtype=np.float32)
    s = 0.0
    for i in range(n):
        s += (0.01 + 0.35 * (i / n) ** 2.2) * (x[i] - s)
        y[i] = s
    y *= (np.linspace(0, 1, n) ** 2.0)
    y = y / (np.abs(y).max() + 1e-6) * 0.8
    return np.stack([y, y], 1)


def pitched(snd, p):
    if abs(p - 1.0) < 0.03:
        return snd
    n = int(len(snd) / p)
    idx = np.linspace(0, len(snd) - 1, n)
    return np.stack([np.interp(idx, np.arange(len(snd)), snd[:, k]) for k in range(2)], 1).astype(np.float32)


# ---------------------------------------------------------------- görüntü
def font(path, size):
    return ImageFont.truetype(path, size)


def caption_layer(fmt, big, sub):
    if fmt == "wide":
        W, H = 1920, 1080
        lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        f1, f2, f3 = font(T.FONT, 58), font(T.FONT2, 30), font(T.FONT, 36)
        d.text((60, 1080 - 26), big, font=f1, fill=(255, 240, 220, 255), anchor="ls", stroke_width=2, stroke_fill=(110, 40, 10, 255))
        bw = d.textlength(big, font=f1)
        d.text((60 + bw + 26, 1080 - 28), sub, font=f2, fill=(255, 176, 96, 255), anchor="ls")
        d.text((W - 60, 66), "Zenvex Online", font=f3, fill=(255, 232, 200, 255), anchor="rs")
        return lay
    W, H = 1080, 1920
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    # oyuncunun altındaki, kontrollerin üstündeki bant: yazı oyun arayüzüyle çakışmaz
    for y in range(420):
        d.line([(0, 1560 - y), (W, 1560 - y)], fill=(0, 0, 0, int(150 * (1 - y / 420) ** 1.4)))
    size = 150
    f = font(T.FONT, size)
    while d.textlength(big, font=f) > W - 100 and size > 60:
        size -= 6
        f = font(T.FONT, size)
    tw = d.textlength(big, font=f)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).text(((W - tw) / 2, 1150), big, font=f, fill=(255, 120, 40, 255))
    lay = Image.alpha_composite(lay, glow.filter(ImageFilter.GaussianBlur(20)))
    d = ImageDraw.Draw(lay)
    d.text(((W - tw) / 2, 1150), big, font=f, fill=(255, 240, 220, 255), stroke_width=5, stroke_fill=(120, 38, 8, 255))
    f2 = font(T.FONT2, 50)
    sw = d.textlength(sub, font=f2)
    d.text(((W - sw) / 2, 1150 + size + 18), sub, font=f2, fill=(255, 176, 96, 255), stroke_width=2, stroke_fill=(30, 10, 4, 255))
    return lay


def cta_card(path, fmt):
    W, H = (1920, 1080) if fmt == "wide" else (1080, 1920)
    img = Image.new("RGB", (W, H), (8, 6, 10))
    g = Image.new("RGB", (W, H), (0, 0, 0))
    ImageDraw.Draw(g).ellipse([-W * .1, H * .5, W * 1.1, H * 1.3], fill=(80, 26, 8))
    img = Image.blend(img, g.filter(ImageFilter.GaussianBlur(150)), 0.9).convert("RGBA")
    k = 1.0 if fmt == "wide" else 1.1
    big, mid, small = font(T.FONT, int(250 * k)), font(T.FONT, int(64 * k)), font(T.FONT2, int(48 * k))
    cy = H * (0.30 if fmt == "wide" else 0.34)
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    tw = d.textlength(CTA["big"], font=big)
    d.text(((W - tw) / 2, cy), CTA["big"], font=big, fill=(255, 120, 40, 255))
    img = Image.alpha_composite(img, lay.filter(ImageFilter.GaussianBlur(28)))
    l2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(l2)
    d2.text(((W - tw) / 2, cy), CTA["big"], font=big, fill=(255, 238, 215, 255), stroke_width=4, stroke_fill=(120, 40, 10, 255))
    sw = d2.textlength(CTA["sub"], font=mid)
    d2.text(((W - sw) / 2, cy + 280 * k), CTA["sub"], font=mid, fill=(255, 170, 90, 255))
    bw, bh = int(560 * k), int(110 * k)
    x0, y0 = (W - bw) / 2, cy + 420 * k
    d2.rounded_rectangle([x0, y0, x0 + bw, y0 + bh], 24, fill=(96, 160, 64, 255), outline=(190, 240, 150, 255), width=4)
    d2.text((W / 2, y0 + bh / 2), CTA["tag"], font=small, fill=(255, 255, 255, 255), anchor="mm")
    Image.alpha_composite(img, l2).convert("RGB").save(path)


def frame_plan(clip_dir, start, n, slow_at):
    """Çıktı kareleri için kaynak kare listesi. slow_at: (kare, kare sayısı, çarpan) yavaş çekim bölgeleri."""
    seq, firstpos = [], {}
    for f in range(start, start + n):
        firstpos[f] = len(seq)
        hold = 1
        for (s, ln, k) in slow_at:
            if s <= f < s + ln:
                hold = k
        seq += [f] * hold
    return seq, firstpos


def scene_video(sc, fmt, out, tmp, idx, W, H, src_dir, frames_avail):
    n = int(sc["dur"] * FPS)
    start = sc.get("start") or auto_start(os.path.join(src_dir, sc["clip"]), n, frames_avail[sc["clip"]], sc.get("hook"))
    start = max(0, min(start, frames_avail[sc["clip"]] - n))
    # yavaş çekim: ilk patlamada 0,5 sn (kancada ve titan sahnesinde)
    slow = []
    ev = load_sfx(os.path.join(src_dir, sc["clip"]))
    booms = [e["f"] for e in ev if e["n"] == "explosion" and start <= e["f"] < start + n - 8]
    if booms and sc["clip"] in ("rocket",):
        slow.append((booms[0], 6, 3))
    seq, firstpos = frame_plan(os.path.join(src_dir, sc["clip"]), start, n, slow)
    seq = seq[: int(n * 1.15)]
    lst = os.path.join(tmp, f"seq{idx}.txt")
    with open(lst, "w") as f:
        for fr in seq:
            f.write(f"file '{os.path.join(src_dir, sc['clip'], 'f%04d.png' % fr)}'\nduration {1 / FPS:.5f}\n")
        f.write(f"file '{os.path.join(src_dir, sc['clip'], 'f%04d.png' % seq[-1])}'\n")
    cap = os.path.join(tmp, f"cap{idx}.png")
    caption_layer(fmt, sc["big"], sc["sub"]).save(cap)
    dur = len(seq) / FPS
    if fmt == "wide":
        base = "scale=1920:886:flags=lanczos,pad=1920:1080:0:97:color=black"
    else:
        # dikeyde sarsıntı için biraz büyütüp kırp
        base = "scale=1132:2012:flags=lanczos,crop=1080:1920:x='26+14*sin(58*t)*exp(-6*t)':y='46+14*cos(49*t)*exp(-6*t)'"
    vf = (f"[0:v]{base},eq=contrast=1.08:saturation=1.12[v];[1:v]format=rgba,fade=t=in:st=0.1:d=0.2:alpha=1[c];"
          f"[v][c]overlay=0:0:shortest=1" + (",fade=t=in:st=0:d=0.12:color=white" if sc.get("hook") else "") + f",fade=t=out:st={dur - 0.08:.2f}:d=0.08")
    T.run("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-loop", "1", "-i", cap, "-filter_complex", vf, "-t", f"{dur:.3f}", "-r", str(FPS),
          "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-pix_fmt", "yuv420p", out)
    return dict(start=start, dur=dur, firstpos=firstpos, ev=ev)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--format", choices=["wide", "tall"], default="wide")
    ap.add_argument("--voice", default=os.environ.get("PIPER_MODEL", ""))
    ap.add_argument("--out", default="")
    ap.add_argument("--tmp", default="/tmp/zenvex_edit")
    a = ap.parse_args()
    fmt = a.format
    W, H = (1920, 1080) if fmt == "wide" else (1080, 1920)
    src_dir = os.path.join(BUILD, "promo_hud" if fmt == "wide" else "promo_v")
    scenes = WIDE if fmt == "wide" else TALL
    out = a.out or os.path.join(T.ROOT, "art/promo", "zenvex_tanitim.mp4" if fmt == "wide" else "zenvex_reel.mp4")
    tmp = a.tmp + "_" + fmt
    os.makedirs(tmp, exist_ok=True)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    avail = {sc["clip"]: len([f for f in os.listdir(os.path.join(src_dir, sc["clip"])) if f.endswith(".png")]) for sc in scenes}

    parts, infos, t = [], [], 0.0
    for i, sc in enumerate(scenes):
        v = os.path.join(tmp, f"s{i}.mp4")
        info = scene_video(sc, fmt, v, tmp, i, W, H, src_dir, avail)
        info["t0"] = t
        parts.append(v)
        infos.append(info)
        t += info["dur"]
    cp = os.path.join(tmp, "cta.png")
    cta_card(cp, fmt)
    cv = os.path.join(tmp, "cta.mp4")
    n = int(CTA["dur"] * FPS)
    T.run("ffmpeg", "-y", "-loop", "1", "-i", cp, "-vf",
          f"zoompan=z='1+0.04*on/{n}':d={n}:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s={W}x{H}:fps={FPS},fade=t=in:st=0:d=0.12,fade=t=out:st={CTA['dur'] - 0.3:.2f}:d=0.3",
          "-frames:v", str(n), "-c:v", "libx264", "-crf", "17", "-pix_fmt", "yuv420p", cv)
    parts.append(cv)
    cta_t = t
    total = t + CTA["dur"]
    lst = os.path.join(tmp, "list.txt")
    open(lst, "w").write("".join(f"file '{p}'\n" for p in parts))
    silent = os.path.join(tmp, "video.mp4")
    T.run("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", silent)

    # ---------------- ses: oyunun kendi çaldığı sesler, olayın anında
    N = int(total * SR)
    mix = np.zeros((N, 2), dtype=np.float32)
    music = T.load_audio(os.path.join(T.ASSETS, "music/music_game.ogg"), tmp)
    mus = np.tile(music, (int(np.ceil(N / len(music))), 1))[:N] * 0.42
    mus *= np.minimum(1.0, (N - np.arange(N)) / (SR * 1.2))[:, None]
    cache = {}

    def snd(name):
        if name not in cache:
            p = sfx_path(name)
            cache[name] = T.load_audio(p, tmp) if p else None
        return cache[name]

    wh = whoosh()
    for k, (info, sc) in enumerate(zip(infos, scenes)):
        if k > 0:
            T.put(mix, wh, info["t0"] - 0.22, 0.65)
        last = {}
        for e in info["ev"]:
            f = e["f"]
            if not (info["start"] <= f < info["start"] + int(sc["dur"] * FPS)):
                continue
            nm = e["n"]
            if nm in ("step0", "step1", "click"):
                continue
            if f - last.get(nm, -99) < 3:
                continue
            last[nm] = f
            s = snd(nm)
            if s is None:
                continue
            tpos = info["firstpos"].get(f, f - info["start"]) / FPS
            gain = float(np.clip(e["v"], 0.15, 1.0)) * (1.0 if nm in ("explosion", "boss", "levelup") else 0.85)
            T.put(mix, pitched(s, e["p"]), info["t0"] + tpos, gain)
        if sc["clip"] == "chase":  # motor sürekli sesi
            en = snd("engine")
            for q in range(int(info["dur"] / 1.3) + 1):
                T.put(mix, en, info["t0"] + q * 1.25, 0.5)
    # kanca: patlamadan önce yükselen ses; sondaki kart
    T.put(mix, riser(0.7), 0.0, 0.5)
    T.put(mix, wh, cta_t - 0.22, 0.65)
    T.put(mix, snd("levelup"), cta_t + 0.25, 0.7)
    T.put(mix, snd("explosion"), cta_t + 0.05, 0.55)

    vo = np.zeros((N, 2), dtype=np.float32)
    srt = []
    cues = [(infos[i]["t0"] + 0.25, VO_SCRIPT[fmt][i]) for i in range(len(scenes))] + [(cta_t + 0.5, VO_SCRIPT[fmt][-1])]
    if a.voice and os.path.exists(a.voice):
        import wave as _w
        for i, (at, text) in enumerate(cues):
            wv = os.path.join(tmp, f"vo{i}.wav")
            T.synth_vo(a.voice, text, wv, scale=0.9)
            with _w.open(wv) as w:
                sr0, d = w.getframerate(), np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
            if sr0 != SR:
                d = np.interp(np.linspace(0, len(d), int(len(d) * SR / sr0), endpoint=False), np.arange(len(d)), d)
            d = np.stack([d, d], 1) / (np.abs(d).max() + 1e-6) * 0.95
            T.put(vo, d, at, 1.0)
            srt.append((at, at + len(d) / SR, text))
    else:
        srt = [(at, at + 0.4 + 0.07 * len(text), text) for at, text in cues]
    env = np.convolve(np.abs(vo[:, 0]), np.ones(int(SR * 0.15)) / int(SR * 0.15), mode="same")
    duck = np.convolve(1.0 - 0.5 * np.clip(env * 8, 0, 1), np.ones(int(SR * 0.2)) / int(SR * 0.2), mode="same")
    res = np.tanh((mus * duck[:, None] + mix * 0.95 + vo * 1.1) * 0.95)
    wavp = os.path.join(tmp, "mix.wav")
    with wave.open(wavp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(res, -1, 1) * 32767).astype(np.int16).tobytes())
    T.run("ffmpeg", "-y", "-i", silent, "-i", wavp, "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-c:v", "libx264", "-preset", "slow", "-crf", "23",
          "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", out)
    with open(os.path.splitext(out)[0] + ".srt", "w", encoding="utf-8") as f:
        for i, (s, e, text) in enumerate(srt, 1):
            f.write(f"{i}\n{T.srt_time(s)} --> {T.srt_time(e)}\n{text}\n\n")
    print("hazır:", out, f"({total:.1f} sn)", "· başlangıç kareleri:", [i["start"] for i in infos])


if __name__ == "__main__":
    main()
