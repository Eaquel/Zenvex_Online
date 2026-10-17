//! Zenvex Online oyun çekirdeği. Motordan ve platformdan bağımsızdır;
//! Android tarafı `zenvex_jni` üzerinden kullanır, ileride sunucu da aynı kodu kullanacak.
pub mod game;
pub mod progress;
pub mod rng;
pub mod world;

pub use game::{Game, Input};
pub use progress::Progress;

/// Dünya verisi (bir kez): karolar, zemin lekeleri, statik nesne isimleri.
pub fn world_json(g: &Game) -> String {
    use serde_json::json;
    let w = &g.world;
    let decals: Vec<_> = w.decals.iter().map(|d| json!([d.name, d.x, d.y])).collect();
    json!({
        "tile": world::TILE,
        "size": world::MAP_TILES,
        "tiles": w.tiles,
        "decals": decals,
        "names": &world::STATIC_NAMES[..],
        "statics": w.statics.len(),
    })
    .to_string()
}

pub fn skills_json(p: &Progress) -> String {
    use serde_json::json;
    let list: Vec<_> = progress::SKILLS
        .iter()
        .map(|s| {
            json!({
                "id": s.id,
                "branch": format!("{:?}", s.branch),
                "name": s.name,
                "desc": s.desc,
                "max": s.max,
                "req": s.req_level,
                "icon": s.icon,
                "lvl": p.skills[s.id],
                "can": p.can_upgrade(s.id).is_ok(),
                "why": p.can_upgrade(s.id).err().unwrap_or(""),
            })
        })
        .collect();
    json!({ "points": p.points, "level": p.level, "skills": list }).to_string()
}

pub fn progress_from_json(s: &str) -> Progress {
    serde_json::from_str(s).unwrap_or_default()
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
        p.wood = 12;
        let s = progress_to_json(&p);
        let q = progress_from_json(&s);
        assert_eq!(q.level, p.level);
        assert_eq!(q.wood, 12);
        assert_eq!(progress_from_json("bozuk").level, 1);
        assert_eq!(progress_from_json("{\"level\":7}").level, 7);
    }

    #[test]
    fn json_outputs_parse() {
        let g = Game::new(1, Progress::default());
        let w: serde_json::Value = serde_json::from_str(&world_json(&g)).unwrap();
        assert_eq!(w["tiles"].as_array().unwrap().len(), 128 * 128);
        let s: serde_json::Value = serde_json::from_str(&skills_json(&g.progress)).unwrap();
        assert_eq!(s["skills"].as_array().unwrap().len(), 12);
    }
}
