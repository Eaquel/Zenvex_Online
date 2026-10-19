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
    pub cloak: String,
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
    fn eq_item(&self, id: &str) -> Option<&'static data::ItemDef> {
        if id.is_empty() {
            None
        } else {
            data::item_index(id).map(|i| &ITEMS[i])
        }
    }

    /// V4: zırh setleri kalktı; savunma skill ağacından gelir.
    pub fn armor(&self) -> f32 {
        0.0
    }

    /// Çanta kalktı: her 5 seviyede +2 slot.
    pub fn inventory_cap(&self) -> usize {
        BASE_SLOTS + 2 * (self.level / 5) as usize
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
        (20.0 + 0.6 * (self.level - 1) as f32 + b.melee_flat) * (1.0 + b.fist_pct)
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
        let target: &mut String = match def.kind {
            ItemKind::Weapon => &mut self.eq.weapon,
            ItemKind::Helmet => &mut self.eq.helmet,
            ItemKind::Jacket => &mut self.eq.jacket,
            ItemKind::Boots => &mut self.eq.boots,
            ItemKind::Bag => &mut self.eq.bag,
            ItemKind::Cloak => &mut self.eq.cloak,
            _ => return Err("Bu eşya giyilemez"),
        };
        let old = std::mem::replace(target, def.id.to_string());
        self.inv.slots[slot] = None;
        if !old.is_empty() {
            self.inv.add_id(&old, 1);
        }
        let cap = self.inventory_cap();
        self.inv.resize(cap);
        Ok(def.name)
    }

    pub fn unequip(&mut self, which: &str) -> Result<(), &'static str> {
        let cap_after = if which == "bag" {
            BASE_SLOTS
        } else {
            self.inventory_cap()
        };
        let target = match which {
            "weapon" => &mut self.eq.weapon,
            "helmet" => &mut self.eq.helmet,
            "jacket" => &mut self.eq.jacket,
            "boots" => &mut self.eq.boots,
            "bag" => &mut self.eq.bag,
            "cloak" => &mut self.eq.cloak,
            _ => return Err("Geçersiz slot"),
        };
        if target.is_empty() {
            return Err("Zaten boş");
        }
        let id = target.clone();
        let used = self.inv.slots.iter().filter(|s| s.is_some()).count();
        if used + 1 > cap_after {
            return Err("Envanterde yer yok");
        }
        *target = String::new();
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
        if let Some(q) = data::MAIN_QUESTS.get(self.quests.main) {
            let hit = match (q.kind, kind) {
                (data::QuestKind::Level, data::QuestKind::Level) => {
                    self.quests.main_progress = self.level;
                    false
                }
                (a, b) => a == b,
            };
            if hit {
                self.quests.main_progress += amount;
            }
            if self.quests.main_progress >= q.goal {
                self.add_gold(q.gold);
                self.add_xp(q.xp);
                self.pass.xp += q.pass_xp;
                self.quests.main += 1;
                self.quests.main_progress = 0;
                done.push(q.title.to_string());
            }
        }
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

    #[test]
    fn quests_progress() {
        let mut p = Progress::default();
        p.ensure_dailies(3);
        assert_eq!(p.quests.daily.len(), 3);
        let g0 = p.gold;
        for _ in 0..5 {
            p.quest_event(data::QuestKind::Kill, 1);
        }
        assert_eq!(p.quests.main, 1);
        assert!(p.gold > g0);
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
