//! Zenvex Online oyun çekirdeği. Motordan ve platformdan bağımsızdır;
//! Android tarafı `zenvex_jni` üzerinden kullanır, ileride sunucu da aynı kodu kullanacak.
pub mod data;
pub mod game;
pub mod ground;
pub mod inventory;
pub mod progress;
pub mod rng;
pub mod rpg;
pub mod story;
pub mod pet;
pub mod ui;
pub mod world;

mod combat;
mod draw;
pub mod net;
mod vehicles;
mod zombies;

pub use game::{Game, Input};
pub use progress::Progress;

/// Dünya verisi (bir kez): boyutlar, statik nesne ve zemin isimleri, eşya/zombi/araç tabloları.
pub fn world_json(g: &Game) -> String {
    use serde_json::json;
    let w = &g.world;
    let items: Vec<_> = data::ITEMS
        .iter()
        .map(|i| json!([i.id, i.name, i.rarity as u8]))
        .collect();
    let zombies: Vec<_> = data::ZOMBIES
        .iter()
        .map(|z| json!([z.name, z.sprite, z.color]))
        .collect();
    let vehicles: Vec<_> = data::VEHICLES
        .iter()
        .map(|v| json!([v.id, v.name, v.seats]))
        .collect();
    let weapons: Vec<_> = data::WEAPONS
        .iter()
        .map(|v| json!([v.item, v.sprite]))
        .collect();
    let zones: Vec<_> = world::ZONES_ALL
        .iter()
        .map(|z| json!([z.name(), z.level_range().0, z.level_range().1, z.safe()]))
        .collect();
    let travel: Vec<_> = world::TRAVEL
        .iter()
        .map(|t| {
            let (x, y) = t.pos();
            json!([t.id, t.name, x, y, t.level, t.cost])
        })
        .collect();
    let chars: Vec<_> = data::CHARACTERS
        .iter()
        .map(|c| json!([c.id, c.name]))
        .collect();
    json!({
        "tile": world::TILE,
        "size": world::MAP_TILES,
        "map_px": world::MAP_PX,
        "names": world::STATIC_NAMES,
        "ground_names": world::World::ground_names(),
        "statics": w.statics.len(),
        "merchants": w.merchants,
        "benches": w.benches,
        "camp": world::CAMP,
        "travel": travel,
        "landmarks": world::LANDMARKS.iter().map(|l| { let (x, y) = l.pos(); json!([l.name, x, y]) }).collect::<Vec<_>>(),
        "boss": world::BOSS,
        "wall_y": world::WALL_Y as f32 * world::TILE,
        "items": items,
        "zombies": zombies,
        "vehicles": vehicles,
        "weapons": weapons,
        "zones": zones,
        "chars": chars,
        "city_level": progress::CITY_LEVEL,
    })
    .to_string()
}

/// Harita görüntüsü (ARGB, `n`x`n`): zemin + binalar (çatı), ağaçlar, kayalar, çalılar.
/// Mini harita ve tam harita bunu kullanır; 1024 px'te bir piksel ≈ 22 dünya pikseli.
pub fn minimap(g: &Game, n: usize, out: &mut [u32]) {
    const COL: [u32; world::ground::COUNT] = [
        0xFF4E7A34, 0xFF365A26, 0xFF8A8A4A, 0xFF7A5E3E, 0xFF8C8478, 0xFF3A3C40, 0xFF9C9890,
        0xFF7E7C78, 0xFF4A4630,
    ];
    for py in 0..n {
        for px in 0..n {
            let tx = px * world::MAP_TILES / n;
            let ty = py * world::MAP_TILES / n;
            if let Some(o) = out.get_mut(py * n + px) {
                *o = COL[(g.world.ground_at(tx, ty) as usize).min(world::ground::COUNT - 1)];
            }
        }
    }
    let k = n as f32 / world::MAP_PX;
    let put = |x: i32, y: i32, c: u32, out: &mut [u32]| {
        if x >= 0 && y >= 0 && (x as usize) < n && (y as usize) < n {
            out[y as usize * n + x as usize] = c;
        }
    };
    let disc = |cx: f32, cy: f32, r: f32, c: u32, out: &mut [u32]| {
        let ri = r.ceil() as i32;
        for dy in -ri..=ri {
            for dx in -ri..=ri {
                if (dx * dx + dy * dy) as f32 <= r * r + 0.3 {
                    put(cx as i32 + dx, cy as i32 + dy, c, out);
                }
            }
        }
    };
    use world::{Kind, Shape};
    // önce zemin nesneleri, sonra binalar (üstte)
    for pass in 0..2 {
        for s in &g.world.statics {
            if s.broken {
                continue;
            }
            let (cx, cy) = (s.x * k, s.y * k);
            match (pass, s.kind, s.shape) {
                (0, Kind::Tree, _) => {
                    disc(cx + 0.6, cy + 0.6, 2.2, 0xFF1E3416, out);
                    disc(cx, cy, 1.8, 0xFF2E5A22, out);
                    put(cx as i32 - 1, cy as i32 - 1, 0xFF4A7A34, out);
                }
                (0, Kind::Bush, _) => disc(cx, cy, 0.9, 0xFF3A6A2A, out),
                (0, Kind::Rock, _) => disc(cx, cy, 1.1, 0xFF8A8A88, out),
                (1, Kind::Building | Kind::Tower | Kind::Stall, Shape::Rect(hw, hh)) => {
                    let (x0, x1) = ((cx - hw * k) as i32, (cx + hw * k) as i32);
                    // çatı ön yüzün üstünde: binanın derinliği kadar yukarı uzat
                    let (y0, y1) = ((cy - hh * k * 3.0) as i32, (cy + hh * k) as i32);
                    for y in y0..=y1 {
                        for x in x0..=x1 {
                            let edge = x == x0 || x == x1 || y == y0 || y == y1;
                            let c = if edge { 0xFF1E1A16 } else if (y - y0) % 3 == 0 { 0xFF6A4A3A } else { 0xFF7E5A44 };
                            put(x, y, c, out);
                        }
                    }
                }
                (1, Kind::Fire, _) => disc(cx, cy, 1.4, 0xFFFFA040, out),
                _ => {}
            }
        }
    }
}

pub fn progress_from_json(s: &str) -> Progress {
    let mut p: Progress = serde_json::from_str(s).unwrap_or_default();
    if p.level == 0 {
        p.level = 1;
    }
    p.unlocked[0] = true;
    if !p.unlocked[p.character.min(3)] {
        p.character = 0;
    }
    p
}

pub fn progress_to_json(p: &Progress) -> String {
    serde_json::to_string(p).unwrap_or_else(|_| "{}".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn save_round_trip() {
        let mut p = Progress::default();
        p.add_xp(500);
        p.inv.add_id("wood", 12);
        let s = progress_to_json(&p);
        let q = progress_from_json(&s);
        assert_eq!(q.level, p.level);
        assert_eq!(q.inv.count_id("wood"), 12);
        assert_eq!(progress_from_json("bozuk").level, 1);
        assert_eq!(progress_from_json("{\"level\":7}").level, 7);
    }

    #[test]
    fn json_outputs_parse() {
        let mut g = Game::new(1, Progress::default(), 0);
        let w: serde_json::Value = serde_json::from_str(&world_json(&g)).unwrap();
        assert_eq!(
            w["names"].as_array().unwrap().len(),
            world::STATIC_NAMES.len()
        );
        for k in [
            "profile",
            "inventory",
            "craft",
            "merchant",
            "skills",
            "characters",
            "quests",
            "pass",
            "cosmetics",
        ] {
            let v: serde_json::Value = serde_json::from_str(&g.ui_json(k)).unwrap();
            assert!(v.get("error").is_none(), "{k}");
        }
        let r: serde_json::Value =
            serde_json::from_str(&g.command(r#"{"cmd":"cheat","text":"gold 500"}"#)).unwrap();
        assert_eq!(r["ok"], true);
        let r: serde_json::Value =
            serde_json::from_str(&g.command(r#"{"cmd":"buy","i":0,"n":2}"#)).unwrap();
        assert_eq!(r["ok"], true, "{r}");
    }

    #[test]
    fn simulate_a_while() {
        let mut g = Game::new(3, Progress::default(), 0);
        g.command(r#"{"cmd":"cheat","text":"weapons"}"#);
        g.command(r#"{"cmd":"cheat","text":"spawn tank 5"}"#);
        let mut buf = vec![0f32; game::STRIDE * 4000];
        for i in 0..1800 {
            let a = i as f32 * 0.01;
            let inp = Input {
                move_x: a.cos(),
                move_y: a.sin(),
                atk_x: 1.0,
                atk_y: 0.0,
                attacking: i % 3 != 0,
            };
            g.step(1.0 / 60.0, inp);
            if i % 300 == 0 {
                g.command(r#"{"cmd":"cycle_weapon"}"#);
            }
            g.draw_list(
                g.player.x - 600.0,
                g.player.y - 400.0,
                1200.0,
                800.0,
                &mut buf,
                1.0,
            );
            let _ = g.hud();
            let _ = g.take_events_json();
        }
    }

    /// Harita görüntüsünü ham ARGB olarak yazar (ZX_DUMP_MAP=dosya; görsel kontrol için).
    #[test]
    fn dump_map() {
        let Ok(path) = std::env::var("ZX_DUMP_MAP") else { return };
        let g = Game::new(7, Progress::default(), 1);
        let n = 1024;
        let mut buf = vec![0u32; n * n];
        minimap(&g, n, &mut buf);
        let bytes: Vec<u8> = buf.iter().flat_map(|c| c.to_le_bytes()).collect();
        std::fs::write(path, bytes).unwrap();
    }
}
