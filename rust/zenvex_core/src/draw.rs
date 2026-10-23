//! Çizim listesi (y-sıralı), HUD ve radar.
use crate::data::{self, VEHICLES, ZOMBIES};
use crate::game::*;
use crate::world::{Kind, Shape, STATIC_NAMES};

/// HUD dizisinin uzunluğu (Kotlin Native.HUD_SIZE)
pub const HUD_N: usize = 41;

/// Ekipman kodları (Kotlin ile ortak): 0 yok, 1 basit, 2 polis, 3 askerî, 4 titan
pub fn tier_code(id: &str) -> f32 {
    if id.is_empty() {
        0.0
    } else if id.ends_with("basic") {
        1.0
    } else if id.ends_with("police") {
        2.0
    } else if id.ends_with("military") {
        3.0
    } else if id.ends_with("titan") {
        4.0
    } else {
        0.0
    }
}

impl Game {
    /// Yerel oyuncunun ekipman paketleri [kask*8+ceket, bot*8+çanta, silah+pelerin*16]
    pub fn equip_codes(&self) -> [f32; 3] {
        let e = &self.progress.eq;
        let w = data::weapon_for(&e.weapon) as f32;
        let cloak = if e.cloak.is_empty() { 0.0 } else { 1.0 };
        [
            tier_code(&e.helmet) * 8.0 + tier_code(&e.jacket),
            tier_code(&e.boots) * 8.0 + tier_code(&e.bag),
            w + cloak * 16.0,
        ]
    }

    pub fn draw_list(
        &mut self,
        vx: f32,
        vy: f32,
        vw: f32,
        vh: f32,
        out: &mut [f32],
        deco_density: f32,
    ) -> usize {
        let margin = 260.0;
        let (x0, y0, x1, y1) = (
            vx - margin,
            vy - margin,
            vx + vw + margin,
            vy + vh + margin * 2.5,
        );
        self.draw_tmp.clear();
        self.world.query(x0, y0, x1, y1, &mut self.scratch);
        for &i in &self.scratch {
            let s = &self.world.statics[i as usize];
            if s.x < x0 || s.x > x1 || s.y < y0 || s.y > y1 {
                continue;
            }
            if s.kind == Kind::Deco && ((s.phase * 1000.0) % 1.0) > deco_density {
                continue;
            }
            let mut sprite = s.sprite as f32;
            if s.broken {
                match s.kind {
                    Kind::Tree if s.sprite == crate::world::sprite("burnt_tree") => sprite = crate::world::sprite("burnt_stump") as f32,
                    Kind::Tree => sprite = crate::world::sprite("stump") as f32,
                    Kind::Rock | Kind::Crate => continue,
                    _ => {}
                }
            }
            let shake = if s.shake > 0.0 {
                (s.shake * 60.0).sin() * 3.0 * (s.shake / 0.3)
            } else {
                0.0
            };
            let hp = if matches!(s.kind, Kind::Tree | Kind::Rock | Kind::Crate)
                && s.hp < s.max_hp
                && !s.broken
            {
                s.hp / s.max_hp
            } else {
                -1.0
            };
            let key = s.y + s.sort_off;
            let kind = s.kind as u8 as f32;
            let (hw, hh) = match s.shape {
                Shape::Rect(a, b) => (a, b),
                Shape::Circle(r) => (r, r),
                Shape::None => (0.0, 0.0),
            };
            self.draw_tmp.push((
                key,
                [
                    D_STATIC, sprite, s.x, s.y, i as f32, kind, s.phase, shake, hp, hw, hh, 0.0,
                ],
            ));
        }
        for d in &self.drops {
            if d.x < x0 || d.x > x1 || d.y < y0 || d.y > y1 {
                continue;
            }
            let def = &data::ITEMS[d.item as usize];
            self.draw_tmp.push((
                d.y - 1.0,
                [
                    D_DROP,
                    d.item as f32,
                    d.x,
                    d.y,
                    0.0,
                    0.0,
                    d.age,
                    def.rarity as u8 as f32,
                    d.count as f32,
                    d.pop,
                    0.0,
                    0.0,
                ],
            ));
        }
        for a in &self.areas {
            if a.x < x0 - a.r || a.x > x1 + a.r || a.y < y0 - a.r || a.y > y1 + a.r {
                continue;
            }
            // yer efektleri en altta çizilir (anahtar çok küçük)
            let key = if matches!(
                a.kind,
                AreaKind::ToxicPuddle | AreaKind::Heal | AreaKind::Shockwave
            ) {
                -1e9 + a.y
            } else {
                a.y + 1.0
            };
            self.draw_tmp.push((
                key,
                [
                    D_AREA,
                    a.kind as u8 as f32,
                    a.x,
                    a.y,
                    0.0,
                    0.0,
                    a.max_life - a.life,
                    a.r,
                    a.life / a.max_life,
                    0.0,
                    0.0,
                    0.0,
                ],
            ));
        }
        for z in &self.zombies {
            if z.x < x0 || z.x > x1 || z.y < y0 || z.y > y1 {
                continue;
            }
            let hp = if (z.bar_t > 0.0 || z.boss) && z.state != ZState::Dead {
                z.hp / z.max_hp
            } else {
                -1.0
            };
            let flash = if z.flash_t > 0.0 { 1.0 } else { 0.0 };
            // bit 0: öfkeli; üstü: azami can (Kotlin can çubuğunda "kalan/azami" yazar)
            let special = if z.enraged { 1.0 } else { 0.0 } + 2.0 * z.max_hp.round().min(4_000_000.0);
            self.draw_tmp.push((
                z.y,
                [
                    D_ZOMBIE,
                    z.kind as u8 as f32,
                    z.x,
                    z.y,
                    z.dir as f32,
                    z.anim as f32,
                    z.anim_t,
                    flash,
                    hp,
                    z.level as f32,
                    z.alpha,
                    special,
                ],
            ));
        }
        for p in &self.projectiles {
            if p.x < x0 || p.x > x1 || p.y < y0 || p.y > y1 {
                continue;
            }
            let ang = p.vx.atan2(p.vy);
            self.draw_tmp.push((
                p.y + 30.0,
                [
                    D_PROJ,
                    p.kind as u8 as f32,
                    p.x,
                    p.y,
                    0.0,
                    0.0,
                    p.life,
                    ang,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                ],
            ));
        }
        for (i, v) in self.vehicles.iter().enumerate() {
            if v.x < x0 || v.x > x1 || v.y < y0 || v.y > y1 {
                continue;
            }
            let driver = if v.driver {
                self.progress.character as f32
            } else {
                -1.0
            };
            let hp = v.hp / VEHICLES[v.kind].hp;
            let wreck = if v.wrecked { 1.0 } else { 0.0 };
            self.draw_tmp.push((
                v.y,
                [
                    D_VEHICLE,
                    v.kind as f32,
                    v.x,
                    v.y,
                    Game::vehicle_dir(v.angle) as f32,
                    0.0,
                    v.speed,
                    driver,
                    hp,
                    wreck,
                    i as f32,
                    0.0,
                ],
            ));
        }
        for n in &self.npcs {
            if n.x < x0 || n.x > x1 || n.y < y0 || n.y > y1 {
                continue;
            }
            // keskin nişancı kulede: kule yüksekliği kadar yukarıda çizilir
            // karakter kodu: 4 asker, 5 tüccar, 6 görevci, 7 doktor, 8 tamirci, 9 köpek eğitmeni
            let (ch, anim, lift) = if n.kind == 0 {
                (4.0, A_RIFLE_IDLE as f32, 1.0)
            } else {
                (4.0 + n.kind as f32, A_IDLE as f32, 0.0)
            };
            self.draw_tmp.push((
                n.y + 2.0,
                [
                    D_HUMAN,
                    ch,
                    n.x,
                    n.y,
                    n.dir as f32,
                    anim,
                    n.anim_t,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    -2.0 - lift,
                ],
            ));
        }
        for (ri, r) in self.remotes.iter().enumerate() {
            // kişisel üs: herkesin üssü aynı yerde; başka oyuncular orada görünmez
            if r.x < x0 || r.x > x1 || r.y < y0 || r.y > y1 || Game::in_home(r.x, r.y) {
                continue;
            }
            self.draw_tmp.push((
                r.y,
                [
                    D_HUMAN,
                    r.character as f32,
                    r.x,
                    r.y,
                    r.dir as f32,
                    r.anim as f32,
                    r.anim_t,
                    0.0,
                    r.equip[0],
                    r.equip[1],
                    r.equip[2],
                    ri as f32,
                ],
            ));
        }
        if self.player.vehicle.is_none() && !self.passenger {
            let p = &self.player;
            let flash = if p.flash_t > 0.0 {
                1.0
            } else if p.cloak_t > 0.0 {
                2.0
            } else {
                0.0
            };
            let eq = self.equip_codes();
            self.draw_tmp.push((
                p.y,
                [
                    D_HUMAN,
                    self.progress.character as f32,
                    p.x,
                    p.y,
                    p.dir as f32,
                    p.anim as f32,
                    p.anim_t,
                    flash,
                    eq[0],
                    eq[1],
                    eq[2],
                    -1.0,
                ],
            ));
        }
        if self.progress.character == 1 && !self.player.dead {
            self.draw_tmp.push((
                self.drone.y + 80.0,
                [
                    D_DRONE,
                    0.0,
                    self.drone.x,
                    self.drone.y,
                    0.0,
                    0.0,
                    self.drone.t,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                ],
            ));
        }
        if let Some(e) = self.dog_draw() {
            self.draw_tmp.push(e);
        }
        self.draw_bases(x0, y0, x1, y1);
        self.draw_tmp
            .sort_by(|a, b| a.0.partial_cmp(&b.0).unwrap_or(std::cmp::Ordering::Equal));
        let cap = out.len() / STRIDE;
        let n = self.draw_tmp.len().min(cap);
        for (k, (_, rec)) in self.draw_tmp.iter().take(n).enumerate() {
            out[k * STRIDE..(k + 1) * STRIDE].copy_from_slice(rec);
        }
        n
    }

    /// HUD dizisi (Kotlin Native.HUD_* ile aynı sıra)
    pub fn hud(&self) -> [f32; HUD_N] {
        let p = &self.player;
        let g = &self.progress;
        let w = self.weapon();
        let mag = g.mag(w.item) as f32;
        let ammo = if w.ammo.is_empty() {
            -1.0
        } else {
            g.inv.count_id(w.ammo) as f32
        };
        let near_v = if p.vehicle.is_none() && self.nearest_vehicle(70.0).is_some() {
            1.0
        } else {
            0.0
        };
        let near_m = self
            .world
            .merchants
            .iter()
            .any(|&(x, y)| (x - p.x).hypot(y - p.y) < 150.0);
        let near_b = self
            .world
            .benches
            .iter()
            .any(|&(x, y)| (x - p.x).hypot(y - p.y) < 150.0);
        let boss = self
            .zombies
            .iter()
            .find(|z| z.boss && z.state != ZState::Dead && (z.x - p.x).hypot(z.y - p.y) < 1400.0);
        let (bhp, bmax) = boss.map_or((-1.0, 0.0), |b| (b.hp, b.max_hp));
        let veh = p.vehicle.map(|i| &self.vehicles[i]);
        [
            p.hp,
            g.max_hp(),
            p.stamina,
            g.max_stamina(),
            g.xp as f32,
            crate::progress::xp_to_next(g.level) as f32,
            g.level as f32,
            g.points as f32,
            g.gold as f32,
            g.kills as f32,
            p.x,
            p.y,
            if p.dead { 1.0 } else { 0.0 },
            p.shield,
            self.light(),
            data::weapon_for(&g.eq.weapon) as f32,
            mag,
            ammo,
            if p.reload_t > 0.0 { 1.0 } else { 0.0 },
            p.ability_cd,
            g.character as f32,
            g.char_level[g.character] as f32,
            near_v,
            if near_m { 1.0 } else { 0.0 },
            if near_b { 1.0 } else { 0.0 },
            if veh.is_some() { 1.0 } else { 0.0 },
            veh.map_or(0.0, |v| v.fuel),
            veh.map_or(0.0, |v| v.hp / VEHICLES[v.kind].hp),
            bhp,
            bmax,
            p.poison_t,
            p.zone as f32,
            p.speed_t,
            p.power_t,
            p.heal_t,
            p.pot_cd[0],
            p.pot_cd[1],
            p.pot_cd[2],
            p.food_t.max(0.0),
            p.energy_t,
            if self.pvp_at(p.x, p.y) { 1.0 } else { 0.0 },
        ]
    }

    /// Radar: [x, y, tür, ...] — tür: 0 zombi, 1 boss, 2 araç, 3 tüccar, 4 uzak oyuncu, 5 görev hedefi, 6 devlet üssü, 7 asker
    pub fn radar(&self, out: &mut [f32]) -> usize {
        let mut k = 0;
        let mut push = |x: f32, y: f32, t: f32, out: &mut [f32]| {
            if k + 3 <= out.len() {
                out[k] = x;
                out[k + 1] = y;
                out[k + 2] = t;
                k += 3;
            }
        };
        let (px, py) = (self.player.x, self.player.y);
        for z in &self.zombies {
            if z.state != ZState::Dead && z.alpha > 0.3 {
                push(z.x, z.y, if z.boss { 1.0 } else { 0.0 }, out);
            }
        }
        for v in &self.vehicles {
            if !v.wrecked && (v.x - px).hypot(v.y - py) < 3000.0 {
                push(v.x, v.y, 2.0, out);
            }
        }
        for &(x, y) in &self.world.merchants {
            push(x, y, 3.0, out);
        }
        for r in &self.remotes {
            if !Game::in_home(r.x, r.y) {
                push(r.x, r.y, 4.0, out);
            }
        }
        // keşfedilen devlet üsleri (6) ve düşman askerler (7)
        let cell = crate::world::MAP_PX / crate::progress::FOG_N as f32;
        for b in crate::bases::base_defs() {
            let (bx, by) = b.pos();
            if (bx - px).hypot(by - py) < 6000.0 && self.progress.explored((bx / cell) as usize, (by / cell) as usize) {
                push(bx, by, 6.0, out);
            }
        }
        for s in &self.soldiers {
            if s.dead_t <= 0.0 {
                push(s.x, s.y, 7.0, out);
            }
        }
        for (x, y) in self.story_targets() {
            push(x, y, 5.0, out);
        }
        let _ = (STATIC_NAMES.len(), ZOMBIES.len());
        k / 3
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
