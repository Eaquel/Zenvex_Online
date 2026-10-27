#!/usr/bin/env python3
"""Tanıtım videosu: oyunun kendi motorundan alınmış gerçek oynanış karelerinden (yapay zekâ video YOK).

Hat:
  1) Kareler:  cd android && ./gradlew :app:recordPaparazziDebug -x cargoBuild --tests '*GameShotsTest.z0*'
               → android/app/build/promo/<klip>/f0000.png … (GameShotsTest.promoClip, temiz mod: arayüz yok)
  2) Bu betik: kareleri 1080p'ye kırpar, renk/vinyet/yazı ekler, sahneleri birleştirir;
               müziği (assets/music), oyunun kendi ses efektlerini (assets/sfx) ve Türkçe seslendirmeyi (Piper) karıştırır.
Çalıştırma:    python3 tools/promo/trailer.py [--voice <model.onnx>] [--out art/promo/zenvex_tanitim.mp4]
Seslendirme:   pip install piper-tts (başka bir ortamdaysa PIPER_PY=<o ortamın python yolu>); Türkçe ses: huggingface.co/rhasspy/piper-voices/tr/tr_TR/dfki/medium
               (model yoksa seslendirme atlanır; metin art/promo/seslendirme.srt olarak yine yazılır).
Gereken:       ffmpeg, python3 (numpy, scipy, pillow).
"""
import argparse
import json
import os
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PROMO = os.path.join(ROOT, "android/app/build/promo")
ASSETS = os.path.join(ROOT, "android/app/src/main/assets")
FONT = os.path.join(ROOT, "android/app/src/main/res/font/display_bold.ttf")
FONT2 = os.path.join(ROOT, "android/app/src/main/res/font/outfit_bold.ttf")
W, H, FPS, SR = 1920, 1080, 30, 44100

# sahne: klip, ilk kare, kare sayısı, üst yazı, seslendirme (başlangıçtan sonra sn), ses efekti türü
SCENES = [
    dict(clip="scythe", start=36, n=114, cap="TIRPAN", sub="Üç vuruşluk kombo", vo="Elinde bir tırpan var. Önünde ise bir sürü.", sfx="slash"),
    dict(clip="rpg", start=24, n=170, cap="ROKETATAR", sub="Her atışta yeni roket", vo="Roketle yık.", sfx="rocket"),
    dict(clip="moto", start=0, n=117, cap="ARAÇLAR", sub="Çölde tam gaz", vo="Çölü motorunla uçarak geç.", sfx="engine"),
    dict(clip="chop", start=6, n=100, cap="ÜRET", sub="Ağaç kes, kaya kır", vo="Kes. Kır. Üret.", sfx="chop", gamma=1.35, lift=0.05),
    dict(clip="magma", start=50, n=125, cap="TİTAN", sub="Her çeyrek saatte bir uyanır", vo="Titan uyandı.", sfx="boss"),
    dict(clip="ice", start=10, n=130, cap="DONMUŞ LİMAN", sub="Buz, bıçak ve nefes", vo="Soğuk, seni durdurmaz.", sfx="slash"),
    dict(clip="aura", start=0, n=110, cap="GÜÇLEN", sub="Rün mühürlü aura", vo="Güçlen. Klanını topla.", sfx="levelup"),
]
INTRO = dict(dur=3.2, vo="Dünya çöktü.", title="ZENVEX", sub="O N L I N E", tag="Hayatta kal. Güçlen. Üssünü savun.")
OUTRO = dict(dur=4.6, vo="Zenvex Online. Hayatta kal. Güçlen. Üssünü savun.", title="ZENVEX", sub="O N L I N E", tag="Şimdi test sürümünde")
XF = 0.35  # sahne geçişi (sn)


def run(*a):
    subprocess.run(list(a), check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def card(path, title, sub, tag, glow=(255, 120, 40)):
    img = Image.new("RGB", (W, H), (8, 6, 10))
    # köşelerde sıcak ışık, ortada kor
    g = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(g)
    d.ellipse([W * .18, H * .55, W * .82, H * 1.35], fill=(70, 22, 8))
    g = g.filter(ImageFilter.GaussianBlur(160))
    img = Image.blend(img, g, 0.9)
    big = ImageFont.truetype(FONT, 250)
    mid = ImageFont.truetype(FONT, 64)
    small = ImageFont.truetype(FONT2, 46)
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lay)
    tw = ld.textlength(title, font=big)
    ld.text(((W - tw) / 2, 250), title, font=big, fill=glow + (255,))
    glowimg = lay.filter(ImageFilter.GaussianBlur(28))
    img = Image.alpha_composite(img.convert("RGBA"), glowimg)
    img = Image.alpha_composite(img, glowimg)
    lay2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(lay2)
    d2.text(((W - tw) / 2, 250), title, font=big, fill=(255, 238, 215, 255), stroke_width=3, stroke_fill=(120, 40, 10, 255))
    sw = d2.textlength(sub, font=mid)
    d2.text(((W - sw) / 2, 540), sub, font=mid, fill=(255, 170, 90, 255))
    gw = d2.textlength(tag, font=small)
    d2.text(((W - gw) / 2, 760), tag, font=small, fill=(230, 220, 210, 255))
    d2.rectangle([W / 2 - 260, 700, W / 2 + 260, 704], fill=(255, 120, 40, 255))
    Image.alpha_composite(img, lay2).convert("RGB").save(path)


def caption_png(path, cap, sub):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    f1 = ImageFont.truetype(FONT, 96)
    f2 = ImageFont.truetype(FONT2, 40)
    # alt koyu şerit (okunurluk)
    band = Image.new("RGBA", (W, 300), (0, 0, 0, 0))
    bd = ImageDraw.Draw(band)
    for y in range(300):
        bd.line([(0, y), (W, y)], fill=(0, 0, 0, int(170 * (y / 300) ** 1.6)))
    lay.alpha_composite(band, (0, H - 300))
    d.text((96, H - 230), cap, font=f1, fill=(255, 240, 220, 255), stroke_width=2, stroke_fill=(110, 40, 10, 255))
    d.text((100, H - 112), sub, font=f2, fill=(255, 170, 90, 255))
    d.rectangle([96, H - 128, 96 + 150, H - 124], fill=(255, 120, 40, 255))
    lay.save(path)


def scene_video(sc, out, tmp):
    n = sc["n"]
    src = os.path.join(PROMO, sc["clip"])
    cap = os.path.join(tmp, f"cap_{sc['clip']}.png")
    caption_png(cap, sc["cap"], sc["sub"])
    dur = n / FPS
    # kırp (2340x1080 → 1920x1080), hafif yaklaşma, renk, vinyet; yazı 0.5 sn'de kayarak gelir
    vf = (
        f"[0:v]crop=1920:1080:210:0,scale=2400:1350:flags=lanczos,"
        f"zoompan=z='1.28+0.14*on/{n}':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s={W}x{H}:fps={FPS},"
        f"eq=contrast=1.12:saturation=1.18:gamma={sc.get('gamma', 0.96)}:brightness={sc.get('lift', 0.0)},vignette=PI/4.2[v];"
        f"[1:v]format=rgba,fade=t=in:st=0.5:d=0.4:alpha=1,fade=t=out:st={dur - 0.7:.2f}:d=0.4:alpha=1[c];"
        f"[v][c]overlay=0:0:shortest=1,fade=t=in:st=0:d={XF},fade=t=out:st={dur - XF:.2f}:d={XF}"
    )
    run("ffmpeg", "-y", "-framerate", str(FPS), "-start_number", str(sc["start"]), "-i", os.path.join(src, "f%04d.png"),
        "-loop", "1", "-i", cap, "-filter_complex", vf, "-frames:v", str(n), "-r", str(FPS),
        "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-pix_fmt", "yuv420p", out)
    return dur


def card_video(png, dur, out):
    n = int(dur * FPS)
    vf = (f"zoompan=z='1+0.05*on/{n}':d={n}:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s={W}x{H}:fps={FPS},"
          f"fade=t=in:st=0:d=0.6,fade=t=out:st={dur - 0.5:.2f}:d=0.5")
    run("ffmpeg", "-y", "-loop", "1", "-i", png, "-vf", vf, "-frames:v", str(n), "-c:v", "libx264", "-preset", "medium",
        "-crf", "17", "-pix_fmt", "yuv420p", out)


def load_audio(path, tmp):
    wav = os.path.join(tmp, os.path.basename(path) + ".wav")
    if not os.path.exists(wav):
        run("ffmpeg", "-y", "-i", path, "-ar", str(SR), "-ac", "2", "-sample_fmt", "s16", wav)
    with wave.open(wav) as w:
        d = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
    return d.reshape(-1, 2)


def onsets(clip, start, n, k=4, min_gap=0.45):
    """Karelerdeki hareket yoğunluğunun tepe noktaları: vuruş/patlama anları (saniye, güç)."""
    prev, e = None, []
    for i in range(start, start + n):
        im = Image.open(os.path.join(PROMO, clip, f"f{i:04d}.png")).convert("L").resize((234, 108))
        a = np.asarray(im, dtype=np.float32)
        e.append(0.0 if prev is None else float(np.abs(a - prev).mean()))
        prev = a
    e = np.array(e)
    e = np.convolve(e, np.ones(3) / 3, mode="same")
    out, last = [], -9.0
    order = np.argsort(-e)
    for i in order:
        t = i / FPS
        if all(abs(t - o[0]) >= min_gap for o in out) and e[i] > e.mean() * 1.25:
            out.append((t, float(e[i] / e.max())))
        if len(out) >= k:
            break
    return sorted(out)


def put(buf, snd, t, gain=1.0, pan=0.0):
    i = int(t * SR)
    if i < 0 or i >= len(buf):
        return
    m = min(len(snd), len(buf) - i)
    g = np.array([1.0 - max(0.0, pan), 1.0 + min(0.0, pan)]) * gain
    buf[i:i + m] += snd[:m] * g


def synth_vo(model, text, wav, scale=0.95):
    subprocess.run([os.environ.get("PIPER_PY", sys.executable), "-m", "piper", "-m", model, "--length-scale", str(scale), "-f", wav], input=text.encode(),
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def srt_time(t):
    return f"{int(t // 3600):02d}:{int(t % 3600 // 60):02d}:{int(t % 60):02d},{int(t % 1 * 1000):03d}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default=os.environ.get("PIPER_MODEL", ""))
    ap.add_argument("--out", default=os.path.join(ROOT, "art/promo/zenvex_tanitim.mp4"))
    ap.add_argument("--tmp", default="/tmp/zenvex_trailer")
    a = ap.parse_args()
    tmp = a.tmp
    os.makedirs(tmp, exist_ok=True)
    os.makedirs(os.path.dirname(a.out), exist_ok=True)

    # --- görüntü ---
    parts, starts, t = [], [], 0.0
    ip, op = os.path.join(tmp, "intro.png"), os.path.join(tmp, "outro.png")
    card(ip, INTRO["title"], INTRO["sub"], INTRO["tag"])
    card(op, OUTRO["title"], OUTRO["sub"], OUTRO["tag"])
    v = os.path.join(tmp, "intro.mp4")
    card_video(ip, INTRO["dur"], v)
    parts.append(v)
    starts.append(0.0)
    t = INTRO["dur"]
    sc_info = []
    for i, sc in enumerate(SCENES):
        v = os.path.join(tmp, f"s{i}.mp4")
        dur = scene_video(sc, v, tmp)
        parts.append(v)
        starts.append(t)
        sc_info.append((t, dur))
        t += dur
    v = os.path.join(tmp, "outro.mp4")
    card_video(op, OUTRO["dur"], v)
    parts.append(v)
    starts.append(t)
    total = t + OUTRO["dur"]
    lst = os.path.join(tmp, "list.txt")
    open(lst, "w").write("".join(f"file '{p}'\n" for p in parts))
    silent = os.path.join(tmp, "video.mp4")
    run("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", silent)

    # --- ses ---
    N = int(total * SR)
    mix = np.zeros((N, 2), dtype=np.float32)
    music = load_audio(os.path.join(ASSETS, "music/music_game.ogg"), tmp)
    reps = int(np.ceil(N / len(music)))
    mus = np.tile(music, (reps, 1))[:N] * 0.55
    fade = np.minimum(1.0, np.minimum(np.arange(N) / (SR * 1.5), (N - np.arange(N)) / (SR * 2.5)))[:, None]
    mus = mus * fade

    sfx = {k: load_audio(os.path.join(ASSETS, "sfx", f), tmp) for k, f in dict(
        swing="swing.wav", hit="hit.ogg", rocket="rocket.wav", boom="explosion.wav", engine="engine.wav", chop="chop.ogg",
        boss="boss.wav", lvl="levelup.wav", thunder="thunder.wav", zd="zdeath.wav", tele="teleport.wav", groan="groan0.wav",
        reload="reload.wav", step="step0.ogg").items()}
    put(mix, sfx["thunder"], 0.2, 0.9)
    put(mix, sfx["boom"], INTRO["dur"] - 0.35, 0.8)  # başlıktan oyuna sert geçiş
    for (st, dur), sc in zip(sc_info, SCENES):
        ons = onsets(sc["clip"], sc["start"], sc["n"], k=6)
        kind = sc["sfx"]
        for j, (ot, pw) in enumerate(ons):
            at = st + ot
            if kind == "slash":
                put(mix, sfx["swing"], at - 0.05, 0.9 * pw + 0.3)
                put(mix, sfx["hit"], at + 0.07, 0.6 * pw + 0.2)
                if j % 2 == 0:
                    put(mix, sfx["zd"], at + 0.15, 0.35)
            elif kind == "rocket":
                put(mix, sfx["rocket"], at - 0.1, 0.8)
                put(mix, sfx["boom"], at + 0.45, 0.75 * pw + 0.2)
            elif kind == "chop":
                put(mix, sfx["chop"], at, 0.9)
            elif kind == "boss":
                put(mix, sfx["swing"], at - 0.05, 0.7)
                put(mix, sfx["boom"], at + 0.1, 0.45 * pw + 0.2)
        if kind == "engine":
            for k in range(int(dur / 1.5) + 1):
                put(mix, sfx["engine"], st + k * 1.4, 0.55)
        if kind == "boss":
            put(mix, sfx["boss"], st + 0.2, 0.9)
        if kind == "levelup":
            put(mix, sfx["lvl"], st + 0.4, 0.8)
            put(mix, sfx["tele"], st + 1.7, 0.5)

    # --- seslendirme ---
    cues = [(0.5, INTRO["vo"])] + [(st + 0.5, sc["vo"]) for (st, _), sc in zip(sc_info, SCENES)] + [(starts[-1] + 0.7, OUTRO["vo"])]
    vo = np.zeros((N, 2), dtype=np.float32)
    srt = []
    if a.voice and os.path.exists(a.voice):
        for i, (at, text) in enumerate(cues):
            wv = os.path.join(tmp, f"vo{i}.wav")
            synth_vo(a.voice, text, wv)
            with wave.open(wv) as w:
                sr0, d = w.getframerate(), np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
            if sr0 != SR:
                d = np.interp(np.linspace(0, len(d), int(len(d) * SR / sr0), endpoint=False), np.arange(len(d)), d)
            d = d / (np.abs(d).max() + 1e-6) * 0.95
            d = np.stack([d, d], 1)
            put(vo, d, at, 1.0)
            srt.append((at, at + len(d) / SR, text))
            print(f"seslendirme {i}: {at:.1f}-{at + len(d) / SR:.1f}s  {text}")
    else:
        for at, text in cues:
            srt.append((at, at + 0.35 + 0.075 * len(text), text))
        print("Piper modeli yok: seslendirme atlandı (yalnız altyazı)")
    # müzik, konuşma sırasında kısılır
    env = np.abs(vo[:, 0])
    env = np.convolve(env, np.ones(int(SR * 0.15)) / int(SR * 0.15), mode="same")
    duck = 1.0 - 0.55 * np.clip(env * 8, 0, 1)
    duck = np.convolve(duck, np.ones(int(SR * 0.2)) / int(SR * 0.2), mode="same")
    out = mus * duck[:, None] + mix * 0.9 + vo * 1.15
    out = np.tanh(out * 0.95)
    pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16)
    wavp = os.path.join(tmp, "mix.wav")
    with wave.open(wavp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    run("ffmpeg", "-y", "-i", silent, "-i", wavp, "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-c:v", "libx264", "-preset", "slow", "-crf", "25", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
        "-shortest", "-movflags", "+faststart", a.out)
    with open(os.path.splitext(a.out)[0].replace("zenvex_tanitim", "seslendirme") + ".srt", "w", encoding="utf-8") as f:
        for i, (s, e, text) in enumerate(srt, 1):
            f.write(f"{i}\n{srt_time(s)} --> {srt_time(e)}\n{text}\n\n")
    print("hazır:", a.out, f"({total:.1f} sn)")


if __name__ == "__main__":
    main()
