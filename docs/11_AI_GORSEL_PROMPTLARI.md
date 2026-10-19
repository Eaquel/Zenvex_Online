# Yapay Zekâ Görsel Prompt'ları (V4 piksel sanat)

**Nasıl kullanılır**
- Karakter/zombi/köpek: **PixelLab** (pixellab.ai) → "Create character" (metinden) ya da "Rotate / Animate" (referanstan).
  Tuval: kahraman ve zombiler **96×96**, köpek **64×64**, Titan **128×128**, eşya **32×32**. Görünüm: **high top-down**.
- Sahne/ekran/simge görselleri (sinema, giriş ekranı, uygulama simgesi): ChatGPT görsel ya da Gemini da olur.
- Prompt'lar İngilizce: araçlar İngilizceyi daha iyi anlıyor. Altındaki Türkçe not ne istediğimizi açıklar.
- Her prompt'un başına **STİL** bloğunu ekle (kopyala-yapıştır). Sonuçları bu sohbete at; temizliği ben yaparım.
- Bir sonuç tutarsız çıkarsa: aynı referansla tekrar üret, ya da sonuna `same character as reference, keep exact colors` ekle.

## STİL (her prompt'un başına)

```
high-detail pixel art, 16-bit era quality like modern indie games (Children of Morta, Hyper Light Drifter),
crisp 1px dark outline, soft dithered shading, limited palette of deep night colors (navy, charcoal, crimson, ember orange),
three-quarter top-down view (camera above at about 45 degrees), flat even lighting with NO cast shadows and NO glow,
single subject centered, full body visible, transparent or plain flat magenta (#FF00FF) background, no text, no watermark, no ground
```
**Negatif (aracın destekliyorsa)**: `blurry, anti-aliased, 3d render, photo, painterly, gradient background, extra limbs, cropped, text, logo, shadow on ground`

---

## H — Kahraman (Savaşçı)

**H1 — Ana referans (İLK BUNU YAP)** · 96×96 · güneye (kameraya) bakar
```
[STİL] a lone hooded warrior survivor of a zombie apocalypse, tall slim man, deep black hood casting shadow over the face
with only the lower jaw visible, dark charcoal layered cloak with torn ragged edges reaching the knees, dark leather vest
with straps, brown cloth belt with a small skull-shaped pouch, black wrapped forearms and bare-knuckle fists with cloth
bandages, dark baggy trousers, worn brown leather boots, subtle dark purple stitching, standing in a relaxed fighting
stance facing the viewer
```
> Türkçe: Örnek fotoğraftaki siyah kapüşonlu adam; silahsız, yumruklarında sargı bezi. Kapüşon yüzü gölgeliyor.

**H2 — Kapüşonsuz sivil hâli** (yalnızca hikâye sineması için) · aynı tuval
```
[STİL] same man as reference but before the apocalypse: no hood, short messy dark hair, tired face, grey hoodie with the
hood down, dark jeans, sneakers, holding a game controller, casual standing pose
```

**H3 — Dönüş**: PixelLab → Rotate → H1'i yükle → 8 yön. Biz G, GD, D, KD, K'yi kullanırız (batı tarafı ayna).

**H4 — Animasyonlar** (PixelLab → Animate, referans = H1 + yönler). Her biri için kısa açıklama yaz:

| Ad | PixelLab'e yazılacak açıklama | Kare |
|---|---|---|
| idle | `breathing idle, cloak gently swaying` | 6 |
| walk | `walking, cloak and hood tip swaying with a slight delay` | 8 |
| run | `running fast, cloak flowing behind` | 8 |
| roll | `quick forward dodge roll in the facing direction, cloak wrapping around the body` | 6 |
| punch_1 | `fast left jab` | 4 |
| punch_2 | `fast right straight punch` | 4 |
| punch_3 | `heavy uppercut finisher with a short hop` | 6 |
| hurt | `flinch backward when hit` | 2 |
| knockdown | `knocked back and falling to the ground on his back` | 5 |
| getup | `getting up from the ground` | 5 |
| death | `collapsing to the knees then falling face down` | 7 |
| knife_attack | `holding a combat knife in the right hand, quick slash` | 5 |
| machete_attack | `two-handed wide machete swing` | 6 |
| bat_attack | `baseball bat overhead swing` | 6 |
| sword_attack | `long sword diagonal slash` | 6 |
| pistol_idle / pistol_shoot | `aiming a pistol with both hands` / `firing pistol with recoil` | 4 / 3 |
| shotgun_idle / shotgun_shoot | `holding a pump shotgun at the hip` / `firing shotgun, strong recoil, pumping` | 4 / 5 |
| rifle_idle / rifle_shoot | `shouldering an assault rifle` / `firing rifle in short bursts` | 4 / 3 |
| torch_walk | `walking while holding a lit wooden torch up in the left hand` (alevsiz istersen: `unlit torch`) | 8 |

> Silahlar ayrı çizilmezse sorun yok: silahsız animasyon + silahı biz el noktasına koyarız. Ama her silahın kendine has
> tutuş/vuruşu istendiği için yukarıdakiler ideal.

**H5 — Seviye 10 Karanlık Aura** (efekt, ayrı) · 128×128
```
[STİL] a swirling dark purple and black aura ring with glowing violet sparks, seen from above at 45 degrees, loopable,
4 animation frames in a horizontal strip, transparent background
```

## P — Köpek (yardımcı)

**P1** · 64×64
```
[STİL] a loyal scruffy german shepherd mix dog companion, dark brown and black fur, red bandana around the neck,
alert ears, standing facing the viewer
```
Animasyonlar: idle 4, run 6, bite_attack 4 (`lunging bite`), hurt 2, lie_down 4 (bayıldığında).
**P2 / P3** (seviye 5 ve 10 görünümü, isteğe bağlı): `same dog with a leather armor vest` / `same dog with spiked
armor and glowing amber eyes`.

## Z — Zombiler · 96×96 (Titan 128×128)

Ortak ek: `undead zombie, grey-green rotting skin, torn clothes, hunched posture, empty glowing pale eyes`

| Kod | Prompt (STİL + ortak ek + aşağısı) | Animasyon |
|---|---|---|
| Z1 Yürüyen | `ordinary townsperson zombie, torn office shirt and tie, one shoe missing, shambling` | idle 4, walk 8, attack 6 (`clawing swipe`), hurt 2, death 6 |
| Z2 Koşucu | `thin fast runner zombie in a torn tracksuit, leaning forward, long arms` | idle 4, run 8, attack 5 (`leaping scratch`), hurt 2, death 6 |
| Z3 Toksik | `bloated toxic zombie with green glowing boils, yellow hazmat suit torn open, dripping green slime` | idle 4, walk 8, attack 6 (`vomits green acid`), death 6 (`bursts into green gas`) |
| Z4 Karanlık | `shadow zombie made of black smoke-like flesh, purple glowing veins and eyes, wispy edges` | idle 4, walk 8, attack 5, teleport 6 (`dissolves into black smoke`), death 6 |
| Z5 Tank | `huge muscular brute zombie, torn military pants, metal plates bolted to shoulders, tiny head` | idle 4, walk 8, attack 7 (`double fist ground smash`), death 7 |
| Z6 Gizemli | `tall faceless zombie in a black suit wearing a cracked white porcelain mask with red tear marks` (2. örnek fotoğrafındaki maske) | idle 4, walk 8, attack 6, death 6 |
| Z7 Titan (boss) · 128×128 | `colossal titan boss zombie, three times human size, exposed rib cage, chains hanging from wrists, burning red eyes, cracked stone-like skin` | idle 6, walk 8, slam 8 (`raises both arms and slams the ground`), roar 6, death 10 |
| Z8 Köpek zombi · 64×64 | `rotting zombie dog with exposed bones, red eyes` | run 6, bite 4, death 5 |

## N — Kişiler (NPC) · 96×96

| Kod | Prompt |
|---|---|
| N1 Tüccar | `[STİL] old wasteland merchant with a big backpack full of goods, goggles on forehead, long coat, friendly` (idle 4) |
| N2 Asker | `[STİL] city wall soldier in dark tactical gear and helmet with an assault rifle, guarding` (idle 4, shoot 3) |

## W — Silah ve eşya simgeleri · 32×32 (her biri ayrı üret)

Ortak: `[STİL] single game inventory icon, item centered at a slight angle, 32x32 pixels`

Silahlar: `brass knuckles` · `combat knife` · `rusty machete` · `wooden baseball bat with nails` · `long steel sword`
· `black 9mm pistol` · `pump-action shotgun` · `assault rifle` · `wooden torch with flame` · `unlit wooden torch`.
Kaliteler için ek: `rusty and worn` / `clean steel` / `masterwork with engraved runes and a faint glow`.

İksirler: `round glass potion bottle with glowing red liquid and a cork` (Can) · `... glowing cyan liquid, lightning
symbol on the label` (Hız) · `... glowing orange liquid` (Güç) · `... glowing green liquid` (Panzehir).

Malzemeler: `stack of wooden planks` · `grey stones` · `folded cloth` · `glue tube` · `amber resin chunk` ·
`leather piece` · `scrap metal pieces` · `screws` · `gunpowder pouch` · `circuit board` · `zombie tooth` ·
`toxic green crystal` · `dark purple crystal` · `titan shard glowing red` · `fuel jerrycan` · `bandage roll` ·
`first aid kit` · `canned food` · `energy drink can` · `gold coin` · `ammo box` (mermi türleri için renk eki).

## T — Zemin ve dünya · 32×32 dikişsiz karo

Ortak: `[STİL] seamless tileable top-down ground texture tile, 32x32 pixels, no objects` — Çim · Koyu çim · Kuru çim ·
Toprak yol · Çamur · Çakıl · Asfalt (çatlak) · Kaldırım · Arnavut kaldırımı · Sığ su · Bataklık suyu · Kum.

Nesneler (STİL + `top-down 3/4 view game prop`): ağaç ×3 (çam, meşe, ölü ağaç) · çalı ×2 · kaya ×3 · çit ·
sokak lambası · kamp ateşi (4 kare alev) · sandık (kapalı/açık) · varil · çöp konteyneri · enkaz yığını · mezar taşı.

Binalar (128×128 ya da 192×192, `top-down 3/4 view pixel art building, roof visible`): ahşap ev · tuğla ev ·
market · benzin istasyonu · karakol · hastane · yıkık bina · şehir duvarı parçası · nöbet kulesi.

Araçlar (128×128, 8 yön için PixelLab Rotate): `rusty orange sedan car` · `pickup truck` · `motorbike` · `bicycle` ·
`military jeep`. Her biri için hasar ekleri: `dented with broken windows` / `heavily damaged, smoking` / `burnt wreck`.

## E — Efektler (yatay şerit, 4–8 kare, şeffaf)

`muzzle flash` · `blood splatter` · `dust puff from a dodge roll` · `explosion with smoke` · `green toxic gas cloud` ·
`ground shockwave ring` · `fire burning` · `sparks hitting metal` · `level up golden pillar of light` · `heal green
sparkles` · `speed potion blue wind lines`.

## U — Arayüz

| Kod | Prompt |
|---|---|
| U1 Panel | `pixel art game UI panel frame, dark charcoal stone with rusted iron corners and rivets, empty center, 9-slice friendly, 96x96` |
| U2 Tuş | `pixel art game button, dark green metal plate with a lighter top bevel, empty, 64x24` (+ `pressed` hâli, + kırmızı ve mavi) |
| U3 Yuvarlak tuş | `round pixel art action button, dark metal ring with glowing ember edge, empty center, 48x48` |
| U4 Çerçeveler | `pixel art avatar frame, ornate dark iron with a red gem`, `... golden laurel`, `... purple flames animated 4 frames` |
| U5 Simgeler | `pixel art UI icon set: map, quest scroll, mail envelope, backpack, hammer and anvil, skull, gear, chat bubble, trophy, people group, paw print, potion, torch` (her biri 24×24) |

## S — Hikâye sineması ve ekranlar (ChatGPT/Gemini, 1920×1080, piksel sanat)

Ortak: `cinematic pixel art scene, 16:9, detailed, moody night lighting, in the style of the reference: dark purples,
crimson moon, warm orange window light`

| Sahne | Prompt |
|---|---|
| S1 Oda | `a young man without a hood sitting in a dark bedroom playing a video game on a glowing PC monitor, posters on the wall, window showing a calm night city` |
| S2 Balkon | `the same man on a small apartment balcony looking down in shock at a street where people run from zombies, burning cars, screams` |
| S3 Savaş | `military jets flying over a burning city at night, bombs exploding, tracer bullets in the sky, helicopters` |
| S4 Roket | `a rocket streaking toward an apartment building at night, bright explosion flash` (+ ayrı: `pure white flash frame with debris`) |
| S5 Enkaz | `the man waking up in the rubble of a destroyed apartment, dust in the air, a black hood lying on the floor in a beam of red moonlight` |
| S6 Tepe | `the hooded warrior standing on a high cliff seen from behind, black cloak flowing in the wind, overlooking a burning city under a huge crimson moon` |
| S7 Logo arka planı | `a huge crimson full moon over a ruined skyline, empty sky area at the top for a title` |
| L1 Giriş ekranı | `the hooded warrior sitting on a broken wall at night, a torch beside him, ruined city and crimson moon behind, space on the right side for menu buttons` |
| I1 Uygulama simgesi | `app icon, the hooded warrior's shadowed face and glowing eyes close-up, crimson moon behind, bold silhouette, 512x512, no text` |

> Sahneleri ben paralaks katmanlarına ayırıp hareket (kamera kayması, kül/yağmur parçacıkları, sarsıntı) ve altyazı ekleyeceğim.
> Mümkünse her sahneyi **iki parça** iste: `background only` ve `character only on plain magenta background`.

## Teslim sırası (öncelik)

1. **H1** (kahraman ana referansı) → onaylayınca H3 + H4.
2. P1 köpek, Z1–Z7 zombiler.
3. W silah + iksir simgeleri, U1–U3 arayüz.
4. T zemin/nesne/bina/araç.
5. S sahneler, L1, I1.

---

## Ek: Benim kullandığım ücretsiz yapay zekâ yolu (Stable Horde)

`tools/ai/horde.py` Stable Horde'a (gönüllü GPU ağı, anahtarsız da çalışır) istek atar. En iyi sonuç:
**AlbedoBase XL (SDXL) + Pixel Art XL LoRA (civitai 120096)**. Örnek: `art/v4/hero_ref_ai_*.webp` (kahraman referansı).

```
/opt/bvenv/bin/python tools/ai/horde.py --model "AlbedoBase XL (SDXL)" --lora 120096 --w 832 --h 1216 --n 2 \
  --out /home/user/zx_ai/ab "pixel art game character sprite, full body, ..." "blurry, photo, text, ..."
```
Notlar: anonim sırada bekleme 5–10 dk; koyu görseller bazen yanlışlıkla sansürlenir (prompt'a "fully clothed" eklemek yardımcı).
Tek görsel kaliteli ama 8 yön × animasyon tutarlılığı vermez; bu yüzden oyundaki kareler kod hattından gelir:
`tools/blender/warrior_b.py` (model) → `tools/blender/render_warrior.py` (2× render) → `tools/pixel/pixelize.py` (palet, kontur, ışık kenarı).
