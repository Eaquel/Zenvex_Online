//! Envanter: yığınlanabilir slotlar, çanta ile genişleyen kapasite.
use crate::data::{self, ItemKind, ITEMS};
use serde::{Deserialize, Serialize};

pub const BASE_SLOTS: usize = 20;

#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct Slot {
    pub item: u16,
    pub count: u16,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Inventory {
    pub slots: Vec<Option<Slot>>,
}

impl Inventory {
    pub fn new(cap: usize) -> Self {
        Inventory {
            slots: vec![None; cap],
        }
    }

    pub fn resize(&mut self, cap: usize) {
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
        self.slots.iter().filter(|s| s.is_none()).count()
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
        for s in self.slots.iter_mut() {
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
        for s in &self.slots {
            match s {
                None => room += stack,
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
        for s in self.slots.iter().flatten() {
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
        self.slots = vec![None; cap];
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
        inv.slots[3] = Some(Slot {
            item: wood as u16,
            count: 10,
        });
        inv.slots[5] = Some(Slot {
            item: wood as u16,
            count: 15,
        });
        inv.slots[1] = Some(Slot {
            item: glock as u16,
            count: 1,
        });
        inv.sort();
        assert_eq!(inv.slots[0].unwrap().item as usize, glock);
        assert_eq!(inv.slots[1].unwrap().count, 25);
        assert!(inv.slots[2].is_none());
    }
}
