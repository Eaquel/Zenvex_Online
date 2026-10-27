# Changelog

## Unreleased (V16 — 3D)

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
- Promo kit: image prompts and a trailer pipeline built from real engine gameplay with game sounds and Turkish voice-over
  (`docs/23_TANITIM.md`, `tools/promo/trailer.py`)

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
