//! Yazılımsal üçgen çizici: dik izdüşüm, z-tampon, mip'li doku, cel (bantlı) ışık, kenar ışığı,
//! dış kontur ve derinlik kırığı çizgileri. Çıktı RGBA8.
//!
//! İki geçiş: (1) tüm üçgenler yalnız derinlik + üçgen kimliği + ağırlık merkezi koordinatlarıyla çizilir,
//! (2) yalnız görünen pikseller bir kez gölgelendirilir (üst üste binen yüzeyler için boşa doku okunmaz).

use crate::math::{v3, M3, V3};
use crate::model::Texture;

/// Kamera: karakteri ekranda bakılan yöne çevirir (yaw) ve yukarıdan eğik bakar (pitch).
/// Görünüm uzayı: X sağ, Y yukarı (ekran), Z kameraya doğru.
#[derive(Clone, Copy, Debug)]
pub struct Camera {
    /// dünya → görünüm dönmesi
    pub rot: M3,
    /// metre başına piksel
    pub scale: f32,
    /// ekran üzerinde dünya orijininin (ayak tabanı) konumu
    pub cx: f32,
    pub cy: f32,
}

impl Camera {
    /// yaw: derece, 0 = kameraya (ekranda aşağı) bakar, 90 = sağa; pitch: derece, yukarıdan bakış
    pub fn new(yaw_deg: f32, pitch_deg: f32, scale: f32, cx: f32, cy: f32) -> Camera {
        let (sp, cp) = pitch_deg.to_radians().sin_cos();
        // görünüm eksenleri dünya cinsinden: sağ (1,0,0), yukarı (0, sp, cp), kameraya (0, -cp, sp)
        let view = M3 { m: [[1.0, 0.0, 0.0], [0.0, sp, cp], [0.0, -cp, sp]] };
        Camera { rot: view * M3::rz(yaw_deg), scale, cx, cy }
    }
    /// görünüm uzayına
    #[inline]
    pub fn view(&self, p: V3) -> V3 {
        self.rot.apply(p)
    }
    /// ekran pikseli + derinlik (büyük = yakın)
    #[inline]
    pub fn project(&self, p: V3) -> V3 {
        let q = self.rot.apply(p);
        v3(self.cx + q.x * self.scale, self.cy - q.y * self.scale, q.z)
    }
}

pub struct Light {
    /// görünüm uzayında ışığa doğru birim yön
    pub dir: V3,
    pub ambient: f32,
    pub diffuse: f32,
    pub rim: f32,
    pub rim_color: [f32; 3],
    pub tint: [f32; 3],
    /// gökyüzü/yer ışığı: yukarı bakan yüzeyler aydınlanır, aşağı bakanlar kararır
    pub sky: f32,
    /// parlama gücü (0..1)
    pub spec: f32,
    /// doygunluk çarpanı
    pub sat: f32,
}

impl Default for Light {
    fn default() -> Self {
        Light {
            dir: v3(-0.45, 0.62, 0.64).norm(),
            // V18: karakterler karanlık görünüyordu: gölge tarafı ve ortam ışığı yükseltildi (+ çıkışta hafif gama)
            // V19: biraz daha kontrast (gölge tarafı ~%73, ışık tarafı ~%130), kenar ışığı güçlü: arka plandan ayrışır
            ambient: 0.55,
            diffuse: 0.76,
            rim: 0.42,
            rim_color: [0.85, 0.92, 1.0],
            tint: [1.0, 1.0, 1.0],
            sky: 0.07,
            spec: 0.16,
            sat: 1.12,
        }
    }
}

/// Görünüm uzayında hazırlanmış köşe
#[derive(Clone, Copy, Default)]
pub struct SVert {
    /// ekran x, y, derinlik
    pub s: V3,
    /// görünüm uzayı normali
    pub n: V3,
    pub uv: [f32; 2],
}

/// Bir çizim parçası (gölgelendirmede gereken bilgi)
pub struct Layer<'a> {
    pub verts: &'a [SVert],
    pub tris: &'a [[u16; 3]],
    pub tex: Option<&'a Texture>,
    pub glow: f32,
    pub id: u8,
}

pub struct Frame {
    pub w: usize,
    pub h: usize,
    pub color: Vec<[u8; 4]>,
    pub depth: Vec<f32>,
    /// (katman + 1) << 24 | üçgen indeksi; 0 = boş
    pub tri: Vec<u32>,
    pub bary: Vec<[f32; 2]>,
}

impl Frame {
    pub fn new(w: usize, h: usize) -> Frame {
        Frame { w, h, color: vec![[0; 4]; w * h], depth: vec![f32::NEG_INFINITY; w * h], tri: vec![0; w * h], bary: vec![[0.0; 2]; w * h] }
    }
}

#[inline]
fn band(x: f32) -> f32 {
    // 3 bantlı ışık, bant geçişleri dar yumuşak (piksel sanatına yakın, ama tırtıksız)
    0.48 + 0.26 * smooth(0.05, 0.13, x) + 0.26 * smooth(0.48, 0.56, x)
}

#[inline]
fn smooth(a: f32, b: f32, x: f32) -> f32 {
    let t = ((x - a) / (b - a)).clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

/// Geçiş 1: derinlik ve görünürlük
pub fn rasterize(f: &mut Frame, layer: usize, verts: &[SVert], tris: &[[u16; 3]], cull: bool) {
    let w = f.w as i32;
    let h = f.h as i32;
    let tag = ((layer as u32) + 1) << 24;
    for (ti, t) in tris.iter().enumerate() {
        let a = verts[t[0] as usize].s;
        let b = verts[t[1] as usize].s;
        let c = verts[t[2] as usize].s;
        let area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        // arka yüz eleme (ekran y aşağı: önden bakan üçgenin alanı negatif)
        if area.abs() < 1e-7 || (cull && area > 0.0) {
            continue;
        }
        let minx = a.x.min(b.x).min(c.x).floor().max(0.0) as i32;
        let maxx = (a.x.max(b.x).max(c.x).ceil() as i32).min(w - 1);
        let miny = a.y.min(b.y).min(c.y).floor().max(0.0) as i32;
        let maxy = (a.y.max(b.y).max(c.y).ceil() as i32).min(h - 1);
        if minx > maxx || miny > maxy {
            continue;
        }
        let inv = 1.0 / area;
        // ağırlık merkezi koordinatları: w_k(x, y) = R_k + E_k·(x − x0) (satır içinde doğrusal)
        let x0 = minx as f32 + 0.5;
        let y0 = miny as f32 + 0.5;
        let e0x = -(c.y - b.y) * inv;
        let e0y = (c.x - b.x) * inv;
        let e1x = -(a.y - c.y) * inv;
        let e1y = (a.x - c.x) * inv;
        let e2x = -(e0x + e1x);
        // kenar başına sınır: e > 0 ise alt, e < 0 ise üst sınır (bölme satır başına değil üçgen başına)
        let i0 = if e0x.abs() > 1e-12 { -1.0 / e0x } else { 0.0 };
        let i1 = if e1x.abs() > 1e-12 { -1.0 / e1x } else { 0.0 };
        let i2 = if e2x.abs() > 1e-12 { -1.0 / e2x } else { 0.0 };
        let mut r0 = ((b.x - x0) * (c.y - y0) - (b.y - y0) * (c.x - x0)) * inv;
        let mut r1 = ((c.x - x0) * (a.y - y0) - (c.y - y0) * (a.x - x0)) * inv;
        let dz1 = b.z - a.z;
        let dz2 = c.z - a.z;
        let id = tag | ti as u32;
        for py in miny..=maxy {
            // bu satırda üç kenarın da içinde kalan x aralığı (yalnız kapsanan pikseller dolaşılır)
            let r2 = 1.0 - r0 - r1;
            let mut lo = 0.0f32;
            let mut hi = (maxx - minx) as f32;
            let mut empty = false;
            for (r, e, ie) in [(r0, e0x, i0), (r1, e1x, i1), (r2, e2x, i2)] {
                if e > 1e-12 {
                    lo = lo.max(r * ie);
                } else if e < -1e-12 {
                    hi = hi.min(r * ie);
                } else if r < 0.0 {
                    empty = true;
                }
            }
            if !empty && lo <= hi + 1e-4 {
                let xa = (lo - 1e-4).ceil() as i32;
                let xb = (hi + 1e-4).floor() as i32;
                let row = (py * w + minx) as usize;
                for k in xa.max(0)..=xb.min(maxx - minx) {
                    let kf = k as f32;
                    let w1 = r1 + e1x * kf;
                    let w2 = r2 + e2x * kf;
                    let z = a.z + dz1 * w1 + dz2 * w2;
                    let i = row + k as usize;
                    if z > f.depth[i] {
                        f.depth[i] = z;
                        f.tri[i] = id;
                        f.bary[i] = [w1, w2];
                    }
                }
            }
            r0 += e0y;
            r1 += e1y;
        }
    }
}

/// Geçiş 2: görünen pikselleri gölgelendir
pub fn shade(f: &mut Frame, layers: &[Layer], light: &Light) {
    // üçgen başına doku ayrıntı seviyesi (piksel başına düşen doku pikseli → mip)
    let lods: Vec<Vec<f32>> = layers
        .iter()
        .map(|ly| {
            let s = ly.tex.map(|t| t.size()).unwrap_or(1.0);
            ly.tris
                .iter()
                .map(|t| {
                    let (a, b, c) = (&ly.verts[t[0] as usize], &ly.verts[t[1] as usize], &ly.verts[t[2] as usize]);
                    let area = ((b.s.x - a.s.x) * (c.s.y - a.s.y) - (b.s.y - a.s.y) * (c.s.x - a.s.x)).abs().max(1e-6);
                    let uva = ((b.uv[0] - a.uv[0]) * (c.uv[1] - a.uv[1]) - (b.uv[1] - a.uv[1]) * (c.uv[0] - a.uv[0])).abs();
                    (0.5 * (uva * s * s / area).max(1e-6).log2()).clamp(0.0, 15.0)
                })
                .collect()
        })
        .collect();
    for i in 0..f.w * f.h {
        let tag = f.tri[i];
        if tag == 0 {
            continue;
        }
        let li = ((tag >> 24) - 1) as usize;
        let ti = (tag & 0xFF_FFFF) as usize;
        let ly = &layers[li];
        let t = ly.tris[ti];
        let a = &ly.verts[t[0] as usize];
        let b = &ly.verts[t[1] as usize];
        let c = &ly.verts[t[2] as usize];
        let [w1, w2] = f.bary[i];
        let w0 = 1.0 - w1 - w2;
        let mut n = (a.n * w0 + b.n * w1 + c.n * w2).norm();
        if n.z < 0.0 {
            n = -n;
        }
        let base = match ly.tex {
            Some(tx) => {
                // üç doğrusal: komşu iki mip seviyesinin çift doğrusal örnekleri harmanlanır (en yakın örnek kumlu/lekeli görünüyordu)
                let lod = lods[li][ti];
                let u = a.uv[0] * w0 + b.uv[0] * w1 + c.uv[0] * w2;
                let v = a.uv[1] * w0 + b.uv[1] * w1 + c.uv[1] * w2;
                let l0 = lod.floor();
                let fr = lod - l0;
                let s0 = tx.sample(l0 as usize, u, v);
                if fr < 0.05 {
                    s0
                } else {
                    let s1 = tx.sample(l0 as usize + 1, u, v);
                    [s0[0] + (s1[0] - s0[0]) * fr, s0[1] + (s1[1] - s0[1]) * fr, s0[2] + (s1[2] - s0[2]) * fr]
                }
            }
            None => [180.0, 180.0, 180.0],
        };
        let ndl = n.dot(light.dir);
        // V19: yarı bantlı ışık (cel bandı + yumuşak yarım Lambert) ve gökyüzü/yer ışığı: hacim okunur, düz ve soluk görünmez
        let half = (0.5 + 0.5 * ndl).clamp(0.0, 1.0);
        let l = light.ambient + light.diffuse * (0.5 * band(ndl) + 0.5 * half * half) + light.sky * n.y;
        let e = 1.0 - n.z.clamp(0.0, 1.0);
        let rim = e * e * e * light.rim * smooth(-0.2, 0.4, ndl + 0.3);
        // hafif parlama (Blinn): deri, metal ve kumaş kıvrımlarında ışık noktaları
        let hv = (light.dir + v3(0.0, 0.0, 1.0)).norm();
        let sp = n.dot(hv).max(0.0);
        let sp = sp * sp;
        let sp = sp * sp;
        let spec = light.spec * sp * sp * sp * 255.0;
        // doygunluk: doku renkleri ışık çarpanıyla grileşmesin
        let lum = 0.3 * base[0] + 0.59 * base[1] + 0.11 * base[2];
        let g = ly.glow;
        let mut out = [0u8, 0, 0, 255];
        for k in 0..3 {
            let bk = lum + (base[k] - lum) * light.sat;
            let v = bk * (l * light.tint[k] + g) + rim * 255.0 * light.rim_color[k] + spec;
            out[k] = lift(v.clamp(0.0, 255.0) as u8);
        }
        f.color[i] = out;
    }
}

/// Koyu tonları kaldıran hafif gama (0.84): siyah pelerin ve koyu zırhlar ormanda kaybolmasın
fn lift(v: u8) -> u8 {
    static LUT: std::sync::OnceLock<[u8; 256]> = std::sync::OnceLock::new();
    LUT.get_or_init(|| {
        let mut t = [0u8; 256];
        for (i, o) in t.iter_mut().enumerate() {
            *o = (255.0 * (i as f32 / 255.0).powf(0.84)).round() as u8;
        }
        t
    })[v as usize]
}

/// Dış kontur (kalınlık `px`) ve iç derinlik kırığı çizgileri.
pub fn outline(f: &mut Frame, px: usize, color: [u8; 4], crease_m: f32, crease_dark: f32) {
    let (w, h) = (f.w, f.h);
    if crease_m > 0.0 && w > 2 && h > 2 {
        let mut dark = vec![false; w * h];
        for y in 1..h - 1 {
            for x in 1..w - 1 {
                let i = y * w + x;
                if f.tri[i] == 0 {
                    continue;
                }
                let d = f.depth[i] + crease_m;
                if (f.tri[i - 1] != 0 && f.depth[i - 1] > d)
                    || (f.tri[i + 1] != 0 && f.depth[i + 1] > d)
                    || (f.tri[i - w] != 0 && f.depth[i - w] > d)
                    || (f.tri[i + w] != 0 && f.depth[i + w] > d)
                {
                    dark[i] = true;
                }
            }
        }
        for (i, dk) in dark.iter().enumerate() {
            if *dk {
                for ch in f.color[i].iter_mut().take(3) {
                    *ch = (*ch as f32 * crease_dark) as u8;
                }
            }
        }
    }
    let mut solid: Vec<bool> = f.color.iter().map(|c| c[3] != 0).collect();
    for _ in 0..px {
        let mut add = Vec::new();
        for y in 0..h {
            for x in 0..w {
                let i = y * w + x;
                if solid[i] {
                    continue;
                }
                if (x > 0 && solid[i - 1]) || (x + 1 < w && solid[i + 1]) || (y > 0 && solid[i - w]) || (y + 1 < h && solid[i + w]) {
                    add.push(i);
                }
            }
        }
        for i in add {
            f.color[i] = color;
            solid[i] = true;
        }
    }
}

/// k×k kutu süzgeciyle küçültür (kenar yumuşatma). Renkler alfa ağırlıklı.
pub fn downsample(src: &Frame, k: usize) -> Vec<[u8; 4]> {
    let (w, h) = (src.w / k, src.h / k);
    let n = (k * k) as u32;
    let mut out = vec![[0u8; 4]; w * h];
    for y in 0..h {
        for x in 0..w {
            let mut acc = [0u32; 4];
            for dy in 0..k {
                let row = (y * k + dy) * src.w + x * k;
                for c in &src.color[row..row + k] {
                    let a = c[3] as u32;
                    acc[0] += c[0] as u32 * a;
                    acc[1] += c[1] as u32 * a;
                    acc[2] += c[2] as u32 * a;
                    acc[3] += a;
                }
            }
            if let (Some(r), Some(g), Some(b)) = (acc[0].checked_div(acc[3]), acc[1].checked_div(acc[3]), acc[2].checked_div(acc[3])) {
                out[y * w + x] = [r as u8, g as u8, b as u8, (acc[3] / n) as u8];
            }
        }
    }
    out
}

/// Keskinleştirme (unsharp): yalnız dört komşusu da tam opak iç pikseller; c + amt·(c − komşu ortalaması)
pub fn sharpen(px: &mut [[u8; 4]], w: usize, h: usize, amt: f32) {
    if w < 3 || h < 3 {
        return;
    }
    let src = px.to_vec();
    for y in 1..h - 1 {
        for x in 1..w - 1 {
            let i = y * w + x;
            let (l, r, u, d) = (src[i - 1], src[i + 1], src[i - w], src[i + w]);
            if src[i][3] < 255 || l[3] < 255 || r[3] < 255 || u[3] < 255 || d[3] < 255 {
                continue;
            }
            for k in 0..3 {
                let c = src[i][k] as f32;
                let avg = (l[k] as f32 + r[k] as f32 + u[k] as f32 + d[k] as f32) * 0.25;
                px[i][k] = (c + amt * (c - avg)).clamp(0.0, 255.0) as u8;
            }
        }
    }
}
