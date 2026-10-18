//! Zenvex Online oyun çekirdeği. Motordan ve platformdan bağımsızdır;
//! Android tarafı `zenvex_jni` üzerinden kullanır, ileride sunucu da aynı kodu kullanacak.
pub mod data;
pub mod game;
pub mod ground;
pub mod inventory;
pub mod progress;
pub mod rng;
pub mod ui;
pub mod world;

mod combat;
mod draw;
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

/// Mini harita için küçültülmüş zemin+bölge görüntüsü (ARGB, `n`x`n`).
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
}
