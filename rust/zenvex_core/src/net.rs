//! Çevrimiçi eşitleme: ev sahibi zombi anlık görüntüsü üretir, istemciler uygular.
//! Kayıt (NET_STRIDE): id, tür, x, y, yön, anim, anim_t, can, maks_can, seviye, alfa, bayrak(1 öfke, 2 boss, 4 ölü)
use crate::data::ZKind;
use crate::game::*;

pub const NET_STRIDE: usize = 12;

fn kind_of(k: u8) -> ZKind {
    match k {
        1 => ZKind::Runner,
        2 => ZKind::Dog,
        3 => ZKind::Toxic,
        4 => ZKind::Dark,
        5 => ZKind::Tank,
        6 => ZKind::Mystery,
        7 => ZKind::Titan,
        _ => ZKind::Walker,
    }
}

impl Game {
    /// Ev sahibi: yaşayan zombilerin anlık görüntüsü; yazılan kayıt sayısını döndürür.
    pub fn net_snapshot(&self, out: &mut [f32]) -> usize {
        let mut n = 0;
        for z in &self.zombies {
            if (n + 1) * NET_STRIDE > out.len() {
                break;
            }
            if z.decoy && z.state == ZState::Dead {
                continue;
            }
            let flags = (z.enraged as u8) | ((z.boss as u8) << 1) | (((z.state == ZState::Dead) as u8) << 2);
            let rec = [
                z.id as f32, (z.kind as u8 + z.species * 8) as f32, z.x, z.y, z.dir as f32, z.anim as f32, z.anim_t, z.hp, z.max_hp,
                z.level as f32, z.alpha, flags as f32,
            ];
            out[n * NET_STRIDE..(n + 1) * NET_STRIDE].copy_from_slice(&rec);
            n += 1;
        }
        n
    }

    /// İstemci: ev sahibinin görüntüsünü uygular (eksikleri oluşturur, olmayanları siler).
    pub fn net_apply(&mut self, data: &[f32]) {
        let mut seen = Vec::with_capacity(data.len() / NET_STRIDE);
        for r in data.chunks(NET_STRIDE) {
            if r.len() < NET_STRIDE {
                break;
            }
            let id = r[0] as u32;
            seen.push(id);
            let idx = self.zombies.iter().position(|z| z.id == id);
            let i = match idx {
                Some(i) => i,
                None => {
                    // tür alanı: temel tür + 8 × (bölge yaratığı indeksi + 1)
                    let code = r[1] as u32;
                    let s = (code / 8) as u8;
                    let mut z = if crate::data::species(s).is_some() { self.make_species(s, r[9] as u32, r[2], r[3]) } else { self.make_zombie(kind_of((code % 8) as u8), r[9] as u32, r[2], r[3]) };
                    z.id = id;
                    self.zombies.push(z);
                    self.zombies.len() - 1
                }
            };
            let now = self.time;
            let z = &mut self.zombies[i];
            let flags = r[11] as u8;
            // iki görüntü arası hız (sıçrama/ışınlanmada sıfır)
            let gap = now - z.snap_at;
            let (vx, vy) = if idx.is_some() && gap > 0.05 && gap < 1.0 { ((r[2] - z.snap_x) / gap, (r[3] - z.snap_y) / gap) } else { (0.0, 0.0) };
            let fast = vx.hypot(vy) > 900.0;
            z.snap_vx = if fast { 0.0 } else { vx };
            z.snap_vy = if fast { 0.0 } else { vy };
            z.snap_at = now;
            z.snap_x = r[2];
            z.snap_y = r[3];
            z.dir = r[4] as u8 & 7;
            let anim = r[5] as u8;
            if anim != z.anim {
                z.anim = anim;
                z.anim_t = r[6];
            }
            if r[7] < z.hp {
                z.bar_t = 3.0;
            }
            z.hp = r[7];
            z.max_hp = r[8];
            z.level = r[9] as u32;
            z.alpha = r[10];
            z.enraged = flags & 1 != 0;
            z.boss = flags & 2 != 0;
            if flags & 4 != 0 {
                z.state = ZState::Dead;
            } else if z.state == ZState::Dead {
                z.state = ZState::Chase;
            }
        }
        self.zombies.retain(|z| seen.contains(&z.id));
    }

    /// Ev sahibi: istemciden gelen vuruşu uygula (killer: Kotlin'in oyuncu tablosundaki sırası).
    pub fn net_hit(&mut self, id: u32, dmg: f32, kx: f32, ky: f32, stun: bool, killer: i32) {
        if let Some(i) = self.zombies.iter().position(|z| z.id == id) {
            self.kill_credit = killer;
            let mode = self.net;
            self.net = 1;
            self.hurt_zombie(i, dmg, false, kx, ky, stun, false);
            self.net = mode;
            self.kill_credit = -1;
        }
    }

    /// Ağ modu değişimi. İstemciden ev sahibine geçerken zombiler kaldığı yerden çalışmaya devam eder.
    pub fn set_net(&mut self, mode: u8) {
        if self.net == 2 && mode != 2 {
            for z in self.zombies.iter_mut() {
                z.x = z.snap_x;
                z.y = z.snap_y;
                if z.state != ZState::Dead {
                    z.state = ZState::Wander;
                    z.timer = 0.5;
                }
            }
        }
        if mode == 2 && self.net != 2 {
            self.zombies.clear();
        }
        // kimlik çakışmasını önle
        if mode == 1 {
            let mx = self.zombies.iter().map(|z| z.id).max().unwrap_or(0);
            self.next_zid = self.next_zid.max(mx + 1);
        }
        self.net = mode;
    }

    /// Ağdan gelen öldürme ödülü (bu oyuncu öldürdü, ev sahibi hesapladı).
    pub fn net_kill(&mut self, kind: u8, level: u32, x: f32, y: f32) {
        let z = self.make_zombie(kind_of(kind), level, x, y);
        self.kill_rewards(z);
    }

    /// Ev sahibinden gelen zombi hasarı.
    pub fn net_damage(&mut self, v: f32, poison: bool) {
        if poison {
            self.player.poison_t = self.player.poison_t.max(4.0);
        }
        self.damage_player(v);
    }

    /// Yolcu modu: sürücünün konumunu takip et.
    pub fn ride(&mut self, on: bool, x: f32, y: f32) {
        if on && self.player.vehicle.is_some() {
            return;
        }
        self.passenger = on;
        if on {
            self.player.x = x;
            self.player.y = y;
            self.player.anim = A_RIDE;
        } else {
            let (mut px, mut py) = (x + 60.0, y + 20.0);
            self.world.resolve(&mut px, &mut py, 14.0, self.progress.level >= crate::progress::CITY_LEVEL, &mut self.scratch);
            self.player.x = px;
            self.player.y = py;
            self.player.anim = A_IDLE;
        }
    }
}
