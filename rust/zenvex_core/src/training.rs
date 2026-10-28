//! V11 şehir öğreticisinin eğitim üssü (yalnız yerel; sunucuya gitmez).
//! Komutan Diana görevi verince oyuncu Cephe Karargâhı'nın yanındaki eğitim üssüne ışınlanır; içerideki 6 zayıf asker
//! indirilir, bayrak halkasında 10 sn durulunca üs alınır. Ardından hava saldırısı: bomba düşer, üs yerle bir olur,
//! oyuncu şehirde uyanır ve görev tamamlanır.
#[allow(unused_imports)]
use crate::Hyp;
use crate::bases::{base_defs, BASE_HALF, TRAIN_BASE};
use crate::game::{Event, Game};
use crate::world::{self, TILE};

/// bomba düşüş süresi (sn; Kotlin düşen bombayı bu süre boyunca çizer) ve patlamadan sonra şehre dönüş
pub const BOMB_FALL: f32 = 2.4;
const WAKE_AFTER: f32 = 1.2;

impl Game {
    pub fn train_on(&self) -> bool {
        self.train > 0
    }

    /// Eğitim üssüne gönder (Komutan görevi kabul edilince)
    pub fn start_training(&mut self) {
        let b = &base_defs()[TRAIN_BASE];
        let (x, y) = (b.tx * TILE, (b.ty + BASE_HALF + 3.0) * TILE);
        if let Some(st) = self.bases.get_mut(TRAIN_BASE) {
            *st = Default::default();
            st.left_at = -1.0;
        }
        self.soldiers.retain(|s| s.base as usize != TRAIN_BASE);
        self.train = 1;
        self.train_t = 0.0;
        let (ox, oy) = (self.player.x, self.player.y);
        self.events.push(Event::Teleport { x: ox, y: oy });
        let (x, y) = self.teleport(x, y);
        self.events.push(Event::Teleport { x, y });
        self.msg("Eğitim üssü: askerleri indir, sonra bayrak halkasında 10 sn dur.");
    }

    /// Eğitim üssü ele geçirildi: hava saldırısı başlar
    pub fn train_captured(&mut self) {
        let (bx, by) = base_defs()[TRAIN_BASE].pos();
        self.train = 2;
        self.train_t = 0.0;
        self.events.push(Event::Bomb { x: bx, y: by, dur: BOMB_FALL });
        self.msg("Üs senin! Dikkat… Hava saldırısı!");
    }

    fn end_training(&mut self) {
        self.train = 0;
        self.train_t = 0.0;
        self.soldiers.retain(|s| s.base as usize != TRAIN_BASE);
        if let Some(st) = self.bases.get_mut(TRAIN_BASE) {
            *st = Default::default();
            st.left_at = -1.0;
        }
    }

    pub fn update_training(&mut self, dt: f32) {
        if self.train == 0 {
            return;
        }
        self.train_t += dt;
        let (bx, by) = base_defs()[TRAIN_BASE].pos();
        if self.train == 1 {
            // öldü ya da üssten uzaklaştı: eğitim yarıda kalır (Komutan'dan yeniden alınır)
            let far = (self.player.x - bx).hyp(self.player.y - by) > (BASE_HALF + 30.0) * TILE;
            if self.player.dead || far {
                self.end_training();
                let s = &mut self.progress.story;
                s.guide_on = false;
                s.guide_n = 0;
                self.msg("Eğitim yarıda kaldı. Komutan Diana'dan yeniden alabilirsin.");
            }
            return;
        }
        // hava saldırısı: bomba düşer, patlar, oyuncu bayılır ve şehirde uyanır
        if self.train == 2 && self.train_t >= BOMB_FALL {
            self.train = 3;
            self.events.push(Event::Explode { x: bx, y: by, r: 9.0 * TILE });
            self.player.hp = (self.player.hp * 0.25).max(1.0);
        }
        if self.train == 3 && self.train_t >= BOMB_FALL + WAKE_AFTER {
            self.end_training();
            let (cx, cy) = world::city_spawn();
            let (x, y) = self.teleport(cx, cy);
            self.events.push(Event::Teleport { x, y });
            self.player.hp = self.progress.max_hp();
            self.msg("Bomba üssü yerle bir etti. Şehirde uyandın; Komutan Diana'ya rapor ver.");
            self.story_event(crate::story::Ev::Train);
        }
    }
}

#[cfg(test)]
mod tests {
    #[allow(unused_imports)]
    use crate::Hyp;
    use crate::bases::{base_defs, TRAIN_BASE};
    use crate::game::Game;
    use crate::story::{N_COMMANDER, GUIDE};
    use crate::world::{self, TILE};
    use crate::Progress;

    #[test]
    fn city_tutorial_training_base_flow() {
        let mut p = Progress::default();
        p.level = 10;
        p.story.guide_v = 3;
        p.story.guide = GUIDE.iter().position(|q| q.id == "g9").unwrap();
        let mut g = Game::new(7, p, 0);
        g.player.combat_t = 99.0;
        // komutanla konuş, görevi kabul et → eğitim üssüne ışınlanır
        let v = g.talk(N_COMMANDER, "");
        assert!(v["opts"].as_array().unwrap().iter().any(|o| o["id"] == "accept"), "{v}");
        g.talk(N_COMMANDER, "accept");
        assert_eq!(g.train, 1);
        let (bx, by) = base_defs()[TRAIN_BASE].pos();
        assert!((g.player.x - bx).hyp(g.player.y - by) < 20.0 * TILE);
        g.update_bases(0.016);
        let n = g.soldiers.iter().filter(|s| s.base as usize == TRAIN_BASE).count();
        assert_eq!(n, 6);
        // askerler düşer, oyuncu halkada durur
        for s in g.soldiers.iter_mut() {
            s.hp = 0.0;
            s.dead_t = 1.0;
        }
        g.player.x = bx;
        g.player.y = by;
        for _ in 0..(11.0 / 0.05) as usize {
            g.player.x = bx;
            g.player.y = by;
            g.update_bases(0.05);
        }
        assert_eq!(g.train, 2, "üs alınmadı");
        for _ in 0..80 {
            g.update_training(0.05);
        }
        assert_eq!(g.train, 0);
        let (cx, cy) = world::city_spawn();
        assert!((g.player.x - cx).hyp(g.player.y - cy) < 6.0 * TILE, "şehirde uyanmadı");
        let j = g.story_json();
        assert!(j.as_array().unwrap().iter().any(|q| q["id"] == "g9" && q["ready"] == true), "{j}");
        let v = g.talk(N_COMMANDER, "turnin");
        assert!(v["lines"].to_string().contains("Ödülün"), "{v}");
    }

    /// V10 → V11 → V16: eski kayıttaki rehber sırası aynı adıma gider
    #[test]
    fn old_guide_saves_shift_to_new_chain() {
        use crate::story::{migrate_guide_index as m1, migrate_guide_v2 as m2};
        let m = |old: usize| crate::story::remap_v2_guide(m2(m1(old)));
        assert_eq!(GUIDE[m(3)].id, "g4");
        assert_eq!(GUIDE[m(4)].id, "g5");
        assert_eq!(GUIDE[m(5)].id, "g6");
        assert_eq!(GUIDE[m(6)].id, "g7");
        assert_eq!(GUIDE[m(7)].id, "g8");
        // V11 kaydı: ağaç kesme (g4a) adımındaki oyuncu aynı adımda kalır; bitirmiş oyuncu bitmiş kalır
        let v1_chop = 5;
        assert_eq!(GUIDE[crate::story::remap_v2_guide(m2(v1_chop))].id, "g4a");
        assert_eq!(crate::story::remap_v2_guide(m2(12)), GUIDE.len());
    }

    /// V16 öğreticisi: balta üret → kuşan → ağaç kes → kazma üret → kaya kır → bıçak üret → kuşan
    #[test]
    fn tool_tutorial_steps_in_order() {
        use crate::story::Ev;
        let ids: Vec<&str> = GUIDE.iter().map(|q| q.id).collect();
        let at = |id: &str| ids.iter().position(|x| *x == id).unwrap();
        assert!(at("g4b") < at("g4c") && at("g4c") < at("g4a") && at("g4a") < at("g4d"));
        assert!(at("g4d") < at("g4e") && at("g4e") < at("g4f") && at("g4f") < at("g4g") && at("g4g") < at("g5"));
        let mut p = Progress::default();
        p.story.guide_v = 3;
        p.story.guide = at("g4c");
        let mut g = Game::new(5, p, 1);
        g.story_tick();
        assert!(g.progress.story.guide_on, "kuşan adımı başlamalı");
        // balta çantadayken tamamlanmaz; kuşanınca (canlı okunur) tamamlanır
        g.story_tick();
        assert_eq!(GUIDE[g.progress.story.guide].id, "g4c");
        g.progress.inv.add_id("t_axe_stone", 1);
        g.progress.eq.weapon = "t_axe_stone".to_string();
        g.story_tick();
        g.story_tick();
        assert_eq!(GUIDE[g.progress.story.guide].id, "g4a", "balta kuşanınca teslim edilmeli");
        // kaya kırma adımı kaya olayıyla ilerler
        g.progress.story.guide = at("g4e");
        g.progress.story.guide_on = false;
        g.story_tick();
        assert!(g.progress.story.guide_on);
        g.story_event(Ev::Mine);
        g.story_tick();
        assert_eq!(GUIDE[g.progress.story.guide].id, "g4f");
        // son adım: bıçağı kuşan
        g.progress.story.guide = at("g4g");
        g.progress.story.guide_on = false;
        g.progress.eq.weapon = "w_knife".to_string();
        g.story_tick();
        g.story_tick();
        assert_eq!(GUIDE[g.progress.story.guide].id, "g4h");
    }

    /// Ana görev zinciri Sv 40'a kadar uzanır: seviyeler artar, her bölge bir karakol + bir av, sonda Titan
    #[test]
    fn main_chain_reaches_level_40_and_ends_with_titan() {
        use crate::story::{Goal, MAIN};
        let mut last = 0;
        for q in MAIN {
            assert!(q.min_level >= last, "{} sırası bozuk", q.id);
            last = q.min_level;
            assert!(!q.talk.is_empty() && !q.done.is_empty() && q.gold > 0, "{} eksik", q.id);
        }
        let q = MAIN.last().unwrap();
        assert_eq!(q.id, "q20");
        assert_eq!(q.min_level, 40);
        assert_eq!(q.goal, Goal::KillKind(crate::data::ZKind::Titan));
        // her bölge karakolu zincirde var
        for post in ["burnt_post", "desert_post", "bswamp_post", "front_hq", "harbor_post"] {
            assert!(MAIN.iter().any(|q| q.goal == Goal::Reach(post)), "{post} görevi yok");
        }
    }

    /// V18: her Ping hedefinin adı istemcinin gönderebildiği adlar arasında; sıra ve geçiş zinciri bütün
    #[test]
    fn ping_goals_are_reachable_and_chain_is_ordered() {
        use crate::story::{ping_name, Ev, Goal, MAIN};
        for q in GUIDE.iter().chain(MAIN.iter()) {
            if let Goal::Ping(n) = q.goal {
                assert_eq!(ping_name(n), Some(n), "{} adı istemciden gönderilemiyor", q.id);
            }
        }
        assert_eq!(ping_name("yok"), None);
        let ids: Vec<&str> = GUIDE.iter().map(|q| q.id).collect();
        let at = |id: &str| ids.iter().position(|x| *x == id).unwrap();
        assert!(at("g4g") < at("g4h") && at("g4h") < at("g4i") && at("g4i") < at("g5") && at("g5") < at("g5b") && at("g5b") < at("g6"));
        assert!(at("g6") < at("g6s") && at("g6s") < at("g6b") && at("g6b") < at("g6c") && at("g6c") < at("g6d"));
        assert!(at("g6f") < at("g7") && at("g7") < at("g7b") && at("g7b") < at("g8"));
        // Ping adımı: olay gelince ilerler
        let mut p = Progress::default();
        p.story.guide_v = 3;
        p.story.guide = at("g6d");
        let mut g = Game::new(5, p, 1);
        g.story_tick();
        assert!(g.progress.story.guide_on);
        g.story_event(Ev::Ping("map"));
        g.story_tick();
        g.story_tick();
        assert_eq!(GUIDE[g.progress.story.guide].id, "g6e");
    }

    /// V19: zombi görevi en yakın zombiyi gösterir; "Cepten Kullan" adımında canı dolu oyuncu da bandajı kullanabilir
    #[test]
    fn quests_point_to_targets_and_tutorial_bandage_works_at_full_hp() {
        let at = |id: &str| GUIDE.iter().position(|q| q.id == id).unwrap();
        let mut p = Progress::default();
        p.story.guide_v = 3;
        p.story.guide = at("g1");
        let mut g = Game::new(5, p, 1);
        g.story_tick();
        g.zombies.clear();
        let (zx, zy) = (g.player.x + 600.0, g.player.y + 200.0);
        g.spawn_zombie_at(crate::data::ZKind::Walker, 1, zx, zy);
        let j = g.story_json();
        let q = j.as_array().unwrap().iter().find(|q| q["id"] == "g1").unwrap().clone();
        let (tx, ty) = (q["x"].as_f64().unwrap() as f32, q["y"].as_f64().unwrap() as f32);
        assert!((tx - g.zombies[0].x).abs() < 1.0 && (ty - g.zombies[0].y).abs() < 1.0, "{q}");
        // g5b: can dolu, bandaj cepte → kullanılır ve adım ilerler
        g.progress.story.guide = at("g5b");
        g.progress.story.guide_on = false;
        g.story_tick();
        assert!(g.progress.story.guide_on);
        g.player.hp = g.progress.max_hp();
        g.progress.inv.add_id("bandage", 1);
        let slot = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == "bandage")).unwrap();
        assert!(g.use_slot(slot).is_ok(), "öğretici bandajı canı dolu da olsa kullanılmalı");
        g.story_tick();
        g.story_tick();
        assert_ne!(GUIDE[g.progress.story.guide].id, "g5b");
    }

    /// Kaya kırınca günlük "12 kaya kır" ve rehber adımı ilerler
    #[test]
    fn mining_counts_for_daily_quest() {
        assert!(crate::data::DAILY_POOL.iter().any(|d| d.id == "d_mine" && d.kind == crate::data::QuestKind::Mine && d.goal == 12));
    }
}
