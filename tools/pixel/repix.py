"""Ham 2× renderları yeniden pikselleştirir: repix.py <ham_klasör> <çıktı_klasör>"""
import os
import sys

from PIL import Image

from pixelize import pixelize

src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
for f in sorted(os.listdir(src)):
    if f.endswith(".png"):
        pixelize(Image.open(os.path.join(src, f))).save(os.path.join(dst, f))
