# Changelog

## Unreleased (V16 — 3D)

- V19 performance pass (no quality loss):
  - map fix: the detailed region map was never built (a "ground ready" flag fell once on the first frames and never
    recovered), so the minimap and the big map kept showing the old flat-colour world map
  - crowds: creature separation O(n²) → grid O(n), solid-object collision grid, cheaper draw-order sort and distance
    maths; 10,000 creatures: 311 → 3.9 ms per simulation frame (desktop), 1,000 creatures drawn: 85 → 22 ms per frame
  - no per-frame garbage: gradients and colour filters cached, sprite lookups cached by number, particle cap with
    O(n) removal; creature plates and silhouette shadows thin out only in huge crowds
  - memory: atlas pages unused for 45 s are freed from GPU memory (they used to pile up to ~277 MB), ground tiles live
    only on the GPU, weapon/wing/vehicle models load when first seen, caches shrink on low-memory phones and when the
    system asks
  - heat: the scene is not drawn behind full-screen windows (world keeps running at 20 Hz), predictive thermal headroom
    on Android 11+ eases off before the phone heats up, HUD data is only re-parsed when it changes, saves are written
    in the background

- V19: every 3D model (4 heroes, 9 NPCs, 18 weapons, car and motorcycle, 4 wing sets) rebuilt from the original
  high-detail sources: ~2.4× the triangles (heroes 26k, NPCs 16k, cars 22k, weapons 5k), sharper textures (heroes and
  cars 1536, weapons 512) and new normal maps so stitches, straps, armour plates and wrinkles catch the light
- V19: surfaces repainted: blotches and muddy smears cleaned up per material, clean material boundaries, fine grain kept,
  worn edge highlights and crevice shading from the geometry; heroes' arms no longer get wrong, washed-out colours
  (colours are baked before the arms are lowered); Kael's face is hidden in the dark of his hood
- V19: very bright surfaces no longer blow out to white; specular and rim light fade on near-black surfaces; model
  textures kept without alpha and halved on low-memory phones
- V19: 3D characters look sharper up close and from afar: textures use trilinear filtering (no more grainy patches),
  High quality renders with 3× anti-aliasing plus light sharpening, lighting has more contrast (soft shadow side, sky light,
  specular glints, stronger rim light, richer colours); showcase renders (character screen, lobby) are no longer upscaled
- V19: menus restyled: bevelled panels, tabs with icons and a gold underline, segmented choices in a recessed track,
  animated switches, section headers with an accent bar, highlighted title strip; 77 new strings translated
- V19: repository cleanup: old prompt documents, archive, audit reference images, one-off tool scripts and unused
  backgrounds removed
- V19: personal base without ice ground or zombies; border decorations removed (only soft cloud bands); chests reduced to
  Wood, Iron and Military; admin panel rewritten; quest markers use the tap hand and every early quest points at its target

- Quests rewritten: the tutorial now teaches every system step by step (equip armor, set up a workbench, move items to
  pockets and use them, sell to Marco, teleport to the personal base and back with Aldric, minimap, skill tree, mail, chat)
  and Silas's chain adds the motorcycle build (frame, wheels, engine, order at Viktor); saves move to the new order by quest id
- Interactive tutorial markers: glowing frame and bouncing arrow on the exact item, button, tab, recipe and target slot
  (bag, crafting screen, pockets and HUD buttons), with a short explanation card; new strings in all ten languages
- V18: VIP system removed (shop tab, points, badge, chat theme, perks); wings can no longer be chosen by players and are
  equipped or removed only by an admin (admin panel → Wings; needs the v18 admin migration)
- Shop: 19 new generated images (tabs, tickets, cards, packs, gem piles, background)
- 3D portraits (profile pictures, top-left player portrait, NPC conversation panel with the talking animation)
- Vehicles: dust behind the vehicle (no longer drawn over the driver when heading down), trees fade harder while driving
- Combat animations: third finisher for one-handed weapons, horizontal/reverse second move for hammer, pickaxe and storm axe,
  fist combo (jab, cross, uppercut), animated wings with tip bend and ember/sparkle effects, drinking while walking/running,
  brighter 3D characters, per-stage slash trails; no slash trail when chopping or mining
- Regions are isolated: neighbour objects are not drawn and the border is a cloud sea; always daytime; fewer zombies (14 per area)
- Titan no longer respawns on every join (kill is remembered per 15-minute slot)
- Workbench lists hand recipes too; crate button says collection items are coming soon; admin button moved out from under the event banner
- Map: camp characters and landmarks with labels, radar shows characters and places; first-run numbered controls guide (also in Settings)
- Skill tree redrawn (metal bezels, gem sockets, energy beams, star-burst keystones, nebulae); new respawn icon
- Admin: maintenance message per language; heat protection (frame and effect cap follows the device thermal status)
- Settings: no scrolling (columns), slider knobs no longer jump to zero when switching tabs

- Settings: no scrolling anywhere — tabs sit in one row on top and each tab's controls are laid out in columns so everything
  shows at once even on the smallest landscape phones (800×360 dp); the support form, account page, language list and legal
  texts are laid out the same way; the support message box is now a real multi-line field
- Lobby: the gameplay preview window at the bottom left is gone
- Lobby: the live sunset city scene stays; the street now shows the game's own zombies (walker, runner, tank, dark,
  dog sprites) walking toward the campfire and the game's own car (3D Hellhound) instead of generic silhouettes; news tab
  lists this version's changes; info dialog fits small phones
- Realistic lobby scene (GPU shaders on Android 13+, the old drawing on older phones and on the lowest effects setting):
  sky with light scattering around the sun, soft drifting clouds lit on the sun side and light rays through the gaps;
  rooftop fires as moving noise flames (dark red → orange → white-hot core) spreading along the roof, with thick smoke
  columns bending in the wind and fire light washing over the nearby walls; drifting fog between the city layers and at
  street level; buildings with lit side faces, floors, glass windows (dark, broken, lit, flickering TV, burning with soot
  marks), torn holes, water tanks, AC boxes, antennas with blinking red lights and rebar on broken roofs; wet street with
  kerb, worn lane line, cracks, rubble and puddles reflecting the sky; street lamp light cone; campfire with stone ring,
  glowing logs and smoke; sandbag barricade lit by the fire; falling ash. Buildings and street are drawn once per screen
  size, so the scene costs little per frame

- Smoothness: ground chunks are built on a background thread in short strips (the game loop no longer waits for 256 px
  chunks when entering new ground); the ring around the view and two chunks ahead in the walking direction load early
- Audio mix: per-sound levels and priorities, background sounds drop out in crowded fights so hits and explosions stay
  audible, small pitch variation on repeated sounds, music ducks briefly under explosions, boss, level-up and quest jingles
- Trade: gear keeps its durability, the confirm button waits 3 s after the other offer changes, and a trade cannot complete
  if the other side changed the offer after you confirmed; clan depot send dialog fits small phones

- Screen layout: one top-centre stack (zone name → boss bar / base card / event banner, only one at a time → announcement);
  announcements are queued instead of overlapping; the tutorial bubble waits while a banner shows; toasts moved above the
  pockets; gold and gems share one compact pill; correct banner titles (NEW QUEST / QUEST COMPLETE / QUEST READY)
- No language screen: the game follows the device language until a language is picked in Settings
- Events unlock at level 10 (no banner and no points before that)
- Reload button moved next to the attack stick, new magazine icon, rounds left under it, red pulse when empty
- Vehicles: car 500 HP, motorcycle 200 HP; while driving the car takes all damage, the motorcycle 70%; wear 1% per 60 s (car)
  / 30 s (motorcycle) of driving; at 0 HP it explodes and can be summoned again after 60 / 30 s of play time; HP, wear and
  cooldown are saved (dismounting, switching vehicles, quitting or re-joining cannot refresh them); the mechanic repairs
- Durability for every weapon, tool, armour piece, ring and necklace: a bar under each slot, "Durability %" in the item
  panel; weapons wear per hit or shot, tools per gather hit, armour/jewellery per hit taken (bags never break); broken items
  stop working (weapon = fist) until repaired at a workbench or the mechanic; worn items sell for less; durability is kept
  when equipping, moving, storing in chests or dropping on the ground
- Axes and pickaxes only work when held (stone 50, iron 100 per hit; fist 25)
- Hand Axe removed (owned ones become knives); the tutorial now crafts and equips a knife
- Fewer creature types: two signature creatures per region; a regional creature replaces the classic look of its role
- Leaves: a falling tree drops leaves from its crown around the trunk instead of a green burst; swings no longer hit nearby
  trees and rocks while a zombie is close

- NPCs are 3D models (Marco, Silas, Elena, Viktor, Jack, Aldric, Diana, soldier, sniper): they breathe, turn to face the
  player and switch to a talking animation when you come close; new NPC animations (talk, aim, shoot) in the renderer
- Wings: four 3D wing cosmetics worn on the back that flap with the hero's movement (Ember Wings VIP 3, Dragon Wings VIP 6,
  Titan Wings VIP 9, Season 1 Wings pass tier 30); equipped from the character screen
- New art: city buildings, shops, houses, gas station, watchtower, car wrecks, barrier, rubble wall, fence, tent, sandbags,
  barrel, cone, tyres, log and billboard; all 16 armor pieces, necklaces and rings (rings renamed Bone, Rune and Dark Ring)
- Lobby: title reads "Zenvex Online"; the character purchase shortcut is gone from the lobby (heroes are bought in game)

- Heroes, weapons and vehicles are real 3D models (`rust/zenvex_r3d`): software renderer with cel lighting and outline,
  13-bone auto-rig, two-bone arm IK, code-driven animation for every state (idle, walk, run, roll, melee combos per weapon
  family, shouldered aim, recoil, magazine swap, gathering, riding, death, get-up); frames are cached per pose and direction
- Heroes renamed to Kael, Lyra, Vex and Morvath; character screen, lobby and bag show the live 3D hero with the equipped weapon
- All 18 weapon models in game: pistol, AK-47, sniper rifle and RPG-7 return (magazine only, no spare ammo item), new Uzi,
  hatchet, battle axe and cursed stone mallet; event weapons use the blood scythe, magma hammer, storm axe, chainsaw sword,
  void claw and ice scythe models; weapon and character card icons rendered from the models
- Code effects: weapon swing trails, element effects (embers, frost and snow, blood drops, void wisps, lightning, chainsaw
  sparks, spirit flames), muzzle flash with smoke and ejected shells, elemental impact bursts, vehicle nitro
- Hellhound (car) and Nightstalker (motorcycle) with spinning wheels, body tilt, rider on the seat; remote players' vehicles too
- New setting: 3D character quality (low / medium / high); tree fade fixed for high-density sprites
- Effect engine v2: procedural pixel-art textures, rune-seal hero aura (levels 10/25/40/50), silhouette shadows for heroes,
  vehicles, NPCs, creatures and chests, textured dust/tire tracks; explosions, level-up, poison, teleport, lightning, fire,
  rage, frost, blood and hit sparks are all code (the old sprite effect sheets are no longer used)
- Scythe: three-stroke combo (wide sweep, rising backhand, full whirlwind) with crescent shock waves; axe, hatchet, pickaxe
  and hammer combos reworked; axes and pickaxes can now be held (1.6x gather power) and show as real tools while chopping
  and mining, with bark, splinter, leaf, rock and spark effects
- RPG-7: the grenade leaves the launcher when fired, the new grenade is taken from the back and pushed in at the muzzle;
  back-blast, smoke trail and a textured rocket projectile
- Settings audit: shadows, run threshold, effect quality and render resolution now really work; graphics presets cut draw
  calls and render size (test `p01_presetCost`); colour-blind field removed
- Lobby: no hero showcase; code-drawn animated ruined-city scene, fires, embers, distant zombie silhouettes, code logo;
  rotating gallery of real in-engine gameplay shots (`assets/ui/shot_*`, made by `tools/promo/stills.py`)
- First launch opens a language screen with the device language preselected, then goes straight to sign-in and the lobby
- Quests refreshed: tutorial rewritten (axe, pickaxe, first weapon), main quest chain up to level 40 (outposts, region
  hunts, Titan), daily "break 12 rocks"; new strings translated into all nine languages
- Unused effect sprites, old hero/vehicle frames, old backgrounds and concept art folders removed (smaller APK)
- Release: version 0.4, optional store signing key from GitHub secrets, AAB output (`docs/teknik/YAYIN.md`)
- Promo kit: image prompts and a trailer/Reels pipeline built from real engine gameplay with the real game HUD, the game's own
  sounds (captured per event) and optional voice-over (`docs/23_TANITIM.md`, `tools/promo/edit.py`)
- Item icon prompts for armor sets, necklaces, rings, bags, ammo and chests (`docs/24_ESYA_PROMPTLARI.md`)

## Unreleased (V15)

- Nine interface languages besides Turkish, language picker in Settings, device language by default
- Regions are separate square maps with void between them; only Aldric travels between regions
- Real minimap and world map drawn from the game's own ground textures; markers and legend
- Workbench build mode, merchant rework, clan depot send limit
- Store, VIP, season pass and character cards with Google Play Billing and server-side verification
- Support tickets with photos, reports with a reason, admin tabs for reports, support and economy
- Own Rust game server (`rust/zenvex_server`) with one-command deployment
- Toolchain: Gradle 9.8, AGP 9.4, Kotlin 2.4, Compose BOM 2026.09, compileSdk 37; zero warnings on CI

## 0.3.0-beta

- First public beta: 8 regions, Titan events, clans and base capture, crafting, vehicles, dogs, leaderboards, mail
