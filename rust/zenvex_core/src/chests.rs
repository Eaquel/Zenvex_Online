//! Sandıklar: kişisel üsse kurulan kalıcı depolar (kayıtta konum + içerik).
#[allow(unused_imports)]
use crate::Hyp;
use crate::data::{ItemKind, ITEMS};
use crate::game::Game;
use crate::inventory::{self, Slot, POCKETS};
use crate::progress::Progress;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

/// Sandığa erişim mesafesi (px)
pub const CHEST_NEAR: f32 = 130.0;
/// İki sandık arası en az mesafe (px)
const CHEST_GAP: f32 = 70.0;
pub const MAX_CHESTS: usize = 12;

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Chest {
    /// sandık eşyasının dizini (chest_wood / chest_iron / chest_military)
    pub item: u16,
    pub x: f32,
    pub y: f32,
    pub slots: Vec<Option<Slot>>,
}

impl Chest {
    pub fn cap(&self) -> usize {
        ITEMS.get(self.item as usize).map_or(16, |d| d.armor as usize)
    }
    pub fn used(&self) -> usize {
        self.slots.iter().filter(|s| s.is_some()).count()
    }
}

impl Progress {
    /// Envanterdeki sandık eşyasını (x, y) konumuna kurar. (Üs kontrolü Game::place_chest'te.)
    pub fn place_chest(&mut self, slot: usize, x: f32, y: f32) -> Result<usize, &'static str> {
        let s = self.inv.slots.get(slot).copied().flatten().ok_or("Boş slot")?;
        if ITEMS[s.item as usize].kind != ItemKind::Chest {
            return Err("Bu eşya sandık değil");
        }
        if self.chests.len() >= MAX_CHESTS {
            return Err("Üssüne en fazla 12 sandık kurabilirsin");
        }
        if self.chests.iter().any(|c| (c.x - x).hyp(c.y - y) < CHEST_GAP) {
            return Err("Burada başka bir sandık var");
        }
        self.inv.take_n(slot, Some(1));
        let mut c = Chest { item: s.item, x, y, slots: Vec::new() };
        c.slots = vec![None; c.cap()];
        self.chests.push(c);
        Ok(self.chests.len() - 1)
    }

    /// Boş sandığı söküp çantaya geri alır.
    pub fn pickup_chest(&mut self, i: usize) -> Result<&'static str, &'static str> {
        let c = self.chests.get(i).ok_or("Sandık yok")?;
        if c.used() > 0 {
            return Err("Önce sandığı boşalt");
        }
        if !self.inv.can_fit(c.item as usize, 1) {
            return Err("Çantada yer yok");
        }
        let c = self.chests.remove(i);
        self.inv.add(c.item as usize, 1);
        Ok(ITEMS[c.item as usize].name)
    }

    /// Çanta slotundan sandığa koyar (n = None → tamamı). Konan miktarı döndürür.
    pub fn chest_put(&mut self, i: usize, slot: usize, n: Option<u32>) -> Result<u32, &'static str> {
        let c = self.chests.get_mut(i).ok_or("Sandık yok")?;
        let s = self.inv.slots.get(slot).copied().flatten().ok_or("Boş slot")?;
        let want = n.unwrap_or(s.count as u32).clamp(1, s.count as u32);
        let left = inventory::put_slot(&mut c.slots, Slot { count: want as u16, ..s });
        let put = want - left;
        if put == 0 {
            return Err("Sandık dolu");
        }
        self.inv.take_n(slot, Some(put));
        Ok(put)
    }

    /// Sandık slotundan çantaya alır (n = None → tamamı). Alınan miktarı döndürür.
    pub fn chest_take(&mut self, i: usize, cslot: usize, n: Option<u32>) -> Result<u32, &'static str> {
        let c = self.chests.get(i).ok_or("Sandık yok")?;
        let s = c.slots.get(cslot).copied().flatten().ok_or("Boş slot")?;
        let want = n.unwrap_or(s.count as u32).clamp(1, s.count as u32);
        let left = self.inv.put(Slot { count: want as u16, ..s });
        let took = want - left;
        if took == 0 {
            return Err("Çantada yer yok");
        }
        inventory::take_from(&mut self.chests[i].slots, cslot, Some(took));
        Ok(took)
    }

    /// Hepsini koy (ceplere dokunmaz) / hepsini al. Taşınan yığın sayısını döndürür.
    pub fn chest_all(&mut self, i: usize, put: bool) -> Result<usize, &'static str> {
        if i >= self.chests.len() {
            return Err("Sandık yok");
        }
        let mut moved = 0;
        if put {
            for slot in POCKETS..self.inv.slots.len() {
                if self.inv.slots[slot].is_some() && self.chest_put(i, slot, None).is_ok() {
                    moved += 1;
                }
            }
        } else {
            for cs in 0..self.chests[i].slots.len() {
                if self.chests[i].slots[cs].is_some() && self.chest_take(i, cs, None).is_ok() {
                    moved += 1;
                }
            }
        }
        if moved == 0 {
            return Err(if put { "Konacak eşya yok ya da sandık dolu" } else { "Alınacak eşya yok ya da çanta dolu" });
        }
        Ok(moved)
    }

    /// Sandık içinde sürükle-bırak (yer değiştir / birleştir).
    pub fn chest_move(&mut self, i: usize, from: usize, to: usize) -> Result<(), &'static str> {
        let c = self.chests.get_mut(i).ok_or("Sandık yok")?;
        let n = c.slots.len();
        if from >= n || to >= n {
            return Err("Geçersiz slot");
        }
        let a = c.slots[from].ok_or("Boş slot")?;
        match c.slots[to] {
            Some(b) if b.item == a.item => {
                let stack = ITEMS[a.item as usize].stack.max(1);
                let put = stack.saturating_sub(b.count).min(a.count);
                c.slots[to] = Some(Slot { count: b.count + put, ..b });
                c.slots[from] = if a.count > put { Some(Slot { count: a.count - put, ..a }) } else { None };
            }
            _ => c.slots.swap(from, to),
        }
        Ok(())
    }
}

fn slots_json(slots: &[Option<Slot>]) -> Vec<Value> {
    slots
        .iter()
        .map(|s| match s {
            Some(s) => {
                let d = &ITEMS[s.item as usize];
                let mut v = json!({ "i": s.item, "id": d.id, "name": d.name, "kind": d.kind, "rarity": d.rarity as u8, "desc": d.desc,
                        "armor": d.armor, "bonus": d.bonus, "value": d.value, "count": s.count });
                if crate::inventory::durable(s.item as usize) {
                    v["dur"] = json!((s.dur as f32 / 10.0).round() as i32);
                }
                v
            }
            None => Value::Null,
        })
        .collect()
}

impl Game {
    /// En yakın erişilebilir sandık
    pub fn near_chest(&self) -> Option<usize> {
        let (px, py) = (self.player.x, self.player.y);
        self.progress
            .chests
            .iter()
            .enumerate()
            .map(|(i, c)| (i, (c.x - px).hyp(c.y - py)))
            .filter(|&(_, d)| d < CHEST_NEAR)
            .min_by(|a, b| a.1.total_cmp(&b.1))
            .map(|(i, _)| i)
    }

    /// Sandığı oyuncunun önüne kurar (yalnız kişisel üste).
    pub fn place_chest(&mut self, slot: usize) -> Result<String, String> {
        if !self.at_home() {
            return Err("Sandığı yalnız kişisel üssüne kurabilirsin (Büyücü seni oraya ışınlar)".into());
        }
        let (x, y) = (self.player.x, self.player.y + 40.0);
        let i = self.progress.place_chest(slot, x, y)?;
        Ok(format!("{} kuruldu", ITEMS[self.progress.chests[i].item as usize].name))
    }

    fn chest_reach(&self, i: usize) -> Result<(), String> {
        let c = self.progress.chests.get(i).ok_or("Sandık yok")?;
        if (c.x - self.player.x).hyp(c.y - self.player.y) > CHEST_NEAR * 1.5 {
            return Err("Sandığa çok uzaksın".into());
        }
        Ok(())
    }

    /// Sandık komutları (ui.rs command() buraya yönlendirir); bilinmeyen komutta None.
    pub fn chest_command(&mut self, cmd: &str, v: &Value) -> Option<Result<String, String>> {
        let int = |k: &str| v[k].as_u64().unwrap_or(0) as usize;
        let n = v["n"].as_u64().map(|n| n as u32);
        let ci = int("chest");
        let r = match cmd {
            "place_chest" => self.place_chest(int("slot")),
            "pickup_chest" => self.chest_reach(ci).and_then(|_| {
                self.progress.pickup_chest(ci).map(|n| format!("{n} çantaya alındı")).map_err(String::from)
            }),
            "chest_put" => self.chest_reach(ci).and_then(|_| self.progress.chest_put(ci, int("slot"), n).map(|_| String::new()).map_err(String::from)),
            "chest_take" => self.chest_reach(ci).and_then(|_| self.progress.chest_take(ci, int("cslot"), n).map(|_| String::new()).map_err(String::from)),
            "chest_all" => self.chest_reach(ci).and_then(|_| {
                let put = v["put"].as_bool().unwrap_or(true);
                self.progress
                    .chest_all(ci, put)
                    .map(|k| format!("{k} yığın {}", if put { "sandığa kondu" } else { "çantaya alındı" }))
                    .map_err(String::from)
            }),
            "chest_move" => self.chest_reach(ci).and_then(|_| self.progress.chest_move(ci, int("from"), int("to")).map(|_| String::new()).map_err(String::from)),
            _ => return None,
        };
        if r.is_ok() && matches!(cmd, "chest_put" | "chest_take" | "chest_all") {
            self.play_pose(crate::game::A_OPEN, 0.7);
        }
        Some(r)
    }

    /// "chests": kurulu sandıklar (çizim) + yakındaki; "chest:i": tek sandığın içeriği
    pub fn chests_json(&self, kind: &str) -> Option<Value> {
        if kind == "chests" {
            let list: Vec<Value> = self
                .progress
                .chests
                .iter()
                .enumerate()
                .map(|(i, c)| json!({ "i": i, "id": ITEMS[c.item as usize].id, "x": c.x, "y": c.y, "used": c.used(), "cap": c.cap() }))
                .collect();
            return Some(json!({ "list": list, "near": self.near_chest().map_or(-1, |i| i as i64), "home": self.at_home() }));
        }
        let i: usize = kind.strip_prefix("chest:")?.parse().ok()?;
        let c = self.progress.chests.get(i)?;
        let d = &ITEMS[c.item as usize];
        Some(json!({ "i": i, "id": d.id, "name": d.name, "cap": c.cap(), "used": c.used(), "slots": slots_json(&c.slots) }))
    }
}

#[cfg(test)]
mod tests {
    #[allow(unused_imports)]
    use crate::Hyp;
    use super::*;
    use crate::data;

    fn with_chest(p: &mut Progress, id: &str) -> usize {
        p.inv.add_id(id, 1);
        let slot = p.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == id)).unwrap();
        p.place_chest(slot, 100.0, 100.0).unwrap()
    }

    #[test]
    fn chest_place_transfer_pickup() {
        let mut p = Progress::default();
        let ci = with_chest(&mut p, "chest_wood");
        assert_eq!(p.chests[ci].cap(), 16);
        assert_eq!(p.inv.count_id("chest_wood"), 0);
        // aynı yere ikinci sandık kurulamaz
        p.inv.add_id("chest_iron", 1);
        let s2 = p.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == "chest_iron")).unwrap();
        assert!(p.place_chest(s2, 110.0, 100.0).is_err());
        // kısmi koy, tamamını al
        p.inv.add_id("wood", 30);
        let w = p.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == "wood")).unwrap();
        assert_eq!(p.chest_put(ci, w, Some(12)).unwrap(), 12);
        assert_eq!(p.inv.count_id("wood"), 18);
        assert!(p.pickup_chest(ci).is_err()); // dolu sandık sökülmez
        let cs = p.chests[ci].slots.iter().position(|s| s.is_some()).unwrap();
        assert_eq!(p.chest_take(ci, cs, None).unwrap(), 12);
        assert_eq!(p.inv.count_id("wood"), 30);
        // hepsini koy: cepler kalır
        p.inv.add_id("potion_heal", 2);
        let pot = p.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == "potion_heal")).unwrap();
        p.inv.move_slot(pot, 0).unwrap();
        p.chest_all(ci, true).unwrap();
        assert_eq!(p.inv.count_id("potion_heal"), 2);
        assert_eq!(p.inv.count_id("wood"), 0);
        assert!(p.chests[ci].used() > 0);
        p.chest_all(ci, false).unwrap();
        assert_eq!(p.chests[ci].used(), 0);
        assert_eq!(p.pickup_chest(ci).unwrap(), "Ahşap Sandık");
        assert!(p.chests.is_empty());
        assert_eq!(p.inv.count_id("chest_wood"), 1);
        // kayıttan geri yükleme
        let ci = with_chest(&mut p, "chest_military");
        let s = serde_json::to_string(&p).unwrap();
        let q: Progress = serde_json::from_str(&s).unwrap();
        assert_eq!(q.chests[ci].cap(), 32);
        assert_eq!(q.chests[ci].x, 100.0);
        let _ = data::item_index("wood");
    }

    #[test]
    fn chest_needs_home() {
        let mut g = Game::new(7, Progress::default(), 0);
        g.progress.inv.add_id("chest_wood", 1);
        let slot = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == "chest_wood")).unwrap();
        // kampta (üs dışında) kurulamaz
        if !g.at_home() {
            assert!(g.place_chest(slot).is_err());
            assert_eq!(g.progress.inv.count_id("chest_wood"), 1);
        }
        assert!(g.chests_json("chests").is_some());
        assert!(g.chests_json("chest:0").is_none());
    }
}
