//! Oyunun kullandığı üst katman: modelleri tutar, karakter (+ silah) ve araç (+ sürücü) karelerini çizer,
//! efektler için ekran noktalarını (silah ucu, namlu, avuç, baş) döndürür.

use crate::anim::{self, In, Style, Wk};
use crate::math::{v3, Xf, M3, V3};
use crate::model::{Model, Texture};
use crate::raster::Light;
use crate::rig::{self, Skeleton, HAND_R, HEAD};
use crate::{render, transform_model, Image, Item, Settings};
use std::collections::HashMap;

/// Motosiklet eyeri: gövdenin orta şeridinde, gidonun arkasındaki en alçak üst nokta (sele çukuru)
fn seat_of(m: &Model) -> V3 {
    let l = m.meta.length.max(0.5);
    let n = 16usize;
    let (y0, y1) = (-0.05 * l, 0.32 * l);
    let mut top = vec![f32::MIN; n];
    for v in &m.verts {
        if v.p.x.abs() < 0.12 * l / 2.3 && v.p.y >= y0 && v.p.y < y1 {
            let b = (((v.p.y - y0) / (y1 - y0)) * n as f32) as usize;
            let b = b.min(n - 1);
            top[b] = top[b].max(v.p.z);
        }
    }
    let (mut bi, mut bz) = (n / 2, f32::MAX);
    for (i, &z) in top.iter().enumerate() {
        if z > 0.2 * l / 2.3 && z < bz {
            bz = z;
            bi = i;
        }
    }
    if bz == f32::MAX {
        return v3(0.0, 0.10 * l, 0.36 * l);
    }
    v3(0.0, y0 + (bi as f32 + 0.5) / n as f32 * (y1 - y0), bz)
}

/// Efekt noktaları (çıktı resmi koordinatında) ve güçler
#[derive(Clone, Copy, Debug, Default)]
pub struct Points {
    pub tip: [f32; 2],
    pub base: [f32; 2],
    pub muzzle: [f32; 2],
    pub palm: [f32; 2],
    pub head: [f32; 2],
    pub trail: f32,
    pub flash: f32,
    /// silahın türü (Wk sırası) — efekt rengi
    pub weapon: u8,
}

impl Points {
    pub const LEN: usize = 13;
    pub fn write(&self, o: &mut [f32]) {
        let v = [
            self.tip[0], self.tip[1], self.base[0], self.base[1], self.muzzle[0], self.muzzle[1], self.palm[0], self.palm[1], self.head[0],
            self.head[1], self.trail, self.flash, self.weapon as f32,
        ];
        o[..Self::LEN].copy_from_slice(&v);
    }
}

pub struct ActorReq<'a> {
    pub ch: &'a str,
    pub weapon: &'a str,
    pub anim: u8,
    pub t: f32,
    pub period: f32,
    pub yaw: f32,
    pub pitch: f32,
    pub scale: f32,
    pub ssaa: bool,
}

pub struct VehicleReq<'a> {
    pub name: &'a str,
    pub yaw: f32,
    pub pitch: f32,
    pub scale: f32,
    /// tekerlek dönüş açısı (derece)
    pub wheel: f32,
    /// gövde sarsıntısı (m)
    pub bounce: f32,
    /// öne/arkaya eğim (derece; hızlanma/fren)
    pub tilt: f32,
    /// sürücü (motosiklette görünür) — boş ise yok
    pub rider: &'a str,
    pub rider_t: f32,
    pub ssaa: bool,
}

#[derive(Default)]
pub struct Service {
    models: HashMap<String, Model>,
    skels: HashMap<String, Skeleton>,
    bp: Vec<V3>,
    bn: Vec<V3>,
    wp: Vec<V3>,
    wn: Vec<V3>,
    mp: Vec<V3>,
    mn: Vec<V3>,
    light: Option<Light>,
}

impl Service {
    pub fn new() -> Service {
        Service { light: Some(Light::default()), ..Default::default() }
    }

    pub fn load(&mut self, name: &str, zxm: &[u8], tw: usize, th: usize, rgba: &[u8]) -> Result<(), String> {
        let mut m = Model::parse(zxm)?;
        if tw > 0 && th > 0 && rgba.len() >= tw * th * 4 {
            m.tex = Some(Texture::from_rgba(tw, th, rgba));
        }
        if !m.meta.bones.is_empty() {
            self.skels.insert(name.to_string(), Skeleton::of(&m));
        }
        self.models.insert(name.to_string(), m);
        Ok(())
    }

    pub fn has(&self, name: &str) -> bool {
        self.models.contains_key(name)
    }

    /// Karakter karesi
    pub fn actor(&mut self, r: &ActorReq) -> Option<(Image, Points)> {
        let m = self.models.get(r.ch)?;
        let sk = self.skels.get(r.ch)?;
        let mut wk = Wk::from_model(r.weapon);
        if wk != Wk::None && !self.models.contains_key(wk.model()) {
            wk = Wk::None;
        }
        let mut inp = In { anim: r.anim, t: r.t, period: r.period, weapon: wk, style: Style::of(r.ch), wback: 0.0 };
        let shown = anim::key_of(&inp, sk.height / 1.86).3;
        let wm = if shown != Wk::None { self.models.get(shown.model()) } else { None };
        if let Some(w) = wm {
            inp.wback = -w.meta.back;
        }
        let out = anim::evaluate(sk, &inp);
        rig::skin(m, &out.world, &mut self.bp, &mut self.bn);
        let mut items = vec![Item { model: m, pos: &self.bp, nrm: &self.bn, range: None, id: 1, glow: 0.0 }];
        let mut pts = Points { weapon: Wk::ALL.iter().position(|w| *w == shown).unwrap_or(0) as u8, ..Default::default() };
        let mut wxf = None;
        if let (Some(xf), Some(w)) = (out.weapon, wm) {
            transform_model(w, &xf, &mut self.wp, &mut self.wn);
            let rng = match (out.mag_hidden, w.part("mag")) {
                (true, Some(pd)) => Some((0usize, pd.start as usize)),
                _ => None,
            };
            items.push(Item { model: w, pos: &self.wp, nrm: &self.wn, range: rng, id: 2, glow: shown.glow() });
            wxf = Some((xf, w));
            if let (Some(mx), Some(pd)) = (out.mag, w.part("mag")) {
                let piv = V3::from(pd.pivot);
                let xf2 = Xf::new(mx.r, mx.t - mx.r.apply(piv));
                transform_model(w, &xf2, &mut self.mp, &mut self.mn);
                items.push(Item { model: w, pos: &self.mp, nrm: &self.mn, range: Some((pd.start as usize, pd.count as usize)), id: 3, glow: 0.0 });
            }
        }
        let s = Settings { yaw: r.yaw, pitch: r.pitch, scale: r.scale, ssaa: r.ssaa, outline_px: 1 };
        let img = render(&items, &s, self.light.as_ref().unwrap());
        let pp = |p: V3| {
            let q = crate::project_point(&s, &img, p);
            [q.0, q.1]
        };
        if let Some((xf, w)) = wxf {
            pts.tip = pp(xf.apply(v3(w.meta.tip, 0.0, 0.0)));
            pts.base = pp(xf.apply(v3(w.meta.tip * 0.35, 0.0, 0.0)));
            let mz = w.meta.muzzle.map(V3::from).unwrap_or(v3(w.meta.tip, 0.0, 0.0));
            pts.muzzle = pp(xf.apply(mz));
        }
        pts.palm = pp(out.world[HAND_R].apply(sk.palm_r));
        pts.head = pp(out.world[HEAD].apply(sk.head[HEAD] + v3(0.0, 0.0, 0.18 * sk.height / 1.86)));
        pts.trail = out.trail;
        pts.flash = out.muzzle;
        Some((img, pts))
    }

    /// Araç karesi (tekerlek dönüşü, sarsıntı, eğim; motosiklette sürücü)
    pub fn vehicle(&mut self, r: &VehicleReq) -> Option<(Image, Points)> {
        let m = self.models.get(r.name)?;
        let parts: Vec<_> = m.meta.parts.iter().map(|p| (p.name.clone(), p.start as usize, p.count as usize, V3::from(p.pivot))).collect();
        // gövde: arka tekerlek etrafında eğim + sarsıntı; tekerlekler kendi eksenlerinde döner
        let rear = parts.iter().find(|p| p.0 == "wheel_r").map(|p| p.3).unwrap_or(V3::ZERO);
        let body = Xf::new(M3::rx(r.tilt), rear - M3::rx(r.tilt).apply(rear) + v3(0.0, 0.0, r.bounce));
        let mut pos = vec![V3::ZERO; m.verts.len()];
        let mut nrm = vec![V3::ZERO; m.verts.len()];
        let mut owner = vec![0u8; m.verts.len()];
        for (pi, p) in parts.iter().enumerate() {
            for t in &m.tris[p.1..p.1 + p.2] {
                for &i in t {
                    owner[i as usize] = pi as u8;
                }
            }
        }
        for (i, v) in m.verts.iter().enumerate() {
            let p = parts.get(owner[i] as usize);
            let xf = match p {
                Some(p) if p.0.starts_with("wheel") => {
                    let rw = M3::rx(-r.wheel);
                    Xf::new(rw, p.3 - rw.apply(p.3)).then(&Xf::new(M3::rx(r.tilt), rear - M3::rx(r.tilt).apply(rear)))
                }
                _ => body,
            };
            pos[i] = xf.apply(v.p);
            nrm[i] = xf.r.apply(v.n);
        }
        let mut items = vec![Item { model: m, pos: &pos, nrm: &nrm, range: None, id: 1, glow: 0.0 }];
        // motosiklet sürücüsü: oturma pozunda, eyerin üstünde
        let mut rider_ok = false;
        if !r.rider.is_empty() {
            if let (Some(cm), Some(sk)) = (self.models.get(r.rider), self.skels.get(r.rider)) {
                let inp = In { anim: anim::a::RIDE, t: r.rider_t, period: 1.0, weapon: Wk::None, style: Style::of(r.rider), wback: 0.0 };
                let mut out = anim::evaluate(sk, &inp);
                // oturma pozundaki kalça eklemi eyerin üstüne (kalça kemiği eyer yüzeyinden ~10 cm yukarıda)
                let seat = seat_of(m);
                let hip = out.world[rig::PELVIS].apply(sk.head[rig::PELVIS]);
                let place = Xf::new(M3::IDENTITY, seat + v3(0.0, 0.0, 0.10 * sk.height / 1.86) - hip).then(&body);
                for x in out.world.iter_mut() {
                    *x = x.then(&place);
                }
                rig::skin(cm, &out.world, &mut self.bp, &mut self.bn);
                rider_ok = true;
                let _ = cm;
            }
        }
        if rider_ok {
            let cm = self.models.get(r.rider).unwrap();
            items.push(Item { model: cm, pos: &self.bp, nrm: &self.bn, range: None, id: 2, glow: 0.0 });
        }
        let s = Settings { yaw: r.yaw, pitch: r.pitch, scale: r.scale, ssaa: r.ssaa, outline_px: 1 };
        let img = render(&items, &s, self.light.as_ref().unwrap());
        let mut pts = Points::default();
        let rw = parts.iter().find(|p| p.0 == "wheel_r").map(|p| p.3).unwrap_or(V3::ZERO);
        let q = crate::project_point(&s, &img, body.apply(v3(rw.x, rw.y, 0.0)));
        pts.base = [q.0, q.1];
        let fw = parts.iter().find(|p| p.0 == "wheel_f").map(|p| p.3).unwrap_or(V3::ZERO);
        let q = crate::project_point(&s, &img, body.apply(v3(fw.x, fw.y, 0.0)));
        pts.tip = [q.0, q.1];
        let q = crate::project_point(&s, &img, body.apply(v3(0.0, rw.y + 0.15 * m.meta.length, 0.45)));
        pts.muzzle = [q.0, q.1];
        Some((img, pts))
    }
}
