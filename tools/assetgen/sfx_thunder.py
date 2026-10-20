#!/usr/bin/env python3
"""Yıldırım sesi (güvenli bölge koruması): keskin çatırtı + uzayan gök gürültüsü.

Çıktı: android/app/src/main/assets/sfx/thunder.wav (22.05 kHz mono, APK boyutu için düşük örnekleme).
"""
import os
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "android", "app", "src", "main", "assets", "sfx", "thunder.wav")
rng = np.random.default_rng(77)


def lowpass(x, hi):
    F = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(F / (1 + (f / hi) ** 4), len(x))


d = 2.2
n = int(SR * d)
tt = np.arange(n) / SR
# çatırtı: ilk 120 ms yüksek frekanslı kırık gürültü darbeleri
crack = rng.standard_normal(n) * np.exp(-tt / 0.05)
crack *= (rng.random(n) < 0.35)
# gürleme: alçak geçirgen gürültü, yavaş titreşimli zarf
rum = lowpass(rng.standard_normal(n), 180.0)
rum /= np.abs(rum).max()
wob = 0.6 + 0.4 * np.sin(2 * np.pi * 3.1 * tt + 2 * np.sin(2 * np.pi * 0.7 * tt))
env = np.minimum(1, tt / 0.04) * np.exp(-tt / 0.75) * wob
sig = 0.75 * crack + 1.0 * rum * env
# kısa yankı
for dly, g in ((0.11, 0.35), (0.27, 0.22), (0.5, 0.12)):
    k = int(SR * dly)
    sig[k:] += g * sig[:-k]
sig /= np.abs(sig).max()
sig *= np.minimum(1, (d - tt) / 0.3)
pcm = (sig * 0.92 * 32767).astype("<i2")
with wave.open(os.path.abspath(OUT), "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print("thunder.wav", len(pcm) * 2 // 1024, "KB")
