//! İskelet: kemik dünya dönüşümleri, doğrusal deri bağlama, iki kemikli IK.

use crate::math::{v3, Xf, M3, V3};
use crate::model::Model;

pub const PELVIS: usize = 0;
pub const SPINE: usize = 1;
pub const HEAD: usize = 2;
pub const UPPER_R: usize = 3;
pub const FORE_R: usize = 4;
pub const HAND_R: usize = 5;
pub const UPPER_L: usize = 6;
pub const FORE_L: usize = 7;
pub const HAND_L: usize = 8;
pub const THIGH_R: usize = 9;
pub const SHIN_R: usize = 10;
pub const THIGH_L: usize = 11;
pub const SHIN_L: usize = 12;
pub const NBONES: usize = 13;

/// Kemiklerin yerel dönmeleri (ebeveyne göre, dinlenme pozundan) + kök kayması.
#[derive(Clone, Debug)]
pub struct Pose {
    pub local: [M3; NBONES],
    /// pelvis dünya kayması (metre)
    pub root_t: V3,
    /// tüm gövdenin dönmesi (ayak tabanı etrafında; takla, düşme)
    pub body: M3,
    pub body_pivot: V3,
}

impl Default for Pose {
    fn default() -> Self {
        Pose { local: [M3::IDENTITY; NBONES], root_t: V3::ZERO, body: M3::IDENTITY, body_pivot: V3::ZERO }
    }
}

pub struct Skeleton {
    pub head: Vec<V3>,
    pub parent: Vec<i32>,
    pub height: f32,
    /// avuç (tutma) noktaları, dinlenme pozu
    pub palm_r: V3,
    pub palm_l: V3,
}

impl Skeleton {
    pub fn of(m: &Model) -> Skeleton {
        let head: Vec<V3> = m.meta.bones.iter().map(|b| V3::from(b.head)).collect();
        let parent = m.meta.bones.iter().map(|b| b.parent).collect();
        let h = if m.meta.height > 0.0 { m.meta.height } else { 1.8 };
        let palm = |hand: usize, fore: usize| head[hand] + (head[hand] - head[fore]).norm() * (0.06 * h / 1.86);
        let palm_r = m.meta.palm_r.map(V3::from).unwrap_or_else(|| palm(HAND_R, FORE_R));
        let palm_l = m.meta.palm_l.map(V3::from).unwrap_or_else(|| palm(HAND_L, FORE_L));
        Skeleton { head, parent, height: h, palm_r, palm_l }
    }

    /// Kemik dünya dönüşümleri (dinlenme konumundaki noktayı pozdaki yerine taşır)
    pub fn world(&self, pose: &Pose) -> [Xf; NBONES] {
        let mut g = [M3::IDENTITY; NBONES];
        let mut xf = [Xf::IDENTITY; NBONES];
        for b in 0..NBONES {
            let p = self.parent[b];
            let (rot, pos) = if p < 0 {
                (pose.local[b], self.head[b] + pose.root_t)
            } else {
                let pi = p as usize;
                (g[pi] * pose.local[b], xf[pi].apply(self.head[b]))
            };
            g[b] = rot;
            xf[b] = Xf::new(rot, pos - rot.apply(self.head[b]));
        }
        // tüm gövde dönmesi (ayak tabanı merkezli)
        let body = Xf::new(pose.body, pose.body_pivot - pose.body.apply(pose.body_pivot));
        for x in xf.iter_mut() {
            *x = x.then(&body);
        }
        xf
    }

    /// İki kemikli IK: `upper` kemiğinin yerel dönmesini ve `fore` kemiğininkini, `palm` noktası `target`a
    /// (dünya) ulaşacak şekilde ayarlar; dirsek `pole` yönüne kıvrılır. Ebeveyn dönüşümleri `pose`tan hesaplanır.
    pub fn solve_arm(&self, pose: &mut Pose, upper: usize, fore: usize, palm_rest: V3, target: V3, pole: V3) {
        // önce kol kemikleri dinlenmede: ebeveyn (spine) dünya dönüşümü
        pose.local[upper] = M3::IDENTITY;
        pose.local[fore] = M3::IDENTITY;
        let xf = self.world(pose);
        let par = self.parent[upper] as usize;
        let sh = xf[upper].apply(self.head[upper]);
        let el = xf[upper].apply(self.head[fore]);
        let pa = xf[upper].apply(palm_rest);
        let l1 = (el - sh).len();
        let l2 = (pa - el).len();
        let mut d = target - sh;
        let mut dist = d.len().max(1e-5);
        let maxr = (l1 + l2) * 0.985;
        let minr = (l1 - l2).abs() + 0.02;
        if dist > maxr {
            d = d * (maxr / dist);
            dist = maxr;
        } else if dist < minr {
            d = d * (minr / dist);
            dist = minr;
        }
        let dir = d * (1.0 / dist);
        let ca = ((l1 * l1 + dist * dist - l2 * l2) / (2.0 * l1 * dist)).clamp(-1.0, 1.0);
        let sa = (1.0 - ca * ca).max(0.0).sqrt();
        let mut pl = pole - dir * pole.dot(dir);
        if pl.len() < 1e-4 {
            pl = v3(0.0, -1.0, 0.0);
        }
        let pl = pl.norm();
        let el_new = sh + (dir * ca + pl * sa) * l1;
        let r_up = M3::from_to(el - sh, el_new - sh);
        let fa = r_up.apply(pa - el);
        let r_fo = M3::from_to(fa, sh + d - el_new);
        // dünya dönmelerini yerel dönmelere çevir
        let gp = xf[par].r;
        let g_up = r_up * gp;
        let g_fo = r_fo * g_up;
        pose.local[upper] = gp.transpose() * g_up;
        pose.local[fore] = g_up.transpose() * g_fo;
    }
}

/// Deri bağlanmış köşe konum + normali (dünya)
pub fn skin(m: &Model, xf: &[Xf; NBONES], out_p: &mut Vec<V3>, out_n: &mut Vec<V3>) {
    out_p.clear();
    out_n.clear();
    for v in &m.verts {
        let a = &xf[v.b[0] as usize];
        let b = &xf[v.b[1] as usize];
        let w = v.w;
        let p = a.apply(v.p) * w + b.apply(v.p) * (1.0 - w);
        let n = a.r.apply(v.n) * w + b.r.apply(v.n) * (1.0 - w);
        out_p.push(p);
        out_n.push(n);
    }
}
