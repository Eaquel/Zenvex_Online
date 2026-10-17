//! Level, XP ve yetenek (skill) sistemi.
use serde::{Deserialize, Serialize};

pub const MAX_LEVEL: u32 = 50;

/// n. seviyeden n+1'e geçmek için gereken XP.
pub fn xp_to_next(level: u32) -> u32 {
    (50.0 * (level as f32).powf(1.6)).round() as u32
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Branch {
    Power,
    Endurance,
    Agility,
    Woodcutting,
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
        desc: "Yumruk hasarı +%6",
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
        branch: Branch::Woodcutting,
        name: "Sert Eller",
        desc: "Ağaçlara verilen hasar +%10",
        max: 10,
        req_level: 1,
        icon: "wood",
    },
    SkillDef {
        id: 10,
        branch: Branch::Woodcutting,
        name: "Bereket",
        desc: "Ağaçtan +1 odun şansı +%10",
        max: 5,
        req_level: 5,
        icon: "gem",
    },
    SkillDef {
        id: 11,
        branch: Branch::Woodcutting,
        name: "Ormanın Dostu",
        desc: "Kesilen ağaçlar %50 daha hızlı büyür",
        max: 1,
        req_level: 10,
        icon: "star",
    },
];

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default)]
pub struct Progress {
    pub name: String,
    pub level: u32,
    pub xp: u32,
    pub points: u32,
    pub skills: [u8; 12],
    pub kills: u32,
    pub wood: u32,
    pub trees: u32,
    pub deaths: u32,
    pub play_time: f64,
    pub zombie_teeth: u32,
}

impl Default for Progress {
    fn default() -> Self {
        Progress {
            name: "Kaan".into(),
            level: 1,
            xp: 0,
            points: 0,
            skills: [0; 12],
            kills: 0,
            wood: 0,
            trees: 0,
            deaths: 0,
            play_time: 0.0,
            zombie_teeth: 0,
        }
    }
}

impl Progress {
    pub fn skill(&self, id: usize) -> f32 {
        self.skills[id] as f32
    }

    /// XP ekler, atlanan seviye sayısını döndürür.
    pub fn add_xp(&mut self, amount: u32) -> u32 {
        let mut ups = 0;
        if self.level >= MAX_LEVEL {
            return 0;
        }
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

    pub fn can_upgrade(&self, id: usize) -> Result<(), &'static str> {
        let def = SKILLS.get(id).ok_or("Geçersiz yetenek")?;
        if self.skills[id] >= def.max {
            return Err("Maksimum seviyede");
        }
        if self.level < def.req_level {
            return Err("Seviyen yetersiz");
        }
        // aynı dalda önceki yetenek en az 1 olmalı
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

    // ---- türetilmiş istatistikler
    pub fn max_hp(&self) -> f32 {
        (100.0 + 5.0 * (self.level - 1) as f32) * (1.0 + 0.04 * self.skill(3))
    }
    pub fn max_stamina(&self) -> f32 {
        100.0 + 2.0 * (self.level - 1) as f32
    }
    pub fn punch_damage(&self) -> f32 {
        (8.0 + 0.6 * (self.level - 1) as f32) * (1.0 + 0.06 * self.skill(0))
    }
    pub fn tree_damage_mult(&self) -> f32 {
        1.0 + 0.10 * self.skill(9)
    }
    pub fn crit_chance(&self) -> f32 {
        0.05 + 0.02 * self.skill(1)
    }
    pub fn stun_chance(&self) -> f32 {
        if self.skills[2] > 0 {
            0.2
        } else {
            0.0
        }
    }
    pub fn regen_mult(&self) -> f32 {
        1.0 + 0.2 * self.skill(4)
    }
    pub fn has_last_breath(&self) -> bool {
        self.skills[5] > 0
    }
    pub fn move_mult(&self) -> f32 {
        1.0 + 0.02 * self.skill(6)
    }
    pub fn stamina_cost_mult(&self) -> f32 {
        1.0 - 0.08 * self.skill(7)
    }
    pub fn run_mult(&self) -> f32 {
        if self.skills[8] > 0 {
            1.15
        } else {
            1.0
        }
    }
    pub fn bonus_wood_chance(&self) -> f32 {
        0.10 * self.skill(10)
    }
    pub fn regrow_mult(&self) -> f32 {
        if self.skills[11] > 0 {
            0.5
        } else {
            1.0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn xp_curve_matches_design() {
        assert_eq!(xp_to_next(1), 50);
        assert_eq!(xp_to_next(4), 459);
        assert_eq!(xp_to_next(9), 1682);
        assert_eq!(xp_to_next(49), 25310);
    }

    #[test]
    fn level_up_gives_points() {
        let mut p = Progress::default();
        let ups = p.add_xp(50);
        assert_eq!(ups, 1);
        assert_eq!(p.level, 2);
        assert_eq!(p.points, 1);
        assert_eq!(p.xp, 0);
    }

    #[test]
    fn every_fifth_level_bonus_point() {
        let mut p = Progress::default();
        let total: u32 = (1..5).map(xp_to_next).sum();
        p.add_xp(total);
        assert_eq!(p.level, 5);
        assert_eq!(p.points, 5);
    }

    #[test]
    fn max_level_caps() {
        let mut p = Progress::default();
        p.add_xp(u32::MAX / 2);
        assert_eq!(p.level, MAX_LEVEL);
        assert_eq!(p.add_xp(1000), 0);
    }

    #[test]
    fn skill_rules() {
        let mut p = Progress::default();
        assert!(p.upgrade(0).is_err()); // puan yok
        p.points = 3;
        assert!(p.upgrade(1).is_err()); // seviye yetersiz
        p.level = 5;
        assert!(p.upgrade(1).is_err()); // önceki yetenek yok
        assert!(p.upgrade(0).is_ok());
        assert!(p.upgrade(1).is_ok());
        assert_eq!(p.points, 1);
    }
}
