//! Köpek yardımcı: oyuncuyu takip eder, yakındaki zombiye saldırır, bayılırsa 30 sn sonra döner.
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
        if (d.x - px).hypot(d.y - py) > 900.0 || self.player.vehicle.is_some() {
            d.x = px + 50.0;
            d.y = py + 30.0;
        }
        // hedef: oyuncunun yakınındaki en yakın zombi
        let mut target: Option<(usize, f32)> = None;
        for (i, z) in self.zombies.iter().enumerate() {
            if z.state == ZState::Dead || z.alpha < 0.3 {
                continue;
            }
            if (z.x - px).hypot(z.y - py) > m(6.0) {
                continue;
            }
            let dd = (z.x - d.x).hypot(z.y - d.y);
            if target.is_none_or(|(_, b)| dd < b) {
                target = Some((i, dd));
            }
        }
        let (mut mx, mut my, mut speed) = (0.0f32, 0.0f32, 0.0f32);
        if let Some((i, dist)) = target {
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
                    self.hurt_zombie(i, dmg, false, kx, ky, false, true);
                } else if d.anim == Z_ATTACK && d.anim_t > 0.45 {
                    d.anim = Z_IDLE;
                }
            }
        } else {
            // takip: oyuncunun biraz arkasında
            let (fx, fy) = dir_vec(self.player.dir);
            let (tx, ty) = (px - fx * 60.0 + fy * 30.0, py - fy * 50.0 - fx * 30.0);
            let dist = (tx - d.x).hypot(ty - d.y);
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
            let l = mx.hypot(my).max(1e-3);
            let (mut nx, mut ny) = (d.x + mx / l * speed * dt, d.y + my / l * speed * dt);
            self.world.resolve(&mut nx, &mut ny, 12.0, true, &mut self.scratch);
            d.x = nx;
            d.y = ny;
            d.dir = dir_from_vec(mx, my);
        }
        // yakındaki saldıran zombiler köpeğe de vurur
        let mut taken = 0.0;
        for z in &self.zombies {
            if z.state == ZState::Attack && (z.x - d.x).hypot(z.y - d.y) < ZOMBIES[z.kind as usize].radius + 30.0 {
                taken += (4.0 + 0.4 * z.level as f32) * dt;
            }
        }
        if taken > 0.0 && !self.player.god {
            d.hp -= taken;
            d.flash_t = 0.1;
            if d.hp <= 0.0 {
                d.dead_t = 30.0;
                d.hp = 0.0;
                self.msg("Köpeğin bayıldı, 30 sn sonra döner");
            }
        }
        self.dog = Some(d);
    }

    /// Çizim listesi kaydı (zombi düzeniyle aynı; tür alanı köpek)
    pub fn dog_draw(&self) -> Option<(f32, [f32; STRIDE])> {
        let d = self.dog.as_ref()?;
        if d.dead_t > 0.0 {
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
        if !self.near_merchant_wide() {
            return Err("Köpeği kamptaki eğitmenden alabilirsin".into());
        }
        if self.progress.gold < rpg::DOG_PRICE {
            return Err(format!("{} altın gerekli", rpg::DOG_PRICE));
        }
        self.progress.gold -= rpg::DOG_PRICE;
        self.progress.dog_owned = true;
        Ok("Yeni dostun seninle! Köpek seni takip edecek.".into())
    }

    pub fn dog_upgrade(&mut self, stat: usize) -> Result<String, String> {
        if !self.progress.dog_owned {
            return Err("Önce kamptaki eğitmenden bir köpek al".into());
        }
        if !self.near_merchant_wide() {
            return Err("Geliştirme kamptaki köpek eğitmeninde yapılır".into());
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
