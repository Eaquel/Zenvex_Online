//! Kişisel üs (büyücünün ışınladığı, duvarla kapalı güvenli bölge) ve büyücü ışınlanması.
//! Her oyuncu üssünü yalnız kendisi görür: başka oyuncular üs alanında çizilmez, sandıklar kayıttan gelir.
use crate::game::*;
use crate::areas;
use crate::world::{self, TILE};

/// Işınlanma bekleme süresi (sn); ücret bölgeye göre (areas.rs)
pub const WIZ_CD: f32 = 8.0;

pub struct Dest {
    pub id: &'static str,
    pub name: &'static str,
    pub x: f32,
    pub y: f32,
    pub cost: u32,
}

impl Game {
    /// Oyuncu kişisel üssünün içinde mi (sandık yalnız burada kurulur).
    pub fn at_home(&self) -> bool {
        Self::in_home(self.player.x, self.player.y)
    }

    pub fn in_home(x: f32, y: f32) -> bool {
        x >= 0.0 && y >= 0.0 && world::in_home_tile((x / TILE) as usize, (y / TILE) as usize)
    }

    /// V12: büyücü bölgeler arası ışınlar (bölgeler ayrı harita). Bulunulan bölge dışındaki, seviyesi yeten her bölge;
    /// varış bölgenin güvenli giriş karakolu. Kişisel üs ve Başlangıç Ormanı ücretsiz.
    pub fn wizard_dests(&self) -> Vec<Dest> {
        let here = areas::area_at(self.player.x, self.player.y);
        areas::AREAS
            .iter()
            .enumerate()
            .filter(|&(i, a)| i != here && self.progress.level >= a.level)
            .map(|(i, a)| {
                let (x, y) = areas::hub(i);
                Dest { id: a.id, name: a.name, x, y, cost: a.cost }
            })
            .collect()
    }

    pub fn wizard_teleport(&mut self, id: &str) -> Result<String, String> {
        if self.player.dead {
            return Err("Önce yeniden doğ".into());
        }
        if self.player.vehicle.is_some() {
            return Err("Önce araçtan in".into());
        }
        if self.wiz_cd > 0.0 {
            return Err(format!("Büyü toparlanıyor: {:.0} sn", self.wiz_cd.ceil()));
        }
        if self.player.combat_t < 5.0 {
            return Err("Çatışmadayken ışınlanamazsın".into());
        }
        let Some(a) = areas::AREAS.iter().find(|a| a.id == id) else {
            return Err("Bilinmeyen bölge".into());
        };
        if self.progress.level < a.level {
            return Err(format!("{} için {}. seviye gerekli", a.name, a.level));
        }
        let Some(d) = self.wizard_dests().into_iter().find(|d| d.id == id) else {
            return Err("Zaten bu bölgedesin".into());
        };
        if !self.world.zone(d.x, d.y).safe() {
            return Err("Orası güvenli değil".into());
        }
        if self.progress.gold < d.cost {
            return Err(format!("{} altın gerekli", d.cost));
        }
        self.progress.gold -= d.cost;
        let (ox, oy) = (self.player.x, self.player.y);
        self.events.push(Event::Teleport { x: ox, y: oy });
        let (x, y) = self.teleport(d.x, d.y);
        self.events.push(Event::Teleport { x, y });
        if let Some(dg) = self.dog.as_mut() {
            dg.x = x + 50.0;
            dg.y = y + 30.0;
        }
        self.zombies.retain(|z| (z.x - x).hypot(z.y - y) > 700.0 || z.boss);
        self.wiz_cd = WIZ_CD;
        Ok(format!("{} konumuna ışınlandın", d.name))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::Progress;

    #[test]
    fn every_teleport_lands_in_safe_zone() {
        let mut p = Progress::default();
        p.level = 50;
        let mut g = Game::new(7, p, 0);
        // haritanın tamamı keşfedilmiş say
        for x in (0..world::MAP_TILES).step_by(4) {
            for y in (0..world::MAP_TILES).step_by(4) {
                g.progress.reveal(x as f32 * TILE, y as f32 * TILE, 1);
            }
        }
        let dests = g.wizard_dests();
        assert_eq!(dests.len(), areas::AREAS.len() - 1);
        for d in &dests {
            assert!(g.world.zone(d.x, d.y).safe(), "{} güvenli değil", d.id);
        }
        // harita seyahatleri de
        for t in world::TRAVEL {
            let (x, y) = t.pos();
            assert!(g.world.zone(x, y).safe(), "seyahat {} güvenli değil", t.id);
        }
        g.player.combat_t = 99.0;
        g.progress.gold = 100;
        assert!(g.wizard_teleport("home").is_ok());
        assert!(g.at_home());
        assert_eq!(g.world.zone(g.player.x, g.player.y), world::Zone::Home);
        // bekleme süresi
        assert!(g.wizard_teleport("forest").is_err());
        g.wiz_cd = 0.0;
        assert!(g.wizard_teleport("forest").is_ok());
        assert!(!g.at_home());
        // seviye yetmeyen bölge
        g.wiz_cd = 0.0;
        g.progress.level = 12;
        assert!(g.wizard_teleport("harbor").unwrap_err().contains("40"));
        assert!(g.wizard_teleport("city").is_ok());
        assert_eq!(areas::area_at(g.player.x, g.player.y), areas::CITY);
    }

    #[test]
    fn home_is_sealed() {
        let g = Game::new(7, Progress::default(), 0);
        let c = ((world::HOME_X0 + world::HOME_X1) as f32 / 2.0 * TILE, (world::HOME_Y0 + world::HOME_Y1) as f32 / 2.0 * TILE);
        let mut scratch = Vec::new();
        // dışarıdan merkeze doğru yürümek duvara takılır (dört yönden)
        for (ox, oy) in [(0.0, -20.0), (0.0, 20.0), (-20.0, 0.0), (20.0, 0.0)] {
            let (mut x, mut y) = (c.0 + ox * TILE, c.1 + oy * TILE);
            for _ in 0..600 {
                let (dx, dy) = (c.0 - x, c.1 - y);
                let l = dx.hypot(dy).max(1.0);
                let (mut nx, mut ny) = (x + dx / l * 5.0, y + dy / l * 5.0);
                g.world.resolve(&mut nx, &mut ny, 14.0, true, &mut scratch);
                x = nx;
                y = ny;
            }
            assert!((x - c.0).hypot(y - c.1) > 12.0 * TILE, "duvar delik ({ox},{oy}): {x},{y}");
        }
    }
}
