//! V4 gelişim sistemi: Savaşçı karakter seviyeleri (1–10), herkes için aynı skill ağacı, köpek yardımcı.
//! Tüm sayısal etkiler `Bonus` içinde toplanır; Progress ve Game buradan okur.
use crate::progress::Progress;
use serde::Serialize;
use std::sync::OnceLock;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
pub enum Stat {
    MeleeFlat,
    FistPct,
    DmgPct,
    Crit,
    Stun,
    Lifesteal,
    BladePct,
    Reach,
    AtkSpeed,
    CritDmg,
    HpFlat,
    DmgTaken,
    Reflect,
    BlastRes,
    Regen,
    LastBreath,
    Dodge,
    Speed,
    RollCd,
    Stamina,
    IFrame,
}

/// Bir istatistiğin oyuncuya gösterilen açıklaması.
pub fn stat_text(s: Stat, v: f32) -> String {
    let p = (v * 100.0).round() as i32;
    match s {
        Stat::MeleeFlat => format!("Yakın dövüş hasarı +{v:.0}"),
        Stat::FistPct => format!("Yumruk hasarı +%{p}"),
        Stat::DmgPct => format!("Tüm hasar +%{p}"),
        Stat::Crit => format!("Kritik vuruş şansı +%{p}"),
        Stat::Stun => format!("Sersemletme şansı +%{p}"),
        Stat::Lifesteal => format!("Yakın dövüş hasarının %{p}'i kadar can çalar"),
        Stat::BladePct => format!("Bıçak ve katana hasarı +%{p}"),
        Stat::Reach => format!("Silah menzili +%{p}"),
        Stat::AtkSpeed => format!("Silah saldırı hızı +%{p}"),
        Stat::CritDmg => format!("Kritik vuruş hasarı +%{p}"),
        Stat::HpFlat => format!("Can +{v:.0}"),
        Stat::DmgTaken => format!("Alınan hasar −%{p}"),
        Stat::Reflect => format!("Alınan hasarın %{p}'ini saldırana yansıtır"),
        Stat::BlastRes => format!("Patlama koruması +%{p}"),
        Stat::Regen => format!("Can yenilenmesi +%{p}"),
        Stat::LastBreath => "Ölümcül darbede 1 canla kalırsın (120 sn'de bir)".into(),
        Stat::Dodge => format!("Hasardan kaçınma şansı +%{p}"),
        Stat::Speed => format!("Hareket hızı +%{p}"),
        Stat::RollCd => format!("Takla bekleme süresi −%{p}"),
        Stat::Stamina => format!("Dayanıklılık tüketimi −%{p}"),
        Stat::IFrame => format!("Takladan sonra +{v:.1} sn hasar almama"),
    }
}

#[derive(Clone, Debug, Serialize)]
pub struct Node {
    pub id: u16,
    /// 0 yakın dövüş, 1 silah ustalığı (bıçak/katana), 2 dayanıklılık, 3 çeviklik, 255 merkez
    pub branch: u8,
    pub x: f32,
    pub y: f32,
    pub stat: Stat,
    pub val: f32,
    /// 0 küçük, 1 belirgin, 2 anahtar
    pub kind: u8,
    pub name: String,
    pub desc: String,
    pub links: Vec<u16>,
}

pub const BRANCHES: [&str; 4] = ["Yakın Dövüş", "Silah Ustalığı", "Dayanıklılık", "Çeviklik"];

struct BranchDef {
    dir: (f32, f32),
    small: &'static [(Stat, f32)],
    notable: (&'static str, Stat, f32),
    keys: [(&'static str, Stat, f32); 3],
}

const DEFS: [BranchDef; 4] = [
    BranchDef {
        dir: (0.0, -1.0),
        small: &[(Stat::MeleeFlat, 5.0), (Stat::FistPct, 0.03), (Stat::Crit, 0.01), (Stat::MeleeFlat, 5.0), (Stat::DmgPct, 0.02)],
        notable: ("Ağır El", Stat::FistPct, 0.08),
        keys: [("Kan Emici", Stat::Lifesteal, 0.03), ("Sarsıcı Darbe", Stat::Stun, 0.15), ("Yıkım", Stat::DmgPct, 0.15)],
    },
    BranchDef {
        dir: (1.0, 0.0),
        small: &[(Stat::BladePct, 0.05), (Stat::Reach, 0.04), (Stat::Crit, 0.01), (Stat::BladePct, 0.05), (Stat::AtkSpeed, 0.03)],
        notable: ("Usta Kılıç", Stat::BladePct, 0.10),
        keys: [("Kanlı Kenar", Stat::CritDmg, 0.30), ("Rüzgâr Kesik", Stat::AtkSpeed, 0.15), ("Ölüm Ustası", Stat::BladePct, 0.20)],
    },
    BranchDef {
        dir: (0.0, 1.0),
        small: &[(Stat::HpFlat, 10.0), (Stat::DmgTaken, 0.01), (Stat::Reflect, 0.01), (Stat::BlastRes, 0.10), (Stat::Regen, 0.10)],
        notable: ("Kalın Deri", Stat::HpFlat, 30.0),
        keys: [("Kaya", Stat::DmgTaken, 0.08), ("Son Nefes", Stat::LastBreath, 1.0), ("Titan Derisi", Stat::HpFlat, 150.0)],
    },
    BranchDef {
        dir: (-1.0, 0.0),
        small: &[(Stat::Dodge, 0.01), (Stat::Speed, 0.02), (Stat::RollCd, 0.05), (Stat::Stamina, 0.05), (Stat::Dodge, 0.01)],
        notable: ("Rüzgâr", Stat::Speed, 0.06),
        keys: [("Gölge Adım", Stat::IFrame, 0.5), ("Fırtına", Stat::Speed, 0.10), ("Hayalet", Stat::Dodge, 0.08)],
    },
];

/// Ağaç: merkez + 4 dal; her dalda 15 düğümlük ana yol (5/10/15'te anahtar) ve 3 yan kol (4 düğüm, sonda belirgin).
pub fn tree() -> &'static [Node] {
    static T: OnceLock<Vec<Node>> = OnceLock::new();
    T.get_or_init(|| {
        let mut v: Vec<Node> = Vec::new();
        v.push(Node {
            id: 0, branch: 255, x: 0.0, y: 0.0, stat: Stat::HpFlat, val: 0.0, kind: 1,
            name: "Hayatta Kalan".into(), desc: "Başlangıç noktası. Buraya komşu düğümlerden başla.".into(), links: vec![],
        });
        fn link(v: &mut [Node], a: u16, b: u16) {
            v[a as usize].links.push(b);
            v[b as usize].links.push(a);
        }
        for (bi, d) in DEFS.iter().enumerate() {
            let (dx, dy) = d.dir;
            let (px, py) = (-dy, dx); // dala dik yön
            let mut prev = 0u16;
            let mut si = 0usize;
            for depth in 1..=15u32 {
                let id = v.len() as u16;
                let key = depth % 5 == 0;
                let (stat, val, kind, name) = if key {
                    let k = d.keys[(depth / 5 - 1) as usize];
                    (k.1, k.2, 2u8, k.0.to_string())
                } else {
                    let s = d.small[si % d.small.len()];
                    si += 1;
                    (s.0, s.1, 0u8, String::new())
                };
                let desc = stat_text(stat, val);
                let name = if name.is_empty() { desc.clone() } else { name };
                v.push(Node {
                    id, branch: bi as u8, x: dx * depth as f32, y: dy * depth as f32, stat, val, kind, name, desc, links: vec![],
                });
                link(&mut v, prev, id);
                prev = id;
                // yan kollar 3., 8. ve 13. derinlikte, sırayla iki yana
                if depth == 3 || depth == 8 || depth == 13 {
                    let side = if depth == 8 { -1.0 } else { 1.0 };
                    let mut sp = id;
                    for k in 1..=4u32 {
                        let sid = v.len() as u16;
                        let last = k == 4;
                        let (stat, val, kind, name) = if last {
                            (d.notable.1, d.notable.2, 1u8, d.notable.0.to_string())
                        } else {
                            let s = d.small[(si + k as usize) % d.small.len()];
                            (s.0, s.1, 0u8, String::new())
                        };
                        let desc = stat_text(stat, val);
                        let name = if name.is_empty() { desc.clone() } else { name };
                        v.push(Node {
                            id: sid, branch: bi as u8,
                            x: dx * depth as f32 + px * side * k as f32 * 0.9 + dx * k as f32 * 0.35,
                            y: dy * depth as f32 + py * side * k as f32 * 0.9 + dy * k as f32 * 0.35,
                            stat, val, kind, name, desc, links: vec![],
                        });
                        link(&mut v, sp, sid);
                        sp = sid;
                    }
                }
            }
        }
        v
    })
}

/// Toplanmış tüm bonuslar (karakter seviyesi + skill ağacı).
#[derive(Clone, Debug, Default)]
pub struct Bonus {
    pub melee_flat: f32,
    pub fist_pct: f32,
    pub dmg_pct: f32,
    pub crit: f32,
    pub stun: f32,
    pub lifesteal: f32,
    pub blade_pct: f32,
    pub reach: f32,
    pub atk_speed: f32,
    pub crit_dmg: f32,
    pub hp_flat: f32,
    pub dmg_taken: f32,
    pub reflect: f32,
    pub blast_res: f32,
    pub regen: f32,
    pub last_breath: bool,
    pub dodge: f32,
    pub speed: f32,
    pub roll_cd: f32,
    pub stamina: f32,
    pub iframe: f32,
    pub kill_heal: f32,
    pub aura: bool,
}


pub fn bonus(p: &Progress) -> Bonus {
    let mut b = Bonus::default();
    // seçili karakterin seviye özellikleri (data::CHARACTERS[..].perks ile aynı sıra)
    let lv = p.char_level[p.character.min(3)];
    let at = |n: u32| lv >= n;
    match p.character {
        1 => {
            if at(2) { b.blade_pct += 0.10; }
            if at(3) { b.crit += 0.03; }
            if at(4) { b.speed += 0.08; }
            if at(5) { b.roll_cd += 0.20; }
            if at(6) { b.hp_flat += 40.0; }
            if at(7) { b.crit_dmg += 0.25; }
            if at(8) { b.atk_speed += 0.10; }
            if at(9) { b.dodge += 0.05; }
            if at(10) { b.crit_dmg += 0.50; }
        }
        2 => {
            if at(2) { b.hp_flat += 60.0; }
            if at(3) { b.dmg_taken += 0.05; }
            if at(4) { b.reflect += 0.05; }
            if at(5) { b.blast_res += 0.20; }
            if at(6) { b.hp_flat += 100.0; }
            if at(7) { b.regen += 1.0; }
            if at(8) { b.dmg_taken += 0.05; }
            if at(9) { b.stun += 0.10; }
            if at(10) { b.hp_flat += 250.0; }
        }
        3 => {
            if at(2) { b.blade_pct += 0.12; }
            if at(3) { b.lifesteal += 0.02; }
            if at(4) { b.atk_speed += 0.08; }
            if at(5) { b.crit += 0.04; }
            if at(6) { b.hp_flat += 50.0; }
            if at(7) { b.blade_pct += 0.15; }
            if at(8) { b.lifesteal += 0.03; }
            if at(9) { b.speed += 0.08; }
            if at(10) { b.crit_dmg += 0.40; b.lifesteal += 0.03; }
        }
        _ => {
            if at(2) { b.fist_pct += 0.10; }
            if at(3) { b.dodge += 0.05; }
            if at(4) { b.speed += 0.10; }
            if at(5) { b.iframe += 1.0; }
            if at(6) { b.hp_flat += 50.0; }
            if at(7) { b.fist_pct += 0.15; }
            if at(8) { b.roll_cd += 0.25; }
            if at(9) { b.kill_heal = 0.05; }
            b.aura = at(10);
        }
    }
    let t = tree();
    for &id in &p.tree {
        let Some(n) = t.get(id as usize) else { continue };
        let v = n.val;
        match n.stat {
            Stat::MeleeFlat => b.melee_flat += v,
            Stat::FistPct => b.fist_pct += v,
            Stat::DmgPct => b.dmg_pct += v,
            Stat::Crit => b.crit += v,
            Stat::Stun => b.stun += v,
            Stat::Lifesteal => b.lifesteal += v,
            Stat::BladePct => b.blade_pct += v,
            Stat::Reach => b.reach += v,
            Stat::AtkSpeed => b.atk_speed += v,
            Stat::CritDmg => b.crit_dmg += v,
            Stat::HpFlat => b.hp_flat += v,
            Stat::DmgTaken => b.dmg_taken += v,
            Stat::Reflect => b.reflect += v,
            Stat::BlastRes => b.blast_res += v,
            Stat::Regen => b.regen += v,
            Stat::LastBreath => b.last_breath = true,
            Stat::Dodge => b.dodge += v,
            Stat::Speed => b.speed += v,
            Stat::RollCd => b.roll_cd += v,
            Stat::Stamina => b.stamina += v,
            Stat::IFrame => b.iframe += v,
        }
    }
    // V8 ekipman: yüzük (hasar %, kritik), kolye (düz can, can yenilenmesi), titan kaskı (+can %),
    // ceket (hasar direnci), bot (hız)
    let eq = &p.eq;
    if let Some(d) = eq.def("ring") {
        b.dmg_pct += d.armor;
        b.crit += d.bonus;
    }
    if let Some(d) = eq.def("necklace") {
        b.hp_flat += d.armor;
        b.regen += d.bonus;
    }
    if let Some(d) = eq.def("helmet") {
        b.hp_flat += (100.0 + 5.0 * p.level.saturating_sub(1) as f32) * d.bonus;
    }
    if let Some(d) = eq.def("jacket") {
        b.dmg_taken += d.bonus;
    }
    if let Some(d) = eq.def("boots") {
        b.speed += d.bonus;
    }
    if let Some(d) = eq.def("pants") {
        b.speed += d.bonus;
    }
    b.dmg_taken = b.dmg_taken.min(0.6);
    b.dodge = b.dodge.min(0.5);
    b.roll_cd = b.roll_cd.min(0.6);
    b.blast_res = b.blast_res.min(0.8);
    b.crit_dmg = b.crit_dmg.min(1.5);
    b.reach = b.reach.min(0.6);
    b
}

impl Progress {
    /// Düğüm bedeli (elmas): türe göre taban (küçük 10, belirgin 25, anahtar 100), her açık düğümle %8 artar; 5'e yuvarlı.
    pub fn tree_cost(&self, id: u16) -> u32 {
        let base = match tree().get(id as usize).map(|n| n.kind).unwrap_or(0) { 0 => 10.0, 1 => 25.0, _ => 100.0 };
        let k = 1.0 + 0.08 * self.tree.len() as f32;
        (((base * k) / 5.0).round() as u32 * 5).max(5)
    }

    /// Anahtar düğümler seviye 10'dan önce açılmaz.
    pub fn tree_req_level(&self, id: u16) -> u32 {
        if tree().get(id as usize).map(|n| n.kind).unwrap_or(0) == 2 { 10 } else { 1 }
    }

    /// HUD rozeti: şu an parası yetip açılabilecek düğüm sayısı.
    pub fn tree_points(&self) -> u32 {
        tree().iter().filter(|n| self.tree_can(n.id).is_ok()).count() as u32
    }

    pub fn tree_can(&self, id: u16) -> Result<(), String> {
        let t = tree();
        let n = t.get(id as usize).ok_or("Geçersiz düğüm")?;
        if id == 0 {
            return Err("Merkez zaten açık".into());
        }
        if self.tree.contains(&id) {
            return Err("Zaten açık".into());
        }
        if !n.links.iter().any(|&l| l == 0 || self.tree.contains(&l)) {
            return Err("Önce bağlı bir düğümü aç".into());
        }
        let lv = self.tree_req_level(id);
        if self.level < lv {
            return Err(format!("Seviye {lv} gerekli"));
        }
        let c = self.tree_cost(id);
        if self.gems + self.tree_credit < c {
            return Err(format!("{c} elmas gerekli"));
        }
        Ok(())
    }

    pub fn tree_unlock(&mut self, id: u16) -> Result<String, String> {
        self.tree_can(id)?;
        let c = self.tree_cost(id);
        let from_credit = c.min(self.tree_credit);
        self.tree_credit -= from_credit;
        self.gems -= c - from_credit;
        self.tree_spent += c;
        self.tree.push(id);
        Ok(format!("{} açıldı (-{c} elmas)", tree()[id as usize].name))
    }

    pub fn tree_reset_cost(&self) -> u32 {
        100 * self.level
    }

    /// Sıfırlama: altın karşılığı; harcanan elmaslar ağaç kredisi olarak kalır.
    pub fn tree_reset(&mut self) -> Result<String, String> {
        let c = self.tree_reset_cost();
        if self.gold < c {
            return Err(format!("{c} altın gerekli"));
        }
        self.gold -= c;
        self.tree.clear();
        self.tree_credit += self.tree_spent;
        self.tree_spent = 0;
        Ok(format!("Skill ağacı sıfırlandı · {} elmas kredi olarak kaldı", self.tree_credit))
    }
}

// =========================================================================== köpek
pub const DOG_PRICE: u32 = 500;
pub const DOG_MAX: u8 = 10;
pub const DOG_STATS: [&str; 3] = ["Hasar", "Can", "Can Yenilenmesi"];

pub fn dog_damage(lv: u8) -> f32 {
    10.0 + 5.0 * lv as f32
}
pub fn dog_hp(lv: u8) -> f32 {
    90.0 + 35.0 * lv as f32
}
pub fn dog_regen(lv: u8) -> f32 {
    1.0 + 0.8 * lv as f32
}
/// Köpek geliştirme bedeli: yuvarlak tutarlar (250, 500, 1.000 …).
pub fn dog_upgrade_cost(lv: u8) -> u32 {
    const COST: [u32; 10] = [250, 500, 1_000, 1_500, 2_500, 4_000, 6_000, 8_000, 12_000, 15_000];
    COST[(lv as usize).min(COST.len() - 1)]
}

#[cfg(test)]
mod tests {
    #[allow(unused_imports)]
    use crate::Hyp;
    use super::*;

    #[test]
    fn tree_shape() {
        let t = tree();
        assert_eq!(t.len(), 1 + 4 * (15 + 12));
        // her düğüm merkeze bağlı (BFS)
        let mut seen = vec![false; t.len()];
        let mut q = vec![0u16];
        seen[0] = true;
        while let Some(i) = q.pop() {
            for &l in &t[i as usize].links {
                if !seen[l as usize] {
                    seen[l as usize] = true;
                    q.push(l);
                }
            }
        }
        assert!(seen.iter().all(|&s| s));
        assert_eq!(t.iter().filter(|n| n.kind == 2).count(), 12);
    }

    #[test]
    fn tree_unlock_rules() {
        let mut p = Progress { level: 3, gems: 25, ..Default::default() };
        assert_eq!(p.tree_cost(tree()[0].links[0]), 10);
        let first = tree()[0].links[0];
        assert!(p.tree_unlock(first).is_ok());
        // komşusu açık olmayan uzak düğüm alınamaz
        let far = tree().iter().find(|n| n.kind == 2).unwrap().id;
        assert!(p.tree_unlock(far).is_err());
        let next = tree()[first as usize].links.iter().copied().find(|&l| l != 0).unwrap();
        assert!(p.tree_unlock(next).is_ok());
        assert!(p.gems < 10);
        assert!(p.tree_unlock(tree()[0].links[1]).is_err());
        // sıfırlama: elmas geri gelmez, kredi olur
        p.gold = 1000;
        let spent = p.tree_spent;
        assert!(p.tree_reset().is_ok());
        assert_eq!(p.tree_credit, spent);
        assert!(p.tree_unlock(first).is_ok());
    }

    #[test]
    fn warrior_perks() {
        let mut p = Progress::default();
        let b1 = bonus(&p);
        assert_eq!(b1.fist_pct, 0.0);
        p.char_level[0] = 10;
        p.char_cards[0] = crate::data::CHAR_MAX_CARDS;
        let b = bonus(&p);
        assert!((b.fist_pct - 0.25).abs() < 1e-5);
        assert!(b.aura && b.kill_heal > 0.0 && b.hp_flat >= 50.0 && b.iframe >= 1.0);
    }
}
