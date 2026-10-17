import sys, math, time
import numpy as np
from PIL import Image
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from sdf import render
from character import skeleton, human_scene, human_decals, player_pose, zombie_pose, MATERIALS

kind = sys.argv[1] if len(sys.argv) > 1 else "player"
anim = sys.argv[2] if len(sys.argv) > 2 else "idle"
frames = int(sys.argv[3]) if len(sys.argv) > 3 else 1
posef = player_pose if kind == "player" else zombie_pose
W, Hh = 64, 64
t0 = time.time()
rows = []
for d in [int(x) for x in (sys.argv[5] if len(sys.argv)>5 else '01234567')]:
    row = []
    for f in range(frames):
        P = posef(anim, f / frames)
        P["yaw"] = d * math.pi / 4
        J = skeleton(P)
        img = render(human_scene(J, kind), W, Hh, (32, 56), MATERIALS, human_decals(J, kind))
        row.append(img)
    rows.append(np.concatenate(row, 1))
sheet = np.concatenate(rows, 0)
im = Image.fromarray(sheet, "RGBA")
bg = Image.new("RGBA", im.size, (127, 196, 74, 255))
bg.alpha_composite(im)
bg.resize((im.width * 4, im.height * 4), Image.NEAREST).save(sys.argv[4] if len(sys.argv) > 4 else "/tmp/claude-0/test.png")
print("süre", time.time() - t0)
