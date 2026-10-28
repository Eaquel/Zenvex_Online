//! Köpek yardımcı: oyuncuyu takip eder, yakındaki zombiye saldırır, bayılırsa 30 sn sonra döner.
#[allow(unused_imports)]
use crate::Hyp;
use crate::data::ZOMBIES;
use crate::game::*;
use crate::rpg;

pub const D_PET: f32 = 8.0;

#[derive(Clone, Debug, Default)]
pub struct Dog {
    pub x: f32,
    pub y: f32,
    pub hp: f32,
    pub dir: u8,
    pub anim: u8,
    pub anim_t: f32,
    pub atk_t: f32,
    pub dead_t: f32,
    pub flash_t: f32,
}

impl Game {
    pub fn update_dog(&mut self, dt: f32) {
        if !self.progress.dog_owned {
            self.dog = None;
            return;
        }
        let lv = self.progress.dog_lv;
        let max_hp = rpg::dog_hp(lv[1]);
        let (px, py) = (self.player.x, self.player.y);
        let mut d = self.dog.take().unwrap_or_else(|| Dog { x: px + 50.0, y: py + 30.0, hp: max_hp, ..Default::default() });
        d.anim_t += dt;
        d.flash_t = (d.flash_t - dt).max(0.0);
        d.atk_t = (d.atk_t - dt).max(0.0);
        if d.dead_t > 0.0 {
            d.dead_t -= dt;
            if d.dead_t <= 0.0 {
                d = Dog { x: px + 50.0, y: py + 30.0, hp: max_hp, ..Default::default() };
                self.msg("Köpeğin kendine geldi");
            }
            self.dog = Some(d);
            return;
        }
        d.hp = (d.hp + rpg::dog_regen(lv[2]) * dt).min(max_hp);
        if self.player.vehicle.is_some() || self.passenger {
            // araçta oyuncuyla birlikte oturur; inince yanında belirir
            d.x = px + 50.0;
            d.y = py + 30.0;
            d.anim = Z_IDLE;
            self.dog = Some(d);
            return;
        }
        if (d.x - px).hyp(d.y - py) > 900.0 {
            d.x = px + 50.0;
            d.y = py + 30.0;
        }
        // hedef: oyuncunun yakınındaki en yakın zombi
        let mut target: Option<(usize, f32)> = None;
        for (i, z) in self.zombies.iter().enumerate() {
            if z.state == ZState::Dead || z.alpha < 0.3 {
                continue;
            }
            if (z.x - px).hyp(z.y - py) > m(6.0) {
                continue;
            }
            let dd = (z.x - d.x).hyp(z.y - d.y);
            if target.is_none_or(|(_, b)| dd < b) {
                target = Some((i, dd));
            }
        }
        let (mut mx, mut my, mut speed) = (0.0f32, 0.0f32, 0.0f32);
        // zombi yoksa devlet üssünün düşman askerlerine saldırır
        let soldier = if target.is_none() { self.dog_soldier_target(d.x, d.y) } else { None };
        if let Some((si, dist)) = soldier {
            let (sx, sy) = (self.soldiers[si].x, self.soldiers[si].y);
            if dist > 46.0 {
                mx = sx - d.x;
                my = sy - d.y;
                speed = m(5.2);
                d.anim = Z_WALK;
            } else {
                d.dir = dir_from_vec(sx - d.x, sy - d.y);
                if d.atk_t <= 0.0 {
                    d.atk_t = 0.8;
                    d.anim = Z_ATTACK;
                    d.anim_t = 0.0;
                    let dmg = rpg::dog_damage(lv[0]) * self.rng.range(0.9, 1.1);
                    self.hurt_soldier(si, dmg, false);
                } else if d.anim == Z_ATTACK && d.anim_t > 0.45 {
                    d.anim = Z_IDLE;
                }
            }
        } else if let Some((i, dist)) = target {
            let (zx, zy) = (self.zombies[i].x, self.zombies[i].y);
            let reach = ZOMBIES[self.zombies[i].kind as usize].radius + 34.0;
            if dist > reach {
                mx = zx - d.x;
                my = zy - d.y;
                speed = m(5.2);
                d.anim = Z_WALK;
            } else {
                d.dir = dir_from_vec(zx - d.x, zy - d.y);
                if d.atk_t <= 0.0 {
                    d.atk_t = 0.8;
                    d.anim = Z_ATTACK;
                    d.anim_t = 0.0;
                    let (kx, ky) = ((zx - d.x) / dist.max(1.0), (zy - d.y) / dist.max(1.0));
                    let dmg = rpg::dog_damage(lv[0]) * self.rng.range(0.9, 1.1);
                    self.dog_hit = true;
                    self.hurt_zombie(i, dmg, false, kx, ky, false, true);
                    self.dog_hit = false;
                    // ısırılan zombi köpeğe döner
                    if let Some(z) = self.zombies.get_mut(i) {
                        z.dog_aggro = 4.0;
                    }
                } else if d.anim == Z_ATTACK && d.anim_t > 0.45 {
                    d.anim = Z_IDLE;
                }
            }
        } else {
            // takip: oyuncunun biraz arkasında
            let (fx, fy) = dir_vec(self.player.dir);
            let (tx, ty) = (px - fx * 60.0 + fy * 30.0, py - fy * 50.0 - fx * 30.0);
            let dist = (tx - d.x).hyp(ty - d.y);
            if dist > 40.0 {
                mx = tx - d.x;
                my = ty - d.y;
                speed = if dist > 220.0 { m(6.0) } else { m(3.2) };
                d.anim = Z_WALK;
            } else if d.anim != Z_IDLE {
                d.anim = Z_IDLE;
                d.anim_t = 0.0;
            }
        }
        if speed > 0.0 {
            let l = mx.hyp(my).max(1e-3);
            let (mut nx, mut ny) = (d.x + mx / l * speed * dt, d.y + my / l * speed * dt);
            self.world.resolve(&mut nx, &mut ny, 12.0, true, &mut self.scratch);
            crate::areas::clamp(d.x, d.y, &mut nx, &mut ny);
            d.x = nx;
            d.y = ny;
            d.dir = dir_from_vec(mx, my);
        }
        self.dog = Some(d);
    }

    /// Zombilerin köpeğe vurduğu hasar (update_zombies toplar).
    pub fn hurt_dog(&mut self, dmg: f32) {
        if self.player.god {
            return;
        }
        let Some(d) = self.dog.as_mut() else { return };
        if d.dead_t > 0.0 {
            return;
        }
        d.hp -= dmg;
        d.flash_t = 0.12;
        let (x, y) = (d.x, d.y);
        self.events.push(Event::DogHit { x, y, v: dmg.round() as i32 });
        if d.hp <= 0.0 {
            d.dead_t = 30.0;
            d.hp = 0.0;
            self.msg("Köpeğin bayıldı, 30 sn sonra döner");
        }
    }

    /// Çizim listesi kaydı (zombi düzeniyle aynı; tür alanı köpek)
    pub fn dog_draw(&self) -> Option<(f32, [f32; STRIDE])> {
        let d = self.dog.as_ref()?;
        if d.dead_t > 0.0 || self.player.vehicle.is_some() || self.passenger {
            return None;
        }
        let lv = self.progress.dog_lv;
        let hp = d.hp / rpg::dog_hp(lv[1]);
        let avg = (lv[0] as f32 + lv[1] as f32 + lv[2] as f32) / 3.0;
        Some((d.y, [D_PET, 0.0, d.x, d.y, d.dir as f32, d.anim as f32, d.anim_t, if d.flash_t > 0.0 { 1.0 } else { 0.0 }, hp, avg.round().max(1.0), 1.0, 0.0]))
    }

    pub fn dog_buy(&mut self) -> Result<String, String> {
        if self.progress.dog_owned {
            return Err("Zaten bir köpeğin var".into());
        }
        if !self.near_npc(5) {
            return Err("Köpek yalnızca barınaktaki Eğitmen Jack'ten alınır".into());
        }
        if self.progress.gold < rpg::DOG_PRICE {
            return Err(format!("{} altın gerekli", rpg::DOG_PRICE));
        }
        self.progress.gold -= rpg::DOG_PRICE;
        self.progress.dog_owned = true;
        self.story_event(crate::story::Ev::Dog);
        Ok("Yeni dostun seninle! Köpek seni takip edecek.".into())
    }

    pub fn dog_upgrade(&mut self, stat: usize) -> Result<String, String> {
        if !self.progress.dog_owned {
            return Err("Önce barınaktaki Eğitmen Jack'ten bir köpek al".into());
        }
        if !self.near_npc(5) {
            return Err("Köpek geliştirme barınaktaki Eğitmen Jack'te yapılır".into());
        }
        let lv = *self.progress.dog_lv.get(stat).ok_or("Geçersiz")?;
        if lv >= rpg::DOG_MAX {
            return Err("En yüksek seviyede".into());
        }
        let cost = rpg::dog_upgrade_cost(lv);
        if self.progress.gold < cost {
            return Err(format!("{cost} altın gerekli"));
        }
        self.progress.gold -= cost;
        self.progress.dog_lv[stat] += 1;
        if stat == 1 {
            if let Some(d) = self.dog.as_mut() {
                d.hp = rpg::dog_hp(self.progress.dog_lv[1]);
            }
        }
        Ok(format!("Köpek {} seviye {}", rpg::DOG_STATS[stat], lv + 1))
    }
}
