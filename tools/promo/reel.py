#!/usr/bin/env python3
"""Instagram Reels / TikTok / Shorts için 9:16 dikey reklam videosu (1080x1920, ≈14 sn), yine gerçek oynanıştan.

Reklam kurgusu: ilk saniyede en güçlü an (kanca), sonra hızlı mekanik gösterimi, sonda çağrı kartı.
  - logo girişi yok; ilk kare patlama
  - her kesimde ekran sarsıntısı, beyaz parlama ve "vuuş" sesi
  - büyük, kısa, ekranın üst üçte birinde yazılar (telefonda okunur)
Kareler trailer.py ile aynıdır (android/app/build/promo). Çalıştırma:
  PIPER_PY=<piper python> python3 tools/promo/reel.py --voice tr.onnx [--out art/promo/zenvex_reel.mp4]
"""
import argparse
import os
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trailer as T  # noqa: E402

W, H, FPS, SR = 1080, 1920, 30, 44100

# kanca: ilk 2 sn patlama. Sonra dört hızlı sahne, sonda çağrı kartı.
SCENES = [
    dict(clip="rpg", start=116, n=66, big="TEK ROKET.", sub="BÜTÜN SÜRÜ.", vo="Tek roket. Bütün sürü.", sfx="rocket", flash=True),
    dict(clip="scythe", start=62, n=78, big="TIRPAN", sub="Üç vuruşluk kombo", vo="Tırpanla biç.", sfx="slash"),
    dict(clip="moto", start=22, n=60, big="TAM GAZ", sub="Çölde motosiklet", vo="Çölde tam gaz.", sfx="engine"),
    dict(clip="magma", start=60, n=72, big="TİTAN UYANDI", sub="Klanınla çık karşısına", vo="Titan uyandı. Klanını topla.", sfx="boss"),
    dict(clip="ice", start=64, n=62, big="SEN DE KATIL", sub="Ücretsiz test sürümü", vo="Sen de katıl.", sfx="slash"),
]
CTA = dict(dur=2.6, vo="Zenvex Online. Şimdi test sürümünde.", big="ZENVEX", sub="O N L I N E", tag="Test sürümü açık")


def overlay_png(path, big, sub, brand=True):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    # üst okunurluk şeridi
    for y in range(520):
        d.line([(0, y), (W, y)], fill=(0, 0, 0, int(190 * (1 - y / 520) ** 1.5)))
    for y in range(360):
        d.line([(0, H - 1 - y), (W, H - 1 - y)], fill=(0, 0, 0, int(190 * (1 - y / 360) ** 1.6)))
    size = 190
    f = ImageFont.truetype(T.FONT, size)
    while d.textlength(big, font=f) > W - 110 and size > 70:
        size -= 6
        f = ImageFont.truetype(T.FONT, size)
    tw = d.textlength(big, font=f)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).text(((W - tw) / 2, 190), big, font=f, fill=(255, 120, 40, 255))
    lay = Image.alpha_composite(lay, glow.filter(ImageFilter.GaussianBlur(24)))
    d = ImageDraw.Draw(lay)
    d.text(((W - tw) / 2, 190), big, font=f, fill=(255, 240, 220, 255), stroke_width=5, stroke_fill=(120, 38, 8, 255))
    f2 = ImageFont.truetype(T.FONT2, 58)
    sw = d.textlength(sub, font=f2)
    d.text(((W - sw) / 2, 190 + size + 28), sub, font=f2, fill=(255, 176, 96, 255), stroke_width=2, stroke_fill=(30, 10, 4, 255))
    if brand:
        f3 = ImageFont.truetype(T.FONT, 60)
        bw = d.textlength("ZENVEX ONLINE", font=f3)
        d.text(((W - bw) / 2, H - 170), "ZENVEX ONLINE", font=f3, fill=(255, 240, 220, 255), stroke_width=3, stroke_fill=(120, 38, 8, 255))
    lay.save(path)


def scene_video(sc, out, tmp, idx):
    n = sc["n"]
    ov = os.path.join(tmp, f"rov{idx}.png")
    overlay_png(ov, sc["big"], sc["sub"])
    dur = n / FPS
    sh = "exp(-6*t)"
    vf = (
        f"[0:v]crop=608:1080:(iw-608)/2:0,scale=1180:2098:flags=lanczos,"
        f"crop=1080:1920:x='50+22*sin(58*t)*{sh}':y='89+22*cos(49*t)*{sh}',"
        f"eq=contrast=1.14:saturation=1.22:gamma=0.95,vignette=PI/4[v];"
        f"[1:v]format=rgba,fade=t=in:st=0.08:d=0.18:alpha=1[c];"
        f"[v][c]overlay=0:0:shortest=1"
        + (",fade=t=in:st=0:d=0.14:color=white" if sc.get("flash") else "")
        + f",fade=t=out:st={dur - 0.08:.2f}:d=0.08"
    )
    T.run("ffmpeg", "-y", "-framerate", str(FPS), "-start_number", str(sc["start"]), "-i", os.path.join(T.PROMO, sc["clip"], "f%04d.png"),
          "-loop", "1", "-i", ov, "-filter_complex", vf, "-frames:v", str(n), "-r", str(FPS),
          "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-pix_fmt", "yuv420p", out)
    return dur


def cta_card(path):
    img = Image.new("RGB", (W, H), (8, 6, 10))
    g = Image.new("RGB", (W, H), (0, 0, 0))
    ImageDraw.Draw(g).ellipse([-200, H * .45, W + 200, H * 1.25], fill=(80, 26, 8))
    img = Image.blend(img, g.filter(ImageFilter.GaussianBlur(150)), 0.9).convert("RGBA")
    big = ImageFont.truetype(T.FONT, 270)
    mid = ImageFont.truetype(T.FONT, 80)
    small = ImageFont.truetype(T.FONT2, 62)
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    tw = d.textlength(CTA["big"], font=big)
    d.text(((W - tw) / 2, 640), CTA["big"], font=big, fill=(255, 120, 40, 255))
    img = Image.alpha_composite(img, lay.filter(ImageFilter.GaussianBlur(30)))
    lay2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(lay2)
    d2.text(((W - tw) / 2, 640), CTA["big"], font=big, fill=(255, 238, 215, 255), stroke_width=4, stroke_fill=(120, 40, 10, 255))
    sw = d2.textlength(CTA["sub"], font=mid)
    d2.text(((W - sw) / 2, 960), CTA["sub"], font=mid, fill=(255, 170, 90, 255))
    d2.rectangle([W / 2 - 200, 1090, W / 2 + 200, 1096], fill=(255, 120, 40, 255))
    # düğme
    bw, bh = 700, 150
    x0, y0 = (W - bw) / 2, 1230
    d2.rounded_rectangle([x0, y0, x0 + bw, y0 + bh], 26, fill=(96, 160, 64, 255), outline=(190, 240, 150, 255), width=5)
    gw = d2.textlength(CTA["tag"], font=small)
    d2.text(((W - gw) / 2, y0 + 38), CTA["tag"], font=small, fill=(255, 255, 255, 255))
    Image.alpha_composite(img, lay2).convert("RGB").save(path)


def whoosh(dur=0.38):
    n = int(dur * SR)
    rng = np.random.default_rng(7)
    x = rng.standard_normal(n).astype(np.float32)
    y = np.zeros(n, dtype=np.float32)
    s = 0.0
    for i in range(n):  # tek kutuplu süzgeç, kesim frekansı yükselir
        k = 0.02 + 0.5 * (i / n) ** 1.6
        s += k * (x[i] - s)
        y[i] = s
    env = np.sin(np.pi * np.linspace(0, 1, n)) ** 1.4
    y = y * env
    y = y / (np.abs(y).max() + 1e-6) * 0.7
    return np.stack([y, y], 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default=os.environ.get("PIPER_MODEL", ""))
    ap.add_argument("--out", default=os.path.join(T.ROOT, "art/promo/zenvex_reel.mp4"))
    ap.add_argument("--tmp", default="/tmp/zenvex_reel")
    a = ap.parse_args()
    tmp = a.tmp
    os.makedirs(tmp, exist_ok=True)
    os.makedirs(os.path.dirname(a.out), exist_ok=True)

    parts, info, t = [], [], 0.0
    for i, sc in enumerate(SCENES):
        v = os.path.join(tmp, f"r{i}.mp4")
        dur = scene_video(sc, v, tmp, i)
        parts.append(v)
        info.append((t, dur))
        t += dur
    cp = os.path.join(tmp, "cta.png")
    cta_card(cp)
    cv = os.path.join(tmp, "cta.mp4")
    n = int(CTA["dur"] * FPS)
    T.run("ffmpeg", "-y", "-loop", "1", "-i", cp, "-vf",
          f"zoompan=z='1+0.04*on/{n}':d={n}:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s={W}x{H}:fps={FPS},fade=t=in:st=0:d=0.12,fade=t=out:st={CTA['dur'] - 0.25:.2f}:d=0.25",
          "-frames:v", str(n), "-c:v", "libx264", "-crf", "17", "-pix_fmt", "yuv420p", cv)
    parts.append(cv)
    cta_t = t
    total = t + CTA["dur"]
    lst = os.path.join(tmp, "list.txt")
    open(lst, "w").write("".join(f"file '{p}'\n" for p in parts))
    silent = os.path.join(tmp, "video.mp4")
    T.run("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", silent)

    # ses
    N = int(total * SR)
    mix = np.zeros((N, 2), dtype=np.float32)
    music = T.load_audio(os.path.join(T.ASSETS, "music/music_game.ogg"), tmp)
    mus = np.tile(music, (int(np.ceil(N / len(music))), 1))[:N] * 0.5
    mus *= np.minimum(1.0, (N - np.arange(N)) / (SR * 1.2))[:, None]
    sfx = {k: T.load_audio(os.path.join(T.ASSETS, "sfx", f), tmp) for k, f in dict(
        swing="swing.wav", hit="hit.ogg", rocket="rocket.wav", boom="explosion.wav", engine="engine.wav", boss="boss.wav",
        lvl="levelup.wav", zd="zdeath.wav", thunder="thunder.wav").items()}
    wh = whoosh()
    for k, ((st, dur), sc) in enumerate(zip(info, SCENES)):
        if k > 0:
            T.put(mix, wh, st - 0.22, 0.7)
        ons = T.onsets(sc["clip"], sc["start"], sc["n"], k=4, min_gap=0.4)
        kind = sc["sfx"]
        if kind == "rocket":
            T.put(mix, sfx["boom"], st + 0.05, 1.0)  # kanca: ilk saniyede patlama
            T.put(mix, sfx["rocket"], max(0.0, st - 0.0), 0.5)
        for j, (ot, pw) in enumerate(ons):
            at = st + ot
            if kind == "slash":
                T.put(mix, sfx["swing"], at - 0.05, 0.9 * pw + 0.3)
                T.put(mix, sfx["hit"], at + 0.07, 0.6 * pw + 0.25)
                if j % 2 == 0:
                    T.put(mix, sfx["zd"], at + 0.15, 0.35)
            elif kind == "boss":
                T.put(mix, sfx["swing"], at - 0.05, 0.7)
                T.put(mix, sfx["hit"], at + 0.1, 0.6)
        if kind == "engine":
            for q in range(int(dur / 1.4) + 1):
                T.put(mix, sfx["engine"], st + q * 1.35, 0.6)
        if kind == "boss":
            T.put(mix, sfx["boss"], st + 0.1, 0.8)
    T.put(mix, wh, cta_t - 0.22, 0.7)
    T.put(mix, sfx["lvl"], cta_t + 0.2, 0.7)
    T.put(mix, sfx["boom"], cta_t + 0.05, 0.6)

    cues = [(st + 0.25, sc["vo"]) for (st, _), sc in zip(info, SCENES)] + [(cta_t + 0.5, CTA["vo"])]
    vo = np.zeros((N, 2), dtype=np.float32)
    srt = []
    if a.voice and os.path.exists(a.voice):
        for i, (at, text) in enumerate(cues):
            wv = os.path.join(tmp, f"vo{i}.wav")
            T.synth_vo(a.voice, text, wv, scale=0.88)
            with wave.open(wv) as w:
                sr0, d = w.getframerate(), np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
            if sr0 != SR:
                d = np.interp(np.linspace(0, len(d), int(len(d) * SR / sr0), endpoint=False), np.arange(len(d)), d)
            d = d / (np.abs(d).max() + 1e-6) * 0.95
            d = np.stack([d, d], 1)
            T.put(vo, d, at, 1.0)
            srt.append((at, at + len(d) / SR, text))
            print(f"seslendirme {i}: {at:.1f}-{at + len(d) / SR:.1f}s  {text}")
    env = np.convolve(np.abs(vo[:, 0]), np.ones(int(SR * 0.15)) / int(SR * 0.15), mode="same")
    duck = np.convolve(1.0 - 0.5 * np.clip(env * 8, 0, 1), np.ones(int(SR * 0.2)) / int(SR * 0.2), mode="same")
    out = np.tanh((mus * duck[:, None] + mix * 0.9 + vo * 1.15) * 0.95)
    wavp = os.path.join(tmp, "mix.wav")
    with wave.open(wavp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(out, -1, 1) * 32767).astype(np.int16).tobytes())
    T.run("ffmpeg", "-y", "-i", silent, "-i", wavp, "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-c:v", "libx264", "-preset", "slow", "-crf", "23",
          "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", a.out)
    with open(os.path.splitext(a.out)[0] + ".srt", "w", encoding="utf-8") as f:
        for i, (s, e, text) in enumerate(srt, 1):
            f.write(f"{i}\n{T.srt_time(s)} --> {T.srt_time(e)}\n{text}\n\n")
    print("hazır:", a.out, f"({total:.1f} sn)")


if __name__ == "__main__":
    main()
