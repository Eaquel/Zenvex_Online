//! V12.1 kurulabilir tezgâh: "Tezgâh" eşyası elle üretilir, kullanınca bulunulan yere kurulur.
//! Kişisel üste kurulan kalıcıdır (kayıtta, en çok HOME_MAX); başka yerde BENCH_TTL saniye durur (aynı anda bir tane).
//! Gelişmiş tarifler (bench: true) yalnız bir tezgâhın yanında, ayrı "Tezgâh" ekranında üretilir.
use crate::data::{self, ITEMS};
use crate::game::Game;

/// Dışarıda kurulan tezgâhın ömrü (sn)
pub const BENCH_TTL: f32 = 600.0;
/// Kişisel üsteki kalıcı tezgâh sınırı
pub const HOME_MAX: usize = 3;
/// Tezgâha yakınlık (dünya pikseli)
pub const BENCH_NEAR: f32 = 160.0;

impl Game {
    /// Tezgâhı oyuncunun önüne kurar.
    pub fn place_bench(&mut self, slot: usize) -> Result<String, String> {
        let s = self.progress.inv.slots.get(slot).copied().flatten().ok_or("Boş slot")?;
        if ITEMS[s.item as usize].id != "workbench" {
            return Err("Bu bir tezgâh değil".into());
        }
        if self.player.dead {
            return Err("Önce yeniden doğ".into());
        }
        // oyuncunun sağ yanına (önüne kurulunca karakteri örtüyordu)
        let (x, y) = (self.player.x + 70.0, self.player.y + 10.0);
        let msg = if self.at_home() {
            if self.progress.benches.len() >= HOME_MAX {
                return Err(format!("Üssüne en çok {HOME_MAX} tezgâh kurabilirsin"));
            }
            self.progress.benches.push((x, y));
            "Tezgâh kuruldu: kişisel üssünde kalıcı".to_string()
        } else {
            // dışarıda aynı anda tek tezgâh: yenisi eskisinin yerini alır
            self.temp_benches.clear();
            self.temp_benches.push((x, y, BENCH_TTL));
            format!("Tezgâh kuruldu: {} dakika durur", (BENCH_TTL / 60.0) as u32)
        };
        crate::inventory::take_from(&mut self.progress.inv.slots, slot, Some(1));
        Ok(msg)
    }

    /// Yakındaki kalıcı (üs) tezgâhı çantaya geri alır.
    pub fn pickup_bench(&mut self) -> Result<String, String> {
        let (px, py) = (self.player.x, self.player.y);
        let i = self
            .progress
            .benches
            .iter()
            .position(|&(x, y)| (x - px).hypot(y - py) < BENCH_NEAR)
            .ok_or("Yakında kaldırılabilecek tezgâh yok")?;
        let item = data::item_index("workbench").ok_or("Tezgâh eşyası yok")?;
        if !self.progress.inv.can_fit(item, 1) {
            return Err("Çantanda yer yok".into());
        }
        self.progress.benches.remove(i);
        self.progress.inv.add(item, 1);
        Ok("Tezgâh çantana alındı".into())
    }

    /// Oyuncunun kurduğu tezgâhlar: (x, y, kalan sn; kalıcıysa -1)
    pub fn placed_benches(&self) -> Vec<(f32, f32, f32)> {
        let mut v: Vec<(f32, f32, f32)> = self.progress.benches.iter().map(|&(x, y)| (x, y, -1.0)).collect();
        v.extend(self.temp_benches.iter().copied());
        v
    }

    /// Dışarıdaki tezgâhın süresi
    pub(crate) fn update_benches(&mut self, dt: f32) {
        let before = self.temp_benches.len();
        for b in self.temp_benches.iter_mut() {
            b.2 -= dt;
        }
        self.temp_benches.retain(|b| b.2 > 0.0);
        if self.temp_benches.len() < before {
            self.msg("Kurduğun tezgâhın süresi doldu");
        }
    }

    pub fn near_placed_bench(&self) -> bool {
        let (px, py) = (self.player.x, self.player.y);
        self.placed_benches().iter().any(|&(x, y, _)| (x - px).hypot(y - py) < BENCH_NEAR)
    }
}

#[cfg(test)]
mod tests {
    use crate::game::Game;
    use crate::Progress;

    #[test]
    fn workbench_places_temporary_outside_and_permanent_at_home() {
        let mut g = Game::new(7, Progress::default(), 0);
        g.progress.relayout_inv();
        g.progress.inv.add_id("workbench", 2);
        let slot = |g: &Game| g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == "workbench")).unwrap();
        // kampın dışında: tezgâh yok → kur → yanında tezgâh tarifleri açılır, 10 dk sonra kaybolur
        g.player.x = 150.0 * crate::world::TILE;
        g.player.y = 320.0 * crate::world::TILE;
        assert!(!g.near_bench());
        let s = slot(&g);
        g.place_bench(s).unwrap();
        assert!(g.near_bench());
        g.update_benches(super::BENCH_TTL + 1.0);
        assert!(!g.near_bench());
        // kişisel üste kalıcı, geri alınabilir
        let (hx, hy) = crate::world::home_spawn();
        g.player.x = hx;
        g.player.y = hy;
        let s = slot(&g);
        g.place_bench(s).unwrap();
        assert_eq!(g.progress.benches.len(), 1);
        g.update_benches(super::BENCH_TTL + 1.0);
        assert!(g.near_bench());
        assert_eq!(g.progress.inv.count_id("workbench"), 0);
        g.pickup_bench().unwrap();
        assert_eq!(g.progress.inv.count_id("workbench"), 1);
        assert!(g.progress.benches.is_empty());
    }

    #[test]
    fn hand_and_bench_craft_lists_are_separate() {
        let mut g = Game::new(7, Progress::default(), 0);
        let hand = g.ui_json("craft");
        let bench = g.ui_json("craft_bench");
        let ids = |s: &str| -> Vec<String> {
            let v: serde_json::Value = serde_json::from_str(s).unwrap();
            v["recipes"].as_array().unwrap().iter().map(|r| r["out"]["id"].as_str().unwrap().to_string()).collect()
        };
        let (h, b) = (ids(&hand), ids(&bench));
        assert!(h.contains(&"bandage".to_string()) && h.contains(&"workbench".to_string()));
        assert!(b.contains(&"medkit".to_string()) && !b.contains(&"bandage".to_string()));
        assert!(h.iter().all(|x| !b.contains(x)), "el ve tezgâh listeleri ayrı olmalı");
        // tezgâh tarifi elden üretilemez
        let i = crate::data::RECIPES.iter().position(|r| r.bench).unwrap();
        g.player.x = 150.0 * crate::world::TILE;
        g.player.y = 320.0 * crate::world::TILE;
        let r: serde_json::Value = serde_json::from_str(&g.command(&format!(r#"{{"cmd":"craft","i":{i}}}"#))).unwrap();
        assert_eq!(r["ok"], false);
    }
}
