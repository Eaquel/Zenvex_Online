//! Oyuncu profili: hesap seviyesi, karakterler, yetenekler, envanter, ekipman, altın, görevler, pass, kozmetik.
use crate::data::{self, ItemKind, ITEMS};
use crate::inventory::{Inventory, BASE_SLOTS};
use serde::{Deserialize, Serialize};

pub const MAX_LEVEL: u32 = 50;
pub const MAX_CHAR_LEVEL: u32 = 10;
pub const CITY_LEVEL: u32 = 10;

pub fn xp_to_next(level: u32) -> u32 {
    (50.0 * (level as f32).powf(1.6)).round() as u32
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Branch {
    Power,
    Endurance,
    Agility,
    Survival,
}

pub struct SkillDef {
    pub id: usize,
    pub branch: Branch,
    pub name: &'static str,
    pub desc: &'static str,
    pub max: u8,
    pub req_level: u32,
    pub icon: &'static str,
}

pub const SKILLS: [SkillDef; 12] = [
    SkillDef {
        id: 0,
        branch: Branch::Power,
        name: "Demir Yumruk",
        desc: "Tüm hasar +%5",
        max: 10,
        req_level: 1,
        icon: "fist",
    },
    SkillDef {
        id: 1,
        branch: Branch::Power,
        name: "Keskin Göz",
        desc: "Kritik vuruş şansı +%2",
        max: 5,
        req_level: 5,
        icon: "star",
    },
    SkillDef {
        id: 2,
        branch: Branch::Power,
        name: "Sarsıcı Darbe",
        desc: "Vuruşların %20 şansla 1 sn sersemletir",
        max: 1,
        req_level: 15,
        icon: "skill",
    },
    SkillDef {
        id: 3,
        branch: Branch::Endurance,
        name: "Kalın Deri",
        desc: "Maksimum can +%4",
        max: 10,
        req_level: 1,
        icon: "heart",
    },
    SkillDef {
        id: 4,
        branch: Branch::Endurance,
        name: "Yenilenme",
        desc: "Can yenilenmesi +%20",
        max: 5,
        req_level: 5,
        icon: "plus",
    },
    SkillDef {
        id: 5,
        branch: Branch::Endurance,
        name: "Son Nefes",
        desc: "Ölümcül darbede 1 canla kal (90 sn bekleme)",
        max: 1,
        req_level: 15,
        icon: "shield",
    },
    SkillDef {
        id: 6,
        branch: Branch::Agility,
        name: "Hafif Adım",
        desc: "Hareket hızı +%2",
        max: 10,
        req_level: 1,
        icon: "bolt",
    },
    SkillDef {
        id: 7,
        branch: Branch::Agility,
        name: "Nefes Kontrolü",
        desc: "Dayanıklılık harcaması -%8",
        max: 5,
        req_level: 5,
        icon: "sound",
    },
    SkillDef {
        id: 8,
        branch: Branch::Agility,
        name: "Rüzgâr Gibi",
        desc: "Koşu hızı +%15",
        max: 1,
        req_level: 15,
        icon: "play",
    },
    SkillDef {
        id: 9,
        branch: Branch::Survival,
        name: "Toplayıcı",
        desc: "Ağaç/kaya/sandıktan +%10 fazla malzeme",
        max: 10,
        req_level: 1,
        icon: "wood",
    },
    SkillDef {
        id: 10,
        branch: Branch::Survival,
        name: "Şanslı",
        desc: "Nadir eşya düşme şansı +%6",
        max: 5,
        req_level: 5,
        icon: "gem",
    },
    SkillDef {
        id: 11,
        branch: Branch::Survival,
        name: "Pazarlıkçı",
        desc: "Tüccar fiyatları -%15, satış +%15",
        max: 1,
        req_level: 10,
        icon: "coin",
    },
];

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Equipment {
    pub weapon: String,
    pub helmet: String,
    pub jacket: String,
    pub boots: String,
    pub bag: String,
    /// V9'da slotu kaldırıldı; eski kayıtlarda kuşanılı pelerin çantaya döner
    pub cloak: String,
    /// V8
    pub ring: String,
    pub necklace: String,
    /// V9
    pub pants: String,
}

impl Equipment {
    /// Tüm ekipman slotları (arayüz sırası: sol sütun, sağ sütun)
    pub const KEYS: [&'static str; 8] = ["helmet", "jacket", "pants", "boots", "weapon", "bag", "ring", "necklace"];

    pub fn get(&self, key: &str) -> Option<&String> {
        Some(match key {
            "weapon" => &self.weapon,
            "helmet" => &self.helmet,
            "jacket" => &self.jacket,
            "boots" => &self.boots,
            "bag" => &self.bag,
            "cloak" => &self.cloak,
            "pants" => &self.pants,
            "ring" => &self.ring,
            "necklace" => &self.necklace,
            _ => return None,
        })
    }

    pub fn get_mut(&mut self, key: &str) -> Option<&mut String> {
        Some(match key {
            "weapon" => &mut self.weapon,
            "helmet" => &mut self.helmet,
            "jacket" => &mut self.jacket,
            "boots" => &mut self.boots,
            "bag" => &mut self.bag,
            "cloak" => &mut self.cloak,
            "pants" => &mut self.pants,
            "ring" => &mut self.ring,
            "necklace" => &mut self.necklace,
            _ => return None,
        })
    }

    pub fn key_for(kind: ItemKind) -> Option<&'static str> {
        Some(match kind {
            ItemKind::Weapon => "weapon",
            ItemKind::Helmet => "helmet",
            ItemKind::Jacket => "jacket",
            ItemKind::Boots => "boots",
            ItemKind::Bag => "bag",
            ItemKind::Pants => "pants",
            ItemKind::Ring => "ring",
            ItemKind::Necklace => "necklace",
            _ => return None,
        })
    }

    /// Kuşanılan eşyanın tanımı
    pub fn def(&self, key: &str) -> Option<&'static data::ItemDef> {
        self.get(key).filter(|s| !s.is_empty()).and_then(|id| data::item_index(id)).map(|i| &ITEMS[i])
    }
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct QuestState {
    pub main: usize,
    pub main_progress: u32,
    pub day: u32,
    pub daily: Vec<(String, u32, bool)>,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct PassState {
    pub xp: u32,
    pub normal: bool,
    pub elite: bool,
    pub claimed_free: Vec<u32>,
    pub claimed_premium: Vec<u32>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default)]
pub struct Progress {
    pub name: String,
    pub level: u32,
    pub xp: u32,
    pub points: u32,
    pub skills: [u8; 12],
    pub gold: u32,
    pub gems: u32,
    pub inv: Inventory,
    pub eq: Equipment,
    /// silah kimliği → şarjördeki mermi
    pub mags: Vec<(String, u16)>,
    pub character: usize,
    pub unlocked: [bool; 4],
    pub char_level: [u32; 4],
    pub char_xp: [u32; 4],
    pub quests: QuestState,
    pub pass: PassState,
    pub cosmetics: Vec<String>,
    pub frame: String,
    pub tag: String,
    /// karakter başına giyili kostüm kimliği (boş = varsayılan)
    pub costume: [String; 4],
    /// silah kimliği → kaplama kimliği
    pub skins: Vec<(String, String)>,
    pub drone_targets: [bool; 3],
    // istatistikler
    pub kills: u32,
    pub kills_by: [u32; 8],
    pub trees: u32,
    pub crates: u32,
    pub crafted: u32,
    pub deaths: u32,
    pub boss_kills: u32,
    pub gold_earned: u32,
    pub play_time: f64,
    pub city_entered: bool,
    /// V4 skill ağacı: açılmış düğümler
    pub tree: Vec<u16>,
    pub dog_owned: bool,
    /// köpek gelişimi: hasar, can, can yenilenmesi (0–10)
    pub dog_lv: [u8; 3],
    /// hile/ödül ile verilen ek skill puanı
    pub extra_points: u32,
    /// keşfedilen harita hücreleri (FOG_N satır × FOG_N bit; haritada bulut kalkar)
    pub fog: Vec<u64>,
    /// rehber ve görevci zincirleri
    pub story: crate::story::StoryState,
    /// V8: kişisel üsse kurulan sandıklar
    pub chests: Vec<crate::chests::Chest>,
    /// V8: ölünce kişisel üste doğ (büyücüden seçilir)
    pub home_spawn: bool,
    /// V9: etkinlik puanı sayaçları (istemci farkları sunucuya yollar)
    pub ev: EventStats,
}

/// Etkinlik sayaçları: yalnız artar; istemci son gönderilenden farkı sunucuya yazar.
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct EventStats {
    pub soldier_kills: u32,
    pub base_caps: u32,
    pub dog_kills: u32,
    /// tehlikeli (güvenli olmayan) bölgede yaşayarak geçen saniye
    pub danger_secs: f64,
    /// PvP'de öldürülen oyuncu
    pub pvp_kills: u32,
}

/// Keşif ızgarası: 120×120 hücre (her hücre 6 kare = 384 px); satır başına 2 kelime (FOG_W).
/// V8 öncesi kayıtlar 60 satır × 1 kelimeydi: aynı hücre boyunda olduğundan eski bitler sol üst çeyreğe aynen taşınır.
pub const FOG_N: usize = 120;
pub const FOG_W: usize = 2;

impl Progress {
    pub fn explored(&self, cx: usize, cy: usize) -> bool {
        if cx >= FOG_N || cy >= FOG_N {
            return false;
        }
        if self.fog.len() == 60 {
            return cx < 64 && self.fog.get(cy).is_some_and(|r| r >> cx & 1 == 1);
        }
        self.fog.get(cy * FOG_W + cx / 64).is_some_and(|r| r >> (cx % 64) & 1 == 1)
    }

    /// Eski (60×1) keşif kaydını yeni düzene çevirir.
    pub fn migrate_fog(&mut self) {
        if self.fog.len() == FOG_N * FOG_W {
            return;
        }
        let old = std::mem::take(&mut self.fog);
        self.fog = vec![0; FOG_N * FOG_W];
        if old.len() <= 64 {
            for (y, r) in old.iter().enumerate() {
                self.fog[y * FOG_W] = *r;
            }
        }
    }

    /// Satır satır onaltılık dizgi (Kotlin: her satır FOG_W kelime, yüksek kelime önce)
    pub fn fog_rows(&self) -> Vec<String> {
        (0..FOG_N)
            .map(|y| {
                let w = |k: usize| if self.fog.len() == FOG_N * FOG_W { self.fog[y * FOG_W + k] } else if k == 0 { self.fog.get(y).copied().unwrap_or(0) } else { 0 };
                format!("{:016x}{:016x}", w(1), w(0))
            })
            .collect()
    }

    /// (x, y) çevresini keşfedilmiş say; yeni hücre açıldıysa true
    pub fn reveal(&mut self, x: f32, y: f32, r_cells: i32) -> bool {
        self.migrate_fog();
        let cell = crate::world::MAP_PX / FOG_N as f32;
        let (cx, cy) = ((x / cell) as i32, (y / cell) as i32);
        let mut new = false;
        for dy in -r_cells..=r_cells {
            for dx in -r_cells..=r_cells {
                if dx * dx + dy * dy > r_cells * r_cells + 1 {
                    continue;
                }
                let (qx, qy) = (cx + dx, cy + dy);
                if qx < 0 || qy < 0 || qx >= FOG_N as i32 || qy >= FOG_N as i32 {
                    continue;
                }
                let word = &mut self.fog[qy as usize * FOG_W + qx as usize / 64];
                let bit = 1u64 << (qx as usize % 64);
                if *word & bit == 0 {
                    *word |= bit;
                    new = true;
                }
            }
        }
        new
    }
}

impl Default for Progress {
    fn default() -> Self {
        let mut p = Progress {
            name: "Hayatta Kalan".into(),
            level: 1,
            xp: 0,
            points: 0,
            skills: [0; 12],
            gold: 50,
            gems: 0,
            inv: Inventory::new(BASE_SLOTS),
            eq: Equipment::default(),
            mags: Vec::new(),
            character: 0,
            unlocked: [true, false, false, false],
            char_level: [1; 4],
            char_xp: [0; 4],
            quests: QuestState::default(),
            pass: PassState::default(),
            cosmetics: Vec::new(),
            frame: String::new(),
            tag: String::new(),
            costume: Default::default(),
            skins: Vec::new(),
            drone_targets: [true, true, true],
            kills: 0,
            kills_by: [0; 8],
            trees: 0,
            crates: 0,
            crafted: 0,
            deaths: 0,
            boss_kills: 0,
            gold_earned: 0,
            play_time: 0.0,
            city_entered: false,
            tree: Vec::new(),
            dog_owned: false,
            dog_lv: [0; 3],
            extra_points: 0,
            fog: Vec::new(),
            story: Default::default(),
            chests: Vec::new(),
            home_spawn: false,
            ev: EventStats::default(),
        };
        p.inv.add_id("bandage", 3);
        p.inv.add_id("food", 2);
        p
    }
}

impl Progress {
    pub fn skill(&self, id: usize) -> f32 {
        self.skills[id] as f32
    }

    pub fn add_xp(&mut self, amount: u32) -> u32 {
        // karakter XP'si (toplam XP'nin yarısı)
        let c = self.character;
        if self.char_level[c] < MAX_CHAR_LEVEL {
            self.char_xp[c] += amount / 2 + 1;
            while self.char_level[c] < MAX_CHAR_LEVEL
                && self.char_xp[c] >= data::char_xp_to_next(self.char_level[c])
            {
                self.char_xp[c] -= data::char_xp_to_next(self.char_level[c]);
                self.char_level[c] += 1;
            }
        }
        if self.level >= MAX_LEVEL {
            return 0;
        }
        let mut ups = 0;
        self.xp += amount;
        while self.level < MAX_LEVEL && self.xp >= xp_to_next(self.level) {
            self.xp -= xp_to_next(self.level);
            self.level += 1;
            self.points += 1;
            if self.level.is_multiple_of(5) {
                self.points += 1;
            }
            ups += 1;
        }
        if self.level >= MAX_LEVEL {
            self.xp = 0;
        }
        ups
    }

    pub fn add_gold(&mut self, g: u32) {
        self.gold = self.gold.saturating_add(g);
        self.gold_earned = self.gold_earned.saturating_add(g);
    }

    pub fn can_upgrade(&self, id: usize) -> Result<(), &'static str> {
        let def = SKILLS.get(id).ok_or("Geçersiz yetenek")?;
        if self.skills[id] >= def.max {
            return Err("Maksimum seviyede");
        }
        if self.level < def.req_level {
            return Err("Seviyen yetersiz");
        }
        if !id.is_multiple_of(3) && self.skills[id - 1] == 0 {
            return Err("Önce önceki yeteneği aç");
        }
        if self.points == 0 {
            return Err("Yetenek puanın yok");
        }
        Ok(())
    }

    pub fn upgrade(&mut self, id: usize) -> Result<(), &'static str> {
        self.can_upgrade(id)?;
        self.skills[id] += 1;
        self.points -= 1;
        Ok(())
    }

    pub fn reset_skills(&mut self) -> Result<(), &'static str> {
        let cost = 200 * self.level;
        if self.gold < cost {
            return Err("Yetersiz altın");
        }
        self.gold -= cost;
        let spent: u32 = self.skills.iter().map(|&s| s as u32).sum();
        self.points += spent;
        self.skills = [0; 12];
        Ok(())
    }

    // ---- ekipman
    /// V8: kask + ceket + bot zırhı (hasar × 100 / (100 + zırh)).
    pub fn armor(&self) -> f32 {
        ["helmet", "jacket", "pants", "boots"].iter().filter_map(|k| self.eq.def(k)).map(|d| d.armor).sum()
    }

    /// Ana çanta bölmesi: 20 slot, her 5 seviyede +2.
    pub fn inventory_cap(&self) -> usize {
        BASE_SLOTS + 2 * (self.level / 5) as usize
    }

    /// Bölme boyları: ana çanta + kuşanılan çantanın ek bölmeleri (en fazla 3).
    pub fn inv_pages(&self) -> Vec<usize> {
        Self::pages_for(self.inventory_cap(), self.eq.def("bag"))
    }

    fn pages_for(main: usize, bag: Option<&data::ItemDef>) -> Vec<usize> {
        let mut v = vec![main];
        if let Some(b) = bag {
            for _ in 0..(b.bonus as usize).min(crate::inventory::PAGES - 1) {
                v.push(b.armor as usize);
            }
        }
        v
    }

    /// Çanta düzenini seviye ve kuşanılan çantaya göre günceller (eşya kaybolmaz).
    pub fn relayout_inv(&mut self) {
        let caps = self.inv_pages();
        let cur: Vec<usize> = self.inv.pages.iter().map(|&p| p as usize).collect();
        if cur != caps {
            self.inv.layout(&caps);
        }
    }

    pub fn bonus(&self) -> crate::rpg::Bonus {
        crate::rpg::bonus(self)
    }

    pub fn max_hp(&self) -> f32 {
        100.0 + 5.0 * (self.level - 1) as f32 + self.bonus().hp_flat
    }
    pub fn max_stamina(&self) -> f32 {
        100.0 + 2.0 * (self.level - 1) as f32
    }
    pub fn damage_mult(&self) -> f32 {
        1.0 + self.bonus().dmg_pct
    }
    /// Yumruk: temel 20 (+seviye), yakın dövüş düz bonusu, yüzde bonusları (Savaşçı Sv2/Sv7 + ağaç)
    pub fn fist_damage(&self) -> f32 {
        let b = self.bonus();
        (15.0 + 0.5 * (self.level - 1) as f32 + b.melee_flat) * (1.0 + b.fist_pct)
    }
    pub fn crit_chance(&self) -> f32 {
        0.05 + self.bonus().crit
    }
    pub fn stun_chance(&self) -> f32 {
        self.bonus().stun
    }
    pub fn regen_mult(&self) -> f32 {
        1.0 + self.bonus().regen
    }
    pub fn has_last_breath(&self) -> bool {
        self.bonus().last_breath
    }
    pub fn move_mult(&self) -> f32 {
        1.0 + self.bonus().speed
    }
    pub fn stamina_cost_mult(&self) -> f32 {
        (1.0 - self.bonus().stamina).max(0.3)
    }
    /// Takla bekleme süresi (sn)
    pub fn roll_cd(&self) -> f32 {
        5.0 * (1.0 - self.bonus().roll_cd)
    }
    pub fn run_mult(&self) -> f32 {
        if self.skills[8] > 0 {
            1.15
        } else {
            1.0
        }
    }
    pub fn gather_mult(&self) -> f32 {
        1.0 + 0.1 * self.skill(9)
    }
    pub fn luck(&self) -> f32 {
        0.06 * self.skill(10)
    }
    pub fn trade_bonus(&self) -> f32 {
        if self.skills[11] > 0 {
            0.15
        } else {
            0.0
        }
    }
    pub fn damage_resist(&self) -> f32 {
        self.bonus().dmg_taken
    }

    /// Çanta değişince kapanacak ek bölmelerde eşya varsa izin verme.
    fn bag_change_ok(&self, new_bag: Option<&data::ItemDef>) -> Result<(), &'static str> {
        let keep = Self::pages_for(self.inventory_cap(), new_bag).len();
        if (keep..self.inv.pages.len()).any(|p| self.inv.page_used(p) > 0) {
            return Err("Önce kapanacak çanta bölmelerini boşalt");
        }
        Ok(())
    }

    /// Envanterdeki slotu giy. Eskisini envantere geri koyar.
    pub fn equip_slot(&mut self, slot: usize) -> Result<&'static str, &'static str> {
        let s = self
            .inv
            .slots
            .get(slot)
            .copied()
            .flatten()
            .ok_or("Boş slot")?;
        let def = &ITEMS[s.item as usize];
        let key = Equipment::key_for(def.kind).ok_or("Bu eşya giyilemez")?;
        if key == "bag" {
            self.bag_change_ok(Some(def))?;
        }
        let target = self.eq.get_mut(key).ok_or("Bu eşya giyilemez")?;
        let old = std::mem::replace(target, def.id.to_string());
        // yığından bir tane al (çanta/ekipman yığınlanmaz ama güvenli olsun)
        self.inv.take_n(slot, Some(1));
        self.relayout_inv();
        if !old.is_empty() && self.inv.add_id(&old, 1) > 0 {
            // yer kalmadıysa eski eşya son bölmeye eklenir (kaybolmaz)
            let caps: Vec<usize> = self.inv.pages.iter().map(|&p| p as usize).collect();
            let mut caps2 = caps.clone();
            *caps2.last_mut().unwrap() += 1;
            self.inv.layout(&caps2);
            self.inv.add_id(&old, 1);
        }
        Ok(def.name)
    }

    pub fn unequip(&mut self, which: &str) -> Result<(), &'static str> {
        let id = self.eq.get(which).ok_or("Geçersiz slot")?.clone();
        if id.is_empty() {
            return Err("Zaten boş");
        }
        if which == "bag" {
            self.bag_change_ok(None)?;
        }
        // çıkarınca sığacak mı (çanta çıkarılıyorsa yalnız ana bölme kalır)
        let room = if which == "bag" {
            self.inv.page_range(0).map_or(0, |r| self.inv.slots[r].iter().filter(|s| s.is_none()).count())
        } else {
            self.inv.free_slots()
        };
        if room == 0 {
            return Err("Envanterde yer yok");
        }
        *self.eq.get_mut(which).unwrap() = String::new();
        self.relayout_inv();
        self.inv.add_id(&id, 1);
        Ok(())
    }

    pub fn mag(&self, weapon: &str) -> u16 {
        self.mags
            .iter()
            .find(|(w, _)| w == weapon)
            .map_or(0, |(_, m)| *m)
    }

    pub fn set_mag(&mut self, weapon: &str, m: u16) {
        match self.mags.iter_mut().find(|(w, _)| w == weapon) {
            Some(e) => e.1 = m,
            None => self.mags.push((weapon.to_string(), m)),
        }
    }

    // ---- görevler
    pub fn ensure_dailies(&mut self, day: u32) {
        if self.quests.day == day && !self.quests.daily.is_empty() {
            return;
        }
        self.quests.day = day;
        let n = data::DAILY_POOL.len();
        let picks = [
            (day as usize * 7) % n,
            (day as usize * 7 + 3) % n,
            (day as usize * 7 + 5) % n,
        ];
        self.quests.daily = picks
            .iter()
            .map(|&i| (data::DAILY_POOL[i].id.to_string(), 0, false))
            .collect();
    }

    /// Görev ilerlemesi; tamamlanan görevlerin başlıklarını döndürür.
    pub fn quest_event(&mut self, kind: data::QuestKind, amount: u32) -> Vec<String> {
        let mut done = Vec::new();
        let mut bonus_xp = 0;
        // ana görevler artık görevci NPC'den konuşarak alınır (story.rs)
        for (id, prog, claimed) in self.quests.daily.iter_mut() {
            if *claimed {
                continue;
            }
            if let Some(q) = data::DAILY_POOL.iter().find(|q| q.id == id) {
                if q.kind == kind {
                    *prog += amount;
                }
                if *prog >= q.goal {
                    *claimed = true;
                    self.gold = self.gold.saturating_add(q.gold);
                    self.pass.xp += q.pass_xp;
                    done.push(q.title.to_string());
                    bonus_xp += q.xp;
                }
            }
        }
        if bonus_xp > 0 {
            self.add_xp(bonus_xp);
        }
        done
    }

    pub fn pass_tier(&self) -> u32 {
        (self.pass.xp / data::PASS_XP_PER_TIER).min(data::PASS_TIERS)
    }

    pub fn grant(&mut self, reward: &str) -> String {
        let (k, v) = reward.split_once(':').unwrap_or((reward, "1"));
        let n: u32 = v.parse().unwrap_or(1);
        match k {
            "gold" => {
                self.add_gold(n);
                format!("+{n} Altın")
            }
            "gem" => {
                self.gems += n;
                format!("+{n} Elmas")
            }
            "cos" => {
                if !self.cosmetics.iter().any(|c| c == v) {
                    self.cosmetics.push(v.to_string());
                }
                let name = data::COSMETICS
                    .iter()
                    .find(|c| c.id == v)
                    .map_or(v, |c| c.name);
                format!("Kozmetik: {name}")
            }
            id => {
                let left = self.inv.add_id(id, n);
                let name = data::item(id).name;
                if left > 0 {
                    format!("{name} (çanta dolu, {left} adet kayboldu)")
                } else {
                    format!("+{n} {name}")
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn xp_curve_matches_design() {
        assert_eq!(xp_to_next(1), 50);
        assert_eq!(xp_to_next(9), 1682);
    }

    #[test]
    fn level_up_gives_points_and_char_xp() {
        let mut p = Progress::default();
        assert_eq!(p.add_xp(50), 1);
        assert_eq!(p.level, 2);
        assert_eq!(p.points, 1);
        assert!(p.char_xp[0] > 0);
    }

    #[test]
    fn capacity_grows_with_level_and_weapon_equips() {
        let mut p = Progress::default();
        assert_eq!(p.inventory_cap(), BASE_SLOTS);
        p.level = 10;
        assert_eq!(p.inventory_cap(), BASE_SLOTS + 4);
        p.inv.add_id("w_knife", 1);
        let slot = p
            .inv
            .slots
            .iter()
            .position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == "w_knife"))
            .unwrap();
        p.equip_slot(slot).unwrap();
        assert_eq!(p.eq.weapon, "w_knife");
        assert_eq!(p.armor(), 0.0);
    }

    fn equip_id(p: &mut Progress, id: &str) -> Result<&'static str, &'static str> {
        p.inv.add_id(id, 1);
        let slot = p.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == id)).unwrap();
        p.equip_slot(slot)
    }

    #[test]
    fn ring_necklace_armor_bonuses() {
        let mut p = Progress::default();
        let (hp0, dmg0, crit0) = (p.max_hp(), p.damage_mult(), p.crit_chance());
        equip_id(&mut p, "ring_hunter").unwrap();
        equip_id(&mut p, "neck_bone").unwrap();
        equip_id(&mut p, "helm_basic").unwrap();
        equip_id(&mut p, "jacket_basic").unwrap();
        assert_eq!(p.eq.ring, "ring_hunter");
        assert_eq!(p.eq.necklace, "neck_bone");
        assert!((p.max_hp() - hp0 - 15.0).abs() < 0.01);
        assert!(p.damage_mult() > dmg0 && p.crit_chance() > crit0);
        assert!((p.armor() - 9.0).abs() < 0.01);
        // yüzük değişince eskisi çantaya döner
        equip_id(&mut p, "ring_copper").unwrap();
        assert_eq!(p.inv.count_id("ring_hunter"), 1);
        p.unequip("necklace").unwrap();
        assert_eq!(p.inv.count_id("neck_bone"), 1);
        assert!(p.unequip("necklace").is_err());
    }

    #[test]
    fn bag_opens_pages_and_guards_unequip() {
        let mut p = Progress::default();
        assert_eq!(p.inv.pages.len(), 1);
        equip_id(&mut p, "bag_police").unwrap();
        assert_eq!(p.inv.pages, vec![BASE_SLOTS as u16, 12, 12]);
        // ek bölmeye eşya koy: çanta çıkarılamaz, küçük çantayla değiştirilemez
        p.inv.add_id("wood", 1);
        let w = p.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == "wood")).unwrap();
        p.inv.move_to_page(w, 2).unwrap();
        assert!(p.unequip("bag").is_err());
        assert!(equip_id(&mut p, "bag_basic").is_err());
        // boşaltınca olur; askerî çanta 3 bölme açar
        let r = p.inv.page_range(2).unwrap();
        for i in r {
            p.inv.slots[i] = None;
        }
        let b = p.inv.slots.iter().position(|s| s.is_some_and(|s| ITEMS[s.item as usize].id == "bag_basic")).unwrap();
        p.inv.slots[b] = None;
        equip_id(&mut p, "bag_military").unwrap();
        assert_eq!(p.inv.pages, vec![BASE_SLOTS as u16, 16, 16, 16]);
        assert_eq!(p.inv.count_id("bag_police"), 1);
        p.unequip("bag").unwrap();
        assert_eq!(p.inv.pages.len(), 1);
        assert_eq!(p.inv.count_id("bag_military"), 1);
    }

    #[test]
    fn old_save_without_v8_fields_loads() {
        let mut p = Progress::default();
        p.inv.add_id("wood", 7);
        let mut v = serde_json::to_value(&p).unwrap();
        // V7 kaydı: bölme, yüzük, kolye ve sandık alanları yok
        v["inv"].as_object_mut().unwrap().remove("pages");
        v["eq"].as_object_mut().unwrap().remove("ring");
        v["eq"].as_object_mut().unwrap().remove("necklace");
        v.as_object_mut().unwrap().remove("chests");
        let mut q: Progress = serde_json::from_value(v).unwrap();
        assert!(q.chests.is_empty() && q.eq.ring.is_empty());
        q.inv.migrate();
        q.relayout_inv();
        assert_eq!(q.inv.pages, vec![BASE_SLOTS as u16]);
        assert_eq!(q.inv.count_id("wood"), 7);
    }

    #[test]
    fn quests_progress() {
        let mut p = Progress::default();
        p.ensure_dailies(3);
        assert_eq!(p.quests.daily.len(), 3);
        let g0 = p.gold;
        for _ in 0..5 {
            p.quest_event(data::QuestKind::Kill, 1);
        }
        let _ = g0;
    }

    #[test]
    fn grant_rewards() {
        let mut p = Progress::default();
        p.grant("gold:100");
        p.grant("cos:frame_gold");
        p.grant("cloak:1");
        assert!(p.cosmetics.contains(&"frame_gold".to_string()));
        assert_eq!(p.inv.count_id("cloak"), 1);
    }
}
