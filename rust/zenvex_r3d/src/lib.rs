//! Zenvex Online 3B çizici.
//!
//! Karakterler, silahlar ve araçlar gerçek 3B model olarak tutulur (assets/m3d/*.zxm + doku); her kare çalışma
//! anında iskelet pozundan (`anim`) hesaplanır ve yazılımsal çiziciyle (`raster`) piksel sanatı görünümünde
//! (cel ışık + kontur) küçük bir resme çizilir. Oyun bu resmi önbellekleyip dünya üzerinde çizer.

pub mod anim;
pub mod math;
pub mod model;
pub mod raster;
pub mod rig;
pub mod service;

use math::{V3, Xf};
use model::Model;
use raster::{Camera, Frame, Layer, Light, SVert};

/// Çizilecek bir parça: modelin (bir aralığının) dünya köşeleri
pub struct Item<'a> {
    pub model: &'a Model,
    pub pos: &'a [V3],
    pub nrm: &'a [V3],
    /// üçgen aralığı (başlangıç, adet); None = hepsi
    pub range: Option<(usize, usize)>,
    pub id: u8,
    /// kendinden ışıma (0..1): kor, buz, neon
    pub glow: f32,
}

/// Çizim sonucu: sıkı kırpılmış RGBA + çapa (dünya orijininin resimdeki yeri) + ekran noktaları
pub struct Image {
    pub w: usize,
    pub h: usize,
    pub rgba: Vec<[u8; 4]>,
    /// dünya orijini (ayak tabanı merkezi) resimde
    pub ax: f32,
    pub ay: f32,
}

pub struct Settings {
    pub yaw: f32,
    pub pitch: f32,
    /// metre başına çıktı pikseli
    pub scale: f32,
    /// kenar yumuşatma (2× çizip yarıya indirir)
    pub ssaa: bool,
    pub outline_px: usize,
}

/// Parçaları tek z-tamponda çizer.
pub fn render(items: &[Item], s: &Settings, light: &Light) -> Image {
    let k = if s.ssaa { 2.0 } else { 1.0 };
    let scale = s.scale * k;
    let cam0 = Camera::new(s.yaw, s.pitch, scale, 0.0, 0.0);
    // sınırlar
    let (mut x0, mut y0, mut x1, mut y1) = (f32::MAX, f32::MAX, f32::MIN, f32::MIN);
    for it in items {
        for p in it.pos {
            let q = cam0.project(*p);
            x0 = x0.min(q.x);
            x1 = x1.max(q.x);
            y0 = y0.min(q.y);
            y1 = y1.max(q.y);
        }
    }
    if x0 > x1 {
        return Image { w: 1, h: 1, rgba: vec![[0; 4]], ax: 0.0, ay: 0.0 };
    }
    let pad = (s.outline_px as f32 + 2.0) * k;
    let ox = (-x0 + pad).ceil();
    let oy = (-y0 + pad).ceil();
    let mut w = (x1 - x0 + 2.0 * pad).ceil() as usize + 1;
    let mut h = (y1 - y0 + 2.0 * pad).ceil() as usize + 1;
    if s.ssaa {
        w += w & 1;
        h += h & 1;
    }
    let cam = Camera { cx: ox, cy: oy, ..cam0 };
    let mut f = Frame::new(w, h);
    let mut svs: Vec<Vec<SVert>> = Vec::with_capacity(items.len());
    for it in items {
        let mut sv = Vec::with_capacity(it.pos.len());
        for (i, p) in it.pos.iter().enumerate() {
            sv.push(SVert { s: cam.project(*p), n: cam.view(it.nrm[i]), uv: it.model.verts[i].uv });
        }
        svs.push(sv);
    }
    let mut layers = Vec::with_capacity(items.len());
    for (li, it) in items.iter().enumerate() {
        let tris = match it.range {
            Some((a, n)) => &it.model.tris[a..a + n],
            None => &it.model.tris[..],
        };
        raster::rasterize(&mut f, li, &svs[li], tris, true);
        layers.push(Layer { verts: &svs[li], tris, tex: it.model.tex.as_ref(), glow: it.glow, id: it.id });
    }
    raster::shade(&mut f, &layers, light);
    raster::outline(&mut f, s.outline_px * if s.ssaa { 2 } else { 1 }, [12, 11, 16, 255], 0.10, 0.62);
    if s.ssaa {
        let rgba = raster::downsample2(&f);
        Image { w: w / 2, h: h / 2, rgba, ax: ox / 2.0, ay: oy / 2.0 }
    } else {
        Image { w, h, rgba: f.color, ax: ox, ay: oy }
    }
}

/// Bir noktanın çıktı resmindeki yeri (efektler için: silah ucu, namlu, el)
pub fn project_point(s: &Settings, img: &Image, p: V3) -> (f32, f32, f32) {
    let cam = Camera::new(s.yaw, s.pitch, s.scale, img.ax, img.ay);
    let q = cam.project(p);
    (q.x, q.y, q.z)
}

/// Rijit bir modeli (silah, araç parçası) dönüşümle dünya köşelerine taşır
pub fn transform_model(m: &Model, xf: &Xf, out_p: &mut Vec<V3>, out_n: &mut Vec<V3>) {
    out_p.clear();
    out_n.clear();
    for v in &m.verts {
        out_p.push(xf.apply(v.p));
        out_n.push(xf.r.apply(v.n));
    }
}
