//! Savaş: yakın dövüş, ateşli silahlar, mermiler, patlamalar, zombi hasarı, ganimet.
use crate::data::{self, QuestKind, WeaponClass, ZKind, ZOMBIES};
use crate::game::*;
use crate::world::{Kind, Zone};

/// Ağaç/taşa vuruş oranı (kesim hızı)

impl Game {
    /// PvP açık mı: devlet üssü çevresi ya da yüksek seviyeli bölge (Donmuş Liman, Cephe Hattı); güvenli bölgede asla.
    pub fn pvp_at(&self, x: f32, y: f32) -> bool {
        let z = self.world.zone(x, y);
        if z.safe() {
            return false;
        }
        if matches!(z, Zone::Military | Zone::Harbor | Zone::Front) {
            return true;
        }
        let r = (crate::bases::BASE_HALF + 6.0) * crate::world::TILE;
        crate::bases::base_defs().iter().any(|b| {
            let (bx, by) = b.pos();
            (bx - x).abs() < r && (by - y).abs() < r
        })
    }

    /// Yakın dövüş vuruşu düşman oyunculara: olay olarak istemciye gider (hasar hedefin cihazında uygulanır).
    fn melee_players(&mut self, base: f32, range: f32, arc: f32) -> bool {
        let (px, py, pdir) = (self.player.x, self.player.y, self.player.dir);
        if !self.pvp_at(px, py) {
            return false;
        }
        let (fx, fy) = dir_vec(pdir);
        let mut hit = false;
        for k in 0..self.remotes.len() {
            if !self.remote_hostile.get(k).copied().unwrap_or(false) {
                continue;
            }
            let r = &self.remotes[k];
            if r.vehicle >= 1.0 {
                continue;
            }
            let (rx, ry) = (r.x, r.y);
            let (dx, dy) = (rx - px, ry - py);
            let d = dx.hypot(dy);
            if d > range + 18.0 || !self.pvp_at(rx, ry) {
                continue;
            }
            let ang = ((dx * fx + dy * fy) / d.max(1e-3)).clamp(-1.0, 1.0).acos();
            if d > 20.0 && ang > arc {
                continue;
            }
            let crit = self.rng.chance(self.progress.crit_chance());
            let mut v = base * self.rng.range(0.9, 1.1);
            if crit {
                v *= 1.75 + self.progress.bonus().crit_dmg;
            }
            // oyuncular arası hasar zombilere göre düşük (zırhı hedef cihaz uygular)
            v *= 0.6;
            self.events.push(Event::PvpHit { k: k as u32, v, crit });
            self.events.push(Event::Damage { x: rx, y: ry - 110.0, v: v.round() as i32, crit });
            hit = true;
        }
        hit
    }

    /// Uzak oyuncunun vuruşu (ağdan): yalnız ikimiz de PvP bölgesindeysek; bir vuruşta en fazla 400.
    pub fn pvp_damage(&mut self, v: f32) -> bool {
        if self.player.dead || !self.pvp_at(self.player.x, self.player.y) {
            return false;
        }
        self.pvp_hit_t = 6.0;
        self.player.combat_t = 0.0;
        self.damage_player(v.clamp(0.0, 400.0));
        true
    }

    /// Önündeki yayda zombilere, yoksa ağaç/kaya/sandığa vurur.
    pub fn melee_hit(&mut self, base: f32, range: f32, arc: f32, stun: f32) {
        let (px, py, pdir) = (self.player.x, self.player.y, self.player.dir);
        let (fx, fy) = dir_vec(pdir);
        let mult = self.damage_mult();
        let mut hit_any = false;
        for i in 0..self.zombies.len() {
            let z = &self.zombies[i];
            if z.state == ZState::Dead {
                continue;
            }
            let (dx, dy) = (z.x - px, z.y - py);
            let d = dx.hypot(dy);
            let zr = ZOMBIES[z.kind as usize].radius;
            if d > range + zr {
                continue;
            }
            let ang = ((dx * fx + dy * fy) / d.max(1e-3)).clamp(-1.0, 1.0).acos();
            if d > 20.0 && ang > arc {
                continue;
            }
            hit_any = true;
            let crit = self.rng.chance(self.progress.crit_chance());
            let mut dmg = base * mult * self.rng.range(0.9, 1.1);
            if crit {
                dmg *= 1.75 + self.progress.bonus().crit_dmg;
            }
            if self.player.one_hit {
                dmg = 1e6;
            }
            let st = self.rng.chance(stun.max(self.progress.stun_chance()));
            self.hurt_zombie(i, dmg, crit, fx, fy, st, true);
            let ls = self.progress.bonus().lifesteal;
            if ls > 0.0 && dmg < 1e5 {
                let max_hp = self.progress.max_hp();
                self.player.hp = (self.player.hp + dmg * ls).min(max_hp);
            }
        }
        // devlet üssü askerleri (düşmansa)
        let sdmg = base * mult * self.rng.range(0.9, 1.1) * if self.player.one_hit { 1e4 } else { 1.0 };
        hit_any |= self.melee_soldiers(sdmg, range, arc);
        // PvP: üslerde ve yüksek seviyeli bölgelerde düşman oyunculara (klan arkadaşı hariç)
        hit_any |= self.melee_players(base * mult, range, arc);
        if hit_any {
            if matches!(self.player.anim, A_CHOP | A_MINE) {
                self.player.anim = A_PUNCH;
            }
            self.player.combat_t = 0.0;
            self.events.push(Event::Hit {
                x: px + fx * 30.0,
                y: py + fy * 30.0 - 60.0,
                kind: 0,
            });
            return;
        }
        // statik hedef
        self.world.query(
            px - 120.0,
            py - 120.0,
            px + 120.0,
            py + 120.0,
            &mut self.scratch,
        );
        let mut best: Option<(usize, f32)> = None;
        for &i in &self.scratch {
            let s = &self.world.statics[i as usize];
            if s.broken || !matches!(s.kind, Kind::Tree | Kind::Rock | Kind::Crate) {
                continue;
            }
            let (dx, dy) = (s.x - px, s.y - py);
            let d = dx.hypot(dy);
            let sr = match s.shape {
                crate::world::Shape::Circle(r) => r,
                _ => 20.0,
            };
            if d > range + sr {
                continue;
            }
            let ang = ((dx * fx + dy * fy) / d.max(1e-3)).clamp(-1.0, 1.0).acos();
            if d > 24.0 && ang > arc + 0.25 {
                continue;
            }
            if best.is_none_or(|(_, bd)| d < bd) {
                best = Some((i as usize, d));
            }
        }
        if let Some((i, _)) = best {
            // yumrukla ağaç/taş: elle çizilmiş balta ve kazma pozu (vuruş zamanlaması aynı anim_t'den)
            if matches!(self.player.anim, A_PUNCH | A_CHOP | A_MINE) {
                self.player.anim = match self.world.statics[i].kind {
                    Kind::Tree => A_CHOP,
                    Kind::Rock => A_MINE,
                    _ => A_PUNCH,
                };
            }
            let dmg = if self.player.one_hit {
                1e6
            } else {
                base * mult
            };
            self.hit_static(i, dmg);
        }
    }

    pub fn hit_static(&mut self, i: usize, dmg: f32) {
        let gather = self.progress.gather_mult();
        // V11: ağaç ve kaya 500 can; vuruş gücü silaha değil alete bağlı (yumruk 25, taş alet 50, demir 100)
        let tool = match self.world.statics[i].kind {
            Kind::Tree => Some(self.progress.tool_power("t_axe_")),
            Kind::Rock => Some(self.progress.tool_power("t_pick_")),
            _ => None,
        };
        let s = &mut self.world.statics[i];
        let dmg = if self.player.one_hit { dmg } else { tool.unwrap_or(dmg) };
        s.hp -= dmg;
        s.shake = 0.3;
        let (sx, sy, kind) = (s.x, s.y, s.kind);
        let name = crate::world::STATIC_NAMES[s.sprite as usize];
        self.events.push(Event::Hit {
            x: sx,
            y: sy - 40.0,
            kind: match kind {
                Kind::Tree => 1,
                Kind::Rock => 2,
                _ => 3,
            },
        });
        self.events.push(Event::Damage {
            x: sx,
            y: sy - 120.0,
            v: dmg.min(9999.0) as i32,
            crit: false,
        });
        if s.hp > 0.0 {
            return;
        }
        s.broken = true;
        s.respawn = match kind {
            Kind::Tree => 120.0,
            Kind::Rock => 150.0,
            _ => 180.0,
        };
        let zone = self.world.zone(sx, sy);
        match kind {
            Kind::Tree => {
                self.progress.trees += 1;
                self.events.push(Event::TreeFall { x: sx, y: sy });
                let wood = ((3 + self.rng.below(4)) as f32 * gather).round() as u32;
                self.spawn_drop_id("wood", wood, sx, sy);
                if name.starts_with("pine") {
                    let rv = self.rng.below(3);
                    self.spawn_drop_id("resin", 1 + rv, sx, sy);
                }
                self.give_xp(sx, sy, 10);
                self.story_event(crate::story::Ev::Chop);
                let done = self.progress.quest_event(QuestKind::Chop, 1);
                self.quests_done(done);
            }
            Kind::Rock => {
                let n = ((3 + self.rng.below(3)) as f32 * gather).round() as u32;
                self.spawn_drop_id("stone", n, sx, sy);
                if self.rng.chance(0.25) {
                    self.spawn_drop_id("scrap", 1, sx, sy);
                }
                self.give_xp(sx, sy, 8);
            }
            Kind::Crate => {
                self.progress.crates += 1;
                self.events.push(Event::Crate { x: sx, y: sy });
                let mil = name == "crate_mil";
                self.crate_loot(sx, sy, zone, mil);
                self.give_xp(sx, sy, 12);
                let done = self.progress.quest_event(QuestKind::Crate, 1);
                self.quests_done(done);
            }
            _ => {}
        }
    }

    /// Sandık: temel malzemeler + bulunduğu bölgenin özel malzemesi + ara sıra şifa/iksir (V12.1).
    fn crate_loot(&mut self, x: f32, y: f32, zone: Zone, mil: bool) {
        let mult = zone.loot_mult() * (1.0 + self.progress.luck()) * if mil { 1.5 } else { 1.0 };
        let gather = self.progress.gather_mult();
        let common: &[(&str, u32)] = &[("cloth", 3), ("scrap", 3), ("leather", 2), ("bone", 2), ("bandage", 1), ("food", 1)];
        for _ in 0..(2 + self.rng.below(2)) {
            let (id, n) = common[self.rng.below(common.len() as u32) as usize];
            let c = ((1 + self.rng.below(n)) as f32 * gather).round() as u32;
            self.spawn_drop_id(id, c.max(1), x, y);
        }
        // bölge malzemesi: her sandıkta 1, askerî sandıkta 2
        for _ in 0..if mil { 2 } else { 1 } {
            if let Some(id) = self.region_pick(zone) {
                let c = ((1 + self.rng.below(2)) as f32 * gather).round() as u32;
                self.spawn_drop_id(id, c.max(1), x, y);
            }
        }
        let rare: &[(&str, f32)] = &[
            ("energy", 0.2), ("medkit", 0.1), ("potion_heal", 0.08), ("potion_speed", 0.05), ("w_knife", 0.03), ("w_katana", 0.004),
        ];
        let low = zone.level_range().1 < 15;
        for &(id, p) in rare {
            // başlangıç bölgesinde epik/efsanevi eşya çıkmaz
            if low && data::item(id).rarity >= data::Rarity::Epic {
                continue;
            }
            if self.rng.chance(p * mult) {
                self.spawn_drop_id(id, 1, x, y);
            }
        }
    }

    /// Bölgenin özel malzemelerinden ağırlıklı biri (bölgede yoksa None)
    pub(crate) fn region_pick(&mut self, zone: Zone) -> Option<&'static str> {
        let table = data::region_items(zone);
        let total: u32 = table.iter().map(|e| e.1).sum();
        if total == 0 {
            return None;
        }
        let mut k = self.rng.below(total);
        for &(id, w) in table {
            if k < w {
                return Some(id);
            }
            k -= w;
        }
        None
    }

    pub fn fire(&mut self, w: &'static data::WeaponDef, aim: (f32, f32)) {
        let mag = self.progress.mag(w.item);
        if mag == 0 {
            if self.progress.inv.count_id(w.ammo) > 0 {
                self.start_reload(w);
            } else if self.player.noammo_t <= 0.0 {
                self.player.noammo_t = 2.0;
                self.events.push(Event::NoAmmo);
            }
            return;
        }
        let b = self.progress.bonus();
        let keep = self.player.inf_ammo;
        let mag = if keep { mag + 1 } else { mag };
        self.progress.set_mag(w.item, mag - 1);
        self.player.atk_cd = 1.0 / (w.rate * (1.0 + b.atk_speed));
        self.player.combat_t = 0.0;
        let (px, py) = (self.player.x, self.player.y);
        let spread = w.spread * self.rng.range(-1.0, 1.0);
        let base_a = aim.0.atan2(aim.1);
        let a = base_a + spread;
        let (dx, dy) = (a.sin(), a.cos());
        let speed = match w.class {
            WeaponClass::Heavy => m(16.0),
            WeaponClass::Sniper => m(60.0),
            _ => m(38.0),
        };
        let range = m(w.range_m);
        let mult = self.damage_mult();
        let crit = self.rng.chance(self.progress.crit_chance());
        let dmg = if self.player.one_hit {
            1e6
        } else {
            w.damage * mult * (1.0 + b.blade_pct) * if crit { 1.75 + b.crit_dmg } else { 1.0 }
        };
        // namlu: karakterin önünde, el yüksekliğinde
        let mx = px + dx * 40.0;
        let my = py + dy * 28.0;
        self.projectiles.push(Projectile {
            kind: if w.class == WeaponClass::Heavy {
                ProjKind::Rocket
            } else {
                ProjKind::Bullet
            },
            x: mx,
            y: my,
            vx: dx * speed,
            vy: dy * speed,
            dmg,
            pierce: w.pierce,
            life: range / speed,
            splash: m(w.splash_m),
            hostile: false,
            hit: Vec::new(),
        });
        self.events.push(Event::Shoot {
            x: mx,
            y: my - 70.0,
            a,
            w: data::weapon_for(w.item) as u8,
        });
        if mag - 1 == 0 && self.progress.inv.count_id(w.ammo) > 0 {
            self.start_reload(w);
        }
    }

    pub fn start_reload(&mut self, w: &'static data::WeaponDef) {
        if self.player.reload_t > 0.0 {
            return;
        }
        self.player.reload_t = w.reload;
        self.events.push(Event::Reload);
    }

    pub fn finish_reload(&mut self) {
        let w = self.weapon();
        if w.ammo.is_empty() {
            return;
        }
        let have = self.progress.inv.count_id(w.ammo);
        let cur = self.progress.mag(w.item) as u32;
        let need = (w.mag as u32).saturating_sub(cur).min(have);
        if need > 0 {
            self.progress.inv.remove_id(w.ammo, need);
            self.progress.set_mag(w.item, (cur + need) as u16);
        }
    }

    pub fn update_projectiles(&mut self, dt: f32) {
        let mut i = 0;
        while i < self.projectiles.len() {
            let mut remove = false;
            let steps = 3;
            for _ in 0..steps {
                let p = &mut self.projectiles[i];
                p.x += p.vx * dt / steps as f32;
                p.y += p.vy * dt / steps as f32;
                let (x, y) = (p.x, p.y);
                if p.hostile {
                    if (x - self.player.x).hypot(y - self.player.y) < 24.0 && !self.player.dead {
                        let d = p.dmg;
                        self.damage_player(d);
                        self.player.poison_t = self.player.poison_t.max(3.0);
                        remove = true;
                        break;
                    }
                } else {
                    // zombiler
                    let mut hit_z = None;
                    for (zi, z) in self.zombies.iter().enumerate() {
                        if z.state == ZState::Dead || p.hit.contains(&zi) {
                            continue;
                        }
                        let r = ZOMBIES[z.kind as usize].radius + 8.0;
                        if (z.x - x).hypot(z.y - y) < r {
                            hit_z = Some(zi);
                            break;
                        }
                    }
                    if let Some(zi) = hit_z {
                        let p = &mut self.projectiles[i];
                        if p.splash > 0.0 {
                            let (r, d) = (p.splash, p.dmg);
                            self.explode(x, y, r, d);
                            remove = true;
                            break;
                        }
                        p.hit.push(zi);
                        let (vx, vy, d) = (p.vx, p.vy, p.dmg);
                        let l = vx.hypot(vy).max(1.0);
                        let crit = false;
                        self.hurt_zombie(zi, d, crit, vx / l * 0.6, vy / l * 0.6, false, true);
                        let p = &mut self.projectiles[i];
                        if p.pierce == 0 {
                            remove = true;
                            break;
                        }
                        p.pierce -= 1;
                    }
                }
                // katı nesneler
                if let Some(si) = self.world.solid_at(x, y, &mut self.scratch) {
                    let p = &self.projectiles[i];
                    if p.splash > 0.0 {
                        let (r, d) = (p.splash, p.dmg);
                        self.explode(x, y, r, d);
                    } else if !p.hostile {
                        let k = self.world.statics[si].kind;
                        if matches!(k, Kind::Crate) {
                            let d = p.dmg;
                            self.hit_static(si, d);
                        } else {
                            self.events.push(Event::Hit {
                                x,
                                y: y - 50.0,
                                kind: 4,
                            });
                        }
                    }
                    remove = true;
                    break;
                }
            }
            if !remove {
                let p = &mut self.projectiles[i];
                p.life -= dt;
                if p.life <= 0.0 {
                    if p.splash > 0.0 && p.kind == ProjKind::Rocket {
                        let (x, y, r, d) = (p.x, p.y, p.splash, p.dmg);
                        self.explode(x, y, r, d);
                    }
                    remove = true;
                }
            }
            if remove {
                self.projectiles.swap_remove(i);
            } else {
                i += 1;
            }
        }
    }

    pub fn explode(&mut self, x: f32, y: f32, r: f32, dmg: f32) {
        self.events.push(Event::Explode { x, y, r });
        self.areas.push(Area {
            kind: AreaKind::Explosion,
            x,
            y,
            r,
            life: 0.5,
            max_life: 0.5,
            dps: 0.0,
            hit_done: true,
        });
        for zi in 0..self.zombies.len() {
            let z = &self.zombies[zi];
            if z.state == ZState::Dead {
                continue;
            }
            let d = (z.x - x).hypot(z.y - y);
            if d < r {
                let k = 1.0 - d / r * 0.6;
                let (dx, dy) = ((z.x - x) / d.max(1.0), (z.y - y) / d.max(1.0));
                self.hurt_zombie(zi, dmg * k, false, dx, dy, false, true);
            }
        }
    }

    /// Zombiye hasar; ölürse ganimet ve XP. `by_player`: görev/istatistik sayılır mı.
    pub fn hurt_zombie(
        &mut self,
        i: usize,
        dmg: f32,
        crit: bool,
        kx: f32,
        ky: f32,
        stun: bool,
        by_player: bool,
    ) {
        if self.net == 2 {
            // istemci: hasarı ev sahibi uygular; burada sadece geri bildirim
            let z = &mut self.zombies[i];
            if z.state == ZState::Dead {
                return;
            }
            z.flash_t = 0.1;
            z.bar_t = 3.0;
            let (id, zx, zy) = (z.id, z.x, z.y);
            if by_player {
                self.events.push(Event::ZHit { id, dmg, kx, ky, stun });
                self.events.push(Event::Damage { x: zx, y: zy - 120.0, v: dmg.min(99999.0) as i32, crit });
            }
            return;
        }
        let z = &mut self.zombies[i];
        if z.state == ZState::Dead {
            return;
        }
        z.hp -= dmg;
        z.flash_t = 0.1;
        z.bar_t = 3.0;
        let heavy = matches!(z.kind, ZKind::Tank | ZKind::Titan);
        // hafif itme: vuruş hissedilir ama zombi saldırısını kesmez (eskiden 140/220 savruluyordu, oyun çok kolaydı)
        let push = if heavy {
            0.0
        } else if stun {
            90.0
        } else if crit {
            55.0
        } else {
            25.0
        };
        z.kx = kx * push;
        z.ky = ky * push;
        let (zx, zy) = (z.x, z.y);
        let head = if z.kind == ZKind::Titan {
            300.0
        } else if z.kind == ZKind::Tank {
            150.0
        } else {
            120.0
        };
        self.events.push(Event::Damage {
            x: zx,
            y: zy - head,
            v: dmg.min(99999.0).round() as i32,
            crit,
        });
        // Gizemli: ilk vuruşta kopya
        if z.kind == ZKind::Mystery && !z.cloned && !z.decoy {
            z.cloned = true;
            let mut c = z.clone();
            c.decoy = true;
            c.hp = 1.0;
            c.max_hp = 1.0;
            c.x += 60.0;
            self.zombies.push(c);
            let z = &mut self.zombies[i];
            z.x -= 40.0;
            self.events.push(Event::Teleport { x: zx, y: zy });
        }
        let z = &mut self.zombies[i];
        if z.hp <= 0.0 {
            z.state = ZState::Dead;
            z.anim = Z_DEATH;
            z.anim_t = 0.0;
            z.timer = 3.0;
            self.on_zombie_killed(i, by_player);
        } else if stun && !heavy && z.state != ZState::Flee {
            // yalnız sersemletme durdurur; sıradan vuruşta zombi saldırmaya devam eder
            z.state = ZState::Hurt;
            z.anim = Z_HURT;
            z.anim_t = 0.0;
            z.timer = 1.0;
        } else if matches!(z.state, ZState::Wander | ZState::Notice) && !z.ret {
            // vurulan zombi hemen saldırgana döner
            z.state = ZState::Chase;
            z.anim = Z_WALK;
            z.anim_t = 0.0;
        }
    }

    fn on_zombie_killed(&mut self, i: usize, by_player: bool) {
        let z = self.zombies[i].clone();
        if z.decoy {
            return;
        }
        if self.kill_credit >= 0 {
            // uzak oyuncu öldürdü: ödül ona gider
            self.events.push(Event::NetKill { k: self.kill_credit, kind: z.kind as u8, level: z.level, x: z.x, y: z.y });
            self.events.push(Event::Kill { x: z.x, y: z.y, z: z.kind as u8 });
            return;
        }
        if !by_player {
            // kule askeri öldürdü: XP, görev ve ganimet oyuncuya yazılmaz
            self.events.push(Event::Kill { x: z.x, y: z.y, z: z.kind as u8 });
            return;
        }
        self.kill_rewards(z);
    }

    /// Bölgeye özel eşya (V12.1): %25 (seçkinlerde %60) şansla zombinin öldüğü bölgenin malzemelerinden biri.
    fn region_drop(&mut self, z: &Zombie) {
        let zone = self.world.zone(z.x, z.y);
        let pc = if matches!(z.kind, ZKind::Tank | ZKind::Mystery | ZKind::Titan) { 0.6 } else { 0.25 };
        if self.rng.chance(pc) {
            if let Some(id) = self.region_pick(zone) {
                self.spawn_drop_id(id, 1, z.x, z.y);
            }
        }
    }

    /// Öldürme ödülleri (yerel ya da ağdan gelen öldürme için).
    pub fn kill_rewards(&mut self, z: Zombie) {
        let def = &ZOMBIES[z.kind as usize];
        let kh = self.progress.bonus().kill_heal;
        if kh > 0.0 && !self.player.dead {
            let max_hp = self.progress.max_hp();
            self.player.hp = (self.player.hp + max_hp * kh).min(max_hp);
        }
        // iksirler: %4 (seçkinlerde %15)
        let pc = if matches!(z.kind, ZKind::Tank | ZKind::Mystery | ZKind::Titan) { 0.15 } else { 0.04 };
        if self.rng.chance(pc) {
            let pots = ["potion_heal", "potion_heal", "potion_speed", "potion_power"];
            let id = pots[self.rng.below(pots.len() as u32) as usize];
            self.spawn_drop_id(id, 1, z.x, z.y);
        }
        self.region_drop(&z);
        self.progress.kills += 1;
        self.progress.kills_by[z.kind as usize] += 1;
        if self.dog_hit {
            self.progress.ev.dog_kills += 1;
        }
        self.events.push(Event::Kill {
            x: z.x,
            y: z.y,
            z: z.kind as u8,
        });
        self.story_event(crate::story::Ev::Kill { kind: z.kind, x: z.x, y: z.y });
        let mut done = self.progress.quest_event(QuestKind::Kill, 1);
        done.extend(
            self.progress
                .quest_event(QuestKind::KillType(z.kind as u8), 1),
        );
        if z.kind == ZKind::Toxic {
            self.areas.push(Area {
                kind: AreaKind::PoisonCloud,
                x: z.x,
                y: z.y,
                r: m(2.2),
                life: 5.0,
                max_life: 5.0,
                dps: 6.0,
                hit_done: false,
            });
            self.events.push(Event::ToxicCloud { x: z.x, y: z.y });
        }
        // ganimet
        let zone = self.world.zone(z.x, z.y);
        let mult = zone.loot_mult() * (1.0 + self.progress.luck());
        // para düşmez: altın günlük görev ve etkinliklerden gelir
        if self.rng.chance(0.35 * mult.min(2.0)) {
            self.spawn_drop_id("bone", 1, z.x, z.y);
        }
        if self.rng.chance(0.35) && z.kind != ZKind::Dog {
            let rv = self.rng.below(2);
            self.spawn_drop_id("cloth", 1 + rv, z.x, z.y);
        }
        match z.kind {
            ZKind::Dog => {
                if self.rng.chance(0.5) {
                    self.spawn_drop_id("leather", 1, z.x, z.y);
                }
            }
            // malzemeler bölgeye özel (region_drop); türler yalnız temel malzeme ve nadir hazır eşya verir
            ZKind::Dark => {
                if self.rng.chance(0.008 * mult) {
                    self.spawn_drop_id("neck_dark", 1, z.x, z.y);
                }
            }
            ZKind::Tank => {
                let rv = self.rng.below(3);
                self.spawn_drop_id("scrap", 1 + rv, z.x, z.y);
            }
            ZKind::Mystery => {
                for (id, p) in [("w_katana", 0.03), ("ring_hunter", 0.05), ("neck_amulet", 0.05)] {
                    if self.rng.chance(p * mult) {
                        self.spawn_drop_id(id, 1, z.x, z.y);
                    }
                }
            }
            ZKind::Titan => {
                self.titan_dead = true;
                self.progress.boss_kills += 1;
                done.extend(self.progress.quest_event(QuestKind::Boss, 1));
                let rv = self.rng.below(3);
                self.spawn_drop_id("titan_shard", 3 + rv, z.x, z.y);
                for id in ["w_katana", "potion_power", "potion_speed", "potion_heal", "ring_titan"] {
                    if self.rng.chance(0.25) {
                        self.spawn_drop_id(id, 1, z.x, z.y);
                    }
                }
                self.msg("TİTAN YENİLDİ! Titan Parçaları düştü.");
            }
            _ => {}
        }
        // genel nadir düşüş: şifa
        if self.rng.chance(0.03 * mult) {
            let id = if self.rng.chance(0.5) { "medkit" } else { "energy" };
            self.spawn_drop_id(id, 1, z.x, z.y);
        }
        let lf = crate::progress::xp_level_factor(self.progress.level, z.level, z.boss);
        let xp = ((def.xp + z.level * 3) as f32 * crate::data::species(z.species).map_or(1.0, |s| s.xp) * lf).round() as u32;
        if xp > 0 {
            self.give_xp(z.x, z.y, xp);
        } else if self.time - self.xp_warn_t > 60.0 {
            self.xp_warn_t = self.time;
            let min = self.progress.level.saturating_sub(crate::progress::XP_LEVEL_GAP).max(1);
            self.msg(&format!("XP kazanmak için seviyene uygun zombi avla (Sv {min}+)."));
        }
        self.progress.pass.xp += if z.boss { 500 } else { 5 + z.level / 3 };
        self.quests_done(done);
    }

    pub fn give_xp(&mut self, x: f32, y: f32, xp: u32) {
        let xp = if self.hardcore { xp + xp / 2 } else { xp };
        // klan tecrübe güçlendirmesi: seviye başı %3
        let xp = (xp as f32 * (1.0 + 0.03 * self.clan_boost[1] as f32)).round() as u32;
        self.events.push(Event::Xp {
            x,
            y: y - 140.0,
            v: xp,
        });
        let c = self.progress.character;
        let clvl = self.progress.char_level[c];
        let ups = self.progress.add_xp(xp);
        if self.progress.char_level[c] > clvl {
            self.events.push(Event::CharLevel {
                level: self.progress.char_level[c],
            });
        }
        if ups > 0 {
            self.play_pose(A_CHEER, 1.2);
            self.player.hp = self.progress.max_hp();
            self.player.stamina = self.progress.max_stamina();
            self.events.push(Event::LevelUp {
                level: self.progress.level,
                points: self.progress.points,
            });
            let done = self.progress.quest_event(QuestKind::Level, 0);
            self.quests_done(done);
            if self.progress.level == crate::progress::CITY_LEVEL {
                self.msg("Şehir kapısı açıldı! Kuzeye git.");
                self.msg("Cephe Hattı açıldı: üslere Büyücü Aldric ışınlar (kampta).");
            }
        }
    }

    pub fn update_areas(&mut self, dt: f32) {
        let (px, py) = (self.player.x, self.player.y);
        let mut pdmg = 0.0;
        let mut shock: Option<(f32, f32, f32)> = None;
        for a in self.areas.iter_mut() {
            a.life -= dt;
            match a.kind {
                AreaKind::ToxicPuddle | AreaKind::PoisonCloud => {
                    if (px - a.x).hypot(py - a.y) < a.r {
                        pdmg += a.dps * dt;
                        self.player.poison_t = self.player.poison_t.max(1.5);
                    }
                }
                AreaKind::Shockwave => {
                    let k = 1.0 - a.life / a.max_life;
                    let rr = a.r * k;
                    let d = (px - a.x).hypot(py - a.y);
                    if !a.hit_done && (d - rr).abs() < 40.0 {
                        a.hit_done = true;
                        shock = Some((a.x, a.y, a.dps));
                    }
                }
                _ => {}
            }
        }
        self.areas.retain(|a| a.life > 0.0);
        if pdmg > 0.0 {
            self.damage_player_dot(pdmg);
        }
        if let Some((sx, sy, d)) = shock {
            // Titan dalgası: takla atan kaçar; değerse geri savrulur ve yere düşer
            if !self.player_invulnerable() {
                self.damage_player_blast(d);
                let (dx, dy) = (px - sx, py - sy);
                let l = dx.hypot(dy).max(1.0);
                let (mut nx, mut ny) = (px + dx / l * 110.0, py + dy / l * 110.0);
                let gate = self.progress.level >= crate::progress::CITY_LEVEL;
                self.world.resolve(&mut nx, &mut ny, 14.0, gate, &mut self.scratch);
                crate::areas::clamp(px, py, &mut nx, &mut ny);
                self.player.x = nx;
                self.player.y = ny;
                self.player.hurt_t = 1.1;
                self.player.knocked = true;
                self.player.atk_active = false;
                self.events.push(Event::Knockdown);
            }
        }
    }
}
