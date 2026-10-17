//! Oyun döngüsü: oyuncu, zombiler, yumruk savaşı, ağaç kesme, olaylar.
use crate::progress::Progress;
use crate::rng::Rng;
use crate::world::{spawn_point, Kind, World, MAP_PX, STUMP_SPRITE, TILE};
use serde::Serialize;
use std::f32::consts::{FRAC_PI_4, TAU};

// ---- animasyon kimlikleri (Kotlin ile ortak)
pub const P_IDLE: u8 = 0;
pub const P_WALK: u8 = 1;
pub const P_RUN: u8 = 2;
pub const P_PUNCH: u8 = 3;
pub const P_HURT: u8 = 4;
pub const P_DEATH: u8 = 5;

pub const Z_IDLE: u8 = 0;
pub const Z_WALK: u8 = 1;
pub const Z_ATTACK: u8 = 2;
pub const Z_HURT: u8 = 3;
pub const Z_DEATH: u8 = 4;

// ---- çizim listesi türleri
pub const DRAW_STATIC: f32 = 0.0;
pub const DRAW_PLAYER: f32 = 1.0;
pub const DRAW_ZOMBIE: f32 = 2.0;
/// [tür, id, x, y, yön, anim, anim_zamanı, ek1, ek2]
/// statik: id=sprite, yön=indeks, anim=0/1 (kütük), zaman=rüzgâr fazı, ek1=sallanma, ek2=can
/// zombi: id=saydamlık, ek1=beyaz flaş, ek2=can (-1 gizli) · oyuncu: ek1=flaş
pub const STRIDE: usize = 9;

const WALK_SPEED: f32 = 52.0; // px/sn
const RUN_SPEED: f32 = 112.0;
const PUNCH_FPS: f32 = 14.0;
const PUNCH_HALF: f32 = 4.0 / PUNCH_FPS; // bir yumruk (jab veya düz)
const PUNCH_RANGE: f32 = 34.0;
const PUNCH_ARC: f32 = 0.75; // rad (~43°) yarım açı
const MAX_ZOMBIES: usize = 24;

pub fn dir_from_vec(x: f32, y: f32) -> u8 {
    let mut a = x.atan2(y); // 0 = aşağı (güney)
    if a < 0.0 {
        a += TAU;
    }
    ((a / FRAC_PI_4).round() as i32).rem_euclid(8) as u8
}

pub fn dir_vec(d: u8) -> (f32, f32) {
    let a = d as f32 * FRAC_PI_4;
    (a.sin(), a.cos())
}

#[derive(Clone, Copy, Debug, Default)]
pub struct Input {
    pub move_x: f32,
    pub move_y: f32,
    pub atk_x: f32,
    pub atk_y: f32,
    pub attacking: bool,
}

#[derive(Serialize, Clone, Debug)]
#[serde(tag = "t")]
pub enum Event {
    #[serde(rename = "dmg")]
    Damage { x: f32, y: f32, v: i32, crit: bool },
    #[serde(rename = "pdmg")]
    PlayerDamage { x: f32, y: f32, v: i32 },
    #[serde(rename = "xp")]
    Xp { x: f32, y: f32, v: u32 },
    #[serde(rename = "wood")]
    Wood { x: f32, y: f32, v: u32 },
    #[serde(rename = "item")]
    Item { x: f32, y: f32, name: &'static str },
    #[serde(rename = "lvl")]
    LevelUp { level: u32, points: u32 },
    #[serde(rename = "hit")]
    Hit { x: f32, y: f32, tree: bool },
    #[serde(rename = "treefall")]
    TreeFall { x: f32, y: f32 },
    #[serde(rename = "kill")]
    Kill { x: f32, y: f32 },
    #[serde(rename = "died")]
    Died,
    #[serde(rename = "lastbreath")]
    LastBreath,
    #[serde(rename = "swing")]
    Swing,
    #[serde(rename = "groan")]
    Groan { x: f32, y: f32 },
}

#[derive(Clone, Debug)]
pub struct Player {
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub anim: u8,
    pub anim_t: f32,
    pub hp: f32,
    pub stamina: f32,
    pub stamina_delay: f32,
    pub punch_active: bool,
    pub punch_hit_done: bool,
    pub hurt_t: f32,
    pub flash_t: f32,
    pub dead: bool,
    pub combat_t: f32,
    pub last_breath_cd: f32,
    pub moving: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ZState {
    Wander,
    Notice,
    Chase,
    Attack,
    Hurt,
    Dead,
}

#[derive(Clone, Debug)]
pub struct Zombie {
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub state: ZState,
    pub anim: u8,
    pub anim_t: f32,
    pub hp: f32,
    pub max_hp: f32,
    pub level: u32,
    pub tx: f32,
    pub ty: f32,
    pub timer: f32,
    pub attack_cd: f32,
    pub hit_done: bool,
    pub flash_t: f32,
    pub bar_t: f32,
    pub kx: f32,
    pub ky: f32,
    pub stun_t: f32,
    pub alpha: f32,
}

impl Zombie {
    fn speed(&self) -> f32 {
        match self.state {
            ZState::Chase => 44.0 + self.level as f32 * 1.2,
            _ => 16.0,
        }
    }
    fn damage(&self) -> f32 {
        6.0 + self.level as f32 * 1.4
    }
    fn xp(&self) -> u32 {
        15 + self.level * 3
    }
}

pub struct Game {
    pub world: World,
    pub progress: Progress,
    pub player: Player,
    pub zombies: Vec<Zombie>,
    pub events: Vec<Event>,
    pub time: f32,
    rng: Rng,
    scratch: Vec<u32>,
    spawn_t: f32,
    draw_tmp: Vec<(f32, [f32; STRIDE])>,
}

impl Game {
    pub fn new(seed: u64, progress: Progress) -> Game {
        let world = World::generate(seed);
        let (x, y) = spawn_point();
        let player = Player {
            x,
            y,
            dir: 0,
            anim: P_IDLE,
            anim_t: 0.0,
            hp: progress.max_hp(),
            stamina: progress.max_stamina(),
            stamina_delay: 0.0,
            punch_active: false,
            punch_hit_done: false,
            hurt_t: 0.0,
            flash_t: 0.0,
            dead: false,
            combat_t: 99.0,
            last_breath_cd: 0.0,
            moving: false,
        };
        let mut g = Game {
            world,
            progress,
            player,
            zombies: Vec::new(),
            events: Vec::new(),
            time: 0.0,
            rng: Rng::new(seed ^ 0x5EED),
            scratch: Vec::new(),
            spawn_t: 0.0,
            draw_tmp: Vec::new(),
        };
        for _ in 0..MAX_ZOMBIES {
            g.spawn_zombie(true);
        }
        g
    }

    /// Dünyayı sıfırlamadan oyuncuyu doğuş noktasında yeniden başlatır.
    pub fn respawn(&mut self) {
        let (x, y) = spawn_point();
        let p = &mut self.player;
        p.x = x;
        p.y = y;
        p.dead = false;
        p.hp = self.progress.max_hp();
        p.stamina = self.progress.max_stamina();
        p.anim = P_IDLE;
        p.anim_t = 0.0;
        p.hurt_t = 0.0;
        // doğuş noktasındaki zombileri uzaklaştır
        let (sx, sy) = (x, y);
        self.zombies.retain(|z| (z.x - sx).hypot(z.y - sy) > 400.0);
    }

    fn zombie_level_at(x: f32, y: f32) -> u32 {
        let (cx, cy) = spawn_point();
        let d_tiles = (x - cx).hypot(y - cy) / TILE;
        (1 + (d_tiles / 6.0) as u32).clamp(1, 12)
    }

    fn spawn_zombie(&mut self, initial: bool) {
        for _ in 0..30 {
            let x = self.rng.range(120.0, MAP_PX - 120.0);
            let y = self.rng.range(120.0, MAP_PX - 120.0);
            let d = (x - self.player.x).hypot(y - self.player.y);
            let min_d = if initial { 300.0 } else { 420.0 };
            if d < min_d || self.world.blocked(x, y, 10.0, &mut self.scratch) {
                continue;
            }
            let level = Self::zombie_level_at(x, y);
            let hp = 40.0 + level as f32 * 8.0;
            self.zombies.push(Zombie {
                x,
                y,
                dir: self.rng.below(8) as u8,
                state: ZState::Wander,
                anim: Z_IDLE,
                anim_t: self.rng.range(0.0, 1.0),
                hp,
                max_hp: hp,
                level,
                tx: x,
                ty: y,
                timer: self.rng.range(0.5, 3.0),
                attack_cd: 0.0,
                hit_done: false,
                flash_t: 0.0,
                bar_t: 0.0,
                kx: 0.0,
                ky: 0.0,
                stun_t: 0.0,
                alpha: 1.0,
            });
            return;
        }
    }

    pub fn step(&mut self, dt: f32, input: Input) {
        let dt = dt.clamp(0.0, 0.05);
        self.time += dt;
        self.progress.play_time += dt as f64;
        self.update_player(dt, input);
        self.update_zombies(dt);
        self.update_statics(dt);
        self.spawn_t -= dt;
        let alive = self
            .zombies
            .iter()
            .filter(|z| z.state != ZState::Dead)
            .count();
        if alive < MAX_ZOMBIES && self.spawn_t <= 0.0 {
            self.spawn_zombie(false);
            self.spawn_t = 2.5;
        }
    }

    fn update_player(&mut self, dt: f32, input: Input) {
        let prog = &self.progress;
        let max_hp = prog.max_hp();
        let max_st = prog.max_stamina();
        let p = &mut self.player;
        p.anim_t += dt;
        p.flash_t = (p.flash_t - dt).max(0.0);
        p.last_breath_cd = (p.last_breath_cd - dt).max(0.0);
        p.combat_t += dt;
        if p.dead {
            return;
        }
        // can yenilenmesi (savaş dışında)
        if p.combat_t > 5.0 && p.hp < max_hp {
            p.hp = (p.hp + 2.0 * prog.regen_mult() * dt).min(max_hp);
        }
        p.hp = p.hp.min(max_hp);
        p.stamina_delay -= dt;
        if p.stamina_delay <= 0.0 {
            p.stamina = (p.stamina + 18.0 * dt).min(max_st);
        }

        if p.hurt_t > 0.0 {
            p.hurt_t -= dt;
            if p.anim != P_HURT {
                p.anim = P_HURT;
                p.anim_t = 0.0;
            }
            return;
        }

        // --- saldırı joysticki: yönü çevir, o yöne yumruk at
        let atk_len = input.atk_x.hypot(input.atk_y);
        let wants_attack = input.attacking && atk_len > 0.2;
        if wants_attack {
            p.dir = dir_from_vec(input.atk_x, input.atk_y);
        }
        if wants_attack && !p.punch_active {
            p.punch_active = true;
            p.punch_hit_done = false;
            if p.anim != P_PUNCH {
                p.anim = P_PUNCH;
                p.anim_t = 0.0;
            }
        }
        let mut swing = false;
        if p.punch_active {
            // yorgunsa yumruk yavaşlar
            let tired = p.stamina < 4.0;
            let speed = if tired { 0.6 } else { 1.0 };
            p.anim_t += dt * (speed - 1.0); // ana artışı ölçekle
            let half_t = p.anim_t % PUNCH_HALF;
            if !p.punch_hit_done && half_t >= 1.5 / PUNCH_FPS {
                p.punch_hit_done = true;
                swing = true;
                p.stamina = (p.stamina - 4.0 * prog.stamina_cost_mult()).max(0.0);
                p.stamina_delay = 0.8;
            }
            if half_t + dt * speed >= PUNCH_HALF - 1e-4 {
                // yumruk bitti: devam mı?
                p.punch_hit_done = false;
                if !wants_attack {
                    p.punch_active = false;
                }
            }
        } else if p.anim == P_PUNCH {
            p.anim = P_IDLE;
            p.anim_t = 0.0;
        }

        // --- hareket
        let mlen = input.move_x.hypot(input.move_y).min(1.0);
        let mut moving = false;
        if mlen > 0.08 {
            let (nx, ny) = (input.move_x / mlen.max(1e-4), input.move_y / mlen.max(1e-4));
            let run = mlen > 0.6 && p.stamina > 1.0 && !p.punch_active;
            let mut speed = if run {
                RUN_SPEED * prog.run_mult()
            } else {
                WALK_SPEED * (0.4 + 0.6 * (mlen / 0.6).min(1.0))
            };
            speed *= prog.move_mult();
            if p.punch_active {
                speed *= 0.45;
            }
            if run {
                p.stamina = (p.stamina - 6.0 * prog.stamina_cost_mult() * dt).max(0.0);
                p.stamina_delay = 0.6;
            }
            let (mut x, mut y) = (p.x + nx * speed * dt, p.y + ny * speed * dt);
            self.world
                .resolve_collision(&mut x, &mut y, 9.0, &mut self.scratch);
            p.x = x;
            p.y = y;
            moving = true;
            if !p.punch_active {
                p.dir = dir_from_vec(nx, ny);
                let a = if run { P_RUN } else { P_WALK };
                if p.anim != a {
                    p.anim = a;
                    p.anim_t = 0.0;
                }
            }
        } else if !p.punch_active && p.anim != P_IDLE {
            p.anim = P_IDLE;
            p.anim_t = 0.0;
        }
        p.moving = moving;

        if swing {
            self.events.push(Event::Swing);
            self.resolve_punch();
        }
    }

    fn resolve_punch(&mut self) {
        let (px, py, pdir) = (self.player.x, self.player.y, self.player.dir);
        let (fx, fy) = dir_vec(pdir);
        let base = self.progress.punch_damage();
        let crit = self.rng.chance(self.progress.crit_chance());
        let dmg = if crit { base * 1.75 } else { base } * self.rng.range(0.9, 1.1);
        let stun = self.rng.chance(self.progress.stun_chance());
        let mut hit_any = false;
        let mut kills: Vec<usize> = Vec::new();
        for (i, z) in self.zombies.iter_mut().enumerate() {
            if z.state == ZState::Dead {
                continue;
            }
            let (dx, dy) = (z.x - px, z.y - py);
            let d = dx.hypot(dy);
            if d > PUNCH_RANGE + 10.0 {
                continue;
            }
            let ang = ((dx * fx + dy * fy) / d.max(1e-3)).clamp(-1.0, 1.0).acos();
            if d > 12.0 && ang > PUNCH_ARC {
                continue;
            }
            hit_any = true;
            z.hp -= dmg;
            z.flash_t = 0.1;
            z.bar_t = 3.0;
            let push = if crit { 150.0 } else { 90.0 };
            z.kx = fx * push;
            z.ky = fy * push;
            self.events.push(Event::Damage {
                x: z.x,
                y: z.y - 50.0,
                v: dmg.round() as i32,
                crit,
            });
            if z.hp <= 0.0 {
                z.state = ZState::Dead;
                z.anim = Z_DEATH;
                z.anim_t = 0.0;
                z.timer = 3.0;
                kills.push(i);
            } else {
                z.state = ZState::Hurt;
                z.anim = Z_HURT;
                z.anim_t = 0.0;
                z.timer = if stun { 1.0 } else { 0.25 };
            }
        }
        for i in kills {
            let (x, y, xp) = (self.zombies[i].x, self.zombies[i].y, self.zombies[i].xp());
            self.progress.kills += 1;
            self.events.push(Event::Kill { x, y });
            if self.rng.chance(0.3) {
                self.progress.zombie_teeth += 1;
                self.events.push(Event::Item {
                    x,
                    y: y - 30.0,
                    name: "Zombi Dişi",
                });
            }
            self.give_xp(x, y, xp);
        }
        if hit_any {
            self.player.combat_t = 0.0;
            self.events.push(Event::Hit {
                x: px + fx * 20.0,
                y: py + fy * 20.0 - 24.0,
                tree: false,
            });
            return;
        }
        // zombi yoksa: önündeki ağaca vur
        self.world.query(
            px - 80.0,
            py - 80.0,
            px + 80.0,
            py + 80.0,
            &mut self.scratch,
        );
        let mut best: Option<(usize, f32)> = None;
        for &i in &self.scratch {
            let s = &self.world.statics[i as usize];
            if s.kind != Kind::Tree || s.felled {
                continue;
            }
            let (dx, dy) = (s.x - px, (s.y - py) * 1.3);
            let d = dx.hypot(dy);
            if d > PUNCH_RANGE + s.radius {
                continue;
            }
            let ang = ((dx * fx + dy * fy) / d.max(1e-3)).clamp(-1.0, 1.0).acos();
            if d > 14.0 && ang > PUNCH_ARC + 0.2 {
                continue;
            }
            if best.is_none_or(|(_, bd)| d < bd) {
                best = Some((i as usize, d));
            }
        }
        if let Some((i, _)) = best {
            let tdmg = (base * self.progress.tree_damage_mult()).round();
            let bonus = self.progress.bonus_wood_chance();
            let s = &mut self.world.statics[i];
            s.hp -= tdmg;
            s.shake = 0.3;
            let (sx, sy) = (s.x, s.y);
            self.events.push(Event::Hit {
                x: sx,
                y: sy - 20.0,
                tree: true,
            });
            self.events.push(Event::Damage {
                x: sx,
                y: sy - 60.0,
                v: tdmg as i32,
                crit: false,
            });
            if s.hp <= 0.0 {
                s.felled = true;
                s.regrow = 90.0 * self.progress.regrow_mult();
                let mut wood = 3 + self.rng.below(4);
                if self.rng.chance(bonus) {
                    wood += 1;
                }
                if s.sprite >= 8 && s.sprite <= 9 {
                    wood = 1 + self.rng.below(2);
                }
                self.progress.wood += wood;
                self.progress.trees += 1;
                self.events.push(Event::TreeFall { x: sx, y: sy });
                self.events.push(Event::Wood {
                    x: sx,
                    y: sy - 40.0,
                    v: wood,
                });
                self.give_xp(sx, sy, 10);
            }
        }
    }

    fn give_xp(&mut self, x: f32, y: f32, xp: u32) {
        self.events.push(Event::Xp {
            x,
            y: y - 70.0,
            v: xp,
        });
        let ups = self.progress.add_xp(xp);
        if ups > 0 {
            self.player.hp = self.progress.max_hp();
            self.player.stamina = self.progress.max_stamina();
            self.events.push(Event::LevelUp {
                level: self.progress.level,
                points: self.progress.points,
            });
        }
    }

    fn damage_player(&mut self, amount: f32) {
        let p = &mut self.player;
        if p.dead {
            return;
        }
        p.combat_t = 0.0;
        p.hp -= amount;
        p.flash_t = 0.12;
        self.events.push(Event::PlayerDamage {
            x: p.x,
            y: p.y - 60.0,
            v: amount.round() as i32,
        });
        if p.hp <= 0.0 {
            if self.progress.has_last_breath() && p.last_breath_cd <= 0.0 {
                p.hp = 1.0;
                p.last_breath_cd = 90.0;
                self.events.push(Event::LastBreath);
                return;
            }
            p.hp = 0.0;
            p.dead = true;
            p.anim = P_DEATH;
            p.anim_t = 0.0;
            p.punch_active = false;
            self.progress.deaths += 1;
            self.events.push(Event::Died);
        } else if !p.punch_active {
            p.hurt_t = 0.2;
        }
    }

    fn update_zombies(&mut self, dt: f32) {
        let (px, py, pdead) = (self.player.x, self.player.y, self.player.dead);
        let mut player_damage = 0.0;
        let n = self.zombies.len();
        // birbirini itme (üst üste binmesin)
        for i in 0..n {
            for j in (i + 1)..n {
                let (a, b) = (&self.zombies[i], &self.zombies[j]);
                if a.state == ZState::Dead || b.state == ZState::Dead {
                    continue;
                }
                let (dx, dy) = (b.x - a.x, b.y - a.y);
                let d = dx.hypot(dy);
                if d < 18.0 && d > 0.01 {
                    let push = (18.0 - d) * 0.5;
                    let (ux, uy) = (dx / d * push, dy / d * push);
                    self.zombies[i].x -= ux;
                    self.zombies[i].y -= uy;
                    self.zombies[j].x += ux;
                    self.zombies[j].y += uy;
                }
            }
        }
        for zi in 0..n {
            let mut z = self.zombies[zi].clone();
            z.anim_t += dt;
            z.flash_t = (z.flash_t - dt).max(0.0);
            z.bar_t = (z.bar_t - dt).max(0.0);
            z.attack_cd = (z.attack_cd - dt).max(0.0);
            // geri itilme
            if z.kx != 0.0 || z.ky != 0.0 {
                let (mut x, mut y) = (z.x + z.kx * dt, z.y + z.ky * dt);
                self.world
                    .resolve_collision(&mut x, &mut y, 9.0, &mut self.scratch);
                z.x = x;
                z.y = y;
                z.kx *= (1.0 - 8.0 * dt).max(0.0);
                z.ky *= (1.0 - 8.0 * dt).max(0.0);
                if z.kx.abs() + z.ky.abs() < 2.0 {
                    z.kx = 0.0;
                    z.ky = 0.0;
                }
            }
            let (dx, dy) = (px - z.x, py - z.y);
            let dist = dx.hypot(dy);
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
                ZState::Wander => {
                    z.timer -= dt;
                    let sees =
                        !pdead && (dist < 190.0 || (dist < 290.0 && self.player.anim == P_RUN));
                    if sees {
                        z.state = ZState::Notice;
                        z.timer = 0.4;
                        z.anim = Z_IDLE;
                        z.dir = dir_from_vec(dx, dy);
                        self.events.push(Event::Groan { x: z.x, y: z.y });
                    } else if z.timer <= 0.0 {
                        if z.anim == Z_WALK {
                            z.anim = Z_IDLE;
                            z.anim_t = 0.0;
                            z.timer = self.rng.range(1.0, 3.0);
                        } else {
                            let a = self.rng.range(0.0, TAU);
                            let r = self.rng.range(40.0, 140.0);
                            z.tx = (z.x + a.cos() * r).clamp(64.0, MAP_PX - 64.0);
                            z.ty = (z.y + a.sin() * r).clamp(64.0, MAP_PX - 64.0);
                            z.anim = Z_WALK;
                            z.anim_t = 0.0;
                            z.timer = self.rng.range(2.0, 5.0);
                        }
                    }
                    if z.anim == Z_WALK {
                        let (mx, my) = (z.tx - z.x, z.ty - z.y);
                        let md = mx.hypot(my);
                        if md < 4.0 {
                            z.timer = 0.0;
                        } else {
                            Self::move_zombie(
                                &self.world,
                                &mut self.scratch,
                                &mut z,
                                mx / md,
                                my / md,
                                dt,
                            );
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
                    if pdead || dist > 420.0 {
                        z.state = ZState::Wander;
                        z.anim = Z_IDLE;
                        z.timer = 1.0;
                    } else if dist < 24.0 && z.attack_cd <= 0.0 {
                        z.state = ZState::Attack;
                        z.anim = Z_ATTACK;
                        z.anim_t = 0.0;
                        z.hit_done = false;
                        z.dir = dir_from_vec(dx, dy);
                    } else if dist >= 20.0 {
                        Self::move_zombie(
                            &self.world,
                            &mut self.scratch,
                            &mut z,
                            dx / dist,
                            dy / dist,
                            dt,
                        );
                        if z.anim != Z_WALK {
                            z.anim = Z_WALK;
                            z.anim_t = 0.0;
                        }
                    } else {
                        z.dir = dir_from_vec(dx, dy);
                        z.anim = Z_IDLE;
                    }
                }
                ZState::Attack => {
                    // 6 kare @10fps, 3. karede vuruş
                    if !z.hit_done && z.anim_t >= 0.3 {
                        z.hit_done = true;
                        let (fx, fy) = dir_vec(z.dir);
                        let facing = (dx * fx + dy * fy) / dist.max(1e-3);
                        if dist < 32.0 && facing > 0.3 {
                            player_damage += z.damage();
                        }
                    }
                    if z.anim_t >= 0.6 {
                        z.state = ZState::Chase;
                        z.anim = Z_WALK;
                        z.anim_t = 0.0;
                        z.attack_cd = 1.0;
                    }
                }
            }
            self.zombies[zi] = z;
        }
        self.zombies
            .retain(|z| !(z.state == ZState::Dead && z.timer <= 0.0));
        if player_damage > 0.0 {
            self.damage_player(player_damage);
        }
    }

    fn move_zombie(
        world: &World,
        scratch: &mut Vec<u32>,
        z: &mut Zombie,
        nx: f32,
        ny: f32,
        dt: f32,
    ) {
        let sp = z.speed();
        let (mut x, mut y) = (z.x + nx * sp * dt, z.y + ny * sp * dt);
        world.resolve_collision(&mut x, &mut y, 9.0, scratch);
        // engelde takıldıysa yana kay
        if (x - z.x).hypot(y - z.y) < sp * dt * 0.3 {
            let (sx, sy) = (-ny, nx);
            x = z.x + sx * sp * dt;
            y = z.y + sy * sp * dt;
            world.resolve_collision(&mut x, &mut y, 9.0, scratch);
        }
        z.x = x;
        z.y = y;
        z.dir = dir_from_vec(nx, ny);
    }

    fn update_statics(&mut self, dt: f32) {
        let (px, py) = (self.player.x, self.player.y);
        // sadece sallanan/yeniden büyüyen ağaçlar (ucuz döngü)
        for s in self.world.statics.iter_mut() {
            if s.kind != Kind::Tree {
                continue;
            }
            if s.shake > 0.0 {
                s.shake = (s.shake - dt).max(0.0);
            }
            if s.felled {
                s.regrow -= dt;
                if s.regrow <= 0.0 && (s.x - px).hypot(s.y - py) > 200.0 {
                    s.felled = false;
                    s.hp = s.max_hp;
                }
            }
        }
    }

    /// Görünür alandaki her şeyi y'ye göre sıralı yazar. Döndürür: kayıt sayısı.
    pub fn draw_list(
        &mut self,
        vx: f32,
        vy: f32,
        vw: f32,
        vh: f32,
        out: &mut [f32],
        deco_density: f32,
    ) -> usize {
        let margin = 140.0;
        let (x0, y0, x1, y1) = (
            vx - margin,
            vy - margin,
            vx + vw + margin,
            vy + vh + margin * 1.5,
        );
        self.draw_tmp.clear();
        self.world.query(x0, y0, x1, y1, &mut self.scratch);
        for &i in &self.scratch {
            let s = &self.world.statics[i as usize];
            if s.x < x0 || s.x > x1 || s.y < y0 || s.y > y1 {
                continue;
            }
            if s.kind == Kind::Deco && s.sprite >= 23 && ((s.phase * 1000.0) % 1.0) > deco_density {
                continue;
            }
            let (sprite, state) = if s.kind == Kind::Tree && s.felled {
                (STUMP_SPRITE, 1.0)
            } else {
                (s.sprite, 0.0)
            };
            let shake = if s.shake > 0.0 {
                (s.shake * 60.0).sin() * 2.0 * (s.shake / 0.3)
            } else {
                0.0
            };
            let hp = if s.kind == Kind::Tree && s.hp < s.max_hp && !s.felled {
                s.hp / s.max_hp
            } else {
                -1.0
            };
            self.draw_tmp.push((
                s.y,
                [
                    DRAW_STATIC,
                    sprite as f32,
                    s.x,
                    s.y,
                    i as f32,
                    state,
                    s.phase,
                    shake,
                    hp,
                ],
            ));
        }
        for z in &self.zombies {
            if z.x < x0 || z.x > x1 || z.y < y0 || z.y > y1 {
                continue;
            }
            let hp = if z.bar_t > 0.0 && z.state != ZState::Dead {
                z.hp / z.max_hp
            } else {
                -1.0
            };
            let flash = if z.flash_t > 0.0 { 1.0 } else { 0.0 };
            self.draw_tmp.push((
                z.y,
                [
                    DRAW_ZOMBIE,
                    z.alpha,
                    z.x,
                    z.y,
                    z.dir as f32,
                    z.anim as f32,
                    z.anim_t,
                    flash,
                    hp,
                ],
            ));
        }
        let p = &self.player;
        let flash = if p.flash_t > 0.0 { 1.0 } else { 0.0 };
        self.draw_tmp.push((
            p.y,
            [
                DRAW_PLAYER,
                0.0,
                p.x,
                p.y,
                p.dir as f32,
                p.anim as f32,
                p.anim_t,
                flash,
                -1.0,
            ],
        ));
        self.draw_tmp
            .sort_by(|a, b| a.0.partial_cmp(&b.0).unwrap_or(std::cmp::Ordering::Equal));
        let cap = out.len() / STRIDE;
        let n = self.draw_tmp.len().min(cap);
        for (k, (_, rec)) in self.draw_tmp.iter().take(n).enumerate() {
            out[k * STRIDE..(k + 1) * STRIDE].copy_from_slice(rec);
        }
        n
    }

    /// HUD: [hp, maxhp, st, maxst, xp, xpnext, level, points, wood, kills, px, py, dead, teeth, trees]
    pub fn hud(&self) -> [f32; 15] {
        let p = &self.player;
        let g = &self.progress;
        [
            p.hp,
            g.max_hp(),
            p.stamina,
            g.max_stamina(),
            g.xp as f32,
            crate::progress::xp_to_next(g.level) as f32,
            g.level as f32,
            g.points as f32,
            g.wood as f32,
            g.kills as f32,
            p.x,
            p.y,
            if p.dead { 1.0 } else { 0.0 },
            g.zombie_teeth as f32,
            g.trees as f32,
        ]
    }

    /// Mini harita için yakın zombi konumları [x, y, ...]
    pub fn radar(&self, out: &mut [f32]) -> usize {
        let mut k = 0;
        for z in &self.zombies {
            if z.state == ZState::Dead || k + 2 > out.len() {
                continue;
            }
            out[k] = z.x;
            out[k + 1] = z.y;
            k += 2;
        }
        k / 2
    }

    pub fn take_events_json(&mut self) -> String {
        if self.events.is_empty() {
            return "[]".into();
        }
        let s = serde_json::to_string(&self.events).unwrap_or_else(|_| "[]".into());
        self.events.clear();
        s
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dirs_round_trip() {
        for d in 0..8u8 {
            let (x, y) = dir_vec(d);
            assert_eq!(dir_from_vec(x, y), d);
        }
        assert_eq!(dir_from_vec(0.0, 1.0), 0); // güney
        assert_eq!(dir_from_vec(1.0, 0.0), 2); // doğu
        assert_eq!(dir_from_vec(0.0, -1.0), 4); // kuzey
        assert_eq!(dir_from_vec(-1.0, 0.0), 6); // batı
    }

    #[test]
    fn player_moves_and_runs() {
        let mut g = Game::new(3, Progress::default());
        g.zombies.clear();
        let (x0, y0) = (g.player.x, g.player.y);
        for _ in 0..60 {
            g.step(
                1.0 / 60.0,
                Input {
                    move_x: 1.0,
                    move_y: 0.0,
                    ..Default::default()
                },
            );
        }
        assert!(
            g.player.x > x0 + 80.0,
            "koşu çok yavaş: {}",
            g.player.x - x0
        );
        assert!((g.player.y - y0).abs() < 1.0);
        assert_eq!(g.player.anim, P_RUN);
        assert_eq!(g.player.dir, 2);
    }

    #[test]
    fn punching_kills_zombie_and_gives_xp() {
        let mut g = Game::new(3, Progress::default());
        g.zombies.clear();
        g.spawn_t = 999.0;
        let (px, py) = (g.player.x, g.player.y);
        g.zombies.push(Zombie {
            x: px + 22.0,
            y: py,
            dir: 6,
            state: ZState::Wander,
            anim: Z_IDLE,
            anim_t: 0.0,
            hp: 30.0,
            max_hp: 30.0,
            level: 1,
            tx: px + 22.0,
            ty: py,
            timer: 99.0,
            attack_cd: 99.0,
            hit_done: false,
            flash_t: 0.0,
            bar_t: 0.0,
            kx: 0.0,
            ky: 0.0,
            stun_t: 0.0,
            alpha: 1.0,
        });
        for _ in 0..600 {
            g.step(
                1.0 / 60.0,
                Input {
                    atk_x: 1.0,
                    atk_y: 0.0,
                    attacking: true,
                    ..Default::default()
                },
            );
            // geri itilen zombiyi yakın tut
            if let Some(z) = g.zombies.first_mut() {
                if z.state != ZState::Dead {
                    z.x = px + 22.0;
                    z.y = py;
                    z.attack_cd = 99.0;
                }
            }
        }
        assert_eq!(g.progress.kills, 1);
        assert!(g.progress.level >= 1 && (g.progress.xp > 0 || g.progress.level > 1));
        assert_eq!(g.player.dir, 2);
    }

    #[test]
    fn zombie_hurts_player() {
        let mut g = Game::new(3, Progress::default());
        g.zombies.clear();
        g.spawn_t = 999.0;
        let (px, py) = (g.player.x, g.player.y);
        g.zombies.push(Zombie {
            x: px + 60.0,
            y: py,
            dir: 6,
            state: ZState::Wander,
            anim: Z_IDLE,
            anim_t: 0.0,
            hp: 50.0,
            max_hp: 50.0,
            level: 1,
            tx: px,
            ty: py,
            timer: 1.0,
            attack_cd: 0.0,
            hit_done: false,
            flash_t: 0.0,
            bar_t: 0.0,
            kx: 0.0,
            ky: 0.0,
            stun_t: 0.0,
            alpha: 1.0,
        });
        let hp0 = g.player.hp;
        for _ in 0..600 {
            g.step(1.0 / 60.0, Input::default());
        }
        assert!(g.player.hp < hp0);
    }

    #[test]
    fn draw_list_sorted() {
        let mut g = Game::new(11, Progress::default());
        let mut buf = vec![0.0f32; STRIDE * 4000];
        let (px, py) = (g.player.x, g.player.y);
        let n = g.draw_list(px - 400.0, py - 200.0, 800.0, 400.0, &mut buf, 1.0);
        assert!(n > 10);
        for k in 1..n {
            assert!(buf[k * STRIDE + 3] >= buf[(k - 1) * STRIDE + 3]);
        }
        assert!((0..n).any(|k| buf[k * STRIDE] == DRAW_PLAYER));
    }
}
