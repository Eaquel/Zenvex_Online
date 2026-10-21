//! Kişisel üs (büyücünün ışınladığı, duvarla kapalı güvenli bölge) ve büyücü ışınlanması.
//! Her oyuncu üssünü yalnız kendisi görür: başka oyuncular üs alanında çizilmez, sandıklar kayıttan gelir.
use crate::game::*;
use crate::progress::{CITY_LEVEL, FOG_N};
use crate::world::{self, LANDMARKS, TILE};

/// Işınlanma bekleme süresi (sn) ve karakol ücreti (kişisel üs ve kamp ücretsiz)
pub const WIZ_CD: f32 = 8.0;
pub const WIZ_COST: u32 = 15;

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

    fn explored_at(&self, x: f32, y: f32) -> bool {
        let cell = world::MAP_PX / FOG_N as f32;
        self.progress.explored((x / cell) as usize, (y / cell) as usize)
    }

    /// Büyücünün ışınlayabildiği yerler: kişisel üs, kamp, (seviye yetiyorsa) şehir güvenli bölgesi ve keşfedilen
    /// güvenli karakollar. Hepsi güvenli bölgedir.
    pub fn wizard_dests(&self) -> Vec<Dest> {
        let mut v = Vec::new();
        let (px, py) = (self.player.x, self.player.y);
        let far = |x: f32, y: f32| (x - px).hypot(y - py) > 12.0 * TILE;
        let (hx, hy) = world::home_spawn();
        if !self.at_home() {
            v.push(Dest { id: "home", name: "Kişisel Üssüm", x: hx, y: hy, cost: 0 });
        }
        let (cx, cy) = world::camp_spawn();
        if far(cx, cy) {
            v.push(Dest { id: "camp", name: "Kamp", x: cx, y: cy, cost: 0 });
        }
        let (sx, sy) = world::city_spawn();
        if self.progress.level >= CITY_LEVEL && self.explored_at(sx, sy) && far(sx, sy) {
            v.push(Dest { id: "city", name: "Şehir Güvenli Bölgesi", x: sx, y: sy, cost: WIZ_COST });
        }
        for l in LANDMARKS.iter().filter(|l| l.safe) {
            let (lx, ly) = (l.tx * TILE, (l.ty + 2.5) * TILE);
            if self.explored_at(lx, ly) && far(lx, ly) {
                v.push(Dest { id: l.id, name: l.name, x: lx, y: ly, cost: WIZ_COST });
            }
        }
        v
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
        let Some(d) = self.wizard_dests().into_iter().find(|d| d.id == id) else {
            return Err("Bu yere ışınlanamazsın (önce keşfetmelisin)".into());
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
        assert!(dests.len() >= 10, "{}", dests.len());
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
        assert!(g.wizard_teleport("camp").is_err());
        g.wiz_cd = 0.0;
        assert!(g.wizard_teleport("camp").is_ok());
        assert!(!g.at_home());
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
