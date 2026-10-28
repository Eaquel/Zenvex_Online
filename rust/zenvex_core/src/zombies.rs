//! Zombi yapay zekası, türlere özel yetenekler, doğma ve boss.
#[allow(unused_imports)]
use crate::Hyp;
use crate::data::{ZKind, ZOMBIES};
use crate::game::*;
use crate::world::{self, Zone, TILE};
use std::f32::consts::TAU;

const ACTIVE_R: f32 = 1700.0;
/// V18: her bölgede aynı anda en çok bu kadar zombi (eskiden 26; çok kalabalıktı)
const MAX_ACTIVE: usize = 14;

/// Bölge sınırı: yaratık doğduğu yerden bu kadar uzaklaşınca takibi bırakıp geri döner.
const LEASH: f32 = 15.0 * crate::data::PX_PER_M;
/// Denge: yaratıklar oyuncu hızıyla orantılı yavaşladı (%20), canları ve vuruşları güçlendi.
pub const ZOMBIE_PACE: f32 = 0.8;
pub const ZOMBIE_HP_MULT: f32 = 1.45;
pub const ZOMBIE_DMG_MULT: f32 = 1.2;

impl Game {
    pub fn make_zombie(&mut self, kind: ZKind, level: u32, x: f32, y: f32) -> Zombie {
        let def = &ZOMBIES[kind as usize];
        let hp = (def.hp + def.hp_lv * level as f32) * if kind == ZKind::Titan { 1.0 } else { ZOMBIE_HP_MULT };
        let id = self.next_zid;
        self.next_zid = self.next_zid.wrapping_add(1);
        Zombie {
            id,
            snap_x: x,
            snap_y: y,
            snap_vx: 0.0,
            snap_vy: 0.0,
            snap_at: 0.0,
            kind,
            level,
            x,
            y,
            dir: self.rng.below(8) as u8,
            state: ZState::Wander,
            anim: Z_IDLE,
            anim_t: self.rng.range(0.0, 1.0),
            hp,
            max_hp: hp,
            tx: x,
            ty: y,
            timer: self.rng.range(0.5, 3.0),
            attack_cd: 0.0,
            hit_done: false,
            flash_t: 0.0,
            bar_t: 0.0,
            kx: 0.0,
            ky: 0.0,
            alpha: 1.0,
            special_cd: self.rng.range(2.0, 6.0),
            puddle_t: 0.0,
            decoy: false,
            cloned: false,
            summoned: false,
            enraged: false,
            boss: kind == ZKind::Titan,
            home_x: x,
            home_y: y,
            ret: false,
            dog_aggro: 0.0,
            species: 0,
        }
    }

    pub fn spawn_zombie_at(&mut self, kind: ZKind, level: u32, x: f32, y: f32) {
        let z = self.make_zombie(kind, level, x, y);
        self.zombies.push(z);
    }

    /// V13 bölge yaratığı: temel türün zombisi, türün can çarpanı ve görseli
    pub fn make_species(&mut self, s: u8, level: u32, x: f32, y: f32) -> Zombie {
        let sp = crate::data::species(s).expect("tür");
        let mut z = self.make_zombie(sp.base, level, x, y);
        z.species = s;
        z.hp *= sp.hp;
        z.max_hp = z.hp;
        z
    }

    /// bölgenin yaratık türü: zone tablosunun seçtiği davranışa uyan bölge türü her zaman onun yerine geçer (aynı rolde
    /// iki ayrı görünüş olmasın), yoksa yürüyen/koşan zombi %40 bölgenin rastgele bir türü olur
    fn pick_species(&mut self, x: f32, y: f32, kind: ZKind) -> u8 {
        let area = crate::areas::AREAS[crate::areas::area_at(x, y)].id;
        let list: Vec<u8> = crate::data::SPECIES
            .iter()
            .enumerate()
            .filter(|(_, s)| s.area == area && crate::data::species_active(s))
            .map(|(i, _)| i as u8 + 1)
            .collect();
        if list.is_empty() {
            return 0;
        }
        let same: Vec<u8> = list.iter().copied().filter(|&s| crate::data::species(s).unwrap().base == kind).collect();
        if !same.is_empty() {
            return same[self.rng.below(same.len() as u32) as usize];
        }
        if matches!(kind, ZKind::Walker | ZKind::Runner) && self.rng.chance(0.4) {
            return list[self.rng.below(list.len() as u32) as usize];
        }
        0
    }

    pub fn update_spawning(&mut self, dt: f32) {
        if self.net == 2 {
            return; // istemci: zombileri ev sahibi doğurur
        }
        // ev sahibi: doğma merkezi rastgele bir oyuncu
        let (px, py) = if self.net == 1 && !self.remotes.is_empty() {
            let k = self.rng.below(self.remotes.len() as u32 + 1) as usize;
            if k == 0 {
                (self.player.x, self.player.y)
            } else {
                (self.remotes[k - 1].x, self.remotes[k - 1].y)
            }
        } else {
            (self.player.x, self.player.y)
        };
        // uzaktakileri kaldır
        self.zombies
            .retain(|z| z.boss || (z.x - px).hyp(z.y - py) < ACTIVE_R + 600.0);
        // V12.1 Titan: her 15 dakikada rastgele bir bölgede (areas.rs)
        self.boss_t -= dt;
        self.update_titan(px, py);
        self.spawn_t -= dt;
        if self.spawn_t > 0.0 {
            return;
        }
        self.spawn_t = 1.5;
        let alive = self
            .zombies
            .iter()
            .filter(|z| z.state != ZState::Dead && !z.boss)
            .count();
        let night = self.is_night();
        let cap = if night { MAX_ACTIVE + 8 } else { MAX_ACTIVE };
        if alive >= cap {
            return;
        }
        for _ in 0..12 {
            let a = self.rng.range(0.0, TAU);
            let r = self.rng.range(950.0, ACTIVE_R);
            let x = px + a.cos() * r;
            let y = py + a.sin() * r;
            if x < TILE * 3.0
                || y < TILE * 3.0
                || x > world::MAP_PX - TILE * 3.0
                || y > world::MAP_PX - TILE * 3.0
            {
                continue;
            }
            // V12: yalnız merkezdeki oyuncunun bölgesinde (sınırın ötesi başka harita)
            if crate::areas::area_at(x, y) != crate::areas::area_at(px, py) {
                continue;
            }
            let zone = self.world.zone(x, y);
            let table = zone.spawns();
            if table.is_empty() || self.world.blocked(x, y, 16.0, &mut self.scratch) {
                continue;
            }
            let total: u32 = table
                .iter()
                .map(|(k, w)| {
                    if night && *k == ZKind::Dark {
                        w * 2
                    } else {
                        *w
                    }
                })
                .sum();
            let mut pick = self.rng.below(total.max(1));
            let mut kind = table[0].0;
            for (k, w) in table {
                let w = if night && *k == ZKind::Dark {
                    w * 2
                } else {
                    *w
                };
                if pick < w {
                    kind = *k;
                    break;
                }
                pick -= w;
            }
            let (lo, hi) = zone.level_range();
            let level = lo + self.rng.below(hi - lo + 1);
            let s = self.pick_species(x, y, kind);
            if s > 0 {
                // sürü hayvanları (kurt, kor tazısı) 2-3'lü gelir
                let pack = if crate::data::species(s).unwrap().base == ZKind::Dog { 2 + self.rng.below(2) } else { 1 };
                for k in 0..pack {
                    let oy = self.rng.range(-30.0, 30.0);
                    let z = self.make_species(s, level, x + (k as f32 - 0.5) * 44.0, y + oy);
                    self.zombies.push(z);
                }
                return;
            }
            if kind == ZKind::Dog {
                let n = 2 + self.rng.below(3);
                for k in 0..n {
                    let ox = (k as f32 - 1.0) * 40.0;
                    let rv = self.rng.range(-30.0, 30.0);
                    self.spawn_zombie_at(ZKind::Dog, level, x + ox, y + rv);
                }
            } else {
                self.spawn_zombie_at(kind, level, x, y);
            }
            return;
        }
    }

    fn zombie_blocked_by_safe(&self, x: f32, y: f32) -> bool {
        let z = self.world.zone(x, y);
        z.safe() || z == Zone::Wall
    }

    pub fn update_zombies(&mut self, dt: f32) {
        if self.net == 2 {
            // istemci: son anlık görüntüden hızla ileri tahmin (en çok 0.35 sn), tahmin edilen noktaya yumuşak takip.
            // Görüntüler 5 Hz gelir; yalnız hedefe yaklaşmak zombiyi her görüntüde durdurup sıçratıyordu.
            let k = 1.0 - (-14.0 * dt).exp();
            let now = self.time;
            for z in self.zombies.iter_mut() {
                let ahead = (now - z.snap_at).clamp(0.0, 0.35);
                let (tx, ty) = (z.snap_x + z.snap_vx * ahead, z.snap_y + z.snap_vy * ahead);
                if (tx - z.x).hyp(ty - z.y) > 260.0 {
                    z.x = tx;
                    z.y = ty;
                }
                z.x += (tx - z.x) * k;
                z.y += (ty - z.y) * k;
                z.anim_t += dt;
                z.flash_t = (z.flash_t - dt).max(0.0);
                z.bar_t = (z.bar_t - dt).max(0.0);
            }
            return;
        }
        let (lpx, lpy, lpdead) = (self.player.x, self.player.y, self.player.dead || self.player.invis);
        let l_in_vehicle = self.player.vehicle.is_some() || self.passenger;
        let lp_safe = self.world.zone(lpx, lpy).safe();
        let targets: Vec<(f32, f32, bool, bool, bool, u8, u8)> = if self.net == 1 {
            self.remotes
                .iter()
                .map(|r| (r.x, r.y, false, self.world.zone(r.x, r.y).safe(), r.vehicle >= 0.0, r.dir, r.anim))
                .collect()
        } else {
            Vec::new()
        };
        // köpek de hedef olabilir (araçtayken içeride)
        let dog_t = self
            .dog
            .as_ref()
            .filter(|d| d.dead_t <= 0.0 && !l_in_vehicle)
            .map(|d| (d.x, d.y, self.world.zone(d.x, d.y).safe()));
        let mut dog_damage = 0.0f32;
        let mut remote_hits: Vec<(usize, f32, bool)> = Vec::new();
        let mut player_damage = 0.0f32;
        let mut blast_damage = 0.0f32;
        let n = self.zombies.len();
        // birbirini itme (ızgarayla: komşu hücreler; eskiden her çift karşılaştırılıyordu, 10 bin yaratıkta kare başına 50 milyon)
        self.separate_zombies();
        let mut spawn_puddles: Vec<(f32, f32)> = Vec::new();
        let mut summons: Vec<(f32, f32)> = Vec::new();
        let mut shockwaves: Vec<(f32, f32)> = Vec::new();
        let mut slams: Vec<(f32, f32, f32, f32)> = Vec::new();
        for zi in 0..n {
            let mut z = self.zombies[zi].clone();
            let def = &ZOMBIES[z.kind as usize];
            let (sp_speed, sp_dmg) = crate::data::species(z.species).map_or((1.0, 1.0), |s| (s.speed, s.dmg));
            z.anim_t += dt;
            z.flash_t = (z.flash_t - dt).max(0.0);
            z.bar_t = (z.bar_t - dt).max(0.0);
            z.attack_cd = (z.attack_cd - dt).max(0.0);
            z.special_cd -= dt;
            z.dog_aggro = (z.dog_aggro - dt).max(0.0);
            // geri itilme
            if z.kx != 0.0 || z.ky != 0.0 {
                let (mut x, mut y) = (z.x + z.kx * dt, z.y + z.ky * dt);
                self.world
                    .resolve(&mut x, &mut y, def.radius, false, &mut self.scratch);
                z.x = x;
                z.y = y;
                let k = (1.0 - 8.0 * dt).max(0.0);
                z.kx *= k;
                z.ky *= k;
                if z.kx.abs() + z.ky.abs() < 2.0 {
                    z.kx = 0.0;
                    z.ky = 0.0;
                }
            }
            // hedef: en yakın oyuncu (ev sahibinde uzak oyuncular dahil)
            let mut ti: i32 = -1;
            let (mut px, mut py, mut pdead, mut p_safe, mut in_vehicle) = (lpx, lpy, lpdead, lp_safe, l_in_vehicle);
            let mut tdir = self.player.dir;
            let mut tanim = self.player.anim;
            let mut best = if lpdead || lp_safe { f32::MAX } else { (lpx - z.x).hyp(lpy - z.y) };
            for (k, t) in targets.iter().enumerate() {
                if t.3 {
                    continue;
                }
                let d = (t.0 - z.x).hyp(t.1 - z.y);
                if d < best {
                    best = d;
                    ti = k as i32;
                    px = t.0;
                    py = t.1;
                    pdead = t.2;
                    p_safe = t.3;
                    in_vehicle = t.4;
                    tdir = t.5;
                    tanim = t.6;
                }
            }
            // köpek: yakınsa ya da zombiyi ısırdıysa önce ona saldırır (ti = -2)
            if let Some((qx, qy, qsafe)) = dog_t {
                let d = (qx - z.x).hyp(qy - z.y);
                let w = if z.dog_aggro > 0.0 { d * 0.35 } else { d };
                if !qsafe && w < best && (d < 300.0 || z.dog_aggro > 0.0) {
                    best = w;
                    ti = -2;
                    px = qx;
                    py = qy;
                    pdead = false;
                    p_safe = false;
                    in_vehicle = false;
                    tdir = 0;
                    tanim = 0;
                }
            }
            let (dx, dy) = (px - z.x, py - z.y);
            let dist = dx.hyp(dy);
            let from_home = (z.x - z.home_x).hyp(z.y - z.home_y);
            // tür efektleri (sürekli)
            match z.kind {
                ZKind::Dark => {
                    if z.state != ZState::Dead {
                        let flick = 0.55 + 0.35 * ((self.time * 7.0 + zi as f32).sin() * 0.5 + 0.5);
                        z.alpha = if self.is_night() { flick * 0.85 } else { flick };
                    }
                }
                ZKind::Mystery => {
                    if z.state != ZState::Dead {
                        let target = if dist < 170.0 || z.state == ZState::Flee || z.bar_t > 0.0 {
                            1.0
                        } else {
                            0.12
                        };
                        z.alpha += (target - z.alpha) * (1.0 - (-4.0 * dt).exp());
                    }
                }
                ZKind::Toxic
                    if z.state != ZState::Dead && z.anim == Z_WALK => {
                        z.puddle_t -= dt;
                        if z.puddle_t <= 0.0 {
                            z.puddle_t = 2.2;
                            spawn_puddles.push((z.x, z.y));
                        }
                    }
                _ => {}
            }
            let sees = !pdead
                && !p_safe
                && !z.ret
                && (dist < 300.0
                    || (dist < 460.0 && (tanim == A_RUN || (ti < 0 && self.player.atk_cd > 0.0)))
                    || z.boss && dist < 900.0);
            match z.state {
                ZState::Dead => {
                    z.timer -= dt;
                    if z.timer < 1.0 {
                        z.alpha = z.timer.max(0.0);
                    }
                }
                ZState::Hurt => {
                    z.timer -= dt;
                    if z.timer <= 0.0 {
                        z.state = ZState::Chase;
                        z.anim = Z_WALK;
                        z.anim_t = 0.0;
                    }
                }
                ZState::Flee => {
                    z.timer -= dt;
                    if dist > 1.0 {
                        self.move_zombie(&mut z, -dx / dist, -dy / dist, dt, def.chase * 1.2 * sp_speed);
                    }
                    if z.timer <= 0.0 || dist > 900.0 {
                        z.state = ZState::Wander;
                    }
                }
                ZState::Wander if z.ret => {
                    // bölgesine dönüyor: canı dolar, kimseyi kovalamaz
                    z.hp = (z.hp + z.max_hp * 0.25 * dt).min(z.max_hp);
                    let (mx, my) = (z.home_x - z.x, z.home_y - z.y);
                    let md = mx.hyp(my);
                    if md < 50.0 {
                        z.ret = false;
                        z.anim = Z_IDLE;
                        z.timer = 1.0;
                    } else {
                        self.move_zombie(&mut z, mx / md, my / md, dt, def.chase * sp_speed);
                        if z.anim != Z_WALK {
                            z.anim = Z_WALK;
                            z.anim_t = 0.0;
                        }
                    }
                }
                ZState::Wander => {
                    z.timer -= dt;
                    if sees {
                        z.state = ZState::Notice;
                        z.timer = if matches!(z.kind, ZKind::Runner | ZKind::Dog) {
                            0.15
                        } else {
                            0.4
                        };
                        z.anim = Z_IDLE;
                        z.dir = dir_from_vec(dx, dy);
                        self.events.push(Event::Groan {
                            x: z.x,
                            y: z.y,
                            z: z.kind as u8,
                        });
                    } else if z.timer <= 0.0 {
                        if z.anim == Z_WALK {
                            z.anim = Z_IDLE;
                            z.anim_t = 0.0;
                            z.timer = self.rng.range(1.0, 3.0);
                        } else {
                            let a = self.rng.range(0.0, TAU);
                            let r = self.rng.range(60.0, 220.0);
                            // bölgesinin içinde dolaşır
                            let (cx, cy) = if from_home > m(5.0) { (z.home_x, z.home_y) } else { (z.x, z.y) };
                            z.tx = cx + a.cos() * r;
                            z.ty = cy + a.sin() * r;
                            if z.boss {
                                z.tx = world::BOSS.0 + a.cos() * r;
                                z.ty = world::BOSS.1 + a.sin() * r;
                            }
                            z.anim = Z_WALK;
                            z.anim_t = 0.0;
                            z.timer = self.rng.range(2.0, 5.0);
                        }
                    }
                    if z.anim == Z_WALK {
                        let (mx, my) = (z.tx - z.x, z.ty - z.y);
                        let md = mx.hyp(my);
                        if md < 6.0 {
                            z.timer = 0.0;
                        } else {
                            self.move_zombie(&mut z, mx / md, my / md, dt, def.speed * sp_speed);
                        }
                    }
                }
                ZState::Notice => {
                    z.timer -= dt;
                    z.dir = dir_from_vec(dx, dy);
                    if z.timer <= 0.0 {
                        z.state = ZState::Chase;
                        z.anim = Z_WALK;
                        z.anim_t = 0.0;
                    }
                }
                ZState::Chase => {
                    let reach = def.radius + if z.boss { 70.0 } else { 26.0 };
                    if p_safe && ti == -1 && !z.boss && dist < 650.0 && self.rng.chance(0.4) {
                        // oyuncu güvenli bölgeye kaçtı: tanrılar peşindeki yaratığı çarpabilir
                        z.hp = 0.0;
                        z.state = ZState::Dead;
                        z.anim = Z_DEATH;
                        z.anim_t = 0.0;
                        z.timer = 3.0;
                        self.events.push(Event::Lightning { x: z.x, y: z.y });
                    } else if pdead || p_safe || dist > 1100.0 || (!z.boss && from_home > LEASH) {
                        // takibi bırakır ve bölgesine döner
                        z.state = ZState::Wander;
                        z.ret = !z.boss && from_home > m(4.0);
                        z.anim = Z_IDLE;
                        z.timer = 1.0;
                    } else if z.kind == ZKind::Mystery && z.hp < z.max_hp * 0.4 && !z.decoy {
                        z.state = ZState::Flee;
                        z.timer = 4.0;
                    } else if z.kind == ZKind::Dark
                        && z.special_cd <= 0.0
                        && dist > 180.0
                        && dist < 520.0
                    {
                        // ışınlan: oyuncunun arkasına
                        z.special_cd = self.rng.range(5.0, 8.0);
                        let (bx, by) = dir_vec(tdir);
                        let nx = px - bx * 70.0;
                        let ny = py - by * 70.0;
                        self.events.push(Event::Teleport { x: z.x, y: z.y });
                        if !self.world.blocked(nx, ny, def.radius, &mut self.scratch)
                            && !self.zombie_blocked_by_safe(nx, ny)
                        {
                            z.x = nx;
                            z.y = ny;
                        }
                        self.events.push(Event::Teleport { x: z.x, y: z.y });
                    } else if z.boss && z.special_cd <= 0.0 && dist < 700.0 {
                        // V13: yere vurma (deprem dalgası) ya da yakındaysa ateş püskürtme; sırayla, rastgele
                        z.special_cd = if z.enraged { 4.0 } else { 6.5 };
                        z.state = ZState::Attack;
                        z.anim = if dist < 480.0 && self.rng.chance(0.5) { Z_BREATH } else { Z_SLAM };
                        z.anim_t = 0.0;
                        z.hit_done = false;
                        z.dir = dir_from_vec(dx, dy);
                    } else if dist < reach && z.attack_cd <= 0.0 && !in_vehicle {
                        z.state = ZState::Attack;
                        z.anim = Z_ATTACK;
                        z.anim_t = 0.0;
                        z.hit_done = false;
                        z.dir = dir_from_vec(dx, dy);
                    } else if dist >= reach * 0.8 {
                        let mut sp = def.chase * sp_speed * if z.enraged { 1.4 } else { 1.0 };
                        if z.kind == ZKind::Runner && dist < 260.0 {
                            sp *= 1.35; // atılma
                        }
                        self.move_zombie(&mut z, dx / dist, dy / dist, dt, sp);
                        if z.anim != Z_WALK {
                            z.anim = Z_WALK;
                            z.anim_t = 0.0;
                        }
                    } else {
                        z.dir = dir_from_vec(dx, dy);
                        z.anim = Z_IDLE;
                    }
                    // boss evreleri
                    if z.boss {
                        if !z.summoned && z.hp < z.max_hp * 0.5 {
                            z.summoned = true;
                            summons.push((z.x, z.y));
                        }
                        if !z.enraged && z.hp < z.max_hp * 0.3 {
                            z.enraged = true;
                            self.msg("Titan öfkelendi!");
                        }
                    }
                }
                ZState::Attack => {
                    let (hit_at, end_at) = match z.anim {
                        Z_SLAM => (0.62, 1.05),
                        Z_BREATH => (0.5, 1.2),
                        _ => (0.3, 0.6),
                    };
                    if !z.hit_done && z.anim_t >= hit_at {
                        z.hit_done = true;
                        let (fx, fy) = dir_vec(z.dir);
                        let facing = (dx * fx + dy * fy) / dist.max(1e-3);
                        let lvl_dmg = (def.dmg + def.dmg_lv * z.level as f32) * ZOMBIE_DMG_MULT * sp_dmg;
                        match z.kind {
                            ZKind::Tank => {
                                slams.push((z.x + fx * 40.0, z.y + fy * 40.0, m(2.0), lvl_dmg));
                            }
                            ZKind::Titan if z.anim == Z_SLAM => {
                                shockwaves.push((z.x, z.y));
                                self.events.push(Event::Magma { x: z.x, y: z.y, a: 1, tx: px, ty: py });
                            }
                            ZKind::Titan if z.anim == Z_BREATH => {
                                // ateş konisi: 460 px, ±26°; bakılan yöne (hedef) doğru
                                let (cx, cy) = (dx / dist.max(1e-3), dy / dist.max(1e-3));
                                let cone = |tx: f32, ty: f32| {
                                    let (ex, ey) = (tx - z.x, ty - z.y);
                                    let l = ex.hyp(ey);
                                    l < 460.0 && (ex * cx + ey * cy) / l.max(1e-3) > 0.9
                                };
                                let burn = lvl_dmg * 1.5;
                                if cone(self.player.x, self.player.y) && !in_vehicle {
                                    player_damage += burn;
                                }
                                for (k, t) in targets.iter().enumerate() {
                                    if cone(t.0, t.1) {
                                        remote_hits.push((k, burn, false));
                                    }
                                }
                                self.events.push(Event::Magma { x: z.x, y: z.y, a: 2, tx: z.x + cx * 400.0, ty: z.y + cy * 400.0 });
                            }
                            ZKind::Titan => {
                                self.events.push(Event::Magma { x: z.x, y: z.y, a: 0, tx: z.x + fx * 90.0, ty: z.y + fy * 90.0 });
                                if dist < def.radius + 110.0 {
                                    if ti == -2 {
                                        dog_damage += lvl_dmg;
                                    } else if ti < 0 {
                                        player_damage += lvl_dmg;
                                    } else {
                                        remote_hits.push((ti as usize, lvl_dmg, false));
                                    }
                                }
                            }
                            _ => {
                                if dist < def.radius + 40.0 && facing > 0.2 && !in_vehicle {
                                    let poison = z.kind == ZKind::Toxic;
                                    if ti == -2 {
                                        dog_damage += lvl_dmg;
                                    } else if ti < 0 {
                                        player_damage += lvl_dmg;
                                        if poison {
                                            self.player.poison_t = self.player.poison_t.max(4.0);
                                        }
                                    } else {
                                        remote_hits.push((ti as usize, lvl_dmg, poison));
                                    }
                                    if z.kind == ZKind::Dark {
                                        z.hp = (z.hp + lvl_dmg * 0.5).min(z.max_hp);
                                    }
                                }
                            }
                        }
                    }
                    if z.anim_t >= end_at {
                        z.state = ZState::Chase;
                        z.anim = Z_WALK;
                        z.anim_t = 0.0;
                        z.attack_cd = if z.kind == ZKind::Tank { 3.5 } else { 1.0 };
                    }
                }
            }
            self.zombies[zi] = z;
        }
        self.zombies
            .retain(|z| !(z.state == ZState::Dead && z.timer <= 0.0));
        for (x, y) in spawn_puddles {
            self.areas.push(Area {
                kind: AreaKind::ToxicPuddle,
                x,
                y,
                r: m(0.9),
                life: 6.0,
                max_life: 6.0,
                dps: 4.0,
                hit_done: false,
            });
        }
        for (x, y) in shockwaves {
            self.areas.push(Area {
                kind: AreaKind::Shockwave,
                x,
                y,
                r: m(7.0),
                life: 1.2,
                max_life: 1.2,
                dps: 32.0,
                hit_done: false,
            });
            self.events.push(Event::Slam { x, y, r: m(7.0) });
        }
        for (x, y, r, d) in slams {
            self.areas.push(Area {
                kind: AreaKind::Explosion,
                x,
                y,
                r,
                life: 0.4,
                max_life: 0.4,
                dps: 0.0,
                hit_done: true,
            });
            self.events.push(Event::Slam { x, y, r });
            for (k, t) in targets.iter().enumerate() {
                if (t.0 - x).hyp(t.1 - y) < r {
                    remote_hits.push((k, d, false));
                }
            }
            if dog_t.is_some_and(|(qx, qy, _)| (qx - x).hyp(qy - y) < r) {
                dog_damage += d;
            }
            if (self.player.x - x).hyp(self.player.y - y) < r {
                blast_damage += d;
                let (dx, dy) = (self.player.x - x, self.player.y - y);
                let l = dx.hyp(dy).max(1.0);
                let (mut nx, mut ny) =
                    (self.player.x + dx / l * 60.0, self.player.y + dy / l * 60.0);
                self.world.resolve(
                    &mut nx,
                    &mut ny,
                    14.0,
                    self.progress.level >= crate::progress::CITY_LEVEL,
                    &mut self.scratch,
                );
                crate::areas::clamp(self.player.x, self.player.y, &mut nx, &mut ny);
                self.player.x = nx;
                self.player.y = ny;
            }
        }
        for (x, y) in summons {
            for k in 0..4 {
                let a = k as f32 * TAU / 4.0;
                self.spawn_zombie_at(ZKind::Walker, 40, x + a.cos() * 160.0, y + a.sin() * 160.0);
            }
            self.msg("Titan yardım çağırdı!");
        }
        if player_damage > 0.0 {
            self.damage_player(player_damage);
        }
        if dog_damage > 0.0 {
            self.hurt_dog(dog_damage);
        }
        if blast_damage > 0.0 && !self.player_invulnerable() {
            self.damage_player_blast(blast_damage);
        }
        for (i, v, poison) in remote_hits {
            self.events.push(Event::RemoteDamage { i, v: v.round() as i32, poison });
        }
    }

    /// İki yaratığın üst üste binmesini çözer (yarı yarıya iter)
    #[inline]
    fn push_pair(zs: &mut [Zombie], i: usize, j: usize) {
        let ri = ZOMBIES[zs[i].kind as usize].radius;
        let rj = ZOMBIES[zs[j].kind as usize].radius;
        let (dx, dy) = (zs[j].x - zs[i].x, zs[j].y - zs[i].y);
        let min = ri + rj;
        let d2 = dx * dx + dy * dy;
        if d2 < min * min && d2 > 1e-4 {
            let d = d2.sqrt();
            let push = (min - d) * 0.5;
            let (ux, uy) = (dx / d * push, dy / d * push);
            zs[i].x -= ux;
            zs[i].y -= uy;
            zs[j].x += ux;
            zs[j].y += uy;
        }
    }

    /// Yaratıkların birbirini itmesi, O(n): 64 piksellik hücre ızgarası (sayma sıralaması), her yaratık yalnız kendi ve
    /// komşu 8 hücredekilerle karşılaştırılır. İri yaratıklar (yarıçap > 32, boss'lar; birkaç tane) hepsiyle doğrudan.
    fn separate_zombies(&mut self) {
        const C: f32 = 64.0;
        let n = self.zombies.len();
        if n < 2 {
            return;
        }
        let live = |z: &Zombie| z.state != ZState::Dead;
        let big = |z: &Zombie| ZOMBIES[z.kind as usize].radius > C * 0.5;
        let (mut x0, mut y0, mut x1, mut y1) = (f32::MAX, f32::MAX, f32::MIN, f32::MIN);
        for z in self.zombies.iter().filter(|z| live(z) && !big(z)) {
            x0 = x0.min(z.x);
            y0 = y0.min(z.y);
            x1 = x1.max(z.x);
            y1 = y1.max(z.y);
        }
        if x0 <= x1 {
            let gw = (((x1 - x0) / C) as usize + 1).min(2048);
            let gh = (((y1 - y0) / C) as usize + 1).min(2048);
            let cells = gw * gh;
            let cell_of = |x: f32, y: f32| -> usize {
                let cx = (((x - x0) / C) as usize).min(gw - 1);
                let cy = (((y - y0) / C) as usize).min(gh - 1);
                cy * gw + cx
            };
            self.sep_start.clear();
            self.sep_start.resize(cells + 1, 0);
            self.sep_cell.clear();
            self.sep_cell.resize(n, u32::MAX);
            for (i, z) in self.zombies.iter().enumerate() {
                if live(z) && !big(z) {
                    let c = cell_of(z.x, z.y);
                    self.sep_cell[i] = c as u32;
                    self.sep_start[c + 1] += 1;
                }
            }
            for c in 0..cells {
                self.sep_start[c + 1] += self.sep_start[c];
            }
            let m = self.sep_start[cells] as usize;
            self.sep_order.clear();
            self.sep_order.resize(m, 0);
            let mut fill = self.sep_start.clone();
            for (i, &c) in self.sep_cell.iter().enumerate() {
                if c != u32::MAX {
                    let k = &mut fill[c as usize];
                    self.sep_order[*k as usize] = i as u32;
                    *k += 1;
                }
            }
            for i in 0..n {
                let c = self.sep_cell[i];
                if c == u32::MAX {
                    continue;
                }
                let (cx, cy) = ((c as usize % gw) as isize, (c as usize / gw) as isize);
                for oy in -1..=1isize {
                    for ox in -1..=1isize {
                        let (nx, ny) = (cx + ox, cy + oy);
                        if nx < 0 || ny < 0 || nx >= gw as isize || ny >= gh as isize {
                            continue;
                        }
                        let nc = ny as usize * gw + nx as usize;
                        for k in self.sep_start[nc]..self.sep_start[nc + 1] {
                            let j = self.sep_order[k as usize] as usize;
                            if j > i {
                                Self::push_pair(&mut self.zombies, i, j);
                            }
                        }
                    }
                }
            }
        }
        // iri yaratıklar: hepsiyle
        for i in 0..n {
            if !live(&self.zombies[i]) || !big(&self.zombies[i]) {
                continue;
            }
            for j in 0..n {
                if j != i && live(&self.zombies[j]) && (j > i || !big(&self.zombies[j])) {
                    Self::push_pair(&mut self.zombies, i, j);
                }
            }
        }
    }

    fn move_zombie(&mut self, z: &mut Zombie, nx: f32, ny: f32, dt: f32, speed_m: f32) {
        let sp = m(speed_m) * ZOMBIE_PACE;
        let r = ZOMBIES[z.kind as usize].radius;
        let (mut x, mut y) = (z.x + nx * sp * dt, z.y + ny * sp * dt);
        self.world
            .resolve(&mut x, &mut y, r, false, &mut self.scratch);
        if (x - z.x).hyp(y - z.y) < sp * dt * 0.3 {
            let (sx, sy) = (-ny, nx);
            x = z.x + sx * sp * dt;
            y = z.y + sy * sp * dt;
            self.world
                .resolve(&mut x, &mut y, r, false, &mut self.scratch);
        }
        crate::areas::clamp(z.x, z.y, &mut x, &mut y);
        // güvenli bölgeye giremez
        if self.zombie_blocked_by_safe(x, y) && !z.boss {
            return;
        }
        z.x = x;
        z.y = y;
        z.dir = dir_from_vec(nx, ny);
    }
}

#[cfg(test)]
mod species_tests {
    #[allow(unused_imports)]
    use crate::Hyp;
    use crate::data::{species, SPECIES};
    use crate::game::Game;
    use crate::Progress;

    /// V13: her yaratık türü bir bölgeye ait; ağ görüntüsünden türü ve canı korunarak geri kurulur
    #[test]
    fn species_spawn_and_net_roundtrip() {
        for s in SPECIES {
            assert!(crate::areas::AREAS.iter().any(|a| a.id == s.area), "{}", s.id);
        }
        // V17: her bölgede en çok iki imza yaratık doğar
        for a in crate::areas::AREAS {
            let n = SPECIES.iter().filter(|s| s.area == a.id && crate::data::species_active(s)).count();
            assert!(n <= 2, "{}: {n} tür", a.id);
        }
        let mut g = Game::new(7, Progress::default(), 0);
        let z = g.make_species(2, 5, g.player.x + 300.0, g.player.y);
        assert_eq!(species(z.species).unwrap().id, "wolf");
        let hp = z.max_hp;
        g.zombies.clear();
        g.zombies.push(z);
        let mut buf = vec![0.0f32; 64];
        let n = g.net_snapshot(&mut buf);
        assert_eq!(n, 1);
        let mut c = Game::new(7, Progress::default(), 0);
        c.zombies.clear();
        c.net = 2;
        c.net_apply(&buf[..crate::net::NET_STRIDE]);
        assert_eq!(c.zombies.len(), 1);
        assert_eq!(c.zombies[0].species, 2);
        assert!((c.zombies[0].max_hp - hp).abs() < 0.5);
    }
}

#[cfg(test)]
mod perf {
    #[allow(unused_imports)]
    use crate::Hyp;
    use crate::data::ZKind;
    use crate::game::{Game, Input};
    use crate::Progress;

    /// Kalabalık kıyası: cargo test -p zenvex_core --release perf_crowd -- --ignored --nocapture
    #[test]
    #[ignore]
    fn perf_crowd() {
        let only: Option<usize> = std::env::var("PERF_N").ok().and_then(|v| v.parse().ok());
        for n in [100usize, 1000, 3000, 10000].into_iter().filter(|n| only.is_none_or(|o| o == *n)) {
            let mut p = Progress::default();
            p.level = 30;
            let mut g = Game::new(5, p, 0);
            g.cheat("god").ok();
            g.cheat("tp deep").ok();
            let (px, py) = (g.player.x, g.player.y);
            for k in 0..n {
                let a = k as f32 * 2.399;
                let r = 120.0 + (k as f32).sqrt() * 14.0;
                g.spawn_zombie_at(ZKind::Walker, 20, px + a.cos() * r, py + a.sin() * r);
            }
            let mut out = vec![0f32; 200_000];
            let inp = Input { move_x: 0.3, move_y: 0.0, atk_x: 0.0, atk_y: 0.0, attacking: false };
            for _ in 0..20 {
                g.step(1.0 / 60.0, inp);
            }
            let frames = 60;
            let mut drawn = 0;
            let (mut ts, mut td) = (0.0f64, 0.0f64);
            for _ in 0..frames {
                let t0 = std::time::Instant::now();
                g.step(1.0 / 60.0, inp);
                let t1 = std::time::Instant::now();
                drawn = g.draw_list(g.player.x - 1200.0, g.player.y - 600.0, 2400.0, 1200.0, &mut out, 1.0);
                ts += (t1 - t0).as_secs_f64();
                td += t1.elapsed().as_secs_f64();
            }
            let f = 1000.0 / frames as f64;
            println!("PERF n={n} zombies={} çizim={drawn}: adım {:.2} ms, çizim listesi {:.2} ms", g.zombies.len(), ts * f, td * f);
        }
    }
}
