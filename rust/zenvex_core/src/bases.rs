//! V8 devlet üsleri: haritada 50 duvarlı üs, asker botları (M4 ve keskin nişancı), helikopterle takviye,
//! klanların baskın yapıp ele geçirmesi. Sahiplik, ödül ve süreler sunucuda (Supabase) tutulur; Kotlin bunları
//! `bases` komutuyla verir. Botların savaşı her istemcide yerel çalışır.
//!
//! Kurallar:
//! - Üs başına en fazla 15 asker, her biri 1000 can; doğunca 5 sn ölümsüz; helikopterle iner.
//! - Devlet üssünde bir asker ölünce her 30 sn'de bir yenisi gelir.
//! - Klan ele geçirince garnizon sıfırdan kurulur: dakikada 3 asker; dolduktan sonra ölen her asker için dakikada 1.
//! - Klan üssünün askerleri klan üyelerine dosttur; diğer herkese (ve zombilere) ateş eder.
//! - Askerler en yakın düşmana (oyuncu, köpek ya da zombi) sıkar; bazen ıskalar.
use crate::data;
use crate::game::*;
use crate::rng::Rng;
use crate::world::{self, region_road, zone_at_tile, Zone, LANDMARKS, MAP_TILES, TILE};
use serde_json::{json, Value};
use std::f32::consts::TAU;
use std::sync::OnceLock;

pub const BASE_N: usize = 50;
/// yerleşkenin yarı genişliği (karo)
pub const BASE_HALF: f32 = 11.0;
pub const GARRISON: usize = 15;
pub const SOLDIER_HP: f32 = 1000.0;
pub const SPAWN_INV: f32 = 5.0;
/// devlet üssü: ölen asker başına yenisi (sn)
pub const STATE_RESPAWN: f32 = 30.0;
/// klan üssü ilk kuruluş: dakikada 3
pub const REBUILD_RESPAWN: f32 = 20.0;
/// klan üssü dolduktan sonra: dakikada 1
pub const CLAN_RESPAWN: f32 = 60.0;
/// ele geçirme alanında kalma süresi (sn)
pub const CAPTURE_TIME: f32 = 10.0;
pub const CAPTURE_R: f32 = 3.0 * TILE;
const ACT_R: f32 = 48.0 * TILE;
const DEACT_R: f32 = 62.0 * TILE;
/// çizim türleri (Kotlin GameView ile ortak)
pub const D_SOLDIER: f32 = 10.0;
pub const D_HELI: f32 = 11.0;
pub const D_FLAG: f32 = 12.0;

pub const K_M4: u8 = 0;
pub const K_SNIPER: u8 = 1;

/// M4: 3'lü seri, menzil 10 m; keskin nişancı: 1.3 sn nişan (lazer), tek atış, menzil 20 m
pub const M4_RANGE: f32 = 10.0;
pub const M4_DMG: f32 = 5.0;
pub const M4_CD: f32 = 2.6;
pub const SNIPER_RANGE: f32 = 20.0;
pub const SNIPER_DMG: f32 = 26.0;
pub const SNIPER_CD: f32 = 5.0;
pub const SNIPER_AIM: f32 = 1.3;

pub struct BaseDef {
    pub id: u16,
    pub tx: f32,
    pub ty: f32,
    pub level: u32,
    pub region: Zone,
}

impl BaseDef {
    pub fn pos(&self) -> (f32, f32) {
        (self.tx * TILE, self.ty * TILE)
    }
    pub fn name(&self) -> String {
        let r = match self.region {
            Zone::Military => "Askeri",
            Zone::Harbor => "Liman",
            Zone::BurntForest => "Kül",
            Zone::Desert => "Çöl",
            Zone::BlackSwamp => "Bataklık",
            _ => "Şehir",
        };
        format!("{r} Üssü {}", self.id + 1)
    }
}

pub fn base_defs() -> &'static [BaseDef] {
    static DEFS: OnceLock<Vec<BaseDef>> = OnceLock::new();
    DEFS.get_or_init(generate_defs)
}

/// Üs yerleri tohumdan bağımsızdır (sunucudaki üs kimlikleri her istemcide aynı yeri gösterir).
fn generate_defs() -> Vec<BaseDef> {
    let mut rng = Rng::new(0xBA5E_0050);
    let mut out: Vec<BaseDef> = Vec::new();
    let quotas: [(Zone, usize, u32); 6] = [
        (Zone::Military, 14, 46),
        (Zone::Harbor, 10, 48),
        (Zone::BurntForest, 9, 28),
        (Zone::Desert, 9, 32),
        (Zone::BlackSwamp, 4, 36),
        (Zone::Industrial, 4, 40),
    ];
    let h = BASE_HALF;
    let ok_zone = |z: Zone, want: Zone| if want == Zone::Industrial { matches!(z, Zone::Industrial | Zone::Downtown) } else { z == want };
    for (zone, n, level) in quotas {
        let mut got = 0;
        for _ in 0..40000 {
            if got >= n {
                break;
            }
            let (tx, ty) = if zone == Zone::Industrial {
                // eski şehirde yalnız blok içleri (yollar arası 26×22 karo)
                let bx = 8 + 34 * rng.below(10) as usize;
                let by = 6 + 30 * rng.below(3) as usize;
                ((bx + 19) as f32, (by + 17) as f32)
            } else {
                (rng.range(24.0, MAP_TILES as f32 - 24.0).round(), rng.range(24.0, MAP_TILES as f32 - 24.0).round())
            };
            let corners = [(0.0, 0.0), (-h, -h), (h, -h), (-h, h), (h, h), (0.0, h + 2.0)];
            if !corners.iter().all(|(ox, oy)| ok_zone(zone_at_tile((tx + ox) as usize, (ty + oy) as usize), zone)) {
                continue;
            }
            if LANDMARKS.iter().any(|l| (l.tx - tx).hypot(l.ty - ty) < l.r + h + 10.0) {
                continue;
            }
            let (cx, cy) = world::CAMP;
            if (cx / TILE - tx).hypot(cy / TILE - ty) < 70.0 || (world::BOSS.0 / TILE - tx).hypot(world::BOSS.1 / TILE - ty) < 50.0 {
                continue;
            }
            if out.iter().any(|b| (b.tx - tx).hypot(b.ty - ty) < 40.0) {
                continue;
            }
            // yeni bölgelerde yollar yerleşkenin içinden geçmesin
            let mut road = false;
            let mut k = -h - 2.0;
            while k <= h + 2.0 && !road {
                for (ax, ay) in [(tx + k, ty - h - 2.0), (tx + k, ty + h + 2.0), (tx - h - 2.0, ty + k), (tx + h + 2.0, ty + k), (tx + k, ty)] {
                    if region_road(ax as usize, ay as usize).0 {
                        road = true;
                    }
                }
                k += 1.0;
            }
            if road {
                continue;
            }
            let id = out.len() as u16;
            out.push(BaseDef { id, tx, ty, level: level + (id as u32 % 3), region: zone });
            got += 1;
        }
    }
    out
}

/// Asker mevzileri (üs merkezine göre karo): 0-3 kule keskin nişancıları, 4 arka keskin nişancı, 5-14 M4.
pub const POSTS: [(f32, f32); GARRISON] = [
    (-9.4, -9.4),
    (9.4, -9.4),
    (-9.4, 9.4),
    (9.4, 9.4),
    (0.0, -8.5),
    (-4.5, 1.8),
    (4.5, 1.8),
    (0.0, 5.2),
    (-7.0, -2.8),
    (7.0, -2.8),
    (-3.0, -4.2),
    (3.0, -4.2),
    (-8.4, 4.0),
    (8.4, 4.0),
    (0.0, -1.6),
];

pub fn post_kind(post: usize) -> u8 {
    if post <= 4 {
        K_SNIPER
    } else {
        K_M4
    }
}

/// Sunucudan gelen + yerel üs durumu
#[derive(Clone, Debug, Default)]
pub struct BaseState {
    /// 0 devlet, >0 klan kimliği
    pub owner: i64,
    pub owner_name: String,
    /// klan rengi (0xRRGGBB)
    pub color: i32,
    pub reward: String,
    pub reward_n: u32,
    pub reward_end_ms: f64,
    pub captured_ms: f64,
    pub known: bool,
    // ---- yerel
    pub active: bool,
    pub spawn_t: f32,
    pub pending: u8,
    pub rebuild: bool,
    /// etkin değilken saklanan garnizon ve ayrılma zamanı
    pub garrison: u8,
    pub left_at: f32,
    pub capture_t: f32,
    pub cap_sent: bool,
    pub note_t: f32,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Target {
    None,
    Player,
    Dog,
    Zombie(u32),
}

#[derive(Clone, Debug)]
pub struct Soldier {
    pub base: u16,
    pub post: u8,
    pub kind: u8,
    pub x: f32,
    pub y: f32,
    pub hp: f32,
    pub level: u32,
    pub inv_t: f32,
    pub cd: f32,
    pub burst: u8,
    pub burst_t: f32,
    pub aim_t: f32,
    pub ax: f32,
    pub ay: f32,
    pub face: f32,
    pub anim_t: f32,
    pub flash_t: f32,
    pub recoil: f32,
    pub dead_t: f32,
    pub walking: bool,
    pub think_t: f32,
    pub tgt: Target,
}

#[derive(Clone, Debug)]
pub struct Heli {
    pub base: u16,
    pub x: f32,
    pub y: f32,
    pub sx: f32,
    pub sy: f32,
    pub alt: f32,
    pub t: f32,
    pub phase: u8,
    pub dropped: bool,
}

const HELI_IN: f32 = 4.0;
const HELI_HOVER: f32 = 2.6;
const HELI_OUT: f32 = 4.0;

fn helipad(b: &BaseDef) -> (f32, f32) {
    (b.tx * TILE, (b.ty - 5.0) * TILE)
}

impl Game {
    fn base_hostile_to_me(&self, i: usize) -> bool {
        let o = self.bases[i].owner;
        o == 0 || self.my_clan <= 0 || o != self.my_clan
    }

    /// Kotlin: sunucudaki üs durumları (`{"now": ms, "clan": id, "bases": [...]}`)
    pub fn apply_bases(&mut self, v: &Value) -> usize {
        if let Some(now) = v["now"].as_f64() {
            self.base_clock = now;
        }
        if let Some(c) = v["clan"].as_i64() {
            self.my_clan = c;
        }
        let mut n = 0;
        for b in v["bases"].as_array().map(|a| a.as_slice()).unwrap_or(&[]) {
            let Some(i) = b["id"].as_u64().map(|i| i as usize).filter(|&i| i < BASE_N) else { continue };
            let owner = b["owner"].as_i64().unwrap_or(0);
            let changed = self.bases[i].known && self.bases[i].owner != owner;
            {
                let st = &mut self.bases[i];
                st.owner = owner;
                st.owner_name = b["name"].as_str().unwrap_or("").to_string();
                st.color = b["color"].as_i64().unwrap_or(0) as i32;
                st.reward = b["reward"].as_str().unwrap_or("").to_string();
                st.reward_n = b["n"].as_u64().unwrap_or(1) as u32;
                st.reward_end_ms = b["end"].as_f64().unwrap_or(0.0);
                st.captured_ms = b["cap"].as_f64().unwrap_or(0.0);
                st.known = true;
            }
            if changed {
                self.on_base_owner_changed(i);
            }
            n += 1;
        }
        n
    }

    /// Sahip değişti: eski garnizon dağılır, yeni sahip için sıfırdan kurulur.
    fn on_base_owner_changed(&mut self, i: usize) {
        self.soldiers.retain(|s| s.base as usize != i || s.dead_t > 0.0);
        for s in self.soldiers.iter_mut().filter(|s| s.base as usize == i) {
            s.dead_t = s.dead_t.max(0.01);
        }
        let st = &mut self.bases[i];
        st.pending = 0;
        st.spawn_t = 0.0;
        st.capture_t = 0.0;
        st.cap_sent = false;
        st.rebuild = st.owner != 0;
        st.garrison = if st.owner == 0 { GARRISON as u8 } else { 0 };
        st.left_at = -1.0;
    }

    /// Ele geçirme isteği sunucuda reddedildi
    pub fn base_capture_failed(&mut self, i: usize) {
        if let Some(st) = self.bases.get_mut(i) {
            st.cap_sent = false;
            st.capture_t = 0.0;
        }
    }

    fn spawn_soldier(&mut self, i: usize, post: usize, at_post: bool) {
        let b = &base_defs()[i];
        let (px, py) = (b.tx + POSTS[post].0, b.ty + POSTS[post].1);
        let (x, y) = if at_post { (px * TILE, py * TILE) } else {
            let (hx, hy) = helipad(b);
            (hx + self.rng.range(-50.0, 50.0), hy + self.rng.range(-30.0, 30.0))
        };
        let kind = post_kind(post);
        self.soldiers.push(Soldier {
            base: i as u16,
            post: post as u8,
            kind,
            x,
            y,
            hp: SOLDIER_HP,
            level: b.level,
            inv_t: if at_post { 0.0 } else { SPAWN_INV },
            cd: self.rng.range(0.5, 2.5),
            burst: 0,
            burst_t: 0.0,
            aim_t: 0.0,
            ax: x,
            ay: y,
            face: std::f32::consts::FRAC_PI_2,
            anim_t: self.rng.range(0.0, 3.0),
            flash_t: 0.0,
            recoil: 0.0,
            dead_t: 0.0,
            walking: false,
            think_t: self.rng.range(0.0, 0.4),
            tgt: Target::None,
        });
    }

    fn free_post(&self, i: usize) -> Option<usize> {
        // keskin nişancı kuleleri önce dolar, sonra M4 mevzileri
        (0..GARRISON).find(|&p| !self.soldiers.iter().any(|s| s.base as usize == i && s.post as usize == p && s.dead_t <= 0.0))
    }

    fn alive_in(&self, i: usize) -> usize {
        self.soldiers.iter().filter(|s| s.base as usize == i && s.dead_t <= 0.0).count()
    }

    fn respawn_interval(&self, i: usize) -> f32 {
        let st = &self.bases[i];
        if st.owner == 0 {
            STATE_RESPAWN
        } else if st.rebuild {
            REBUILD_RESPAWN
        } else {
            CLAN_RESPAWN
        }
    }

    /// Yakına gelince üs canlanır: garnizon (geçen süreye göre yeniden dolmuş haliyle) mevzilerine yerleşir.
    fn activate_base(&mut self, i: usize) {
        let now_s = self.time;
        let clock = self.base_clock;
        let st = &mut self.bases[i];
        st.active = true;
        let full = GARRISON as f32;
        let n = if st.left_at > 0.0 {
            // ayrılırken kalan garnizon, uzakta geçen sürede kurallara göre dolar
            let gone = (now_s - st.left_at).max(0.0);
            let int = if st.owner == 0 { STATE_RESPAWN } else if st.rebuild { REBUILD_RESPAWN } else { CLAN_RESPAWN };
            (st.garrison as f32 + (gone / int).floor()).min(full)
        } else if st.owner == 0 {
            full
        } else {
            // klan üssü: ele geçirildiğinden beri dakikada 3 asker
            let el = ((clock - st.captured_ms) / 1000.0).max(0.0) as f32;
            (el / REBUILD_RESPAWN).floor().min(full)
        };
        st.rebuild = st.owner != 0 && n < full;
        st.spawn_t = 0.0;
        st.pending = 0;
        for _ in 0..n as usize {
            if let Some(p) = self.free_post(i) {
                self.spawn_soldier(i, p, true);
            }
        }
    }

    fn deactivate_base(&mut self, i: usize) {
        let alive = self.alive_in(i) + self.bases[i].pending as usize;
        self.soldiers.retain(|s| s.base as usize != i);
        self.helis.retain(|h| h.base as usize != i);
        let t = self.time.max(0.01);
        let st = &mut self.bases[i];
        st.active = false;
        st.garrison = alive.min(GARRISON) as u8;
        st.left_at = t;
        st.pending = 0;
        st.capture_t = 0.0;
    }

    pub fn update_bases(&mut self, dt: f32) {
        if self.bases.len() != BASE_N {
            self.bases = vec![BaseState { left_at: -1.0, ..Default::default() }; BASE_N];
        }
        self.base_clock += dt as f64 * 1000.0;
        let (px, py) = (self.player.x, self.player.y);
        for (i, b) in base_defs().iter().enumerate() {
            let (bx, by) = b.pos();
            let d = (bx - px).hypot(by - py);
            if !self.bases[i].active && d < ACT_R {
                self.activate_base(i);
            } else if self.bases[i].active && d > DEACT_R {
                self.deactivate_base(i);
            }
            if !self.bases[i].active {
                continue;
            }
            // takviye zamanlayıcısı
            let alive = self.alive_in(i) + self.bases[i].pending as usize;
            let int = self.respawn_interval(i);
            let st = &mut self.bases[i];
            st.note_t = (st.note_t - dt).max(0.0);
            if alive < GARRISON {
                st.spawn_t += dt;
                if st.spawn_t >= int {
                    st.spawn_t -= int;
                    st.pending += 1;
                }
            } else {
                st.spawn_t = 0.0;
                st.rebuild = false;
            }
            if self.bases[i].pending > 0 && !self.helis.iter().any(|h| h.base as usize == i && h.phase < 2) {
                let (hx, hy) = helipad(b);
                let a = self.rng.range(0.0, TAU);
                let (sx, sy) = (hx + a.cos() * 1700.0, hy + a.sin() * 1100.0 - 400.0);
                self.helis.push(Heli { base: i as u16, x: sx, y: sy, sx, sy, alt: 320.0, t: 0.0, phase: 0, dropped: false });
                self.events.push(Event::Heli { x: hx, y: hy });
            }
        }
        self.update_helis(dt);
        self.update_soldiers(dt);
        self.update_capture(dt);
    }

    fn update_helis(&mut self, dt: f32) {
        let mut drops: Vec<usize> = Vec::new();
        for h in self.helis.iter_mut() {
            h.t += dt;
            let b = &base_defs()[h.base as usize];
            let (hx, hy) = helipad(b);
            match h.phase {
                0 => {
                    let k = (h.t / HELI_IN).min(1.0);
                    let e = 1.0 - (1.0 - k) * (1.0 - k);
                    h.x = h.sx + (hx - h.sx) * e;
                    h.y = h.sy + (hy - h.sy) * e;
                    h.alt = 320.0 - 200.0 * e;
                    if k >= 1.0 {
                        h.phase = 1;
                        h.t = 0.0;
                    }
                }
                1 => {
                    h.alt = 120.0 - 60.0 * (h.t / HELI_HOVER).min(1.0) + (h.t * 6.0).sin() * 4.0;
                    if h.t > 1.1 && !h.dropped {
                        h.dropped = true;
                        drops.push(h.base as usize);
                    }
                    if h.t >= HELI_HOVER {
                        h.phase = 2;
                        h.t = 0.0;
                    }
                }
                _ => {
                    let k = (h.t / HELI_OUT).min(1.0);
                    h.x = hx + (hx - h.sx) * k * k;
                    h.y = hy + (hy - h.sy) * k * k * 0.5 - 400.0 * k * k;
                    h.alt = 60.0 + 300.0 * k;
                    if k >= 1.0 {
                        h.phase = 3;
                    }
                }
            }
        }
        self.helis.retain(|h| h.phase < 3);
        for i in drops {
            let n = self.bases[i].pending;
            self.bases[i].pending = 0;
            for _ in 0..n {
                if let Some(p) = self.free_post(i) {
                    self.spawn_soldier(i, p, false);
                }
            }
        }
    }

    /// En yakın düşman: düşmansa oyuncu ve köpeği, her durumda zombiler (menzil içinde).
    fn soldier_target(&self, s: &Soldier, range: f32) -> (Target, f32, f32, f32) {
        let mut best = (Target::None, 0.0, 0.0, range);
        let hostile = self.base_hostile_to_me(s.base as usize);
        let p = &self.player;
        if hostile && !p.dead && !self.passenger {
            let d = (p.x - s.x).hypot(p.y - s.y);
            if d < best.3 {
                best = (Target::Player, p.x, p.y, d);
            }
        }
        if hostile && self.player.vehicle.is_none() {
            if let Some(dg) = self.dog.as_ref().filter(|d| d.dead_t <= 0.0) {
                let d = (dg.x - s.x).hypot(dg.y - s.y);
                if d < best.3 {
                    best = (Target::Dog, dg.x, dg.y, d);
                }
            }
        }
        for z in &self.zombies {
            if z.state == ZState::Dead || z.alpha < 0.3 {
                continue;
            }
            let d = (z.x - s.x).hypot(z.y - s.y);
            if d < best.3 {
                best = (Target::Zombie(z.id), z.x, z.y, d);
            }
        }
        best
    }

    fn target_pos(&self, t: Target) -> Option<(f32, f32)> {
        match t {
            Target::None => None,
            Target::Player => (!self.player.dead).then_some((self.player.x, self.player.y)),
            Target::Dog => self.dog.as_ref().filter(|d| d.dead_t <= 0.0).map(|d| (d.x, d.y)),
            Target::Zombie(id) => self.zombies.iter().find(|z| z.id == id && z.state != ZState::Dead).map(|z| (z.x, z.y)),
        }
    }

    fn update_soldiers(&mut self, dt: f32) {
        let mut shots: Vec<(usize, Target)> = Vec::new();
        for si in 0..self.soldiers.len() {
            let mut s = self.soldiers[si].clone();
            s.anim_t += dt;
            if s.dead_t > 0.0 {
                s.dead_t += dt;
                self.soldiers[si] = s;
                continue;
            }
            s.inv_t = (s.inv_t - dt).max(0.0);
            s.cd -= dt;
            s.flash_t = (s.flash_t - dt).max(0.0);
            s.recoil = (s.recoil - dt * 6.0).max(0.0);
            s.think_t -= dt;
            let range = if s.kind == K_SNIPER { SNIPER_RANGE } else { M4_RANGE } * PX_PER_M_F;
            if s.think_t <= 0.0 {
                s.think_t = 0.35;
                let (t, ..) = self.soldier_target(&s, range);
                if t != s.tgt {
                    s.aim_t = 0.0;
                }
                s.tgt = t;
            }
            let tp = self.target_pos(s.tgt).filter(|&(x, y)| (x - s.x).hypot(y - s.y) < range * 1.15);
            if tp.is_none() {
                s.tgt = Target::None;
                s.aim_t = 0.0;
                s.burst = 0;
            }
            // mevziye yürü (helikopterden inenler) ya da mevzide hafif yer değiştir
            let b = &base_defs()[s.base as usize];
            let (mut hx, mut hy) = ((b.tx + POSTS[s.post as usize].0) * TILE, (b.ty + POSTS[s.post as usize].1) * TILE);
            if s.kind == K_M4 && tp.is_some() {
                hx += (s.anim_t * 0.7 + s.post as f32).sin() * 26.0;
            }
            let (dx, dy) = (hx - s.x, hy - s.y);
            let dd = dx.hypot(dy);
            s.walking = dd > 10.0;
            if s.walking {
                let sp = if dd > 120.0 { 2.4 } else { 1.2 } * PX_PER_M_F * dt;
                let k = (sp / dd).min(1.0);
                s.x += dx * k;
                s.y += dy * k;
                if tp.is_none() {
                    s.face = dy.atan2(dx);
                }
            }
            if let Some((tx, ty)) = tp {
                let want = (ty - s.y).atan2(tx - s.x);
                let mut da = want - s.face;
                while da > std::f32::consts::PI {
                    da -= TAU;
                }
                while da < -std::f32::consts::PI {
                    da += TAU;
                }
                s.face += da * (dt * 8.0).min(1.0);
                if s.kind == K_SNIPER {
                    if s.cd <= 0.0 {
                        // nişan alırken lazer hedefi gecikmeli izler; atış anında nereye bakıyorsa oraya
                        s.aim_t += dt;
                        let k = (dt * 3.0).min(1.0);
                        s.ax += (tx - s.ax) * k;
                        s.ay += (ty - s.ay) * k;
                        if s.aim_t >= SNIPER_AIM {
                            s.aim_t = 0.0;
                            s.cd = SNIPER_CD + self.rng.range(0.0, 1.0);
                            shots.push((si, s.tgt));
                        }
                    } else {
                        s.ax = tx;
                        s.ay = ty;
                    }
                } else if s.burst > 0 {
                    s.burst_t -= dt;
                    if s.burst_t <= 0.0 {
                        s.burst -= 1;
                        s.burst_t = 0.11;
                        shots.push((si, s.tgt));
                        if s.burst == 0 {
                            s.cd = M4_CD + self.rng.range(0.0, 0.8);
                        }
                    }
                } else if s.cd <= 0.0 {
                    s.burst = 3;
                    s.burst_t = 0.0;
                }
            }
            self.soldiers[si] = s;
        }
        for (si, t) in shots {
            self.soldier_fire(si, t);
        }
        // ölenler 6 sn sonra kalkar
        self.soldiers.retain(|s| s.dead_t < 6.0);
    }

    fn soldier_fire(&mut self, si: usize, t: Target) {
        let s = self.soldiers[si].clone();
        let Some((tx, ty)) = (if s.kind == K_SNIPER { Some((s.ax, s.ay)) } else { self.target_pos(t) }) else { return };
        let d = (tx - s.x).hypot(ty - s.y);
        let dm = d / PX_PER_M_F;
        let mut acc = if s.kind == K_SNIPER { 0.8 - 0.01 * dm } else { 0.6 - 0.025 * dm };
        if t == Target::Player {
            // koşan oyuncu daha zor vurulur; keskin nişancı lazeri izlenip kaçılabilir
            if self.player.anim == A_RUN {
                acc *= 0.75;
            }
            if s.kind == K_SNIPER {
                let (px, py) = (self.player.x, self.player.y);
                if (px - tx).hypot(py - ty) > 45.0 {
                    acc = 0.0;
                }
            }
        }
        let acc = acc.clamp(0.0, 0.92);
        let hit = self.rng.chance(acc);
        let lvl = 1.0 + 0.012 * s.level as f32;
        let dmg = if s.kind == K_SNIPER { SNIPER_DMG } else { M4_DMG } * lvl * self.rng.range(0.9, 1.1);
        let (ix, iy) = if hit {
            (tx, ty - 50.0)
        } else {
            let a = self.rng.range(0.0, TAU);
            let r = self.rng.range(40.0, 110.0);
            (tx + a.cos() * r, ty + a.sin() * r * 0.6 - 20.0)
        };
        let (mx, my) = (s.x + s.face.cos() * 46.0, s.y - 62.0 + s.face.sin() * 20.0);
        self.events.push(Event::BotShot { x1: mx, y1: my, x2: ix, y2: iy, hit, k: s.kind });
        if let Some(sv) = self.soldiers.get_mut(si) {
            sv.recoil = 1.0;
        }
        if !hit {
            return;
        }
        match t {
            Target::Player => {
                if !self.player_invulnerable() {
                    self.player.combat_t = 0.0;
                }
                self.damage_player(dmg);
            }
            Target::Dog => self.hurt_dog(dmg),
            Target::Zombie(id) => {
                if let Some(zi) = self.zombies.iter().position(|z| z.id == id) {
                    let (kx, ky) = ((tx - s.x) / d.max(1.0), (ty - s.y) / d.max(1.0));
                    // askerler zombilere karşı daha etkili (üssü temiz tutarlar)
                    self.hurt_zombie(zi, dmg * 2.5, false, kx, ky, false, false);
                }
            }
            Target::None => {}
        }
    }

    /// Oyuncu/köpek hasarı. Doğuştan 5 sn ölümsüz. Ölünce XP ve küçük ganimet.
    pub fn hurt_soldier(&mut self, si: usize, dmg: f32, by_player: bool) -> bool {
        let Some(s) = self.soldiers.get_mut(si) else { return false };
        if s.dead_t > 0.0 {
            return false;
        }
        let (x, y) = (s.x, s.y);
        if s.inv_t > 0.0 {
            self.events.push(Event::Immune { x, y: y - 120.0 });
            return true;
        }
        s.hp -= dmg;
        s.flash_t = 0.12;
        if s.tgt == Target::None && by_player {
            s.tgt = Target::Player;
        }
        self.events.push(Event::Damage { x, y: y - 110.0, v: dmg.round() as i32, crit: false });
        if s.hp <= 0.0 {
            s.hp = 0.0;
            s.dead_t = 0.01;
            let lv = s.level;
            self.events.push(Event::BotDown { x, y });
            if by_player {
                self.give_xp(x, y, 40 + lv);
                if self.rng.chance(0.3) {
                    let id = if self.rng.chance(0.3) { "medkit" } else { "bandage" };
                    self.spawn_drop_id(id, 1, x, y);
                }
            }
        }
        true
    }

    /// Yakın dövüş vuruşu askerlere (melee_hit içinden): menzil ve açı içindeki düşman askerler
    pub fn melee_soldiers(&mut self, dmg: f32, range: f32, arc: f32) -> bool {
        let (px, py) = (self.player.x, self.player.y);
        let (fx, fy) = dir_vec(self.player.dir);
        let mut hit = false;
        for si in 0..self.soldiers.len() {
            let s = &self.soldiers[si];
            if s.dead_t > 0.0 || !self.base_hostile_to_me(s.base as usize) {
                continue;
            }
            let (dx, dy) = (s.x - px, s.y - py);
            let d = dx.hypot(dy);
            if d > range + 24.0 {
                continue;
            }
            let ang = ((dx * fx + dy * fy) / d.max(1e-3)).clamp(-1.0, 1.0).acos();
            if d > 20.0 && ang > arc {
                continue;
            }
            hit |= self.hurt_soldier(si, dmg, true);
        }
        hit
    }

    /// Köpek için en yakın düşman asker (oyuncunun 6 m çevresinde)
    pub fn dog_soldier_target(&self, dx: f32, dy: f32) -> Option<(usize, f32)> {
        let (px, py) = (self.player.x, self.player.y);
        let mut best: Option<(usize, f32)> = None;
        for (i, s) in self.soldiers.iter().enumerate() {
            if s.dead_t > 0.0 || !self.base_hostile_to_me(s.base as usize) || (s.x - px).hypot(s.y - py) > m(6.0) {
                continue;
            }
            let d = (s.x - dx).hypot(s.y - dy);
            if best.is_none_or(|(_, b)| d < b) {
                best = Some((i, d));
            }
        }
        best
    }

    /// Oyuncunun içinde durduğu (ya da çok yakın) üs
    pub fn near_base(&self) -> Option<usize> {
        let (px, py) = (self.player.x, self.player.y);
        base_defs().iter().position(|b| {
            let (bx, by) = b.pos();
            (bx - px).abs() < (BASE_HALF + 6.0) * TILE && (by - py).abs() < (BASE_HALF + 6.0) * TILE
        })
    }

    fn update_capture(&mut self, dt: f32) {
        let Some(i) = self.near_base() else { return };
        let (bx, by) = base_defs()[i].pos();
        let inside = (self.player.x - bx).hypot(self.player.y - by) < CAPTURE_R && !self.player.dead;
        let alive = self.alive_in(i);
        let mine = self.my_clan > 0 && self.bases[i].owner == self.my_clan;
        let st = &mut self.bases[i];
        if mine || !st.active {
            st.capture_t = 0.0;
            return;
        }
        if inside && alive == 0 {
            if self.my_clan <= 0 {
                if st.note_t <= 0.0 {
                    st.note_t = 10.0;
                    self.events.push(Event::Msg { text: "Üssü ele geçirmek için bir klanda olmalısın".into() });
                }
                return;
            }
            if st.cap_sent {
                return;
            }
            st.capture_t += dt;
            if st.capture_t >= CAPTURE_TIME {
                st.cap_sent = true;
                self.events.push(Event::BaseCapture { id: i as u32 });
                self.events.push(Event::Msg { text: "Üs ele geçiriliyor… Karargâha bildirildi".into() });
            }
        } else if !st.cap_sent {
            st.capture_t = (st.capture_t - dt * 0.5).max(0.0);
        }
    }

    /// Çizim kayıtları: askerler, helikopterler, üs bayrakları
    pub fn draw_bases(&mut self, x0: f32, y0: f32, x1: f32, y1: f32) {
        for s in &self.soldiers {
            if s.x < x0 || s.x > x1 || s.y < y0 || s.y > y1 {
                continue;
            }
            let hostile = self.base_hostile_to_me(s.base as usize);
            let state = if s.dead_t > 0.0 {
                4.0
            } else if s.recoil > 0.0 {
                3.0
            } else if s.aim_t > 0.0 {
                2.0
            } else if s.walking {
                1.0
            } else {
                0.0
            };
            let tower = if s.post < 4 { 2.0 } else { 0.0 };
            let fx = if s.inv_t > 0.0 { 2.0 } else if s.flash_t > 0.0 { 1.0 } else { 0.0 };
            let extra = if s.dead_t > 0.0 { s.dead_t } else if s.kind == K_SNIPER && s.aim_t > 0.0 { s.aim_t / SNIPER_AIM } else { 0.0 };
            // nişan noktası (lazer) ayrı kayıtta değil: açı + mesafe yeter
            let aim_d = if s.kind == K_SNIPER && s.aim_t > 0.0 { (s.ax - s.x).hypot(s.ay - s.y) } else { 0.0 };
            self.draw_tmp.push((
                s.y + if s.post < 4 { 40.0 } else { 0.0 },
                [D_SOLDIER, s.kind as f32 + tower, s.x, s.y, s.face, state, s.anim_t, fx, s.hp / SOLDIER_HP, s.level as f32,
                 if hostile { 1.0 } else { 0.0 } + aim_d.round() * 2.0, extra],
            ));
        }
        for h in &self.helis {
            if h.x < x0 - 600.0 || h.x > x1 + 600.0 || h.y < y0 - 600.0 || h.y > y1 + 600.0 {
                continue;
            }
            let ang = if h.phase == 0 { (h.y - h.sy).atan2(h.x - h.sx) } else { (h.sy - h.y).atan2(h.sx - h.x) + std::f32::consts::PI };
            self.draw_tmp.push((h.y + 2000.0, [D_HELI, h.phase as f32, h.x, h.y, h.alt, ang, self.time, 0.0, 0.0, 0.0, 0.0, 0.0]));
        }
        for (i, b) in base_defs().iter().enumerate() {
            let (bx, by) = b.pos();
            if bx < x0 - 200.0 || bx > x1 + 200.0 || by < y0 - 200.0 || by > y1 + 300.0 {
                continue;
            }
            let st = &self.bases[i];
            let who = if st.owner == 0 { 0.0 } else if self.my_clan > 0 && st.owner == self.my_clan { 2.0 } else { 1.0 };
            let cap = if st.cap_sent { 1.0 } else { st.capture_t / CAPTURE_TIME };
            self.draw_tmp.push((
                by - 2.0,
                [D_FLAG, i as f32, bx, by, (st.color & 0xFFFFFF) as f32, who, self.time, cap, self.alive_in(i) as f32, 0.0, 0.0, 0.0],
            ));
        }
    }

    fn reward_name(id: &str) -> String {
        data::item_index(id).map_or_else(|| id.to_string(), |i| data::ITEMS[i].name.to_string())
    }

    /// Harita: keşfedilen üsler (konum, sahip, ödül, kalan süre)
    pub fn bases_json(&self) -> Value {
        let cell = world::MAP_PX / crate::progress::FOG_N as f32;
        let list: Vec<Value> = base_defs()
            .iter()
            .enumerate()
            .map(|(i, b)| {
                let (bx, by) = b.pos();
                let found = self.progress.explored((bx / cell) as usize, (by / cell) as usize);
                let st = &self.bases[i];
                let left = ((st.reward_end_ms - self.base_clock) / 1000.0).max(0.0).round();
                json!({
                    "id": i, "x": bx, "y": by, "name": b.name(), "level": b.level, "found": found,
                    "owner": if st.owner == 0 { "Devlet".to_string() } else { st.owner_name.clone() },
                    "clan": st.owner, "color": st.color, "mine": self.my_clan > 0 && st.owner == self.my_clan,
                    "reward": Game::reward_name(&st.reward), "reward_id": st.reward, "n": st.reward_n,
                    "left": if st.known { left } else { -1.0 },
                })
            })
            .collect();
        json!({ "bases": list, "clan": self.my_clan })
    }

    /// HUD: içinde bulunulan üs (ele geçirme çubuğu, kalan asker, ödül)
    pub fn base_near_json(&self) -> Value {
        let Some(i) = self.near_base() else { return json!({}) };
        let b = &base_defs()[i];
        let st = &self.bases[i];
        let left = ((st.reward_end_ms - self.base_clock) / 1000.0).max(0.0).round();
        let alive = self.alive_in(i);
        json!({
            "id": i, "name": b.name(), "level": b.level,
            "owner": if st.owner == 0 { "Devlet".to_string() } else { st.owner_name.clone() },
            "mine": self.my_clan > 0 && st.owner == self.my_clan, "inclan": self.my_clan > 0,
            "alive": alive, "max": GARRISON, "pending": st.pending,
            "cap": if st.cap_sent { 1.0 } else { st.capture_t / CAPTURE_TIME }, "sent": st.cap_sent,
            "reward": Game::reward_name(&st.reward), "n": st.reward_n, "left": if st.known { left } else { -1.0 },
        })
    }
}

const PX_PER_M_F: f32 = crate::data::PX_PER_M;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::Progress;

    fn game() -> Game {
        let mut g = Game::new(7, Progress::default(), 0);
        g.update_bases(0.0);
        g
    }

    #[test]
    fn fifty_bases_in_valid_places() {
        let d = base_defs();
        assert_eq!(d.len(), BASE_N);
        for (i, b) in d.iter().enumerate() {
            let z = zone_at_tile(b.tx as usize, b.ty as usize);
            assert!(!z.safe(), "üs {i} güvenli bölgede");
            for c in d.iter().skip(i + 1) {
                assert!((b.tx - c.tx).hypot(b.ty - c.ty) >= 40.0);
            }
        }
        // çoğu yeni bölgelerde
        let new = d.iter().filter(|b| b.tx >= world::OLD_TILES as f32 || b.ty >= world::OLD_TILES as f32).count();
        assert!(new >= 40, "{new}");
    }

    fn goto_base(g: &mut Game, i: usize) {
        let (bx, by) = base_defs()[i].pos();
        g.player.x = bx;
        g.player.y = by + (BASE_HALF + 4.0) * TILE;
        g.update_bases(0.016);
    }

    #[test]
    fn state_base_full_garrison_and_30s_respawn() {
        let mut g = game();
        goto_base(&mut g, 0);
        assert_eq!(g.alive_in(0), GARRISON);
        assert_eq!(g.soldiers.iter().filter(|s| s.base == 0 && s.kind == K_SNIPER).count(), 5);
        // birini öldür: 30 sn sonra helikopterle yenisi iner, 5 sn ölümsüz
        let si = g.soldiers.iter().position(|s| s.base == 0).unwrap();
        g.soldiers[si].inv_t = 0.0;
        g.hurt_soldier(si, 5000.0, true);
        g.player.god = true;
        for _ in 0..(29.0 / 0.05) as usize {
            g.update_bases(0.05);
        }
        assert_eq!(g.bases[0].pending, 0);
        for _ in 0..(2.0 / 0.05) as usize {
            g.update_bases(0.05);
        }
        assert_eq!(g.bases[0].pending, 1);
        assert!(g.helis.iter().any(|h| h.base == 0));
        for _ in 0..(HELI_IN + 2.0) as usize * 20 {
            g.update_bases(0.05);
        }
        assert_eq!(g.alive_in(0), GARRISON);
        let fresh = g.soldiers.iter().filter(|s| s.base == 0 && s.dead_t <= 0.0).filter(|s| s.inv_t > 0.0).count();
        assert_eq!(fresh, 1, "yeni asker ölümsüz doğmalı");
    }

    #[test]
    fn clan_capture_rebuilds_three_per_minute() {
        let mut g = game();
        g.apply_bases(&json!({"now": 1_000_000.0, "clan": 5, "bases": [{"id": 1, "owner": 0, "reward": "titan_shard", "end": 2_200_000.0}]}));
        goto_base(&mut g, 1);
        g.player.god = true;
        for s in g.soldiers.iter_mut() {
            s.inv_t = 0.0;
        }
        for si in 0..g.soldiers.len() {
            g.hurt_soldier(si, 5000.0, true);
        }
        assert_eq!(g.alive_in(1), 0);
        // ele geçirme alanında 10 sn → istek
        let (bx, by) = base_defs()[1].pos();
        g.player.x = bx;
        g.player.y = by;
        g.events.clear();
        for _ in 0..(CAPTURE_TIME / 0.05) as usize + 4 {
            g.update_bases(0.05);
            g.soldiers.retain(|s| s.base != 1 || s.dead_t > 0.0); // takviyeleri saymayalım
            g.bases[1].pending = 0;
        }
        assert!(g.events.iter().any(|e| matches!(e, Event::BaseCapture { id: 1 })));
        // sunucu onayladı: klan 5 sahibi; garnizon sıfırdan dakikada 3
        g.apply_bases(&json!({"now": 1_011_000.0, "clan": 5, "bases": [{"id": 1, "owner": 5, "name": "Kurtlar", "cap": 1_011_000.0, "reward": "titan_shard", "end": 2_200_000.0}]}));
        assert_eq!(g.alive_in(1), 0);
        assert!(g.bases[1].rebuild);
        assert!((g.respawn_interval(1) - 20.0).abs() < 1e-3);
        let mut dropped = 0;
        for _ in 0..(61.0 / 0.05) as usize {
            g.update_bases(0.05);
            dropped = g.alive_in(1) + g.bases[1].pending as usize;
        }
        assert_eq!(dropped, 3, "dakikada 3 asker");
        // dost asker oyuncuya ateş etmez
        assert!(!g.base_hostile_to_me(1));
    }

    #[test]
    fn soldiers_shoot_nearest_including_dog() {
        let mut g = game();
        goto_base(&mut g, 2);
        let s = g.soldiers.iter().find(|s| s.base == 2 && s.kind == K_M4).unwrap().clone();
        g.progress.dog_owned = true;
        g.dog = Some(crate::pet::Dog { x: s.x + 80.0, y: s.y, hp: 100.0, ..Default::default() });
        g.player.x = s.x + 400.0;
        g.player.y = s.y;
        let (t, ..) = g.soldier_target(&s, M4_RANGE * PX_PER_M_F);
        assert_eq!(t, Target::Dog);
        g.dog = None;
        let (t, ..) = g.soldier_target(&s, M4_RANGE * PX_PER_M_F);
        assert_eq!(t, Target::Player);
        // klan üssü: üyeye ateş yok
        g.my_clan = 9;
        g.bases[2].owner = 9;
        let (t, ..) = g.soldier_target(&s, M4_RANGE * PX_PER_M_F);
        assert_eq!(t, Target::None);
    }

    /// Kaba denge: 3 kişilik Sv10 ekip (katana, ilk yardım kitleriyle) bir üssü düşürebilmeli; yalnız Sv10 zorlanmalı.
    #[test]
    fn rough_balance() {
        // gelen hasar: bir anda en fazla ~4 asker (2 M4 + 2 keskin nişancı) görüş/menzil içinde
        let m4_dps = M4_DMG * 3.0 * 0.45 / (M4_CD + 0.4) * 1.55;
        let sn_dps = SNIPER_DMG * 0.5 / (SNIPER_CD + SNIPER_AIM + 0.5) * 1.55;
        let incoming = 2.0 * m4_dps + 2.0 * sn_dps;
        // giden hasar: katana 33 × 1.5/sn, Sv10 ~%30 bonus, yorgunluk payı %70
        let out_one = 33.0 * 1.5 * 1.3 * 0.7;
        let hp_total = SOLDIER_HP * GARRISON as f32;
        let t_three = hp_total / (3.0 * out_one);
        let t_one = hp_total / out_one;
        // Sv10: 145 can + 6 ilk yardım (60) + 2 can iksiri (50)
        let pool = 145.0 + 6.0 * 60.0 + 2.0 * 50.0;
        let survive_split = pool / (incoming / 3.0);
        let survive_solo = pool / incoming;
        assert!(survive_split > t_three, "3 kişi: {t_three:.0} sn savaş, {survive_split:.0} sn dayanır");
        assert!(survive_solo < t_one, "yalnız Sv10 kolay olmamalı: {t_one:.0} sn savaş, {survive_solo:.0} sn dayanır");
    }
}
