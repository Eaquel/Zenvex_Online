//! Araçlar: binme/inme, sürüş, yakıt, çarpışma, zombilere çarpma.
use crate::data::{self, VEHICLES, ZOMBIES};
use crate::game::*;
use std::f32::consts::{PI, TAU};

impl Game {
    pub fn nearest_vehicle(&self, max_d: f32) -> Option<usize> {
        let (px, py) = (self.player.x, self.player.y);
        let mut best: Option<(usize, f32)> = None;
        for (i, v) in self.vehicles.iter().enumerate() {
            if v.wrecked || v.driver {
                continue;
            }
            let d = (v.x - px).hypot(v.y - py) - VEHICLES[v.kind].radius;
            if d < max_d && best.is_none_or(|(_, bd)| d < bd) {
                best = Some((i, d));
            }
        }
        best.map(|(i, _)| i)
    }

    pub fn toggle_vehicle(&mut self) -> Result<String, String> {
        if self.player.dead {
            return Err("Ölüyken olmaz".into());
        }
        if let Some(i) = self.player.vehicle {
            let v = &mut self.vehicles[i];
            v.driver = false;
            v.speed = 0.0;
            let side = v.angle + PI / 2.0;
            let r = VEHICLES[v.kind].radius + 30.0;
            let (mut x, mut y) = (v.x + side.sin() * r, v.y + side.cos() * r);
            self.world.resolve(
                &mut x,
                &mut y,
                14.0,
                self.progress.level >= crate::progress::CITY_LEVEL,
                &mut self.scratch,
            );
            self.player.x = x;
            self.player.y = y;
            self.player.vehicle = None;
            self.player.anim = A_IDLE;
            self.events.push(Event::ExitVehicle);
            return Ok("Araçtan indin".into());
        }
        let Some(i) = self.nearest_vehicle(70.0) else {
            return Err("Yakında araç yok".into());
        };
        let v = &mut self.vehicles[i];
        v.driver = true;
        self.player.vehicle = Some(i);
        self.player.atk_active = false;
        let name = VEHICLES[v.kind].name;
        self.events.push(Event::EnterVehicle { v: v.kind as u8 });
        self.story_event(crate::story::Ev::Ping("drive"));
        Ok(format!("{name} sürüyorsun"))
    }

    pub fn update_vehicle_drive(&mut self, dt: f32, input: Input) {
        let Some(vi) = self.player.vehicle else {
            return;
        };
        self.player.anim = A_RIDE;
        self.player.anim_t += dt;
        let gate_open = self.progress.level >= crate::progress::CITY_LEVEL;
        let def = &VEHICLES[self.vehicles[vi].kind];
        // aşınma: sürücü koltuğundayken (dursa da) her 30/60 sn'de azami canın %1'i; birikim kayıtta (in-bin sıfırlamaz)
        let slot = if self.vehicles[vi].owned { data::veh_slot(self.vehicles[vi].kind) } else { None };
        if let Some(s) = slot {
            let w = &mut self.progress.veh_wear[s];
            *w += dt;
            while *w >= data::VEH_WEAR_S[s] {
                *w -= data::VEH_WEAR_S[s];
                self.vehicles[vi].hp -= def.hp * 0.01;
            }
        }
        let mlen = input.move_x.hypot(input.move_y).min(1.0);
        {
            let v = &mut self.vehicles[vi];
            let has_fuel = !def.fuel || v.fuel > 0.0;
            if mlen > 0.1 && has_fuel {
                let target = input.move_x.atan2(input.move_y);
                let mut da = (target - v.angle) % TAU;
                if da > PI {
                    da -= TAU;
                }
                if da < -PI {
                    da += TAU;
                }
                let turn = 3.2 * dt * (0.4 + 0.6 * (v.speed / m(def.speed_m)).min(1.0));
                v.angle += da.clamp(-turn, turn);
                let tgt_speed = m(def.speed_m) * mlen * if da.abs() > 1.6 { 0.3 } else { 1.0 };
                v.speed += (tgt_speed - v.speed) * (1.0 - (-2.2 * dt).exp());
            } else {
                v.speed *= (1.0 - 1.8 * dt).max(0.0);
            }
            if def.fuel && v.speed > 1.0 {
                v.fuel = (v.fuel - v.speed / crate::data::PX_PER_M * dt * 0.06).max(0.0);
            }
        }
        // yakıt bittiyse çantadaki benzini kullan
        if def.fuel && self.vehicles[vi].fuel <= 0.0 && self.progress.inv.remove_id("fuel", 1) {
            self.vehicles[vi].fuel = 50.0;
            self.msg("Benzin dolduruldu (+50)");
        }
        let v = &self.vehicles[vi];
        let (dx, dy) = (v.angle.sin(), v.angle.cos());
        let (mut x, mut y) = (v.x + dx * v.speed * dt, v.y + dy * v.speed * dt);
        let r = def.radius * 0.7;
        let (ox, oy) = (v.x, v.y);
        let hit = self
            .world
            .resolve_move(ox, oy, &mut x, &mut y, r, gate_open, &mut self.scratch);
        let speed = v.speed;
        if hit && speed > m(3.0) {
            let dmg = speed / crate::data::PX_PER_M * 2.0;
            self.vehicles[vi].hp -= dmg;
            self.vehicles[vi].speed *= 0.3;
            self.events.push(Event::CarHit { x, y });
        }
        // zombilere çarp
        let mut hits = Vec::new();
        for (zi, z) in self.zombies.iter().enumerate() {
            if z.state == ZState::Dead {
                continue;
            }
            if (z.x - x).hypot(z.y - y) < r + ZOMBIES[z.kind as usize].radius && speed > m(2.0) {
                hits.push(zi);
            }
        }
        for zi in hits {
            let dmg = speed / crate::data::PX_PER_M * 7.0;
            self.hurt_zombie(zi, dmg, false, dx, dy, true, true);
            self.vehicles[vi].speed *= 0.85;
            self.vehicles[vi].hp -= 3.0;
            self.events.push(Event::CarHit { x, y });
        }
        let v = &mut self.vehicles[vi];
        v.x = x;
        v.y = y;
        self.player.x = x;
        self.player.y = y;
        self.player.dir = dir_from_vec(dx, dy);
        if let Some(s) = slot {
            self.progress.veh_hp[s] = v.hp.max(0.0);
        }
        if v.hp <= 0.0 {
            v.wrecked = true;
            v.driver = false;
            let (vx, vy) = (v.x, v.y);
            self.player.vehicle = None;
            self.explode(vx, vy, m(3.0), 40.0);
            self.damage_player(20.0);
            if let Some(s) = slot {
                // patlayan kendi aracın: yeniden çağırma beklemesi (oyun süresi; çıkıp girmek kısaltmaz)
                self.progress.veh_hp[s] = 0.0;
                self.progress.veh_wear[s] = 0.0;
                self.progress.veh_ready[s] = self.progress.play_time + data::VEH_RECALL_S[s];
                self.msg(format!("Araç patladı! {} sn sonra yeniden çağırabilirsin", data::VEH_RECALL_S[s] as i64));
            } else {
                self.msg("Araç patladı!");
            }
        }
        // yerdeki eşyaları araçla da topla
        let _ = data::ITEMS.len();
    }

    pub fn vehicle_dir(angle: f32) -> u8 {
        dir_from_vec(angle.sin(), angle.cos())
    }
}

#[cfg(test)]
mod tests {
    use crate::data::{VEHICLES, VEH_RECALL_S};
    use crate::game::{Game, Input};
    use crate::Progress;

    fn game() -> Game {
        let mut p = Progress::default();
        p.level = 20;
        let mut g = Game::new(3, p, 0);
        g.player.combat_t = 99.0;
        g
    }

    fn drive(g: &mut Game, secs: f32) {
        let n = (secs / 0.05) as usize;
        for _ in 0..n {
            g.progress.play_time += 0.05;
            g.update_vehicle_drive(0.05, Input::default());
        }
    }

    /// Araba 500, motor 200 can; sürerken aşınma: araba 60 sn'de %1, motor 30 sn'de %1; in-bin birikimi sıfırlamaz
    #[test]
    fn wear_while_driving_survives_dismount() {
        let mut g = game();
        assert_eq!(VEHICLES[2].hp, 500.0);
        assert_eq!(VEHICLES[1].hp, 200.0);
        g.summon_vehicle(2).unwrap();
        g.player.x = g.vehicles[0].x;
        g.player.y = g.vehicles[0].y;
        g.toggle_vehicle().unwrap();
        drive(&mut g, 45.0);
        g.toggle_vehicle().unwrap(); // in
        g.toggle_vehicle().unwrap(); // bin
        drive(&mut g, 16.0);
        assert!((g.vehicles[0].hp - 495.0).abs() < 0.01, "61 sn sürüşte %1 aşınma: {}", g.vehicles[0].hp);
        assert!((g.progress.veh_hp[1] - 495.0).abs() < 0.01);
        // motor: 30 sn'de %1 (2 can)
        let mut g = game();
        g.summon_vehicle(1).unwrap();
        g.player.x = g.vehicles[0].x;
        g.player.y = g.vehicles[0].y;
        g.toggle_vehicle().unwrap();
        drive(&mut g, 61.0);
        assert!((g.vehicles[0].hp - 196.0).abs() < 0.01, "{}", g.vehicles[0].hp);
    }

    /// Tür değiştirmek ya da oyundan çıkıp girmek (kayıt + yeni oyun) canı yenilemez
    #[test]
    fn hp_is_saved_per_vehicle() {
        let mut g = game();
        g.summon_vehicle(2).unwrap();
        g.player.x = g.vehicles[0].x;
        g.player.y = g.vehicles[0].y;
        g.toggle_vehicle().unwrap();
        g.vehicles[0].hp = 300.0;
        drive(&mut g, 0.1);
        g.toggle_vehicle().unwrap();
        g.summon_vehicle(1).unwrap();
        assert_eq!(g.vehicles[0].hp, 200.0);
        g.summon_vehicle(2).unwrap();
        assert!((g.vehicles[0].hp - 300.0).abs() < 0.01, "tür değiştirince can yenilendi: {}", g.vehicles[0].hp);
        // çık-gir: kayıttan yeni oyun
        let save = serde_json::to_string(&g.progress).unwrap();
        let p: Progress = serde_json::from_str(&save).unwrap();
        let mut g2 = Game::new(3, p, 0);
        g2.player.combat_t = 99.0;
        g2.summon_vehicle(2).unwrap();
        assert!((g2.vehicles[0].hp - 300.0).abs() < 0.01, "{}", g2.vehicles[0].hp);
    }

    /// Patlayınca araba 60, motor 30 sn (oyun süresi) çağrılamaz; sonra tam canla gelir; araçtayken hasarı araç alır
    #[test]
    fn explosion_starts_recall_cooldown() {
        let mut g = game();
        g.summon_vehicle(2).unwrap();
        g.player.x = g.vehicles[0].x;
        g.player.y = g.vehicles[0].y;
        g.toggle_vehicle().unwrap();
        // savaş bölgesinde gelen hasar araca gider
        let (x, y) = (g.player.x, g.player.y);
        if !g.world.zone(x, y).safe() {
            let hp0 = g.player.hp;
            g.damage_player(50.0);
            assert_eq!(g.player.hp, hp0, "araba hasarın hepsini almalı");
        }
        g.vehicles[0].hp = 1.0;
        g.player.god = true;
        drive(&mut g, 0.1);
        g.vehicles[0].hp = -1.0;
        drive(&mut g, 0.05);
        assert!(g.player.vehicle.is_none());
        assert_eq!(g.progress.veh_hp[1], 0.0);
        let e = g.summon_vehicle(2).unwrap_err();
        assert!(e.contains("sn sonra"), "{e}");
        // çıkıp girmek beklemeyi kısaltmaz (oyun süresi kayıtta)
        let p: Progress = serde_json::from_str(&serde_json::to_string(&g.progress).unwrap()).unwrap();
        let mut g = Game::new(3, p, 0);
        g.player.combat_t = 99.0;
        assert!(g.summon_vehicle(2).is_err());
        g.progress.play_time += VEH_RECALL_S[1];
        g.summon_vehicle(2).unwrap();
        assert_eq!(g.vehicles[0].hp, 500.0);
    }
}
