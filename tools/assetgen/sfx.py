#!/usr/bin/env python3
"""Ses efektlerini kodla sentezler -> android/app/src/main/assets/sfx/*.wav (22050 Hz, mono, 16 bit)."""
import os
import wave
import numpy as np

# Bu adlar artık assets/sfx/*.ogg (Kenney, CC0); üretici üzerine yazmasın
KENNEY = {"click", "coin", "pickup", "chop", "step0", "step1", "crate", "craft", "quest", "car_hit", "hit"}

SR = 22050
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "sfx")
rng = np.random.default_rng(7)


def t(d):
    return np.arange(int(SR * d)) / SR


def env(n, attack=0.005, decay=None, curve=4.0):
    e = np.ones(n)
    a = max(1, int(SR * attack))
    e[:a] = np.linspace(0, 1, a)
    k = np.linspace(0, 1, n - a)
    e[a:] = (1 - k) ** curve
    return e


def noise(d):
    return rng.uniform(-1, 1, int(SR * d))


def lowpass(x, cutoff):
    """Tek kutuplu alçak geçiren; cutoff dizi olabilir (süpürme)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, float), x.shape)
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def bandpass(x, lo, hi):
    return lowpass(x, hi) - lowpass(x, lo)


def sweep_sine(f0, f1, d, shape="sine"):
    tt = t(d)
    f = np.geomspace(f0, f1, len(tt))
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    if shape == "square":
        return np.sign(np.sin(ph))
    return np.sin(ph)


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def save(name, x):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(norm(x), -1, 1)
    data = (x * 32767).astype("<i2").tobytes()
    if name in KENNEY:  # yerine CC0 Kenney sesi kondu (assets/licenses/Kenney-CC0.txt)
        return
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)
    print(" ", name, f"{len(x) / SR:.2f}s")


def swing():
    d = 0.16
    n = noise(d)
    cut = np.geomspace(3500, 600, len(n))
    x = bandpass(n, cut * 0.4, cut) * env(len(n), 0.03, curve=2)
    return x * 0.6


def hit():
    d = 0.14
    body = sweep_sine(140, 45, d) * env(int(SR * d), 0.002, curve=3)
    click = lowpass(noise(d), 2500) * env(int(SR * d), 0.001, curve=14)
    return body + 0.7 * click


def chop():
    d = 0.18
    tt = t(d)
    x = sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt * k) for f, a, k in
            ((520, 1.0, 30), (910, 0.6, 38), (1460, 0.35, 50), (230, 0.6, 22)))
    x += lowpass(noise(d), 3000) * env(len(tt), 0.001, curve=18) * 0.8
    return x


def treefall():
    d1 = 0.55
    tt = t(d1)
    f = 140 + 30 * np.sin(2 * np.pi * 7 * tt) + np.linspace(0, -60, len(tt))
    creak = 2 * ((np.cumsum(f) / SR) % 1.0) - 1
    creak = bandpass(creak, 300, 1800) * np.linspace(0.2, 1, len(tt)) * 0.5
    d2 = 0.7
    crash = lowpass(noise(d2), np.geomspace(2500, 300, int(SR * d2))) * env(int(SR * d2), 0.004, curve=2.5)
    thump = sweep_sine(90, 35, d2) * env(int(SR * d2), 0.002, curve=3)
    return np.concatenate([creak, crash + thump * 0.9])


def groan(seed=0, d=0.85):
    tt = t(d)
    f0 = 95 + seed * 12
    f = f0 + 12 * np.sin(2 * np.pi * 4.5 * tt) + np.linspace(10, -20, len(tt))
    saw = 2 * ((np.cumsum(f) / SR) % 1.0) - 1
    x = bandpass(saw, 350, 900) + 0.5 * bandpass(saw, 900, 1600)
    x += 0.15 * bandpass(noise(d), 300, 1500)
    e = np.sin(np.pi * np.linspace(0, 1, len(tt))) ** 0.7
    return x * e


def zattack():
    d = 0.3
    x = groan(2, d) * 0.8 + bandpass(noise(d), 600, 3000) * env(int(SR * d), 0.01, curve=3) * 0.6
    return x


def hurt():
    d = 0.22
    x = sweep_sine(320, 140, d, "saw")
    x = bandpass(x, 250, 1400) * env(int(SR * d), 0.005, curve=2)
    h = hit()
    h = np.pad(h, (0, max(0, len(x) - len(h))))[: len(x)]
    return x + 0.5 * h


def zdeath():
    d = 0.6
    x = groan(1, d) * np.linspace(1, 0.3, int(SR * d))
    x += sweep_sine(120, 40, d) * env(int(SR * d), 0.01, curve=2) * 0.7
    return x


def levelup():
    notes = [523.25, 659.25, 783.99, 1046.5]
    out = []
    for i, f in enumerate(notes):
        d = 0.11 if i < 3 else 0.45
        tt = t(d)
        sq = 0.6 * np.sign(np.sin(2 * np.pi * f * tt)) + 0.4 * np.sin(2 * np.pi * f * 2 * tt)
        out.append(lowpass(sq, 4000) * env(len(tt), 0.004, curve=1.5 if i < 3 else 2.5))
    return np.concatenate(out)


def click():
    d = 0.05
    tt = t(d)
    return np.sign(np.sin(2 * np.pi * 880 * tt)) * env(len(tt), 0.001, curve=6) * 0.5


def death():
    d = 1.0
    x = sweep_sine(300, 60, d, "square")
    return lowpass(x, 1500) * env(int(SR * d), 0.01, curve=1.5)


def main():
    print("ses efektleri...")
    save("swing", swing())
    save("hit", hit())
    save("chop", chop())
    save("treefall", treefall())
    save("groan0", groan(0))
    save("groan1", groan(1))
    save("groan2", groan(3))
    save("zattack", zattack())
    save("zdeath", zdeath())
    save("hurt", hurt())
    save("levelup", levelup())
    save("click", click())
    save("death", death())


if __name__ == "__main__":
    main()
