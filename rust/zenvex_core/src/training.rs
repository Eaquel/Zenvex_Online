//! V11 şehir öğreticisinin eğitim üssü (yalnız yerel; sunucuya gitmez).
//! Komutan Diana görevi verince oyuncu Cephe Karargâhı'nın yanındaki eğitim üssüne ışınlanır; içerideki 6 zayıf asker
//! indirilir, bayrak halkasında 10 sn durulunca üs alınır. Ardından hava saldırısı: bomba düşer, üs yerle bir olur,
//! oyuncu şehirde uyanır ve görev tamamlanır.
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
            let far = (self.player.x - bx).hypot(self.player.y - by) > (BASE_HALF + 30.0) * TILE;
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
    use crate::bases::{base_defs, TRAIN_BASE};
    use crate::game::Game;
    use crate::story::{N_COMMANDER, GUIDE};
    use crate::world::{self, TILE};
    use crate::Progress;

    #[test]
    fn city_tutorial_training_base_flow() {
        let mut p = Progress::default();
        p.level = 10;
        p.story.guide_v = 1;
        p.story.guide = GUIDE.iter().position(|q| q.id == "g9").unwrap();
        let mut g = Game::new(7, p, 0);
        g.player.combat_t = 99.0;
        // komutanla konuş, görevi kabul et → eğitim üssüne ışınlanır
        let v = g.talk(N_COMMANDER, "");
        assert!(v["opts"].as_array().unwrap().iter().any(|o| o["id"] == "accept"), "{v}");
        g.talk(N_COMMANDER, "accept");
        assert_eq!(g.train, 1);
        let (bx, by) = base_defs()[TRAIN_BASE].pos();
        assert!((g.player.x - bx).hypot(g.player.y - by) < 20.0 * TILE);
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
        assert!((g.player.x - cx).hypot(g.player.y - cy) < 6.0 * TILE, "şehirde uyanmadı");
        let j = g.story_json();
        assert!(j.as_array().unwrap().iter().any(|q| q["id"] == "g9" && q["ready"] == true), "{j}");
        // rehber Nova bu görevi vermez/almaz
        let v = g.talk(crate::story::N_GUIDE, "");
        assert!(!v["opts"].as_array().unwrap().iter().any(|o| o["id"] == "turnin"));
        let v = g.talk(N_COMMANDER, "turnin");
        assert!(v["lines"].to_string().contains("Ödülün"), "{v}");
    }

    #[test]
    fn old_guide_saves_shift_to_new_chain() {
        use crate::story::migrate_guide_index as m;
        assert_eq!(GUIDE[m(4)].id, "g5");
        assert_eq!(GUIDE[m(5)].id, "g6");
        assert_eq!(GUIDE[m(6)].id, "g7");
        assert_eq!(GUIDE[m(7)].id, "g8");
        assert_eq!(GUIDE[m(3)].id, "g4");
    }
}
