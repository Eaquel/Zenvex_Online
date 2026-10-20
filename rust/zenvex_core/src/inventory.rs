//! Envanter: yığınlanabilir slotlar, çanta ile genişleyen kapasite.
use crate::data::{self, ItemKind, ITEMS};
use serde::{Deserialize, Serialize};

pub const BASE_SLOTS: usize = 20;
/// Çantanın sol yanındaki cepler: slots[0..POCKETS]. Oyunda hızlı kullanım tuşları olur;
/// yalnız tüketilebilir eşya alır ve yerden toplanan eşya cebe kendiliğinden girmez (aynı yığına eklenir).
pub const POCKETS: usize = 4;

#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct Slot {
    pub item: u16,
    pub count: u16,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Inventory {
    pub slots: Vec<Option<Slot>>,
    /// eski kayıtlarda cepler yoktu: false ise başa 4 boş cep eklenir
    #[serde(default)]
    pub pk: bool,
}

impl Inventory {
    pub fn new(cap: usize) -> Self {
        Inventory {
            slots: vec![None; cap + POCKETS],
            pk: true,
        }
    }

    /// Cepsiz eski kaydı yeni düzene çevirir.
    pub fn migrate(&mut self) {
        if !self.pk {
            for _ in 0..POCKETS {
                self.slots.insert(0, None);
            }
            self.pk = true;
        }
    }

    pub fn is_pocket(idx: usize) -> bool {
        idx < POCKETS
    }

    /// Cebe konabilir mi (tüketilebilir ve benzin)
    pub fn pocket_ok(item: usize) -> bool {
        ITEMS[item].kind == ItemKind::Consumable || ITEMS[item].id == "fuel"
    }

    /// Sürükle-bırak: aynı eşyaysa yığını birleştirir, değilse yer değiştirir.
    pub fn move_slot(&mut self, from: usize, to: usize) -> Result<(), &'static str> {
        let n = self.slots.len();
        if from >= n || to >= n {
            return Err("Geçersiz slot");
        }
        if from == to {
            return Ok(());
        }
        let a = self.slots[from].ok_or("Boş slot")?;
        let b = self.slots[to];
        if Self::is_pocket(to) && !Self::pocket_ok(a.item as usize) {
            return Err("Cebe yalnız iksir, yiyecek ve ilk yardım konur");
        }
        if let Some(b) = b {
            if b.item == a.item {
                let stack = ITEMS[a.item as usize].stack.max(1);
                let put = (stack.saturating_sub(b.count)).min(a.count);
                self.slots[to] = Some(Slot { item: b.item, count: b.count + put });
                self.slots[from] = if a.count > put { Some(Slot { item: a.item, count: a.count - put }) } else { None };
                return Ok(());
            }
            if Self::is_pocket(from) && !Self::pocket_ok(b.item as usize) {
                return Err("Cebe yalnız iksir, yiyecek ve ilk yardım konur");
            }
        }
        self.slots.swap(from, to);
        Ok(())
    }

    pub fn resize(&mut self, cap: usize) {
        let cap = cap + POCKETS;
        // fazlalık slotlar silinmez; sadece boşsa küçültülür
        while self.slots.len() < cap {
            self.slots.push(None);
        }
        while self.slots.len() > cap && self.slots.last().is_some_and(|s| s.is_none()) {
            self.slots.pop();
        }
    }

    pub fn count(&self, item: usize) -> u32 {
        self.slots
            .iter()
            .flatten()
            .filter(|s| s.item as usize == item)
            .map(|s| s.count as u32)
            .sum()
    }

    pub fn count_id(&self, id: &str) -> u32 {
        data::item_index(id).map_or(0, |i| self.count(i))
    }

    pub fn free_slots(&self) -> usize {
        self.slots.iter().skip(POCKETS).filter(|s| s.is_none()).count()
    }

    /// Ekler, sığmayan miktarı döndürür.
    pub fn add(&mut self, item: usize, mut count: u32) -> u32 {
        let stack = ITEMS[item].stack.max(1) as u32;
        for s in self.slots.iter_mut().flatten() {
            if count == 0 {
                break;
            }
            if s.item as usize == item && (s.count as u32) < stack {
                let put = (stack - s.count as u32).min(count);
                s.count += put as u16;
                count -= put;
            }
        }
        for s in self.slots.iter_mut().skip(POCKETS) {
            if count == 0 {
                break;
            }
            if s.is_none() {
                let put = stack.min(count);
                *s = Some(Slot {
                    item: item as u16,
                    count: put as u16,
                });
                count -= put;
            }
        }
        count
    }

    pub fn add_id(&mut self, id: &str, count: u32) -> u32 {
        match data::item_index(id) {
            Some(i) => self.add(i, count),
            None => count,
        }
    }

    pub fn can_fit(&self, item: usize, count: u32) -> bool {
        let stack = ITEMS[item].stack.max(1) as u32;
        let mut room = 0u32;
        for (k, s) in self.slots.iter().enumerate() {
            match s {
                None if k >= POCKETS => room += stack,
                None => {}
                Some(s) if s.item as usize == item => room += stack.saturating_sub(s.count as u32),
                _ => {}
            }
        }
        room >= count
    }

    pub fn remove(&mut self, item: usize, count: u32) -> bool {
        if self.count(item) < count {
            return false;
        }
        let mut left = count;
        for s in self.slots.iter_mut().rev() {
            if left == 0 {
                break;
            }
            if let Some(sl) = s {
                if sl.item as usize == item {
                    let take = (sl.count as u32).min(left);
                    sl.count -= take as u16;
                    left -= take;
                    if sl.count == 0 {
                        *s = None;
                    }
                }
            }
        }
        true
    }

    pub fn remove_id(&mut self, id: &str, count: u32) -> bool {
        data::item_index(id).is_some_and(|i| self.remove(i, count))
    }

    pub fn take_slot(&mut self, idx: usize) -> Option<Slot> {
        self.slots.get_mut(idx).and_then(|s| s.take())
    }

    /// Türe göre sırala ve yığınları birleştir.
    pub fn sort(&mut self) {
        let cap = self.slots.len();
        let mut totals: Vec<(usize, u32)> = Vec::new();
        for s in self.slots.iter().skip(POCKETS).flatten() {
            match totals.iter_mut().find(|(i, _)| *i == s.item as usize) {
                Some(t) => t.1 += s.count as u32,
                None => totals.push((s.item as usize, s.count as u32)),
            }
        }
        totals.sort_by_key(|(i, _)| {
            (
                kind_order(ITEMS[*i].kind),
                std::cmp::Reverse(ITEMS[*i].rarity),
                *i,
            )
        });
        for s in self.slots.iter_mut().skip(POCKETS) {
            *s = None;
        }
        let _ = cap;
        for (i, c) in totals {
            let left = self.add(i, c);
            debug_assert_eq!(left, 0);
        }
    }
}

fn kind_order(k: ItemKind) -> u8 {
    match k {
        ItemKind::Weapon => 0,
        ItemKind::Ammo => 1,
        ItemKind::Helmet | ItemKind::Jacket | ItemKind::Boots | ItemKind::Bag | ItemKind::Cloak => {
            2
        }
        ItemKind::Consumable => 3,
        ItemKind::Material => 4,
        ItemKind::Currency => 5,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stacking_and_removal() {
        let mut inv = Inventory::new(3);
        let wood = data::item_index("wood").unwrap();
        assert_eq!(inv.add(wood, 120), 0); // 50+50+20
        assert_eq!(inv.free_slots(), 0);
        assert_eq!(inv.add(wood, 40), 10);
        assert_eq!(inv.count(wood), 150);
        assert!(inv.remove(wood, 70));
        assert_eq!(inv.count(wood), 80);
        assert!(!inv.remove(wood, 500));
    }

    #[test]
    fn sort_merges() {
        let mut inv = Inventory::new(6);
        let wood = data::item_index("wood").unwrap();
        let glock = data::item_index("w_knife").unwrap();
        let p = POCKETS;
        inv.slots[p + 3] = Some(Slot {
            item: wood as u16,
            count: 10,
        });
        inv.slots[p + 5] = Some(Slot {
            item: wood as u16,
            count: 15,
        });
        inv.slots[p + 1] = Some(Slot {
            item: glock as u16,
            count: 1,
        });
        inv.sort();
        assert_eq!(inv.slots[p].unwrap().item as usize, glock);
        assert_eq!(inv.slots[p + 1].unwrap().count, 25);
        assert!(inv.slots[p + 2].is_none());
    }

    #[test]
    fn pockets_move_and_stay() {
        let mut inv = Inventory::new(4);
        let pot = data::item_index("potion_heal").unwrap();
        let wood = data::item_index("wood").unwrap();
        inv.add(pot, 3);
        inv.add(wood, 5);
        // toplanan eşya cebe girmez
        assert!(inv.slots[..POCKETS].iter().all(|s| s.is_none()));
        let from = inv.slots.iter().position(|s| s.is_some_and(|s| s.item as usize == pot)).unwrap();
        inv.move_slot(from, 0).unwrap();
        assert_eq!(inv.slots[0].unwrap().count, 3);
        // odun cebe konamaz
        let w = inv.slots.iter().position(|s| s.is_some_and(|s| s.item as usize == wood)).unwrap();
        assert!(inv.move_slot(w, 1).is_err());
        // aynı iksir yerden alınınca cepteki yığına eklenir; sıralama cebe dokunmaz
        inv.add(pot, 2);
        assert_eq!(inv.slots[0].unwrap().count, 5);
        inv.sort();
        assert_eq!(inv.slots[0].unwrap().count, 5);
        // eski kayıt göçü
        let mut old = Inventory { slots: vec![Some(Slot { item: wood as u16, count: 1 })], pk: false };
        old.migrate();
        assert_eq!(old.slots.len(), 1 + POCKETS);
        assert!(old.slots[POCKETS].is_some());
    }
}
