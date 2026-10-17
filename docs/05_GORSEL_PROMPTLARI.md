# 05 — Yapay Zeka Görsel Promptları (AAA Kalite)

Promptlar **İngilizce** yazıldı; görsel yapay zekaları İngilizcede çok daha tutarlı sonuç veriyor. Her başlıkta Türkçe açıklama var.

## 0. Önce Bunu Oku — Tutarlılık Kuralları

### 0.1 İş akışı (önerilen)
1. **Konsept** (yüksek çözünürlük, serbest) → karakterin/zombinin "kimliğini" sabitle.
2. **Pixel sprite** üretimi → konsept görselini **referans görsel** olarak ver (Midjourney `--cref`/`--sref`, GPT-Image'e görseli ekle, SD'de IP-Adapter).
3. **Temizleme:** Aseprite'ta hedef boyuta *nearest-neighbor* küçült → 48 renk palete indir (01 §5) → outline ve kare hizalamasını elle düzelt.
4. **Animasyon:** Yapay zeka tam sprite sheet'te karelerin tutarlılığını genelde bozar. En sağlam yol: idle + 1 kare her poz yapay zeka ile → ara kareleri Aseprite'ta elle / PixelLab / Retro Diffusion "animate" ile.

> Pixel art için en iyi araçlar: **PixelLab** (sprite + animasyon + yön), **Retro Diffusion** (gerçek pixel grid), **Midjourney v7** (konsept, UI illüstrasyon), **GPT-Image / DALL·E** (UI kit, ikon setleri, metin içeren logo), **Stable Diffusion XL + pixel-art LoRA** (toplu üretim).

### 0.2 STİL ÇAPASI — her promptun sonuna ekle
```
STYLE ANCHOR: premium 2.5D top-down pixel art, 3/4 oblique camera view (like Stardew Valley and Graveyard Keeper, not isometric), crisp hand-placed pixels, 1px colored dark outline (#1b1c26, not pure black), limited 48-color palette, warm afternoon sunlight from top-left, soft cel-shaded volumes with 3 tone steps, subtle dithering only on large gradients, saturated greens and warm mustard accents, cozy-but-dangerous survival mood, AAA indie game quality, clean readable silhouettes, consistent pixel density, no anti-aliasing blur, no sub-pixel noise
```

### 0.3 NEGATİF PROMPT — destekleyen araçlarda (SD, Leonardo, Midjourney `--no`)
```
blurry, anti-aliased, smooth gradients, 3D render, photorealistic, isometric diamond grid, mixed pixel sizes, jpeg artifacts, noise, painterly brush strokes, watermark, signature, text, logo, cropped, extra limbs, deformed hands, inconsistent lighting, perspective distortion, chromatic aberration, vignette, background clutter, drop shadow on transparent background, pure black outline, gore, realistic blood
```

### 0.4 Arka plan
Sprite'lar için: `isolated on a flat solid #FF00FF magenta background` (sonra magenta silinir — şeffaf PNG'yi çoğu araç düzgün yapamıyor). UI için de aynısı.

### 0.5 Midjourney parametre önerisi
`--ar <oran> --style raw --stylize 50 --v 7` · Tutarlılık: `--cref <karakter_konsept_url> --cw 100` · Stil tutarlılığı: `--sref <onaylı_ilk_görsel_url>`

---

## A. KARAKTER — "Kaan"

### A1. Karakter konsept sayfası (yüksek çözünürlük, referans için)
*Amaç: karakterin kimliğini sabitlemek. Pixel değil — tüm sonraki görseller buna referans verir.*
```
Professional game character concept sheet of a young male survivor in his early 20s for a cozy-but-dangerous zombie survival game. Short messy dark-brown hair with one distinctive strand falling over the forehead, determined friendly eyes, light-medium skin. Wearing a mustard-yellow hoodie with sleeves rolled up to the elbows, dark navy jeans torn at one knee, dirty off-white sneakers, white cloth hand wraps on both fists like a street brawler, a small worn brown leather backpack. Slightly stylized proportions (head-to-body ratio about 1:2.5, not childish). Show front view, back view, side view (right), and a three-quarter view in a clean turnaround lineup, plus 3 small expression heads (neutral, angry shouting, smiling) and a color palette swatch strip at the bottom. Flat neutral light-gray background, even studio lighting, clean line art with soft cel shading, high detail, AAA indie game art direction, consistent design across all views.
```
`--ar 16:9 --style raw --v 7`

### A2. Pixel sprite — temel poz (3 yön)
*Amaç: 48×48 sprite'ın ana "model" karesi. Bunu onaylayınca tüm animasyonlar buradan türer.*
```
Pixel art game sprite sheet, single row of 3 frames, each frame exactly 48x48 pixels on a strict grid, of the same young male survivor character: mustard-yellow hoodie with rolled sleeves, dark navy torn jeans, off-white sneakers, white hand wraps, small brown backpack, messy dark-brown hair with a forehead strand. Standing idle pose shown facing DOWN (toward camera), facing UP (back view, backpack visible), and facing RIGHT (side profile). Character about 38 pixels tall, feet at the bottom center of each frame, 3/4 top-down oblique camera angle. Isolated on a flat solid #FF00FF magenta background, no shadow. [STYLE ANCHOR]
```
`--ar 3:1` · Referans: A1 görseli

### A3–A12. Animasyon sprite sheet'leri
Her biri için aşağıdaki **şablonu** kullan, `{ANİMASYON}` kısmını tablodaki metinle değiştir:
```
Pixel art animation sprite sheet of the same young male survivor character (mustard-yellow hoodie with rolled sleeves, dark navy torn jeans, off-white sneakers, white hand wraps, small brown backpack, messy dark-brown hair). Grid of {SATIR} rows by {KARE} columns, every frame exactly 48x48 pixels, row 1 facing DOWN toward camera, row 2 facing UP (back view), row 3 facing RIGHT (side view). Animation: {ANİMASYON}. Consistent character size (about 38px tall), feet anchored at the same bottom-center pixel in every frame, smooth readable motion arcs, clear key poses, consistent colors and outline across all frames. 3/4 top-down oblique view. Isolated on flat solid #FF00FF magenta background. [STYLE ANCHOR]
```

| Kod | Animasyon | {KARE} | {ANİMASYON} metni |
|-----|-----------|--------|-------------------|
| A3 | Idle | 4 | `idle breathing loop, shoulders gently rising and falling, slight head bob, fists loosely raised at chest height in a relaxed boxer guard, one frame with eyes blinking` |
| A4 | Walk | 6 | `walk cycle loop, natural alternating arm swing, backpack bouncing slightly, contact-down-pass-up poses, relaxed guard` |
| A5 | Run | 6 | `fast run cycle loop, body leaning forward, arms pumping, long strides, both feet off the ground in one frame, hair and hoodie strings flowing back, tiny dust puff at heel contact` |
| A6 | Punch A | 4 | `quick left jab: anticipation (slight pull back), strike with left arm fully extended forward, impact frame with small white motion smear, recovery to guard` |
| A7 | Punch B | 4 | `quick right straight punch following a jab: hips rotate, right arm fully extended, impact frame with white motion smear arc, recovery` |
| A8 | Punch C | 6 | `powerful rising uppercut finisher: deep crouch anticipation, explosive upward swing of the right fist, body lifting onto toes, big white curved smear arc on the impact frame, small impact star burst, heavy recovery back to guard` |
| A9 | Chop | 5 | `heavy horizontal hook punch aimed at a tree trunk at waist height: wind-up twist, swing, impact frame with small wood-chip particles, recoil, recovery` |
| A10 | Hurt | 2 | `getting hit: frame 1 flinch backward with head snapping back and eyes shut, frame 2 the same pose as a pure white silhouette flash` |
| A11 | Death | 6 | `death: staggers, knees buckle, falls backward, hits the ground, small dust puff, final frame lying still on the ground` |
| A12 | Level Up | 6 | `level-up celebration: crouch, jump with right fist punched high into the air, shout with open mouth, land, confident smile. Only character, no effects` (sadece 1 satır: DOWN) |
| A13 | Spawn | 6 | `waking up: starts lying on the ground, pushes up with arms, kneels, stands, shakes head, ready guard` |
| A14 | Lobi pozu | 8 | `lobby idle loop facing camera, standing confidently next to a campfire, cracking knuckles, then relaxing with fists at sides, warm orange rim light on the right side` (1 satır, 64×64 kare, 3× gösterilecek) |

### A15. Gölge sprite
```
Pixel art soft drop shadow ellipse, 20x8 pixels, single flat dark navy color #172238 with 2 dithered edge steps, top-down game sprite, isolated on #FF00FF magenta background.
```

### A16. HUD portresi (64×64)
```
Pixel art character portrait icon, 64x64 pixels, head and shoulders of a young male survivor with messy dark-brown hair and a forehead strand, mustard-yellow hoodie, determined slight smirk, facing three-quarter left, warm light from top-left, inside a square frame-ready crop with no frame, flat #FF00FF magenta background. [STYLE ANCHOR]
```

### A17. Karakter seçim kartı illüstrasyonu (256×384 pixel)
```
High-detail pixel art character illustration, 256x384 pixels, full body hero pose of a young male survivor (mustard-yellow hoodie with rolled sleeves, dark navy torn jeans, off-white sneakers, white hand wraps, brown backpack), standing on a mossy rock in a forest clearing, fists raised in a boxing guard, wind blowing the hoodie strings, golden afternoon god rays through trees behind him, faint silhouettes of zombies in the misty background, dramatic heroic low angle, rich detailed shading, collectible card art quality. [STYLE ANCHOR]
```
`--ar 2:3`

---

## B. ZOMBİ — "Yürüyen"

### B1. Zombi konsept sayfası
```
Professional game creature concept sheet of a shambling zombie for a stylized cozy-but-dangerous survival game, not gory. Former hiker: torn faded purple t-shirt, ripped gray cargo shorts, one missing shoe, pale sickly green skin with darker green patches, hunched posture, one arm reaching forward, the other dangling, mouth hanging open with a few teeth, one eye glowing pale yellow and the other clouded white, tufts of messy hair. Slightly stylized proportions matching a chibi-adjacent 1:2.5 head-to-body ratio. Show front, back, side (right) and three-quarter views, plus 3 expression heads (idle drooling, snarling attack, stunned) and a palette swatch strip. Flat light-gray background, clean line art, soft cel shading, AAA indie art direction, readable silhouette, family-friendly creepy rather than gory.
```
`--ar 16:9 --style raw --v 7`

### B2–B8. Zombi animasyonları
Şablon (A3 şablonuyla aynı, karakter tanımını değiştir):
```
Pixel art animation sprite sheet of the same shambling zombie (torn faded purple t-shirt, ripped gray cargo shorts, one missing shoe, pale green skin with dark patches, hunched, one glowing yellow eye). Grid of 3 rows by {KARE} columns, every frame exactly 48x48 pixels, row 1 facing DOWN, row 2 facing UP, row 3 facing RIGHT. Animation: {ANİMASYON}. Consistent size (about 36px tall, hunched), feet anchored at the same bottom-center pixel, consistent colors and outline. 3/4 top-down oblique view. Isolated on flat solid #FF00FF magenta background. [STYLE ANCHOR]
```
| Kod | Animasyon | {KARE} | {ANİMASYON} metni |
|-----|-----------|--------|-------------------|
| B2 | Idle | 4 | `idle swaying loop, head lolling side to side, reaching arm twitching` |
| B3 | Walk | 6 | `slow limping shamble loop, dragging the shoeless foot, reaching arm forward` |
| B4 | Lunge | 6 | `aggressive fast lurching run loop, both arms reaching forward, head thrust forward, mouth wide open` |
| B5 | Attack | 5 | `claw swipe attack: rear back, wide overhead swipe with the reaching arm, impact frame with 3 dark-red claw slash marks, recovery` |
| B6 | Hurt | 2 | `getting hit: knocked back with head snapping, then pure white flash silhouette` |
| B7 | Death | 7 | `collapse: staggers, falls face-first to the ground, last 2 frames the body breaks apart into small square green and purple pixel particles that fade` |
| B8 | Spawn | 6 | `rising from the ground: a hand bursts out of the dirt with soil clumps, then head and shoulders, then climbs out and stands hunched` |

### B9. "!" fark etme balonu & zombi HP barı
```
Pixel art game UI micro sprites on #FF00FF background: (1) a 12x16 pixel speech-bubble alert icon with a bold red exclamation mark, white bubble, dark outline; (2) a 24x4 pixel enemy health bar frame with dark background and red fill, 1px outline. [STYLE ANCHOR]
```

---

## C. ZEMİN TILESET (32×32)

### C1. Çim tileset
```
Seamless tileable pixel art ground tileset sheet for a top-down 3/4 view game, every tile exactly 32x32 pixels on a strict grid, 4 columns by 3 rows: row 1 four variants of lush green grass with subtle blade clusters, row 2 four variants of grass with tiny scattered flowers (white daisies, red poppies, blue cornflowers), row 3 two variants of darker shaded grass and two variants of short dry grass patches. All tiles tile seamlessly in every direction with no visible seams or repeating patterns, low-contrast texture so characters stay readable on top, warm afternoon light. [STYLE ANCHOR]
```
`--ar 4:3 --tile` (Midjourney'de tekil karo için `--tile`)

### C2. Toprak & patika
```
Seamless tileable pixel art ground tileset, tiles exactly 32x32 pixels, 4 columns by 2 rows: row 1 four variants of packed brown dirt with small pebbles and cracks, row 2 four variants of a worn forest footpath with lighter compacted soil and faint footprints. Seamless edges, low contrast, readable under characters, top-down 3/4 view. [STYLE ANCHOR]
```

### C3. Çim↔Toprak geçiş autotile (16'lık set)
```
Pixel art autotile transition set for a top-down game, 16 tiles each exactly 32x32 pixels in a 4x4 grid, showing every edge and corner combination of lush green grass overlapping brown dirt (outer corners, inner corners, straight edges on all 4 sides, full grass, full dirt), the grass edge is organic and irregular with small overhanging grass blades and a 1px darker grass lip shadow on the dirt, perfectly aligned so tiles connect seamlessly. Standard RPG Maker / Godot 16-tile blob layout. [STYLE ANCHOR]
```

### C4. Su & kıyı (opsiyonel)
```
Pixel art autotile set of a small calm forest pond, 16 tiles of 32x32 pixels in a 4x4 grid, deep teal water with soft light ripples and sparkle highlights, muddy shore edge with small reeds and stones, all edge and corner combinations, seamless. Plus a separate 4-frame 32x32 water surface animation strip. [STYLE ANCHOR]
```

### C5. Zemin detayları (decal)
```
Pixel art sprite sheet of small ground decoration decals for a top-down forest game on #FF00FF background, each in a 16x16 pixel cell, 8 columns by 2 rows: small gray pebbles x3, fallen twigs x2, fallen oak leaves x2, a red-capped mushroom, a cluster of brown mushrooms, a small bone, a dirt crack, a moss patch, a clover patch, a pinecone, a feather, a small puddle. [STYLE ANCHOR]
```

---

## D. AĞAÇLAR & BİTKİLER

### D1. Meşe ağacı (3 varyant)
```
Pixel art sprite sheet of 3 variants of a large lush oak tree for a top-down 3/4 view forest game, each tree in a 96x128 pixel cell side by side, round bushy layered canopy in rich greens with bright yellow-green sunlit tops and deep green undersides, visible thick brown trunk with bark texture and slight exposed roots at the base, small leaf clusters, the canopy and trunk drawn so the canopy can be split as a separate layer, base pivot at bottom center. Variants differ in canopy shape and size. Isolated on flat #FF00FF magenta background, no ground shadow. [STYLE ANCHOR]
```
`--ar 9:4`

### D2. Çam ağacı (3 varyant)
```
Pixel art sprite sheet of 3 variants of a tall pine tree for a top-down 3/4 view forest game, each in a 64x128 pixel cell, layered triangular needle tiers in deep blue-greens with lighter sunlit edges on the top-left, thin reddish-brown trunk visible at the bottom, a few pinecones, base pivot at bottom center. Isolated on #FF00FF magenta background. [STYLE ANCHOR]
```

### D3. Huş ağacı (2 varyant)
```
Pixel art sprite sheet of 2 variants of a slender birch tree, each in a 64x112 pixel cell, white trunk with black horizontal bark marks, airy light-green canopy with yellowish highlights, top-down 3/4 view, base pivot at bottom center, isolated on #FF00FF magenta background. [STYLE ANCHOR]
```

### D4. Ölü ağaç (2 varyant)
```
Pixel art sprite sheet of 2 variants of a dead leafless tree, each in a 64x96 pixel cell, twisted gray-brown branches, cracked bark, a little moss at the base, slightly eerie, top-down 3/4 view, isolated on #FF00FF magenta background. [STYLE ANCHOR]
```

### D5. Ağaç vurulma & yıkılma animasyonu
```
Pixel art animation sprite sheet of the same lush oak tree (96x128 pixel frames): row 1 a 3-frame hit reaction (trunk shakes, canopy jiggles, a few leaves fall), row 2 a 6-frame felling animation (tree tilts, falls sideways to the right, hits the ground with a dust cloud and leaf burst, then fades), row 3 three growth stages from a 32x24 cut stump to a sapling to a young tree. Consistent colors and outline. Isolated on #FF00FF magenta background. [STYLE ANCHOR]
```

### D6. Kütük
```
Pixel art sprite of a freshly cut oak tree stump, 32x24 pixels, visible light wood rings on top, rough bark sides, a few wood chips around, top-down 3/4 view, isolated on #FF00FF magenta background. [STYLE ANCHOR]
```

### D7. Çalılar
```
Pixel art sprite sheet of forest bushes for a top-down 3/4 view game on #FF00FF background: two round bushes 32x32, two wide bushes 48x32, one berry bush 32x32 with small red berries, and one flowering bush 32x32 with pink blossoms. Dense leafy volume, sunlit top-left, darker bottom, base pivot at bottom center. [STYLE ANCHOR]
```

### D8. Otlar, çiçekler, eğrelti, mantar
```
Pixel art sprite sheet of small forest plants for a top-down 3/4 view game on #FF00FF background, single-row cells: 5 tall grass tufts (16x24 each, varied heights), 6 flowers (16x16 each: white daisy, red poppy, blue cornflower, yellow buttercup, purple lavender, orange marigold), 2 ferns (32x24), 3 mushroom clusters (16x16). Thin readable shapes designed to sway from the base, base pivot at bottom center. [STYLE ANCHOR]
```

### D9. Kayalar & devrik kütük
```
Pixel art sprite sheet of forest rocks and logs for a top-down 3/4 view game on #FF00FF background: 3 large mossy boulders (64x48), 3 medium rocks (32x32), 3 small stones (16x16), and 1 fallen hollow log with moss and a small mushroom (64x32). Gray stone with cool shadows, green moss on top, lit from top-left. [STYLE ANCHOR]
```

### D10. Kamp ateşi (animasyonlu)
```
Pixel art animation strip of a campfire, 6 frames each 32x32 pixels in one row, ring of gray stones around crossed logs, lively flickering orange-yellow flames with a white-hot core, a few rising ember sparks, seamless loop. Plus one extra frame of the same campfire extinguished with gray smoke wisp. Isolated on #FF00FF magenta background. [STYLE ANCHOR]
```

---

## E. EFEKTLER (FX)

### E1. Vuruş & hasar efektleri
```
Pixel art VFX sprite sheet for a top-down action game on #FF00FF background, each effect as a short animation row: (1) white impact star burst 4 frames 32x32, (2) critical hit yellow-orange star burst 5 frames 48x48, (3) dark-red stylized hit splatter squares 4 frames 32x32 (not gory), (4) small dust puff 4 frames 16x16, (5) wood chips burst 4 frames 32x32, (6) falling green leaves 6 frames 16x16, (7) curved white swing smear arcs 3 frames 48x48. Bold readable shapes, limited colors. [STYLE ANCHOR]
```

### E2. Level up & XP
```
Pixel art VFX sprite sheet on #FF00FF background: (1) a level-up golden light pillar rising from the ground with sparkles, 8 frames each 48x96 pixels, (2) small glowing cyan XP orb 4-frame spin 8x8 pixels, (3) XP orb trail sparkle 4 frames 8x8, (4) a ring shockwave 6 frames 64x32 on the ground plane. Bright, celebratory, additive-glow look. [STYLE ANCHOR]
```

### E3. Hasar sayıları fontu
```
Pixel art bitmap number font sprite sheet on #FF00FF background, digits 0-9 plus "+", "-", "!", "x" and "CRIT", each digit 8x12 pixels, chunky bold rounded style with a 1px dark outline and a 1px drop shadow, three color versions in rows: white (normal damage), bright yellow-orange (critical), green (healing). [STYLE ANCHOR]
```

---

## F. ARAYÜZ (UI KIT)

> Not: Yapay zekalar Türkçe metni genelde bozar. **UI görsellerini metinsiz üret**, yazıları oyunda font ile bas.

### F1. Ana UI kiti — paneller & tuşlar
```
Complete pixel art mobile game UI kit sprite sheet, no text, on #FF00FF background, consistent style: 9-slice panels (dark navy #23365a with 2px dark outline, 1px light top highlight and a 2px bottom shadow, slightly rounded pixel corners) in 3 versions (default dark, light parchment, red warning); buttons in 3 colors (mustard-yellow primary, navy secondary, dark-red danger) each in 3 states (normal, pressed shifted down 2px, disabled gray); a small round icon button; tab buttons (active and inactive); a toggle switch (on green, off gray); a checkbox (checked, unchecked); a slider track with a knob; a dropdown box; a tooltip box with a pointer; a toast notification box; a modal dialog frame with a close X button. Chunky, tactile, readable at small mobile sizes, premium AAA mobile game UI. [STYLE ANCHOR]
```
`--ar 16:9`

### F2. İlerleme barları & slotlar
```
Pixel art mobile game UI sprite sheet, no text, on #FF00FF background: horizontal bar frames with fill sprites for health (red with a light top shine), stamina (yellow), and experience (cyan-blue), each with an empty dark background version; a thin XP bar; inventory slot frames 40x40 in states normal, selected (glowing mustard outline), and rarity borders (gray common, green uncommon, blue rare, purple epic, orange legendary); an equipment slot with a faint silhouette placeholder; a small lock badge; a red notification dot; a level badge shield. [STYLE ANCHOR]
```

### F3. Joystickler
```
Pixel art mobile game virtual joystick UI sprites, no text, on #FF00FF background: (1) movement joystick base ring 140x140 pixels, semi-transparent dark navy with a subtle segmented outer ring and 8 tiny directional notches, (2) movement joystick knob 60x60, glossy mustard-yellow with a soft highlight, (3) attack joystick base 120x120, darker red-tinted ring, (4) attack knob 56x56 with a bold white fist icon in the center, (5) a cooldown radial fill overlay ring, (6) a semi-transparent attack direction arc cone indicator 96x64. Clean, readable over a busy green forest background. [STYLE ANCHOR]
```

### F4. Menü ikonları (~40 adet)
```
Pixel art icon set for a mobile survival game menu, 40 icons each exactly 32x32 pixels in an 8x5 grid, no text, consistent 1px dark outline and top-left lighting, mustard and cyan accent colors, on #FF00FF background. Icons: play triangle, settings gear, mail envelope, character silhouette, skill tree (branching star), backpack inventory, shop cart, friends (two people), clan shield banner, chat bubble, quests scroll, events calendar star, leaderboard trophy, profile card, gold coin, diamond gem, energy lightning, plus sign, close X, back arrow, pause, map, minimap compass, globe language, sound speaker, music note, vibration phone, graphics monitor, joystick controller, eye visibility, lock, unlock, refresh, trash, info i, warning triangle, fist punch, heart health, running shoe speed, wood log. [STYLE ANCHOR]
```

### F5. Para birimleri & eşya ikonları
```
Pixel art item icon set, each 32x32 pixels, on #FF00FF background, shiny collectible look with 1px dark outline: gold coin, stack of gold coins, blue diamond gem, pile of gems, energy bolt, wood log, stack of wood logs, small stone, zombie tooth, tree sapling, red berry, skill point star token, XP orb. [STYLE ANCHOR]
```

### F6. Skill ikonları
```
Pixel art skill icon set, 12 icons each 48x48 pixels inside rounded square frames, in 4 color-coded families on #FF00FF background: RED power family (iron fist, rising uppercut with motion arc, stunning shockwave fist), GREEN endurance family (thick skin shield over heart, regenerating heart with plus, last breath glowing heart with a crack), BLUE agility family (light footstep with wind lines, lungs with breath swirl, dash afterimage silhouette), BROWN woodcutting family (tough fist with wood chips, double log bonus, sprouting sapling with sparkles). Plus a locked version frame with chains overlay and dark desaturation. [STYLE ANCHOR]
```

### F7. Logo — "ZENVEX ONLINE"
*(Metin içerdiği için GPT-Image / Ideogram kullan.)*
```
Game logo design for "ZENVEX ONLINE", chunky bold pixel art lettering, the word "ZENVEX" large with a mustard-yellow to orange vertical gradient fill, thick dark navy outline and a 3D extruded bottom edge, a few cracks and a zombie hand claw scratch across the letter "X", small green vines and leaves curling around the letters, the word "ONLINE" smaller underneath in white pixel letters on a dark-red ribbon banner, subtle glowing highlight sparkle, transparent-ready flat #FF00FF magenta background, premium mobile game title logo, centered composition.
```

### F8. Uygulama ikonu
```
Mobile app icon, 1024x1024, pixel art style, rounded square composition: close-up of the young survivor with messy dark-brown hair and mustard hoodie throwing a punch toward the viewer with a white impact burst, a green-skinned zombie silhouette with a glowing yellow eye in the background, lush green forest backdrop with warm sunlight, bold saturated colors, strong silhouette readable at small sizes, no text, premium game icon quality.
```

### F9. Lobi arka planı (katmanlı paralaks)
```
Wide pixel art background for a mobile game lobby, 1280x720 pixels, cozy dusk forest clearing: in the center an empty mossy ground spot with a crackling campfire (character will be placed here), a fallen log seat, a small tent on the left, tall pine and oak trees framing both sides, warm orange firelight glow against cool purple-blue dusk sky, fireflies, distant misty tree layers, very faint silhouettes of shambling zombies far in the background fog, calm but slightly ominous mood, leave clear empty space at the top for UI bars and bottom for buttons. Provide as separate parallax-friendly depth: sky, far trees, mid trees, foreground. [STYLE ANCHOR]
```
`--ar 16:9` · (Katmanları ayrı ayrı istemek için promptu 4 kez "only the sky layer", "only far trees layer on #FF00FF" vb. ile tekrarla.)

### F10. Giriş ekranı arka planı
```
Wide pixel art title screen background, 1280x720 pixels, sunny afternoon forest clearing seen from a 3/4 top-down view, golden god rays through tall trees, a dirt path leading into the distance, flowers and tall grass in the foreground, a few small zombies shambling far along the path, butterflies, rich saturated greens, open space in the upper center for the logo and lower center for buttons, inviting adventurous mood. [STYLE ANCHOR]
```

### F11. Splash ekranı
```
Minimal pixel art splash screen, 1280x720, solid very dark navy #172238 background, centered small pixel art studio emblem: a stylized fist inside a hexagon outline with a tiny leaf, mustard and cyan colors, subtle pixel glow, no text. [STYLE ANCHOR]
```

### F12. Yükleme ekranı illüstrasyonları (3 adet)
```
Cinematic pixel art loading screen illustration, 1280x720 pixels, {SAHNE}, dramatic composition, rich lighting, detailed environment, leave the bottom 15 percent darker and calm for a loading bar and tip text. [STYLE ANCHOR]
```
- `{SAHNE}` 1: `the young survivor in a mustard hoodie landing a powerful uppercut on a green-skinned zombie in a sunlit forest, leaves and impact sparks flying`
- `{SAHNE}` 2: `the survivor punching a big oak tree, wood chips flying, a pile of logs nearby, peaceful afternoon`
- `{SAHNE}` 3: `the survivor standing on a hill at sunset looking over a vast forest with a horde of tiny zombies in the valley below, golden light pillar of a level up around him`

### F13. Yükleme çubuğu
```
Pixel art loading progress bar UI, no text, on #FF00FF background: a long horizontal 9-slice frame with dark navy background and dirt-brown fill, plus a small 4-frame animated sprite of a pale green zombie hand crawling along the fill edge, 16x16 per frame. [STYLE ANCHOR]
```

### F14. Ölüm ekranı & Level Up banner'ı
```
Pixel art UI elements, no text, on #FF00FF background: (1) a dramatic wide banner ribbon 320x64 in dark red with torn edges and small skull motifs for a death screen, (2) a celebratory wide banner 320x64 in golden yellow with sparkles, laurel leaves and star ends for a level-up announcement, (3) a red pulsing screen-edge vignette overlay frame (low health), 4 intensity frames. [STYLE ANCHOR]
```

### F15. Mağaza / "Yakında" kaplaması
```
Pixel art UI overlay, no text, on #FF00FF background: a semi-transparent dark navy diagonal stripe pattern 9-slice overlay for locked screens, a large golden padlock icon 64x64 with a soft shine, and a small "coming soon" style ribbon badge shape 96x24 in mustard yellow without any letters. [STYLE ANCHOR]
```

---

## G. PAZARLAMA (sonra)
### G1. Mağaza afişi (Google Play feature graphic 1024×500)
```
Pixel art promotional key art banner, 1024x500, the young survivor in a mustard hoodie in a dynamic punching pose on the left fighting three green-skinned zombies on the right in a lush sunlit forest, flying leaves and impact effects, epic but cozy mood, bold colors, empty area top-center for a logo, premium mobile game key art. [STYLE ANCHOR]
```

---

## H. Görsel Üretim Kontrol Listesi
| # | Görsel | Boyut | Öncelik |
|---|--------|-------|---------|
| A1 | Karakter konsept | serbest | 🔴 1 |
| B1 | Zombi konsept | serbest | 🔴 1 |
| A2–A11 | Karakter sprite & animasyonlar | 48×48 | 🔴 1 |
| B2–B8 | Zombi animasyonları | 48×48 | 🔴 1 |
| C1–C3, C5 | Zemin tileset | 32×32 | 🔴 1 |
| D1–D9 | Ağaç, bitki, kaya | çeşitli | 🔴 1 |
| E1–E3 | Efektler | çeşitli | 🟠 2 |
| F1–F6 | UI kit, joystick, ikonlar | çeşitli | 🔴 1 |
| F7–F15 | Logo, arka planlar, ekranlar | çeşitli | 🟠 2 |
| A12–A17, C4, D10 | Ek animasyon, su, ateş, portre | çeşitli | 🟡 3 |
| G1 | Pazarlama | 1024×500 | ⚪ sonra |
