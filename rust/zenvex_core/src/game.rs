//! V2 oyun durumu ve ana döngü.
use crate::data::{self, WeaponClass, ZKind, PX_PER_M, WEAPONS};
use crate::ground::Textures;
use crate::progress::{Progress, CITY_LEVEL};
use crate::rng::Rng;
use crate::world::{self, World};
use serde::Serialize;
use std::f32::consts::{FRAC_PI_4, TAU};

// ---- kahraman animasyonları (assetgen/character2.HERO_ANIMS sırası)
pub const A_IDLE: u8 = 0;
pub const A_WALK: u8 = 1;
pub const A_RUN: u8 = 2;
pub const A_PUNCH: u8 = 3;
pub const A_MELEE: u8 = 4;
pub const A_PISTOL_IDLE: u8 = 5;
pub const A_PISTOL_WALK: u8 = 6;
pub const A_RIFLE_IDLE: u8 = 7;
pub const A_RIFLE_WALK: u8 = 8;
pub const A_CAST: u8 = 9;
pub const A_HURT: u8 = 10;
pub const A_DEATH: u8 = 11;
pub const A_RIDE: u8 = 12;
pub const A_ROLL: u8 = 13;
pub const A_KNOCK: u8 = 14;
pub const A_GETUP: u8 = 15;
// zombi animasyonları
pub const Z_IDLE: u8 = 0;
pub const Z_WALK: u8 = 1;
pub const Z_ATTACK: u8 = 2;
pub const Z_HURT: u8 = 3;
pub const Z_DEATH: u8 = 4;

// ---- çizim listesi
pub const STRIDE: usize = 12;
/// takla süresi (sn) ve karanlık aura yarıçapı (px)
pub const ROLL_TIME: f32 = 0.32;
pub const AURA_R: f32 = 190.0;
pub const D_STATIC: f32 = 0.0;
pub const D_HUMAN: f32 = 1.0;
pub const D_ZOMBIE: f32 = 2.0;
pub const D_DROP: f32 = 3.0;
pub const D_PROJ: f32 = 4.0;
pub const D_VEHICLE: f32 = 5.0;
pub const D_DRONE: f32 = 6.0;
pub const D_AREA: f32 = 7.0;

pub fn m(v: f32) -> f32 {
    v * PX_PER_M
}

pub fn dir_from_vec(x: f32, y: f32) -> u8 {
    let mut a = x.atan2(y);
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
    #[serde(rename = "roll")]
    Roll { x: f32, y: f32 },
    #[serde(rename = "dodge")]
    Dodge { x: f32, y: f32 },
    #[serde(rename = "knock")]
    Knockdown,
    #[serde(rename = "potion")]
    Potion { kind: u8 },
    #[serde(rename = "dmg")]
    Damage { x: f32, y: f32, v: i32, crit: bool },
    #[serde(rename = "pdmg")]
    PlayerDamage { x: f32, y: f32, v: i32 },
    #[serde(rename = "xp")]
    Xp { x: f32, y: f32, v: u32 },
    #[serde(rename = "gold")]
    Gold { x: f32, y: f32, v: u32 },
    #[serde(rename = "pickup")]
    Pickup {
        x: f32,
        y: f32,
        name: String,
        count: u32,
        rarity: u8,
    },
    #[serde(rename = "invfull")]
    InvFull,
    #[serde(rename = "lvl")]
    LevelUp { level: u32, points: u32 },
    #[serde(rename = "clvl")]
    CharLevel { level: u32 },
    #[serde(rename = "hit")]
    Hit { x: f32, y: f32, kind: u8 },
    #[serde(rename = "treefall")]
    TreeFall { x: f32, y: f32 },
    #[serde(rename = "kill")]
    Kill { x: f32, y: f32, z: u8 },
    #[serde(rename = "died")]
    Died,
    #[serde(rename = "swing")]
    Swing,
    #[serde(rename = "shoot")]
    Shoot { x: f32, y: f32, a: f32, w: u8 },
    #[serde(rename = "reload")]
    Reload,
    #[serde(rename = "noammo")]
    NoAmmo,
    #[serde(rename = "explode")]
    Explode { x: f32, y: f32, r: f32 },
    #[serde(rename = "groan")]
    Groan { x: f32, y: f32, z: u8 },
    #[serde(rename = "teleport")]
    Teleport { x: f32, y: f32 },
    #[serde(rename = "toxic")]
    ToxicCloud { x: f32, y: f32 },
    #[serde(rename = "slam")]
    Slam { x: f32, y: f32, r: f32 },
    #[serde(rename = "boss")]
    Boss { name: String },
    #[serde(rename = "quest")]
    Quest { title: String },
    #[serde(rename = "zone")]
    ZoneEnter {
        name: String,
        lo: u32,
        hi: u32,
        safe: bool,
    },
    #[serde(rename = "locked")]
    CityLocked,
    #[serde(rename = "sniper")]
    Sniper { x1: f32, y1: f32, x2: f32, y2: f32 },
    #[serde(rename = "drone")]
    DroneShot { x: f32, y: f32 },
    #[serde(rename = "shield")]
    Shield { v: i32 },
    #[serde(rename = "heal")]
    Heal { x: f32, y: f32, v: i32 },
    #[serde(rename = "rage")]
    Rage,
    #[serde(rename = "cloak")]
    Cloak,
    #[serde(rename = "crate")]
    Crate { x: f32, y: f32 },
    #[serde(rename = "car_hit")]
    CarHit { x: f32, y: f32 },
    #[serde(rename = "enter")]
    EnterVehicle { v: u8 },
    #[serde(rename = "exit")]
    ExitVehicle,
    #[serde(rename = "msg")]
    Msg { text: String },
    #[serde(rename = "lastbreath")]
    LastBreath,
    #[serde(rename = "step")]
    Step,
    /// ev sahibi: uzak oyuncuya zombi hasarı (i = setRemotes sırası)
    #[serde(rename = "rdmg")]
    RemoteDamage { i: usize, v: i32, poison: bool },
    /// istemci: zombiye vuruş → ev sahibine gönderilir
    #[serde(rename = "zhit")]
    ZHit { id: u32, dmg: f32, kx: f32, ky: f32, stun: bool },
    /// ev sahibi: uzak oyuncunun öldürdüğü zombi → ödül o oyuncuya
    #[serde(rename = "nkill")]
    NetKill { k: i32, kind: u8, level: u32, x: f32, y: f32 },
}

#[derive(Clone, Debug, Default)]
pub struct Player {
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub anim: u8,
    pub anim_t: f32,
    pub hp: f32,
    pub stamina: f32,
    pub stamina_delay: f32,
    pub shield: f32,
    pub shield_t: f32,
    pub rage_t: f32,
    pub cloak_t: f32,
    pub cloak_cd: f32,
    pub energy_t: f32,
    pub food_t: f32,
    pub poison_t: f32,
    pub heal_t: f32,
    pub heal_rate: f32,
    pub atk_cd: f32,
    pub atk_active: bool,
    pub hit_done: bool,
    pub reload_t: f32,
    pub ability_cd: f32,
    pub cast_t: f32,
    pub vehicle: Option<usize>,
    pub dead: bool,
    pub combat_t: f32,
    pub last_breath_cd: f32,
    pub hurt_t: f32,
    pub flash_t: f32,
    pub moving: bool,
    pub step_t: f32,
    pub noammo_t: f32,
    pub locked_t: f32,
    pub zone: u8,
    pub god: bool,
    pub one_hit: bool,
    pub speed_cheat: f32,
    // V4
    pub roll_t: f32,
    pub roll_dx: f32,
    pub roll_dy: f32,
    /// takladan sonra hasar almama
    pub iframe_t: f32,
    pub speed_t: f32,
    pub power_t: f32,
    /// iksir beklemeleri: can, hız, güç, panzehir
    pub pot_cd: [f32; 4],
    pub aura_t: f32,
    pub inf_ammo: bool,
    pub no_cd: bool,
    pub invis: bool,
    /// Titan dalgasıyla yere düştü (kalkma animasyonu için)
    pub knocked: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ZState {
    Wander,
    Notice,
    Chase,
    Attack,
    Hurt,
    Flee,
    Dead,
}

#[derive(Clone, Debug)]
pub struct Zombie {
    /// ağ kimliği (ev sahibi atar)
    pub id: u32,
    /// istemcide ara değerleme hedefi
    pub snap_x: f32,
    pub snap_y: f32,
    pub kind: ZKind,
    pub level: u32,
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub state: ZState,
    pub anim: u8,
    pub anim_t: f32,
    pub hp: f32,
    pub max_hp: f32,
    pub tx: f32,
    pub ty: f32,
    pub timer: f32,
    pub attack_cd: f32,
    pub hit_done: bool,
    pub flash_t: f32,
    pub bar_t: f32,
    pub kx: f32,
    pub ky: f32,
    pub alpha: f32,
    pub special_cd: f32,
    pub puddle_t: f32,
    pub decoy: bool,
    pub cloned: bool,
    pub summoned: bool,
    pub enraged: bool,
    pub boss: bool,
}

#[derive(Clone, Debug)]
pub struct Drop {
    pub item: u16,
    pub count: u32,
    pub x: f32,
    pub y: f32,
    pub age: f32,
    pub pop: f32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ProjKind {
    Bullet = 0,
    Rocket = 1,
    DroneBullet = 2,
    Acid = 3,
    DroneRocket = 4,
}

#[derive(Clone, Debug)]
pub struct Projectile {
    pub kind: ProjKind,
    pub x: f32,
    pub y: f32,
    pub vx: f32,
    pub vy: f32,
    pub dmg: f32,
    pub pierce: u8,
    pub life: f32,
    pub splash: f32,
    pub hostile: bool,
    pub hit: Vec<usize>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AreaKind {
    ToxicPuddle = 0,
    PoisonCloud = 1,
    Heal = 2,
    ShieldFx = 3,
    Shockwave = 4,
    Explosion = 5,
}

#[derive(Clone, Debug)]
pub struct Area {
    pub kind: AreaKind,
    pub x: f32,
    pub y: f32,
    pub r: f32,
    pub life: f32,
    pub max_life: f32,
    pub dps: f32,
    pub hit_done: bool,
}

#[derive(Clone, Debug)]
pub struct Vehicle {
    pub kind: usize,
    pub x: f32,
    pub y: f32,
    pub angle: f32,
    pub speed: f32,
    pub hp: f32,
    pub fuel: f32,
    pub driver: bool,
    pub wrecked: bool,
}

#[derive(Clone, Debug)]
pub struct Npc {
    pub kind: u8, // 0 keskin nişancı, 1 tüccar
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub cd: f32,
    pub anim_t: f32,
}

#[derive(Clone, Debug, Default)]
pub struct Drone {
    pub x: f32,
    pub y: f32,
    pub cd: f32,
    pub t: f32,
}

#[derive(Clone, Debug, Default)]
pub struct Remote {
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub anim: u8,
    pub anim_t: f32,
    pub character: u8,
    pub equip: [f32; 3],
    pub vehicle: f32,
}

pub struct Game {
    pub world: World,
    pub progress: Progress,
    pub player: Player,
    pub zombies: Vec<Zombie>,
    pub drops: Vec<Drop>,
    pub projectiles: Vec<Projectile>,
    pub areas: Vec<Area>,
    pub vehicles: Vec<Vehicle>,
    pub npcs: Vec<Npc>,
    pub drone: Drone,
    pub remotes: Vec<Remote>,
    /// 0 tek oyunculu, 1 ev sahibi (zombileri o çalıştırır), 2 istemci
    pub net: u8,
    pub next_zid: u32,
    /// ev sahibinde uzak oyuncu vuruşunun sahibi (-1 = yerel)
    pub kill_credit: i32,
    /// başka oyuncunun aracında yolcu
    pub passenger: bool,
    /// Hardcore sunucu: daha sert zombiler, daha çok XP, ölünce çanta yere düşer
    pub hardcore: bool,
    pub dog: Option<crate::pet::Dog>,
    /// hile: zaman ölçeği
    pub time_scale: f32,
    pub events: Vec<Event>,
    pub time: f32,
    pub day_t: f32,
    pub boss_t: f32,
    pub textures: Textures,
    pub rng: Rng,
    pub scratch: Vec<u32>,
    pub spawn_t: f32,
    pub draw_tmp: Vec<(f32, [f32; STRIDE])>,
    pub day_index: u32,
}

impl Game {
    pub fn new(seed: u64, mut progress: Progress, day_index: u32) -> Game {
        progress.character = 0;
        let world = World::generate(seed);
        let cap = progress.inventory_cap();
        progress.inv.resize(cap);
        progress.ensure_dailies(day_index);
        let (x, y) = world::camp_spawn();
        let player = Player {
            x,
            y,
            hp: progress.max_hp(),
            stamina: progress.max_stamina(),
            speed_cheat: 1.0,
            ..Default::default()
        };
        let vehicles = world
            .vehicle_spawns
            .iter()
            .map(|&(k, x, y, a)| Vehicle {
                kind: k,
                x,
                y,
                angle: a,
                speed: 0.0,
                hp: data::VEHICLES[k].hp,
                fuel: 60.0,
                driver: false,
                wrecked: false,
            })
            .collect();
        let mut npcs: Vec<Npc> = world
            .snipers
            .iter()
            .map(|&(x, y)| Npc {
                kind: 0,
                x,
                y,
                dir: 0,
                cd: 0.0,
                anim_t: 0.0,
            })
            .collect();
        npcs.extend(world.merchants.iter().map(|&(x, y)| Npc {
            kind: 1,
            x,
            y,
            dir: 0,
            cd: 0.0,
            anim_t: 0.0,
        }));
        Game {
            world,
            progress,
            player,
            zombies: Vec::new(),
            drops: Vec::new(),
            projectiles: Vec::new(),
            areas: Vec::new(),
            vehicles,
            npcs,
            drone: Drone {
                x,
                y,
                ..Default::default()
            },
            remotes: Vec::new(),
            net: 0,
            next_zid: 1,
            kill_credit: -1,
            passenger: false,
            hardcore: false,
            dog: None,
            time_scale: 1.0,
            events: Vec::new(),
            time: 0.0,
            day_t: 0.3,
            boss_t: 5.0,
            textures: Textures::default(),
            rng: Rng::new(seed ^ 0x5EED),
            scratch: Vec::new(),
            spawn_t: 0.0,
            draw_tmp: Vec::new(),
            day_index,
        }
    }

    pub fn msg(&mut self, t: impl Into<String>) {
        self.events.push(Event::Msg { text: t.into() });
    }

    pub fn respawn(&mut self, city: bool) {
        let (x, y) = if city && self.progress.level >= CITY_LEVEL {
            world::city_spawn()
        } else {
            world::camp_spawn()
        };
        let max_hp = self.progress.max_hp();
        let max_st = self.progress.max_stamina();
        let p = &mut self.player;
        p.x = x;
        p.y = y;
        p.dead = false;
        p.hp = max_hp;
        p.stamina = max_st;
        p.anim = A_IDLE;
        p.anim_t = 0.0;
        p.vehicle = None;
        p.poison_t = 0.0;
        for v in self.vehicles.iter_mut() {
            v.driver = false;
        }
        self.zombies.retain(|z| (z.x - x).hypot(z.y - y) > 900.0);
    }

    pub fn light(&self) -> f32 {
        // 0.25-0.75 gündüz; geceye yumuşak geçiş
        let t = self.day_t.fract();
        let d = (t * TAU - std::f32::consts::FRAC_PI_2).sin() * -1.0; // t=0.5 öğlen → 1
        (0.42 + 0.58 * ((d + 0.35) / 1.35).clamp(0.0, 1.0)).clamp(0.4, 1.0)
    }

    pub fn is_night(&self) -> bool {
        self.light() < 0.6
    }

    pub fn weapon(&self) -> &'static data::WeaponDef {
        &WEAPONS[data::weapon_for(&self.progress.eq.weapon)]
    }

    pub fn step(&mut self, dt: f32, input: Input) {
        let dt = dt.clamp(0.0, 0.05) * self.time_scale;
        self.time += dt;
        self.day_t += dt / 960.0;
        self.progress.play_time += dt as f64;
        if self.passenger {
            // yolcu: konumu ağ katmanı verir; sadece zamanlayıcılar
            self.player.anim_t += dt;
            self.player.ability_cd = (self.player.ability_cd - dt).max(0.0);
        } else if self.player.vehicle.is_some() {
            self.update_vehicle_drive(dt, input);
        } else {
            self.update_player(dt, input);
        }
        self.update_zone();
        self.update_drone(dt);
        self.update_dog(dt);
        self.update_zombies(dt);
        self.update_projectiles(dt);
        self.update_areas(dt);
        self.update_npcs(dt);
        self.update_drops(dt);
        self.update_statics(dt);
        self.update_spawning(dt);
    }

    fn update_zone(&mut self) {
        let z = self.world.zone(self.player.x, self.player.y);
        let zi = world::ZONES_ALL.iter().position(|q| *q == z).unwrap_or(0) as u8;
        if zi != self.player.zone {
            self.player.zone = zi;
            let (lo, hi) = z.level_range();
            self.events.push(Event::ZoneEnter {
                name: z.name().into(),
                lo,
                hi,
                safe: z.safe(),
            });
            if z.city() && !self.progress.city_entered {
                self.progress.city_entered = true;
                let done = self.progress.quest_event(data::QuestKind::EnterCity, 1);
                self.quests_done(done);
            }
        }
    }

    pub fn quests_done(&mut self, done: Vec<String>) {
        for t in done {
            self.events.push(Event::Quest { title: t });
        }
    }

    // ------------------------------------------------------------------ oyuncu
    fn update_player(&mut self, dt: f32, input: Input) {
        let max_hp = self.progress.max_hp();
        let max_st = self.progress.max_stamina();
        let regen = self.progress.regen_mult();
        {
            let p = &mut self.player;
            p.anim_t += dt;
            p.flash_t = (p.flash_t - dt).max(0.0);
            p.last_breath_cd = (p.last_breath_cd - dt).max(0.0);
            p.combat_t += dt;
            p.ability_cd = (p.ability_cd - dt).max(0.0);
            p.cloak_cd = (p.cloak_cd - dt).max(0.0);
            p.noammo_t = (p.noammo_t - dt).max(0.0);
            p.locked_t = (p.locked_t - dt).max(0.0);
            for t in [
                &mut p.rage_t,
                &mut p.cloak_t,
                &mut p.energy_t,
                &mut p.shield_t,
                &mut p.cast_t,
                &mut p.iframe_t,
                &mut p.speed_t,
                &mut p.power_t,
            ] {
                *t = (*t - dt).max(0.0);
            }
            for c in p.pot_cd.iter_mut() {
                *c = (*c - dt).max(0.0);
            }
            if p.no_cd {
                p.ability_cd = 0.0;
                p.pot_cd = [0.0; 4];
            }
            if p.shield_t <= 0.0 {
                p.shield = 0.0;
            }
            if p.dead {
                return;
            }
            if p.food_t > 0.0 {
                p.food_t -= dt;
                p.hp = (p.hp + 3.0 * dt).min(max_hp);
            }
            if p.heal_t > 0.0 {
                p.heal_t -= dt;
                p.hp = (p.hp + p.heal_rate * dt).min(max_hp);
            }
            if p.poison_t > 0.0 {
                p.poison_t -= dt;
                if !p.god {
                    p.hp -= 3.0 * dt;
                }
            }
            if p.combat_t > 5.0 && p.hp < max_hp {
                p.hp = (p.hp + 2.0 * regen * dt).min(max_hp);
            }
            p.hp = p.hp.min(max_hp);
            p.stamina_delay -= dt;
            if p.stamina_delay <= 0.0 || p.energy_t > 0.0 {
                p.stamina = (p.stamina + 18.0 * dt).min(max_st);
            }
        }
        if self.player.hp <= 0.0 && !self.player.dead {
            self.kill_player();
            return;
        }
        self.update_aura(dt);
        if self.player.roll_t > 0.0 {
            self.update_roll(dt);
            self.pickup_drops();
            return;
        }
        if self.player.knocked {
            // yere düşme → kalkma
            self.player.hurt_t -= dt;
            let a = if self.player.hurt_t > 0.5 { A_KNOCK } else { A_GETUP };
            if self.player.anim != a {
                self.player.anim = a;
                self.player.anim_t = 0.0;
            }
            if self.player.hurt_t <= 0.0 {
                self.player.knocked = false;
                self.player.hurt_t = 0.0;
            }
            return;
        }
        if self.player.hurt_t > 0.0 {
            self.player.hurt_t -= dt;
            if self.player.anim != A_HURT {
                self.player.anim = A_HURT;
                self.player.anim_t = 0.0;
            }
            return;
        }
        if self.player.cast_t > 0.0 {
            return;
        }
        let w = self.weapon();
        let ranged = matches!(
            w.class,
            WeaponClass::Pistol | WeaponClass::Rifle | WeaponClass::Sniper | WeaponClass::Heavy
        );
        let (aim_idle, aim_walk) = match w.class {
            WeaponClass::Pistol => (A_PISTOL_IDLE, A_PISTOL_WALK),
            WeaponClass::Fist | WeaponClass::Melee => (A_IDLE, A_WALK),
            _ => (A_RIFLE_IDLE, A_RIFLE_WALK),
        };
        // ---- saldırı
        let atk_len = input.atk_x.hypot(input.atk_y);
        let wants = input.attacking && atk_len > 0.2;
        if wants {
            self.player.dir = dir_from_vec(input.atk_x, input.atk_y);
            self.player.combat_t = self.player.combat_t.min(2.0);
        }
        self.player.atk_cd = (self.player.atk_cd - dt).max(0.0);
        if self.player.reload_t > 0.0 {
            self.player.reload_t -= dt;
            if self.player.reload_t <= 0.0 {
                self.finish_reload();
            }
        }
        let aim = (
            input.atk_x / atk_len.max(1e-3),
            input.atk_y / atk_len.max(1e-3),
        );
        match w.class {
            WeaponClass::Fist => self.update_fist(dt, wants),
            WeaponClass::Melee => self.update_melee(dt, wants, w),
            _ => {
                if wants && self.player.atk_cd <= 0.0 && self.player.reload_t <= 0.0 {
                    self.fire(w, aim);
                }
                self.player.atk_active = wants;
            }
        }
        // ---- hareket
        let mlen = input.move_x.hypot(input.move_y).min(1.0);
        let attacking = self.player.atk_active;
        let mut moving = false;
        if mlen > 0.08 {
            let (nx, ny) = (input.move_x / mlen, input.move_y / mlen);
            let run = mlen > 0.6 && self.player.stamina > 1.0 && !attacking;
            let mut speed = if run {
                m(4.0) * self.progress.run_mult()
            } else {
                m(1.6) * (0.45 + 0.55 * (mlen / 0.6).min(1.0))
            };
            speed *= self.progress.move_mult() * self.player.speed_cheat;
            if self.player.speed_t > 0.0 {
                speed *= 1.25;
            }
            if self.player.cloak_t > 0.0 {
                speed *= 1.2;
            }
            if self.player.energy_t > 0.0 {
                speed *= 1.2;
            }
            if attacking {
                speed *= if ranged { 0.65 } else { 0.45 };
            }
            if run {
                let cost = 6.0 * self.progress.stamina_cost_mult() * dt;
                self.player.stamina = (self.player.stamina - cost).max(0.0);
                self.player.stamina_delay = 0.6;
            }
            let gate_open = self.progress.level >= CITY_LEVEL;
            let (mut x, mut y) = (
                self.player.x + nx * speed * dt,
                self.player.y + ny * speed * dt,
            );
            self.world
                .resolve(&mut x, &mut y, 14.0, gate_open, &mut self.scratch);
            if !gate_open
                && y < (world::WALL_Y as f32 + 2.2) * world::TILE
                && self.player.y >= (world::WALL_Y as f32 + 2.2) * world::TILE
                && (x / world::TILE - 180.0).abs() < 5.0
                && self.player.locked_t <= 0.0
            {
                self.player.locked_t = 3.0;
                self.events.push(Event::CityLocked);
            }
            self.player.x = x;
            self.player.y = y;
            moving = true;
            self.player.step_t -= dt * if run { 1.6 } else { 1.0 };
            if self.player.step_t <= 0.0 {
                self.player.step_t = 0.42;
                self.events.push(Event::Step);
            }
            if !attacking {
                self.player.dir = dir_from_vec(nx, ny);
            }
            let a = if attacking && !ranged {
                self.player.anim
            } else if ranged && (attacking || self.player.combat_t < 1.5) {
                aim_walk
            } else if run {
                A_RUN
            } else {
                A_WALK
            };
            if self.player.anim != a {
                self.player.anim = a;
                self.player.anim_t = 0.0;
            }
        } else if !attacking || ranged {
            let a = if ranged && (attacking || self.player.combat_t < 1.5) {
                aim_idle
            } else if !ranged && attacking {
                self.player.anim
            } else {
                A_IDLE
            };
            if self.player.anim != a {
                self.player.anim = a;
                self.player.anim_t = 0.0;
            }
        }
        self.player.moving = moving;
        self.pickup_drops();
    }

    fn update_fist(&mut self, dt: f32, wants: bool) {
        const FPS: f32 = 14.0;
        const HALF: f32 = 4.0 / FPS;
        let p = &mut self.player;
        if wants && !p.atk_active {
            p.atk_active = true;
            p.hit_done = false;
            if p.anim != A_PUNCH {
                p.anim = A_PUNCH;
                p.anim_t = 0.0;
            }
        }
        if !p.atk_active {
            return;
        }
        let tired = p.stamina < 4.0;
        let speed = if tired { 0.6 } else { 1.0 };
        p.anim_t += dt * (speed - 1.0);
        let half_t = p.anim_t % HALF;
        let mut swing = false;
        if !p.hit_done && half_t >= 1.5 / FPS {
            p.hit_done = true;
            swing = true;
        }
        if half_t + dt * speed >= HALF - 1e-4 {
            p.hit_done = false;
            if !wants {
                p.atk_active = false;
            }
        }
        if swing {
            let cost = 4.0 * self.progress.stamina_cost_mult();
            self.player.stamina = (self.player.stamina - cost).max(0.0);
            self.player.stamina_delay = 0.8;
            self.events.push(Event::Swing);
            let dmg = self.progress.fist_damage();
            self.melee_hit(dmg, m(1.4), 0.75, 0.0);
        }
    }

    fn update_melee(&mut self, _dt: f32, wants: bool, w: &'static data::WeaponDef) {
        let p = &mut self.player;
        let period = 1.0 / w.rate;
        if wants && !p.atk_active {
            p.atk_active = true;
            p.hit_done = false;
            p.anim = A_MELEE;
            p.anim_t = 0.0;
        }
        if !p.atk_active {
            return;
        }
        if p.anim != A_MELEE {
            p.anim = A_MELEE;
            p.anim_t = 0.0;
        }
        let ct = p.anim_t % period;
        if !p.hit_done && ct >= period * 0.4 {
            p.hit_done = true;
            self.player.stamina =
                (self.player.stamina - 5.0 * self.progress.stamina_cost_mult()).max(0.0);
            self.player.stamina_delay = 0.8;
            self.events.push(Event::Swing);
            self.melee_hit(w.damage, m(w.range_m), 0.9, w.stun);
        }
        let p = &mut self.player;
        if ct + 0.02 >= period * 0.98 {
            p.hit_done = false;
            if !wants {
                p.atk_active = false;
                p.anim = A_IDLE;
            }
        }
    }

    pub fn damage_mult(&self) -> f32 {
        let mut k = self.progress.damage_mult();
        if self.player.power_t > 0.0 {
            k *= 1.2;
        }
        if self.player.rage_t > 0.0 {
            k *= 1.4 + 0.03 * self.progress.char_level[0] as f32;
        }
        if self.player.cloak_t > 0.0 {
            k *= 1.25;
        }
        k
    }

    fn kill_player(&mut self) {
        let p = &mut self.player;
        p.hp = 0.0;
        p.dead = true;
        p.anim = A_DEATH;
        p.anim_t = 0.0;
        p.atk_active = false;
        self.progress.deaths += 1;
        self.events.push(Event::Died);
        if self.hardcore {
            // çantadaki her şey ölüm yerine düşer, altının yarısı gider
            let (x, y) = (self.player.x, self.player.y);
            let slots: Vec<_> = self.progress.inv.slots.iter_mut().filter_map(|s| s.take()).collect();
            for (i, s) in slots.into_iter().enumerate() {
                let a = i as f32 * 2.4;
                self.spawn_drop(s.item as usize, s.count as u32, x + a.cos() * 40.0, y + a.sin() * 30.0);
            }
            self.progress.gold /= 2;
            self.msg("Hardcore: çantan ölüm yerine düştü, altının yarısı gitti");
        }
    }

    /// Oyuncuyu (ve sürdüğü aracı bırakarak) verilen noktaya taşır.
    pub fn teleport(&mut self, x: f32, y: f32) -> (f32, f32) {
        let (mut x, mut y) = (x.clamp(100.0, world::MAP_PX - 100.0), y.clamp(100.0, world::MAP_PX - 100.0));
        self.world.resolve(&mut x, &mut y, 14.0, true, &mut self.scratch);
        if let Some(vi) = self.player.vehicle.take() {
            self.vehicles[vi].driver = false;
        }
        self.player.x = x;
        self.player.y = y;
        (x, y)
    }

    /// Patlama hasarı: skill ağacındaki patlama koruması uygulanır.
    pub fn damage_player_blast(&mut self, amount: f32) {
        let k = 1.0 - self.progress.bonus().blast_res;
        self.damage_player(amount * k);
    }

    /// Oyuncu şu an hasar almıyor mu (takla, takla sonrası, ölümsüzlük)
    pub fn player_invulnerable(&self) -> bool {
        self.player.dead || self.player.god || self.player.cloak_t > 0.0 || self.player.roll_t > 0.0 || self.player.iframe_t > 0.0
    }

    pub fn damage_player(&mut self, amount: f32) {
        if self.player_invulnerable() {
            return;
        }
        let b = self.progress.bonus();
        if b.dodge > 0.0 && self.rng.chance(b.dodge) {
            let (x, y) = (self.player.x, self.player.y);
            self.events.push(Event::Dodge { x, y: y - 110.0 });
            return;
        }
        if b.reflect > 0.0 {
            // en yakın zombiye yansıt
            let (px, py) = (self.player.x, self.player.y);
            let near = self
                .zombies
                .iter()
                .enumerate()
                .filter(|(_, z)| z.state != ZState::Dead && (z.x - px).hypot(z.y - py) < 140.0)
                .min_by(|a, b2| {
                    let da = (a.1.x - px).hypot(a.1.y - py);
                    let db = (b2.1.x - px).hypot(b2.1.y - py);
                    da.partial_cmp(&db).unwrap_or(std::cmp::Ordering::Equal)
                })
                .map(|(i, _)| i);
            if let Some(i) = near {
                self.hurt_zombie(i, amount * b.reflect * 4.0, false, 0.0, 0.0, false, true);
            }
        }
        let z = self.world.zone(self.player.x, self.player.y);
        if z.safe() {
            return;
        }
        let armor = self.progress.armor();
        let amount = if self.hardcore { amount * 1.5 } else { amount };
        let mut dmg = amount * 100.0 / (100.0 + armor) * (1.0 - self.progress.damage_resist());
        let p = &mut self.player;
        p.combat_t = 0.0;
        if p.shield > 0.0 {
            let absorbed = p.shield.min(dmg);
            p.shield -= absorbed;
            dmg -= absorbed;
        }
        if dmg <= 0.0 {
            return;
        }
        p.hp -= dmg;
        p.flash_t = 0.12;
        let (x, y) = (p.x, p.y);
        self.events.push(Event::PlayerDamage {
            x,
            y: y - 110.0,
            v: dmg.round().max(1.0) as i32,
        });
        let max_hp = self.progress.max_hp();
        let has_cloak = self.progress.eq.cloak == "cloak";
        let p = &mut self.player;
        if has_cloak && p.cloak_cd <= 0.0 && p.hp > 0.0 && p.hp < max_hp * 0.2 {
            p.cloak_t = 3.0;
            p.cloak_cd = 60.0;
            self.events.push(Event::Cloak);
        }
        let p = &mut self.player;
        if p.hp <= 0.0 {
            if self.progress.has_last_breath() && p.last_breath_cd <= 0.0 {
                p.hp = 1.0;
                p.last_breath_cd = 90.0;
                self.events.push(Event::LastBreath);
                return;
            }
            self.kill_player();
        } else if !p.atk_active {
            p.hurt_t = 0.18;
        }
    }

    // ------------------------------------------------------------------ takla ve aura (V4 Savaşçı)
    pub fn start_roll(&mut self) -> Result<String, String> {
        if self.player.roll_t > 0.0 || self.player.hurt_t > 0.3 {
            return Err(String::new());
        }
        let (dx, dy) = dir_vec(self.player.dir);
        let p = &mut self.player;
        p.roll_t = ROLL_TIME;
        p.roll_dx = dx;
        p.roll_dy = dy;
        p.atk_active = false;
        p.hurt_t = 0.0;
        p.ability_cd = if p.no_cd { 0.0 } else { self.progress.roll_cd() };
        p.anim = A_ROLL;
        p.anim_t = 0.0;
        let (x, y) = (p.x, p.y);
        self.events.push(Event::Roll { x, y });
        Ok(String::new())
    }

    fn update_roll(&mut self, dt: f32) {
        let gate_open = self.progress.level >= CITY_LEVEL;
        let speed = m(3.0) / ROLL_TIME; // 3 metre
        let (mut x, mut y) = (self.player.x + self.player.roll_dx * speed * dt, self.player.y + self.player.roll_dy * speed * dt);
        self.world.resolve(&mut x, &mut y, 14.0, gate_open, &mut self.scratch);
        let p = &mut self.player;
        p.x = x;
        p.y = y;
        p.anim_t += dt;
        p.roll_t -= dt;
        if p.roll_t <= 0.0 {
            p.roll_t = 0.0;
            p.iframe_t = self.progress.bonus().iframe;
            p.anim = A_IDLE;
            p.anim_t = 0.0;
        }
    }

    /// Savaşçı Sv10: yakındaki düşmanlara saniyede 20 hasar
    fn update_aura(&mut self, dt: f32) {
        if !self.progress.bonus().aura || self.player.dead {
            return;
        }
        self.player.aura_t -= dt;
        if self.player.aura_t > 0.0 {
            return;
        }
        self.player.aura_t = 1.0;
        let (px, py) = (self.player.x, self.player.y);
        let hits: Vec<usize> = self
            .zombies
            .iter()
            .enumerate()
            .filter(|(_, z)| z.state != ZState::Dead && (z.x - px).hypot(z.y - py) < AURA_R)
            .map(|(i, _)| i)
            .collect();
        for i in hits.into_iter().rev() {
            if i < self.zombies.len() {
                self.hurt_zombie(i, 20.0, false, 0.0, 0.0, false, true);
            }
        }
    }

    // ------------------------------------------------------------------ yetenekler
    pub fn use_ability(&mut self) -> Result<String, String> {
        if self.player.dead || self.player.vehicle.is_some() {
            return Err("Şu an kullanılamaz".into());
        }
        if self.player.ability_cd > 0.0 {
            return Err(format!("Bekleme: {:.0} sn", self.player.ability_cd));
        }
        let c = self.progress.character;
        let lvl = self.progress.char_level[c] as f32;
        if c == 0 {
            return self.start_roll();
        }
        match c {
            0 => {
                self.player.rage_t = 5.0;
                self.player.ability_cd = 40.0;
                self.events.push(Event::Rage);
                Ok("Öfke!".into())
            }
            1 => Err("Dron otomatik çalışır".into()),
            2 => {
                let v = 50.0 + 20.0 * (lvl - 1.0);
                self.player.shield = v;
                self.player.shield_t = 8.0;
                self.player.ability_cd = 25.0;
                self.player.cast_t = 0.5;
                self.player.anim = A_CAST;
                self.player.anim_t = 0.0;
                let (x, y) = (self.player.x, self.player.y);
                self.areas.push(Area {
                    kind: AreaKind::ShieldFx,
                    x,
                    y,
                    r: m(4.0),
                    life: 0.8,
                    max_life: 0.8,
                    dps: 0.0,
                    hit_done: true,
                });
                self.events.push(Event::Shield { v: v as i32 });
                Ok(format!("Büyü Kalkanı: {v:.0}"))
            }
            _ => {
                // Lv1 %5/5sn, Lv5 %10/4sn, Lv10 %20/3sn
                let (pct, dur) = if lvl <= 5.0 {
                    let k = (lvl - 1.0) / 4.0;
                    (0.05 + 0.05 * k, 5.0 - 1.0 * k)
                } else {
                    let k = (lvl - 5.0) / 5.0;
                    (0.10 + 0.10 * k, 4.0 - 1.0 * k)
                };
                let total = self.progress.max_hp() * pct;
                self.player.heal_t = dur;
                self.player.heal_rate = total / dur;
                self.player.ability_cd = 30.0;
                self.player.cast_t = 0.5;
                self.player.anim = A_CAST;
                self.player.anim_t = 0.0;
                let (x, y) = (self.player.x, self.player.y);
                self.areas.push(Area {
                    kind: AreaKind::Heal,
                    x,
                    y,
                    r: m(4.0),
                    life: dur,
                    max_life: dur,
                    dps: 0.0,
                    hit_done: true,
                });
                self.events.push(Event::Heal {
                    x,
                    y: y - 120.0,
                    v: total as i32,
                });
                Ok(format!("Şifa Alanı: +{total:.0} can / {dur:.1} sn"))
            }
        }
    }

    fn update_drone(&mut self, dt: f32) {
        if self.progress.character != 1 || self.player.dead {
            return;
        }
        let lvl = self.progress.char_level[1];
        let (px, py) = (self.player.x, self.player.y);
        self.drone.t += dt;
        let tx = px + (self.drone.t * 1.3).cos() * 60.0;
        let ty = py - 40.0 + (self.drone.t * 1.3).sin() * 30.0;
        let k = 1.0 - (-6.0 * dt).exp();
        self.drone.x += (tx - self.drone.x) * k;
        self.drone.y += (ty - self.drone.y) * k;
        self.drone.cd -= dt;
        if self.drone.cd > 0.0 {
            return;
        }
        let range = m(9.0);
        let filt = self.progress.drone_targets;
        let mut best: Option<(usize, f32)> = None;
        for (i, z) in self.zombies.iter().enumerate() {
            if z.state == ZState::Dead || z.alpha < 0.3 {
                continue;
            }
            let ok = match z.kind {
                ZKind::Dog => filt[1],
                ZKind::Titan => filt[2],
                _ => filt[0],
            };
            if !ok {
                continue;
            }
            let d = (z.x - self.drone.x).hypot(z.y - self.drone.y);
            if d < range && best.is_none_or(|(_, bd)| d < bd) {
                best = Some((i, d));
            }
        }
        if let Some((i, d)) = best {
            let z = &self.zombies[i];
            let (dx, dy) = ((z.x - self.drone.x) / d, (z.y - 40.0 - self.drone.y) / d);
            let dmg = (6.0 + 1.2 * (lvl as f32 - 1.0)) * self.progress.damage_mult();
            let rocket = lvl >= 10 && self.rng.chance(0.15);
            let barrels = if lvl >= 5 { 2 } else { 1 };
            for b in 0..barrels {
                let off = (b as f32 - 0.5 * (barrels - 1) as f32) * 10.0;
                self.projectiles.push(Projectile {
                    kind: if rocket {
                        ProjKind::DroneRocket
                    } else {
                        ProjKind::DroneBullet
                    },
                    x: self.drone.x + off * dy,
                    y: self.drone.y - off * dx,
                    vx: dx * m(22.0),
                    vy: dy * m(22.0),
                    dmg: if rocket { dmg * 4.0 } else { dmg },
                    pierce: 0,
                    life: 0.8,
                    splash: if rocket { m(1.8) } else { 0.0 },
                    hostile: false,
                    hit: Vec::new(),
                });
            }
            let (x, y) = (self.drone.x, self.drone.y);
            self.events.push(Event::DroneShot { x, y });
            self.drone.cd = 0.8;
        }
    }

    // ------------------------------------------------------------------ eşyalar
    pub fn spawn_drop(&mut self, item: usize, count: u32, x: f32, y: f32) {
        if count == 0 {
            return;
        }
        let a = self.rng.range(0.0, TAU);
        let r = self.rng.range(10.0, 40.0);
        self.drops.push(Drop {
            item: item as u16,
            count,
            x: x + a.cos() * r,
            y: y + a.sin() * r * 0.6,
            age: 0.0,
            pop: 0.0,
        });
    }

    pub fn spawn_drop_id(&mut self, id: &str, count: u32, x: f32, y: f32) {
        if let Some(i) = data::item_index(id) {
            self.spawn_drop(i, count, x, y);
        }
    }

    fn pickup_drops(&mut self) {
        let (px, py) = (self.player.x, self.player.y);
        let mut full = false;
        let mut i = 0;
        while i < self.drops.len() {
            let d = &self.drops[i];
            if d.age < 0.35 || (d.x - px).hypot(d.y - py) > 40.0 {
                i += 1;
                continue;
            }
            let item = d.item as usize;
            let def = &data::ITEMS[item];
            let (dx, dy, cnt) = (d.x, d.y, d.count);
            if def.id == "coin" {
                self.progress.add_gold(cnt);
                self.events.push(Event::Gold {
                    x: dx,
                    y: dy - 60.0,
                    v: cnt,
                });
                let done = self.progress.quest_event(data::QuestKind::Gold, cnt);
                self.quests_done(done);
                self.drops.swap_remove(i);
                continue;
            }
            if def.id == "gem" {
                self.progress.gems += cnt;
                self.drops.swap_remove(i);
                continue;
            }
            let left = self.progress.inv.add(item, cnt);
            if left < cnt {
                self.events.push(Event::Pickup {
                    x: dx,
                    y: dy - 60.0,
                    name: def.name.into(),
                    count: cnt - left,
                    rarity: def.rarity as u8,
                });
            }
            if left == 0 {
                self.drops.swap_remove(i);
            } else {
                self.drops[i].count = left;
                full = true;
                i += 1;
            }
        }
        if full && self.player.noammo_t <= 0.0 {
            self.player.noammo_t = 3.0;
            self.events.push(Event::InvFull);
        }
    }

    fn update_drops(&mut self, dt: f32) {
        for d in self.drops.iter_mut() {
            d.age += dt;
            d.pop = (d.pop + dt * 3.0).min(1.0);
        }
        self.drops.retain(|d| d.age < 150.0);
    }

    // ------------------------------------------------------------------ statikler
    fn update_statics(&mut self, dt: f32) {
        let (px, py) = (self.player.x, self.player.y);
        for s in self.world.statics.iter_mut() {
            if s.shake > 0.0 {
                s.shake = (s.shake - dt).max(0.0);
            }
            if s.broken {
                s.respawn -= dt;
                if s.respawn <= 0.0 && (s.x - px).hypot(s.y - py) > 700.0 {
                    s.broken = false;
                    s.hp = s.max_hp;
                }
            }
        }
    }

    fn update_npcs(&mut self, dt: f32) {
        for ni in 0..self.npcs.len() {
            let npc = &mut self.npcs[ni];
            npc.anim_t += dt;
            npc.cd -= dt;
            if npc.kind != 0 {
                let (dx, dy) = (self.player.x - npc.x, self.player.y - npc.y);
                if dx.hypot(dy) < 500.0 {
                    npc.dir = dir_from_vec(dx, dy);
                }
                continue;
            }
            if npc.cd > 0.0 {
                continue;
            }
            let (nx, ny) = (npc.x, npc.y);
            let range = m(14.0);
            let mut best: Option<(usize, f32)> = None;
            for (i, z) in self.zombies.iter().enumerate() {
                if z.state == ZState::Dead || z.boss {
                    continue;
                }
                let d = (z.x - nx).hypot(z.y - ny);
                if d < range && best.is_none_or(|(_, bd)| d < bd) {
                    best = Some((i, d));
                }
            }
            if let Some((i, _)) = best {
                let (zx, zy) = (self.zombies[i].x, self.zombies[i].y);
                self.npcs[ni].dir = dir_from_vec(zx - nx, zy - ny);
                self.npcs[ni].cd = 1.2;
                self.events.push(Event::Sniper {
                    x1: nx,
                    y1: ny - 300.0,
                    x2: zx,
                    y2: zy - 60.0,
                });
                let dmg = 60.0 + self.zombies[i].max_hp * 0.25;
                self.hurt_zombie(i, dmg, false, 0.0, 0.0, false, false);
            }
        }
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
    }
}
