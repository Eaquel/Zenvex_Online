//! Envanter: yığınlanabilir slotlar; cepler + en fazla 4 bölme (ana çanta + kuşanılan çantanın açtığı ek bölmeler).
use crate::data::{self, ItemKind, ITEMS};
use serde::{Deserialize, Serialize};

pub const BASE_SLOTS: usize = 20;
/// Çantanın sol yanındaki cepler: slots[0..POCKETS]. Oyunda hızlı kullanım tuşları olur;
/// yalnız tüketilebilir eşya alır ve yerden toplanan eşya cebe kendiliğinden girmez (aynı yığına eklenir).
pub const POCKETS: usize = 4;
/// Çanta bölmeleri: 0 = ana çanta (seviyeyle büyür), 1-3 = kuşanılan çantanın açtığı ek bölmeler.
pub const PAGES: usize = 4;

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
    /// bölme boyları (slots = cepler + bölmeler sırayla); eski kayıtta boş → tek bölme sayılır
    #[serde(default)]
    pub pages: Vec<u16>,
}

/// Bir slot dizisine yığınları doldurarak ekler; sığmayan miktarı döndürür (sandıklar da kullanır).
pub fn stack_into(slots: &mut [Option<Slot>], item: usize, mut count: u32) -> u32 {
    let stack = ITEMS[item].stack.max(1) as u32;
    for s in slots.iter_mut().flatten() {
        if count == 0 {
            break;
        }
        if s.item as usize == item && (s.count as u32) < stack {
            let put = (stack - s.count as u32).min(count);
            s.count += put as u16;
            count -= put;
        }
    }
    for s in slots.iter_mut() {
        if count == 0 {
            break;
        }
        if s.is_none() {
            let put = stack.min(count);
            *s = Some(Slot { item: item as u16, count: put as u16 });
            count -= put;
        }
    }
    count
}

/// Slot dizisindeki yığını n kadar azaltır (n = None ise tamamı); alınanı döndürür.
pub fn take_from(slots: &mut [Option<Slot>], idx: usize, n: Option<u32>) -> Option<Slot> {
    let cur = (*slots.get(idx)?)?;
    let n = n.unwrap_or(cur.count as u32).clamp(1, cur.count as u32) as u16;
    slots[idx] = if n < cur.count { Some(Slot { item: cur.item, count: cur.count - n }) } else { None };
    Some(Slot { item: cur.item, count: n })
}

/// Aralıktaki eşyaları türe göre sıralar ve yığınları birleştirir.
pub fn sort_slots(slots: &mut [Option<Slot>]) {
    let mut totals: Vec<(usize, u32)> = Vec::new();
    for s in slots.iter().flatten() {
        match totals.iter_mut().find(|(i, _)| *i == s.item as usize) {
            Some(t) => t.1 += s.count as u32,
            None => totals.push((s.item as usize, s.count as u32)),
        }
    }
    totals.sort_by_key(|(i, _)| (kind_order(ITEMS[*i].kind), std::cmp::Reverse(ITEMS[*i].rarity), *i));
    for s in slots.iter_mut() {
        *s = None;
    }
    for (i, c) in totals {
        let left = stack_into(slots, i, c);
        debug_assert_eq!(left, 0);
    }
}

impl Inventory {
    pub fn new(cap: usize) -> Self {
        Inventory {
            slots: vec![None; cap + POCKETS],
            pk: true,
            pages: vec![cap as u16],
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
        let total: usize = self.pages.iter().map(|&p| p as usize).sum();
        if self.pages.is_empty() || total + POCKETS != self.slots.len() {
            self.pages = vec![self.slots.len().saturating_sub(POCKETS) as u16];
        }
    }

    /// Bölmenin slot aralığı (cepler hariç).
    pub fn page_range(&self, page: usize) -> Option<std::ops::Range<usize>> {
        let len = *self.pages.get(page)? as usize;
        let start = POCKETS + self.pages[..page].iter().map(|&p| p as usize).sum::<usize>();
        Some(start..start + len)
    }

    /// Slot hangi bölmede (cep ise None)
    pub fn page_of(&self, idx: usize) -> Option<usize> {
        (0..self.pages.len()).find(|&p| self.page_range(p).is_some_and(|r| r.contains(&idx)))
    }

    /// Bölme düzenini değiştirir. Hiçbir eşya kaybolmaz: küçülen/kapanan bölmedeki eşyalar boş yere taşınır,
    /// yer yoksa son bölme geçici olarak büyür (boşaldıkça bir sonraki düzende küçülür).
    pub fn layout(&mut self, caps: &[usize]) {
        self.migrate();
        let caps: Vec<usize> = caps.iter().take(PAGES).copied().collect();
        let mut old: Vec<Vec<Option<Slot>>> = Vec::new();
        for p in 0..self.pages.len() {
            let r = self.page_range(p).unwrap();
            old.push(self.slots[r].to_vec());
        }
        let mut pages: Vec<Vec<Option<Slot>>> = Vec::new();
        let mut spill: Vec<Slot> = Vec::new();
        for (i, &cap) in caps.iter().enumerate() {
            let mut v = old.get(i).cloned().unwrap_or_default();
            if v.len() > cap {
                spill.extend(v.drain(cap..).flatten());
            }
            v.resize(cap, None);
            pages.push(v);
        }
        for v in old.iter().skip(caps.len()) {
            spill.extend(v.iter().flatten().copied());
        }
        for s in spill {
            let mut left = s.count as u32;
            for v in pages.iter_mut() {
                left = stack_into(v, s.item as usize, left);
                if left == 0 {
                    break;
                }
            }
            if left > 0 {
                if pages.is_empty() {
                    pages.push(Vec::new());
                }
                pages.last_mut().unwrap().push(Some(Slot { item: s.item, count: left as u16 }));
            }
        }
        let pockets: Vec<Option<Slot>> = self.slots[..POCKETS.min(self.slots.len())].to_vec();
        self.slots = pockets;
        self.slots.resize(POCKETS, None);
        self.pages = pages.iter().map(|v| v.len() as u16).collect();
        for v in pages {
            self.slots.extend(v);
        }
    }

    /// Bölmede eşya var mı
    pub fn page_used(&self, page: usize) -> usize {
        self.page_range(page).map_or(0, |r| self.slots[r].iter().filter(|s| s.is_some()).count())
    }

    /// Yığının bir kısmını aynı bölmedeki (yoksa herhangi bir) boş slota ayırır; yeni slotu döndürür.
    pub fn split(&mut self, from: usize, n: u32) -> Result<usize, &'static str> {
        let s = self.slots.get(from).copied().flatten().ok_or("Boş slot")?;
        if n == 0 || n >= s.count as u32 {
            return Err("Bölmek için daha küçük bir miktar seç");
        }
        let page = self.page_of(from).unwrap_or(0);
        let r = self.page_range(page).unwrap_or(POCKETS..self.slots.len());
        let to = r
            .clone()
            .find(|&i| self.slots[i].is_none())
            .or_else(|| (POCKETS..self.slots.len()).find(|&i| self.slots[i].is_none()))
            .ok_or("Çantada boş yer yok")?;
        self.slots[from] = Some(Slot { item: s.item, count: s.count - n as u16 });
        self.slots[to] = Some(Slot { item: s.item, count: n as u16 });
        Ok(to)
    }

    /// Yığının tamamını başka bölmeye taşır (önce aynı eşyanın yığınlarına).
    pub fn move_to_page(&mut self, from: usize, page: usize) -> Result<(), &'static str> {
        let r = self.page_range(page).ok_or("Bu bölme kapalı")?;
        if r.contains(&from) {
            return Ok(());
        }
        let s = self.slots.get(from).copied().flatten().ok_or("Boş slot")?;
        let left = stack_into(&mut self.slots[r], s.item as usize, s.count as u32);
        if left == s.count as u32 {
            return Err("Bölme dolu");
        }
        self.slots[from] = if left > 0 { Some(Slot { item: s.item, count: left as u16 }) } else { None };
        Ok(())
    }

    /// Slottan n tane alır (None = tamamı).
    pub fn take_n(&mut self, idx: usize, n: Option<u32>) -> Option<Slot> {
        take_from(&mut self.slots, idx, n)
    }

    /// Tek bölmeyi sıralar.
    pub fn sort_page(&mut self, page: usize) {
        if let Some(r) = self.page_range(page) {
            sort_slots(&mut self.slots[r]);
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

    /// Eski API: ana bölmeyi büyütür, ek bölmeleri korur.
    pub fn resize(&mut self, cap: usize) {
        self.migrate();
        let mut caps: Vec<usize> = self.pages.iter().map(|&p| p as usize).collect();
        caps[0] = cap;
        self.layout(&caps);
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
        // önce ceplerdeki yığınlar (yerden alınan iksir cepteki yığına eklenir), sonra çanta
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

    /// Türe göre sırala ve yığınları birleştir (tüm bölmeler, cepler hariç).
    pub fn sort(&mut self) {
        let n = self.slots.len();
        sort_slots(&mut self.slots[POCKETS.min(n)..]);
    }
}

fn kind_order(k: ItemKind) -> u8 {
    match k {
        ItemKind::Weapon => 0,
        ItemKind::Ammo | ItemKind::Tool => 1,
        ItemKind::Helmet | ItemKind::Jacket | ItemKind::Pants | ItemKind::Boots | ItemKind::Bag | ItemKind::Cloak | ItemKind::Ring | ItemKind::Necklace => 2,
        ItemKind::Consumable => 3,
        ItemKind::Chest => 4,
        ItemKind::Material => 5,
        ItemKind::Currency => 6,
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
        let mut old = Inventory { slots: vec![Some(Slot { item: wood as u16, count: 1 })], pk: false, pages: Vec::new() };
        old.migrate();
        assert_eq!(old.slots.len(), 1 + POCKETS);
        assert!(old.slots[POCKETS].is_some());
        assert_eq!(old.pages, vec![1]);
    }

    #[test]
    fn split_and_partial_take() {
        let mut inv = Inventory::new(4);
        let wood = data::item_index("wood").unwrap();
        inv.add(wood, 30);
        let from = POCKETS;
        let to = inv.split(from, 12).unwrap();
        assert_eq!(inv.slots[from].unwrap().count, 18);
        assert_eq!(inv.slots[to].unwrap().count, 12);
        assert!(inv.split(from, 18).is_err()); // tamamı bölünmez
        assert!(inv.split(from, 0).is_err());
        let got = inv.take_n(to, Some(5)).unwrap();
        assert_eq!(got.count, 5);
        assert_eq!(inv.slots[to].unwrap().count, 7);
        assert_eq!(inv.take_n(to, None).unwrap().count, 7);
        assert!(inv.slots[to].is_none());
        assert_eq!(inv.count(wood), 18);
    }

    #[test]
    fn pages_layout_keeps_items() {
        let mut inv = Inventory::new(4);
        let wood = data::item_index("wood").unwrap();
        let stone = data::item_index("stone").unwrap();
        inv.layout(&[4, 6, 6]);
        assert_eq!(inv.slots.len(), POCKETS + 16);
        assert_eq!(inv.page_range(1), Some(POCKETS + 4..POCKETS + 10));
        // ek bölmeye taşı
        inv.add(wood, 10);
        inv.move_to_page(POCKETS, 2).unwrap();
        assert_eq!(inv.page_used(2), 1);
        assert_eq!(inv.page_used(0), 0);
        inv.add(stone, 5);
        // bölmeler kapanınca eşyalar ana çantaya döner, kaybolmaz
        inv.layout(&[4]);
        assert_eq!(inv.pages, vec![4]);
        assert_eq!(inv.count(wood), 10);
        assert_eq!(inv.count(stone), 5);
        // ana çanta dolu iken kapanan bölmenin eşyası son bölmeyi geçici büyütür
        let mut full = Inventory::new(1);
        full.layout(&[1, 2]);
        full.add(wood, 50);
        full.add(stone, 50);
        full.add(data::item_index("cloth").unwrap(), 50);
        full.layout(&[1]);
        assert_eq!(full.count(wood) + full.count(stone), 100);
        assert_eq!(full.pages[0], 3);
        full.slots[POCKETS + 2] = None;
        full.slots[POCKETS + 1] = None;
        full.layout(&[1]);
        assert_eq!(full.pages, vec![1]);
    }

    #[test]
    fn sort_page_only_touches_that_page() {
        let mut inv = Inventory::new(3);
        inv.layout(&[3, 3]);
        let wood = data::item_index("wood").unwrap();
        let r1 = inv.page_range(1).unwrap();
        inv.slots[r1.start + 2] = Some(Slot { item: wood as u16, count: 4 });
        inv.slots[r1.start] = Some(Slot { item: wood as u16, count: 3 });
        inv.slots[POCKETS + 1] = Some(Slot { item: wood as u16, count: 1 });
        inv.sort_page(1);
        assert_eq!(inv.slots[r1.start].unwrap().count, 7);
        assert!(inv.slots[r1.start + 1].is_none());
        assert_eq!(inv.slots[POCKETS + 1].unwrap().count, 1);
    }
}
