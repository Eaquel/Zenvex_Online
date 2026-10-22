#!/usr/bin/env python3
"""V2 ses üretici: zengin efektler (silahlar, patlama, motor, toplama, büyü...) + döngüsel müzikler.

FFT tabanlı süzgeçler ve konvolüsyon yankısı kullanır (hızlı, numpy yeterli).
Çıktı: android/app/src/main/assets/sfx/*.wav (44.1 kHz mono) ve assets/music/*.ogg (ffmpeg/libvorbis).
"""
import os
import subprocess
import wave

# Bu adlar artık assets/sfx/*.ogg (Kenney, CC0); üretici üzerine yazmasın
KENNEY = {"click", "coin", "pickup", "chop", "step0", "step1", "crate", "craft", "quest", "car_hit", "hit"}

import numpy as np

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "sfx")
MUS = os.path.join(ROOT, "android", "app", "src", "main", "assets", "music")
rng = np.random.default_rng(2026)


def t(d):
    return np.arange(int(round(SR * d))) / SR


def noise(d):
    return rng.standard_normal(int(round(SR * d)))


def env(n, a=0.005, curve=4.0, hold=0.0):
    e = np.ones(n)
    ai = max(1, int(round(SR * a)))
    hi = int(round(SR * hold))
    e[:ai] = np.linspace(0, 1, ai)
    k = np.linspace(0, 1, max(1, n - ai - hi))
    e[ai + hi:] = (1 - k) ** curve
    return e


def adsr(n, a, d, s, r):
    ai, di, ri = int(round(SR * a)), int(round(SR * d)), int(round(SR * r))
    si = max(0, n - ai - di - ri)
    return np.concatenate([np.linspace(0, 1, ai), np.linspace(1, s, di), np.full(si, s), np.linspace(s, 0, ri)])[:n]


def filt(x, lo=None, hi=None, order=2.0):
    """FFT bant süzgeci (yumuşak kenarlı)."""
    n = len(x)
    F = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = np.ones_like(f)
    if hi:
        g *= 1 / (1 + (f / hi) ** (2 * order))
    if lo:
        g *= 1 / (1 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    return np.fft.irfft(F * g, n)


def sweep(f0, f1, d, shape="sine", curve="exp"):
    tt = t(d)
    f = np.geomspace(f0, f1, len(tt)) if curve == "exp" else np.linspace(f0, f1, len(tt))
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    if shape == "square":
        return np.tanh(np.sin(ph) * 4)
    if shape == "tri":
        return 2 * np.abs(2 * ((ph / (2 * np.pi)) % 1.0) - 1) - 1
    return np.sin(ph)


def reverb(x, size=1.2, mix=0.25, damp=3000):
    ir = noise(size) * np.exp(-np.linspace(0, 6, int(round(SR * size))))
    ir = filt(ir, hi=damp)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    n = len(x) + len(ir)
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)[: len(x) + len(ir) // 2]
    out = np.pad(x, (0, len(wet) - len(x)))
    return out * (1 - mix) + wet * mix * 0.6


def norm(x, peak=0.92):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def fade(x, a=0.002, r=0.01):
    ai, ri = int(round(SR * a)), int(round(SR * r))
    x = x.copy()
    if ai:
        x[:ai] *= np.linspace(0, 1, ai)
    if ri:
        x[-ri:] *= np.linspace(1, 0, ri)
    return x


def save(name, x, peak=0.92):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(norm(fade(x), peak), -1, 1)
    if name in KENNEY:  # yerine CC0 Kenney sesi kondu (assets/licenses/Kenney-CC0.txt)
        return
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())
    print(" ", name, f"{len(x) / SR:.2f}s")


def mixlen(*xs):
    n = max(len(x) for x in xs)
    return sum(np.pad(x, (0, n - len(x))) for x in xs)


# =========================================================================== efektler
def gunshot(body=120, crack=1.0, tail=0.5, d=0.6):
    n = int(round(SR * d))
    c = filt(noise(d), lo=1500, hi=9000) * env(n, 0.0005, 22) * crack
    b = filt(noise(d), lo=60, hi=1200) * env(n, 0.001, 7)
    th = sweep(body, 40, d) * env(n, 0.001, 9) * 1.2
    x = c + b + th
    return reverb(np.tanh(x * 2.5), 0.9, tail, 5000)


def shot_pistol():
    return gunshot(160, 1.0, 0.35, 0.45)


def shot_rifle():
    return gunshot(110, 1.2, 0.45, 0.55)


def shot_sniper():
    x = gunshot(80, 1.6, 0.7, 1.2)
    return x


def rocket():
    d = 1.0
    n = int(round(SR * d))
    whoosh = filt(noise(d), lo=200, hi=np.inf) * 0
    w = filt(noise(d), lo=300, hi=2500) * np.linspace(1, 0.2, n) ** 1.5
    thump = sweep(90, 50, 0.3) * env(int(round(SR * 0.3)), 0.002, 4)
    return reverb(mixlen(w * 0.9, thump), 0.8, 0.3) + whoosh[: 1]


def explosion():
    d = 2.2
    n = int(round(SR * d))
    boom = filt(noise(d), hi=180) * env(n, 0.003, 2.2) * 3
    mid = filt(noise(d), lo=150, hi=2000) * env(n, 0.002, 5)
    crack = filt(noise(0.3), lo=2000) * env(int(round(SR * 0.3)), 0.0005, 12)
    sub = sweep(60, 25, d) * env(n, 0.005, 2.5) * 1.5
    debris = np.zeros(n)
    for _ in range(30):
        p = rng.integers(int(round(SR * 0.2)), n - 2000)
        dd = rng.integers(200, 1200)
        debris[p:p + dd] += filt(noise(dd / SR), lo=1500, hi=6000) * env(dd, 0.0005, 6) * rng.uniform(0.05, 0.3)
    x = mixlen(boom, mid, crack, sub, debris)
    return reverb(np.tanh(x * 1.6), 1.8, 0.35, 2500)


def reload():
    d = 0.9
    out = np.zeros(int(round(SR * d)))
    for at, f, a in ((0.0, 2400, 0.8), (0.18, 1800, 0.6), (0.55, 3000, 1.0), (0.62, 1200, 0.9)):
        dd = 0.06
        cl = filt(noise(dd), lo=f * 0.6, hi=f * 1.6) * env(int(round(SR * dd)), 0.0005, 10) * a
        tt = t(dd)
        cl += np.sin(2 * np.pi * f * 0.5 * tt) * np.exp(-tt * 90) * a * 0.5
        p = int(round(SR * at))
        out[p:p + len(cl)] += cl
    return reverb(out, 0.3, 0.15, 6000)


def empty():
    d = 0.12
    tt = t(d)
    return filt(noise(d), lo=2500, hi=8000) * env(len(tt), 0.0005, 18) + np.sin(2 * np.pi * 1800 * tt) * np.exp(-tt * 120) * 0.4


def swing():
    d = 0.22
    n = noise(d)
    x = filt(n, lo=300, hi=2600) * np.sin(np.linspace(0, np.pi, len(n))) ** 2
    return x


def hit():
    d = 0.2
    n = int(round(SR * d))
    thud = sweep(150, 50, d) * env(n, 0.001, 6)
    slap = filt(noise(d), lo=400, hi=4000) * env(n, 0.0005, 16)
    return np.tanh((thud * 1.2 + slap) * 1.5)


def chop():
    d = 0.35
    tt = t(d)
    wood = sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt * k) for f, a, k in ((420, 1.0, 22), (760, 0.6, 30), (1240, 0.35, 42), (190, 0.7, 18)))
    crack = filt(noise(d), lo=1500, hi=6000) * env(len(tt), 0.0005, 20)
    return reverb(wood + crack, 0.6, 0.2)


def treefall():
    d1 = 0.8
    tt = t(d1)
    f = 120 + 40 * np.sin(2 * np.pi * 6 * tt) + np.linspace(0, -50, len(tt))
    creak = 2 * ((np.cumsum(f) / SR) % 1.0) - 1
    creak = filt(creak, lo=300, hi=2200) * np.linspace(0.1, 1, len(tt)) * 0.5
    d2 = 1.4
    n2 = int(round(SR * d2))
    crash = filt(noise(d2), hi=2500) * env(n2, 0.003, 3)
    leaves = filt(noise(d2), lo=2000, hi=8000) * env(n2, 0.05, 2) * 0.4
    thump = sweep(80, 30, d2) * env(n2, 0.002, 3) * 1.4
    return reverb(np.concatenate([creak, crash + leaves + thump]), 1.2, 0.3)


def groan(seed=0, d=1.1):
    tt = t(d)
    f0 = 85 + seed * 14
    vib = 10 * np.sin(2 * np.pi * (4 + seed) * tt) + 6 * np.sin(2 * np.pi * 11 * tt)
    f = f0 + vib + np.linspace(15, -25, len(tt))
    saw = 2 * ((np.cumsum(f) / SR) % 1.0) - 1
    voice = filt(saw, lo=300, hi=900) + 0.6 * filt(saw, lo=1100, hi=1700) + 0.3 * filt(saw, lo=2300, hi=2800)
    breath = filt(noise(d), lo=400, hi=3000) * 0.25
    rough = 1 + 0.4 * filt(noise(d), hi=60)
    e = np.sin(np.pi * np.linspace(0, 1, len(tt))) ** 0.6
    return reverb((voice * rough + breath) * e, 0.7, 0.2)


def zattack():
    d = 0.45
    return mixlen(groan(2, d) * 0.8, filt(noise(d), lo=600, hi=3500) * env(int(round(SR * d)), 0.01, 3) * 0.6)


def zdeath():
    d = 0.9
    g = groan(1, d)
    x = g * np.linspace(1, 0.2, len(g))
    thud = sweep(110, 35, 0.5) * env(int(round(SR * 0.5)), 0.003, 4)
    return mixlen(x, np.pad(thud, (int(round(SR * 0.35)), 0)))


def hurt():
    d = 0.3
    x = sweep(360, 160, d, "saw")
    x = filt(x, lo=250, hi=1600) * env(int(round(SR * d)), 0.005, 2.5)
    return mixlen(x, hit() * 0.5)


def death():
    d = 1.8
    x = sweep(260, 50, d, "saw")
    x = filt(x, lo=80, hi=900) * env(int(round(SR * d)), 0.01, 1.4)
    return reverb(x, 1.6, 0.4)


def chime(notes, step=0.09, last=0.6, wave="bell"):
    out = []
    for i, f in enumerate(notes):
        d = step if i < len(notes) - 1 else last
        tt = t(max(d, 0.4))
        if wave == "bell":
            s = sum(a * np.sin(2 * np.pi * f * m * tt) * np.exp(-tt * (3 + 2 * m)) for m, a in ((1, 1), (2.01, 0.5), (3.0, 0.25), (4.2, 0.12)))
        else:
            s = np.sin(2 * np.pi * f * tt) * np.exp(-tt * 5)
        seg = np.zeros(int(SR * (step * (len(notes) - 1) + last + 0.4)))
        p = int(SR * step * i)
        seg[p:p + len(tt)] += s[: len(seg) - p]
        out.append(seg)
    return reverb(mixlen(*out), 1.2, 0.3, 6000)


def levelup():
    return chime([523.25, 659.25, 783.99, 1046.5, 1318.5], 0.08, 0.9)


def quest():
    return chime([783.99, 987.77, 1174.66, 1567.98], 0.1, 0.9)


def coin():
    return chime([1318.5, 1975.5], 0.06, 0.25)


def pickup():
    d = 0.18
    return reverb(sweep(500, 1400, d, "tri") * env(int(round(SR * d)), 0.002, 2) * 0.7, 0.3, 0.2)


def craft():
    out = np.zeros(int(round(SR * 1.0)))
    for k, at in enumerate((0.0, 0.25, 0.5)):
        dd = 0.25
        tt = t(dd)
        s = sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt * 25) for f, a in ((1800, 0.8), (2900, 0.5), (4200, 0.3)))
        s += filt(noise(dd), lo=2000) * env(len(tt), 0.0005, 20) * 0.5
        p = int(round(SR * at))
        out[p:p + len(s)] += s
    return reverb(mixlen(out, np.pad(chime([880, 1318.5], 0.05, 0.3) * 0.4, (int(round(SR * 0.6)), 0))), 0.5, 0.2)


def click():
    d = 0.06
    tt = t(d)
    return (np.sin(2 * np.pi * 1200 * tt) * np.exp(-tt * 80) + filt(noise(d), lo=3000) * env(len(tt), 0.0003, 30) * 0.3) * 0.6


def boss():
    d = 3.0
    n = int(round(SR * d))
    horn = sum(filt(sweep(f, f * 0.98, d, "saw"), hi=900) for f in (55, 55.4, 82.4, 110))
    horn *= adsr(n, 0.3, 0.5, 0.7, 1.5)
    drum = np.zeros(n)
    for at in (0.0, 0.6, 1.2, 1.5, 1.8):
        hitn = sweep(70, 30, 0.6) * env(int(round(SR * 0.6)), 0.002, 3)
        p = int(round(SR * at))
        drum[p:p + len(hitn)] += hitn[: n - p]
    return reverb(horn * 0.5 + drum, 2.0, 0.35, 2000)


def toxic():
    d = 1.4
    n = int(round(SR * d))
    bub = np.zeros(n)
    for _ in range(40):
        p = rng.integers(0, n - 3000)
        dd = rng.integers(600, 2500)
        f0 = rng.uniform(200, 700)
        bub[p:p + dd] += sweep(f0, f0 * 2.2, dd / SR) * env(dd, 0.002, 3) * rng.uniform(0.2, 0.6)
    hiss = filt(noise(d), lo=2000, hi=7000) * env(n, 0.05, 2) * 0.4
    return reverb(bub + hiss, 0.8, 0.3)


def teleport():
    d = 0.8
    n = int(round(SR * d))
    s = sweep(200, 2400, d, "sine") * env(n, 0.01, 2)
    s2 = sweep(2400, 200, d, "sine") * env(n, 0.01, 2) * 0.5
    sh = filt(noise(d), lo=3000) * env(n, 0.01, 3) * 0.3
    return reverb((s + s2) * (0.6 + 0.4 * np.sin(2 * np.pi * 30 * t(d))) + sh, 1.0, 0.4)


def shield():
    d = 1.2
    n = int(round(SR * d))
    tone = sum(np.sin(2 * np.pi * f * t(d)) * a for f, a in ((440, 1), (660, 0.6), (880, 0.4), (1320, 0.2)))
    return reverb(tone * adsr(n, 0.05, 0.2, 0.5, 0.8) * (1 + 0.3 * np.sin(2 * np.pi * 6 * t(d))), 1.4, 0.4, 7000)


def heal():
    return chime([659.25, 880, 1108.7, 1318.5], 0.07, 0.8)


def rage():
    d = 1.0
    x = sweep(90, 60, d, "saw") + sweep(91, 61, d, "saw")
    x = filt(x, hi=1400) * env(int(round(SR * d)), 0.02, 2)
    return reverb(mixlen(np.tanh(x * 3), groan(0, d) * 0.6), 0.8, 0.3)


def engine():
    d = 1.6
    tt = t(d)
    f = np.linspace(28, 46, len(tt))
    ph = np.cumsum(f) / SR
    pulses = np.sin(2 * np.pi * ph) ** 9 + 0.5 * np.sin(2 * np.pi * ph * 2) ** 9
    x = filt(pulses + filt(noise(d), hi=800) * 0.3, hi=1500) * adsr(len(tt), 0.05, 0.3, 0.8, 0.5)
    return np.tanh(x * 2)


def car_hit():
    d = 0.7
    n = int(round(SR * d))
    metal = sum(np.sin(2 * np.pi * f * t(d)) * np.exp(-t(d) * k) for f, k in ((310, 9), (530, 12), (870, 14), (1400, 18)))
    crunch = filt(noise(d), lo=300, hi=5000) * env(n, 0.001, 6)
    return reverb(np.tanh((metal * 0.6 + crunch) * 2), 0.6, 0.2)


def crate():
    d = 0.6
    n = int(round(SR * d))
    wood = sum(np.sin(2 * np.pi * f * t(d)) * np.exp(-t(d) * k) for f, k in ((260, 16), (480, 22), (900, 30)))
    crack = filt(noise(d), lo=800, hi=6000) * env(n, 0.001, 8)
    return reverb(wood + crack + np.pad(coin() * 0.3, (int(round(SR * 0.25)), 0))[:n], 0.5, 0.2)


def step(seed):
    d = 0.09
    x = filt(noise(d), lo=200 + seed * 120, hi=1800) * env(int(round(SR * d)), 0.002, 6)
    return x * 0.5


def drone():
    d = 0.18
    return mixlen(filt(noise(d), lo=2000, hi=8000) * env(int(round(SR * d)), 0.0005, 14) * 0.6, gunshot(300, 0.6, 0.1, 0.18) * 0.5)


def sniper_far():
    return gunshot(90, 0.8, 0.8, 1.0)


# =========================================================================== müzik
NOTE = {n: 440 * 2 ** ((i - 9) / 12) for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def hz(name, octave):
    return NOTE[name] * 2 ** (octave - 4)


def pad(freqs, d, bright=900):
    tt = t(d)
    x = np.zeros(len(tt))
    for f in freqs:
        for det in (-0.12, 0.0, 0.11):
            ff = f * 2 ** (det / 12)
            x += 2 * ((ff * tt + rng.random()) % 1.0) - 1
    x = filt(x, hi=bright, order=1.5)
    return x * adsr(len(tt), d * 0.3, 0.1, 0.9, d * 0.35)


def pluck(f, d=1.2, bright=3000):
    tt = t(d)
    s = sum(a * np.sin(2 * np.pi * f * m * tt) * np.exp(-tt * (2.5 + m * 1.5)) for m, a in ((1, 1), (2, 0.45), (3, 0.2), (4, 0.1)))
    return filt(s, hi=bright) * env(len(tt), 0.003, 1.5)


def kick(d=0.5):
    return sweep(110, 38, d) * env(int(round(SR * d)), 0.002, 4)


def taiko(d=0.9):
    return (sweep(90, 45, d) + filt(noise(d), hi=400) * 0.5) * env(int(round(SR * d)), 0.002, 3)


def place(buf, x, at):
    p = int(round(SR * at))
    if p >= len(buf):
        return
    n = min(len(x), len(buf) - p)
    buf[p:p + n] += x[:n]


def music_lobby():
    """Karanlık, atmosferik ana tema (Am - F - C - G üzerine), ~64 sn döngü."""
    bpm = 72
    beat = 60 / bpm
    bars = 16
    L = bars * 4 * beat
    out = np.zeros(int(SR * L) + SR * 4)
    prog = [("A", ["A", "C", "E"]), ("F", ["F", "A", "C"]), ("C", ["C", "E", "G"]), ("G", ["G", "B", "D"])]
    for b in range(bars):
        root, ch = prog[b % 4]
        at = b * 4 * beat
        place(out, pad([hz(n, 3) for n in ch] + [hz(root, 2)], 4 * beat + 1.0) * 0.18, at)
        # bas
        place(out, filt(sweep(hz(root, 1), hz(root, 1), 4 * beat, "saw"), hi=220) * adsr(int(round(SR * 4 * beat)), 0.2, 0.3, 0.7, 0.6) * 0.35, at)
        # arpej (ikinci yarıdan itibaren)
        if b >= 4:
            seq = [ch[0], ch[1], ch[2], ch[1], ch[2], ch[0], ch[1], ch[2]]
            for k, n in enumerate(seq):
                place(out, pluck(hz(n, 5 if k % 4 else 4), 1.4) * 0.16, at + k * beat / 2)
        # davul (dramatik, seyrek)
        if b >= 8:
            place(out, taiko() * 0.5, at)
            place(out, taiko() * 0.35, at + 2.5 * beat)
        if b % 4 == 3:
            place(out, filt(noise(1.5), lo=3000, hi=9000) * adsr(int(round(SR * 1.5)), 1.2, 0.1, 0.5, 0.2) * 0.05, at + 2 * beat)
    # melodi (son 8 ölçü)
    mel = [("E", 5, 2), ("D", 5, 1), ("C", 5, 1), ("A", 4, 4), ("F", 4, 2), ("A", 4, 1), ("C", 5, 1), ("D", 5, 4),
           ("E", 5, 2), ("G", 5, 1), ("E", 5, 1), ("D", 5, 3), ("C", 5, 1), ("B", 4, 4), ("D", 5, 2), ("C", 5, 2)]
    at = 8 * 4 * beat
    for n, o, dur in mel:
        d = dur * beat
        tt = t(d + 0.6)
        f = hz(n, o)
        v = f * (1 + 0.004 * np.sin(2 * np.pi * 5 * tt))
        s = np.sin(2 * np.pi * np.cumsum(v) / SR) + 0.3 * np.sin(4 * np.pi * np.cumsum(v) / SR)
        place(out, filt(s, hi=2500) * adsr(len(tt), 0.08, 0.2, 0.7, 0.5) * 0.12, at)
        at += d
    out = reverb(out, 3.0, 0.45, 3500)
    # döngü için sonu başa kat
    n = int(SR * L)
    tail = out[n:n + SR * 3]
    out = out[:n].copy()
    out[:len(tail)] += tail * np.linspace(1, 0, len(tail))
    return out


def music_game():
    """Gergin keşif müziği: ostinato + nabız + seyrek perküsyon (Dm), ~60 sn döngü."""
    bpm = 96
    beat = 60 / bpm
    bars = 24
    L = bars * 4 * beat
    out = np.zeros(int(SR * L) + SR * 4)
    prog = [("D", ["D", "F", "A"]), ("D", ["D", "F", "A"]), ("A#", ["A#", "D", "F"]), ("A", ["A", "C#", "E"])]
    for b in range(bars):
        root, ch = prog[b % 4]
        at = b * 4 * beat
        place(out, pad([hz(n, 3) for n in ch], 4 * beat + 0.6, 600) * 0.12, at)
        # ostinato (16'lık)
        for k in range(16):
            n = [root, ch[2], ch[1], ch[2]][k % 4]
            o = 3 if k % 4 == 0 else 4
            d = beat / 4
            s = filt(sweep(hz(n, o), hz(n, o), d * 0.9, "saw"), hi=1400 + 600 * np.sin(b / 3)) * env(int(round(SR * d * 0.9)), 0.003, 3)
            place(out, s * 0.07, at + k * d)
        # nabız: kick + sub
        for k in range(4):
            place(out, kick() * (0.4 if b >= 4 else 0.25), at + k * beat)
        if b >= 8:
            for k in (1, 3):
                place(out, filt(noise(0.2), lo=1500, hi=7000) * env(int(round(SR * 0.2)), 0.001, 10) * 0.12, at + k * beat)
        if b >= 12 and b % 2 == 1:
            place(out, taiko() * 0.4, at + 3.5 * beat)
        # gerilim teli
        if b % 8 == 7:
            place(out, filt(sweep(hz("A", 5), hz("A#", 5), 4 * beat, "saw"), hi=3000) * adsr(int(round(SR * 4 * beat)), 2, 0.5, 0.8, 1) * 0.05, at)
    out = reverb(out, 2.0, 0.3, 4000)
    n = int(SR * L)
    tail = out[n:n + SR * 2]
    out = out[:n].copy()
    out[:len(tail)] += tail * np.linspace(1, 0, len(tail))
    return out


def save_music(name, x):
    os.makedirs(MUS, exist_ok=True)
    x = np.clip(norm(x, 0.85), -1, 1)
    tmp = os.path.join(MUS, name + ".wav")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "4", os.path.join(MUS, name + ".ogg")], check=True)
    os.remove(tmp)
    print(" ", name, f"{len(x) / SR:.1f}s")


def main():
    print("ses efektleri...")
    for name, fn in [
        ("swing", swing), ("hit", hit), ("chop", chop), ("treefall", treefall), ("zattack", zattack), ("zdeath", zdeath),
        ("hurt", hurt), ("levelup", levelup), ("click", click), ("death", death), ("shot_pistol", shot_pistol),
        ("shot_rifle", shot_rifle), ("shot_sniper", shot_sniper), ("rocket", rocket), ("explosion", explosion), ("reload", reload),
        ("empty", empty), ("pickup", pickup), ("coin", coin), ("craft", craft), ("boss", boss), ("toxic", toxic),
        ("teleport", teleport), ("shield", shield), ("heal", heal), ("rage", rage), ("engine", engine), ("car_hit", car_hit),
        ("crate", crate), ("quest", quest), ("drone", drone),
    ]:
        save(name, fn())
    for i, s in enumerate((0, 1, 3)):
        save(f"groan{i}", groan(s))
    for i in range(2):
        save(f"step{i}", step(i), 0.5)
    print("müzik...")
    save_music("music_lobby", music_lobby())
    save_music("music_game", music_game())


if __name__ == "__main__":
    main()
