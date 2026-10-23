//! V12.2 Gezgin Tüccar Marco: sabit tezgâh yok; her bölgede kendi kamyonetiyle dolaşır. Her 20 dakikada (sunucu saatiyle,
//! herkeste aynı) bölgenin karakollarından birine gelir, park eder, satış yapar, süre bitince gider. Her gelişinde
//! malları baştan seçilir: listedeki her eşyanın bir "gelme olasılığı" vardır (dükkânda yazar); aynı dilimde aynı bölgede
//! herkes aynı malları görür. Bir oyuncu bir gelişte her maldan stok kadar alabilir.
use crate::areas::{self, AREAS};
use crate::data::{self, ITEMS};
use crate::game::Game;
use crate::rng::Rng;
use crate::world::{self, TILE};
use serde_json::{json, Value};

/// Bir geliş süresi (sn)
pub const SLOT: f64 = 1200.0;
/// Gelirken ve giderken sürüş süresi (sn)
pub const DRIVE: f32 = 12.0;
/// Kamyonetin geldiği yol uzunluğu (dünya pikseli)
const ROAD: f32 = 22.0 * TILE;
/// Araç türü (data::VEHICLES: suv)
pub const TRUCK: usize = 3;

/// Getirebileceği mallar: (eşya, gelme olasılığı %, fiyat, gereken seviye, en çok stok)
pub const POOL: &[(&str, u32, u32, u32, u32)] = &[
    ("bandage", 90, 15, 1, 10),
    ("food", 80, 10, 1, 10),
    ("energy", 60, 20, 1, 6),
    ("cloth", 50, 6, 1, 20),
    ("rope", 50, 8, 1, 10),
    ("medkit", 45, 65, 5, 4),
    ("potion_heal", 40, 85, 3, 3),
    ("fuel", 40, 25, 8, 6),
    ("antidote", 35, 40, 6, 3),
    ("workbench", 30, 120, 1, 1),
    ("potion_speed", 25, 130, 6, 2),
    ("w_knife", 20, 130, 1, 1),
    ("chest_wood", 20, 150, 5, 1),
    ("potion_power", 15, 190, 12, 2),
    // başka bölgelerin malzemeleri (bölgeler arası ticaret)
    ("resin", 30, 12, 1, 8),
    ("charcoal", 20, 30, 10, 5),
    ("dried_hide", 20, 35, 10, 5),
    ("swamp_moss", 20, 35, 15, 5),
    ("electronics", 15, 45, 10, 3),
    ("gear", 15, 40, 10, 3),
    ("toxic", 15, 40, 15, 3),
    ("battery", 12, 45, 10, 2),
    ("scorpion_venom", 10, 80, 20, 2),
    ("bog_iron", 10, 80, 20, 2),
    ("ice_shard", 8, 90, 25, 2),
    ("powder_pouch", 8, 95, 20, 2),
    ("dark_crystal", 5, 120, 30, 1),
    ("circuit_board", 4, 160, 30, 1),
    ("w_katana", 4, 1900, 10, 1),
    ("gold_watch", 3, 300, 20, 1),
    ("titan_shard", 1, 600, 40, 1),
];

/// Bir bölgedeki kervan: duraklar (park yeri, geliş yönü)
pub struct Caravan {
    pub area: usize,
    pub stops: Vec<((f32, f32), (f32, f32))>,
    /// şu anki konum, yön (açı) ve hız
    pub x: f32,
    pub y: f32,
    pub angle: f32,
    pub speed: f32,
    pub parked: bool,
    /// Npc listesindeki tüccarın sırası
    pub npc: usize,
}

/// Bölgelerin durakları: kamp ve şehir pazarı, Cephe karargâhı ve bölgelerin güvenli karakolları
fn stop_points(w: &world::World, area: usize) -> Vec<(f32, f32)> {
    let mut v: Vec<(f32, f32)> = w.merchants.iter().copied().filter(|&(x, y)| areas::area_at(x, y) == area).collect();
    for l in world::LANDMARKS.iter().filter(|l| l.safe) {
        let (x, y) = (l.tx * TILE, (l.ty + 3.0) * TILE);
        if areas::area_at(x, y) == area && !v.iter().any(|&(a, b)| (a - x).hypot(b - y) < 20.0 * TILE) {
            v.push((x, y));
        }
    }
    if v.is_empty() {
        v.push(areas::hub(area));
    }
    v
}

impl Caravan {
    /// Bölgenin kervanını kurar: her durakta kamyonet için boş yer ve önü açık bir geliş yönü aranır.
    pub fn build(w: &world::World, area: usize, npc: usize) -> Caravan {
        let mut scratch = Vec::new();
        let stops = stop_points(w, area)
            .into_iter()
            .map(|(sx, sy)| {
                // park yeri: tüccarın yanında, katı nesnelerin dışında
                let (mut px, mut py) = (sx + 2.6 * TILE, sy + 0.5 * TILE);
                w.resolve(&mut px, &mut py, 40.0, true, &mut scratch);
                // geliş yönü: 8 yönden yolu en açık olan
                let mut best = (0, (px - ROAD, py));
                for k in 0..8 {
                    let a = k as f32 * std::f32::consts::TAU / 8.0;
                    let (dx, dy) = (a.cos(), a.sin());
                    let clear = (1..=22).take_while(|s| {
                        let d = *s as f32 * TILE;
                        let (qx, qy) = (px + dx * d, py + dy * d);
                        areas::area_at(qx, qy) == area && !w.blocked(qx, qy, 30.0, &mut scratch)
                    }).count();
                    if clear > best.0 {
                        best = (clear, (px + dx * ROAD, py + dy * ROAD));
                    }
                }
                ((px, py), best.1)
            })
            .collect();
        Caravan { area, stops, x: 0.0, y: 0.0, angle: 0.0, speed: 0.0, parked: false, npc }
    }
}

impl Game {
    /// Kervan saati (sn): sunucu saati geldiyse o (herkeste aynı), yoksa oyun süresi
    fn caravan_clock(&self) -> f64 {
        if self.base_clock > 0.0 { self.base_clock / 1000.0 } else { self.time as f64 }
    }

    pub fn caravan_slot(&self) -> i64 {
        (self.caravan_clock() / SLOT) as i64
    }

    /// Kervanın bu dilimdeki park yeri
    pub fn caravan_park(&self, c: &Caravan) -> (f32, f32) {
        c.stops[Self::caravan_stop(self.caravan_slot(), c.area, c.stops.len())].0
    }

    /// Bu dilimde kervanın durağı
    fn caravan_stop(slot: i64, area: usize, n: usize) -> usize {
        let mut r = Rng::new((slot as u64).wrapping_mul(0x9E37_79B9) ^ (area as u64 * 7919) ^ 0xCA2A_7A11);
        r.below(n.max(1) as u32) as usize
    }

    /// Kamyoneti ve tüccarı zamana göre yerleştirir (gelir, park eder, gider).
    pub(crate) fn update_caravans(&mut self) {
        let clock = self.caravan_clock();
        let slot = (clock / SLOT) as i64;
        let t = (clock - slot as f64 * SLOT) as f32;
        for c in self.caravans.iter_mut() {
            let (park, from) = c.stops[Self::caravan_stop(slot, c.area, c.stops.len())];
            let (u, coming) = if t < DRIVE {
                (t / DRIVE, true)
            } else if t > SLOT as f32 - DRIVE {
                ((SLOT as f32 - t) / DRIVE, false)
            } else {
                (1.0, true)
            };
            // yumuşak kalkış/duruş
            let e = u * u * (3.0 - 2.0 * u);
            c.x = from.0 + (park.0 - from.0) * e;
            c.y = from.1 + (park.1 - from.1) * e;
            let (dx, dy) = if coming { (park.0 - from.0, park.1 - from.1) } else { (from.0 - park.0, from.1 - park.1) };
            c.angle = dx.atan2(dy);
            c.parked = u >= 1.0;
            c.speed = if c.parked { 0.0 } else { ROAD / DRIVE };
            // tüccar park edince kamyonetin solunda iner; yoldayken görünmez
            if let Some(n) = self.npcs.get_mut(c.npc) {
                if c.parked {
                    n.x = park.0 - 2.6 * TILE;
                    n.y = park.1 - 0.5 * TILE;
                } else {
                    n.x = -10_000.0;
                    n.y = -10_000.0;
                }
            }
        }
    }

    /// Bu dilimde bu bölgenin malları: (havuz sırası, stok)
    pub fn caravan_stock(slot: i64, area: usize) -> Vec<(usize, u32)> {
        let mut r = Rng::new((slot as u64).wrapping_mul(0x2545_F491) ^ (area as u64 * 104_729) ^ 0x5707_C0DE);
        POOL.iter()
            .enumerate()
            .filter_map(|(i, &(_, chance, _, _, max))| {
                let roll = r.below(100);
                let n = 1 + r.below(max.max(1));
                (roll < chance).then_some((i, n))
            })
            .collect()
    }

    /// Oyuncunun yanındaki (park etmiş) kervan
    pub fn near_caravan(&self) -> Option<usize> {
        let (px, py) = (self.player.x, self.player.y);
        self.caravans.iter().position(|c| {
            c.parked && self.npcs.get(c.npc).is_some_and(|n| (n.x - px).hypot(n.y - py) < 150.0 || (c.x - px).hypot(c.y - py) < 180.0)
        })
    }

    fn caravan_bought(&self, slot: i64, area: usize, item: usize) -> u32 {
        self.progress.caravan_bought.iter().filter(|b| b.0 == slot && b.1 as usize == area && b.2 as usize == item).map(|b| b.3).sum()
    }

    /// Dükkân: bu gelişin malları (gelme olasılığı, stok, fiyat), getirmedikleri ve bir sonraki gelişe kalan süre
    pub fn caravan_json(&self) -> Value {
        let slot = self.caravan_slot();
        let area = areas::area_at(self.player.x, self.player.y);
        let stock = Self::caravan_stock(slot, area);
        let left_s = (SLOT - (self.caravan_clock() - slot as f64 * SLOT)) as f32;
        let lv = self.progress.level;
        let mut buy = Vec::new();
        for &(i, n) in &stock {
            let (id, chance, price, req, _) = POOL[i];
            let price = self.buy_price(price);
            let left = n.saturating_sub(self.caravan_bought(slot, area, i));
            buy.push(json!({ "i": i, "item": crate::ui::item_json(id), "price": price, "req": req, "locked": lv < req,
                "afford": self.progress.gold >= price, "chance": chance, "left": left }));
        }
        let missing: Vec<Value> = POOL
            .iter()
            .enumerate()
            .filter(|(i, _)| !stock.iter().any(|s| s.0 == *i))
            .map(|(_, &(id, chance, _, _, _))| json!({ "item": crate::ui::item_json(id), "chance": chance }))
            .collect();
        let where_: Vec<Value> = self
            .caravans
            .iter()
            .map(|c| json!({ "area": AREAS[c.area].name, "here": c.area == area, "parked": c.parked }))
            .collect();
        json!({ "buy": buy, "missing": missing, "next": left_s, "slot": slot, "area": AREAS.get(area).map(|a| a.name), "caravans": where_ })
    }

    /// Kervandan satın alma (havuz sırası `i`, adet `n`)
    pub fn caravan_buy(&mut self, i: usize, n: u32) -> Result<String, String> {
        if self.near_caravan().is_none() {
            return Err("Tüccar Marco'nun kamyonetinin yanına git".into());
        }
        let slot = self.caravan_slot();
        let area = areas::area_at(self.player.x, self.player.y);
        let &(_, n_stock) = Self::caravan_stock(slot, area).iter().find(|s| s.0 == i).ok_or("Tüccar bu sefer bunu getirmedi")?;
        let (id, _, price, req, _) = POOL[i];
        if self.progress.level < req {
            return Err(format!("Seviye {req} gerekli"));
        }
        let item = data::item_index(id).ok_or("Ürün hatalı")?;
        let left = n_stock.saturating_sub(self.caravan_bought(slot, area, i));
        if left == 0 {
            return Err("Bu maldan stok kalmadı; bir sonraki gelişi bekle".into());
        }
        let n = n.clamp(1, left).min(if ITEMS[item].stack == 1 { 1 } else { left });
        let cost = self.buy_price(price) * n;
        if self.progress.gold < cost {
            return Err(format!("{cost} altın gerekli"));
        }
        if !self.progress.inv.can_fit(item, n) {
            return Err("Çantanda yer yok".into());
        }
        self.progress.gold -= cost;
        self.progress.inv.add(item, n);
        self.progress.caravan_bought.retain(|b| b.0 == slot);
        self.progress.caravan_bought.push((slot, area as u8, i as u16, n));
        Ok(format!("{n}× {} alındı (-{cost} altın)", ITEMS[item].name))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::Progress;

    #[test]
    fn caravan_stock_is_deterministic_and_varies() {
        let a = Game::caravan_stock(1000, 0);
        assert_eq!(a, Game::caravan_stock(1000, 0), "aynı dilimde herkes aynı malı görür");
        let differs = (1001..1010).any(|s| Game::caravan_stock(s, 0) != a);
        assert!(differs, "her gelişte mallar değişmeli");
        for (i, _) in &a {
            assert!(data::item_in_game(POOL[*i].0), "{}", POOL[*i].0);
        }
        for p in POOL {
            assert!(data::item_index(p.0).is_some(), "{}", p.0);
        }
    }

    #[test]
    fn caravan_parks_at_a_stop_and_sells() {
        let mut g = Game::new(7, Progress::default(), 0);
        assert!(g.caravans.len() >= 6, "her bölgede kervan");
        // dilimin ortası: park etmiş
        g.base_clock = (SLOT * 5000.0 + SLOT / 2.0) * 1000.0;
        g.update_caravans();
        let c = &g.caravans[0];
        assert!(c.parked);
        let n = &g.npcs[c.npc];
        assert_eq!(n.kind, crate::story::N_MERCHANT);
        assert_eq!(areas::area_at(n.x, n.y), c.area);
        // yanına git ve al
        g.player.x = n.x;
        g.player.y = n.y + 60.0;
        g.progress.gold = 100_000;
        g.progress.relayout_inv();
        let stock = Game::caravan_stock(g.caravan_slot(), areas::area_at(g.player.x, g.player.y));
        let &(i, _) = stock.iter().find(|(i, _)| POOL[*i].3 <= g.progress.level).expect("satılık mal");
        assert!(g.caravan_buy(i, 1).is_ok());
        // dilim başı: yolda, satış yok
        g.base_clock = (SLOT * 5001.0 + 2.0) * 1000.0;
        g.update_caravans();
        assert!(!g.caravans[0].parked);
        assert!(g.caravan_buy(i, 1).is_err());
    }
}
