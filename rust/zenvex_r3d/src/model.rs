//! .zxm model biçimi (tools/model3d/build.py yazar) ve doku (mip zinciriyle).
//!
//! Düzen: "ZXM1" | u32 json_len | json | köşeler (vstride bayt) | üçgenler (u16 × 3).
//! Köşe: konum u16×3 (lo..hi aralığına nicemli), normal i8×3 + boşluk, uv u16×2, kemik u8×2, ağırlık u8, boşluk.

use crate::math::{v3, V3};
use serde::Deserialize;

#[derive(Clone, Debug, Deserialize)]
pub struct BoneDef {
    pub name: String,
    pub parent: i32,
    pub head: [f32; 3],
}

#[derive(Clone, Debug, Deserialize)]
pub struct PartDef {
    pub name: String,
    pub start: u32,
    pub count: u32,
    pub pivot: [f32; 3],
}

#[derive(Clone, Debug, Deserialize)]
pub struct Meta {
    pub name: String,
    pub kind: String,
    pub nverts: usize,
    pub ntris: usize,
    pub lo: [f32; 3],
    pub hi: [f32; 3],
    pub vstride: usize,
    #[serde(default)]
    pub bones: Vec<BoneDef>,
    #[serde(default)]
    pub parts: Vec<PartDef>,
    #[serde(default)]
    pub height: f32,
    #[serde(default)]
    pub tip: f32,
    #[serde(default)]
    pub back: f32,
    #[serde(default)]
    pub muzzle: Option<[f32; 3]>,
    #[serde(default)]
    pub length: f32,
    #[serde(default)]
    pub palm_r: Option<[f32; 3]>,
    #[serde(default)]
    pub palm_l: Option<[f32; 3]>,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct Vertex {
    pub p: V3,
    pub n: V3,
    pub uv: [f32; 2],
    pub b: [u8; 2],
    pub w: f32,
}

/// RGBA8 doku + 2× küçülen mip seviyeleri (ilk seviye kaynak)
pub struct Texture {
    pub levels: Vec<(usize, usize, Vec<[u8; 4]>)>,
}

impl Texture {
    pub fn from_rgba(w: usize, h: usize, rgba: &[u8]) -> Texture {
        let base: Vec<[u8; 4]> = rgba.chunks_exact(4).map(|c| [c[0], c[1], c[2], c[3]]).collect();
        let mut levels = vec![(w, h, base)];
        loop {
            let (pw, ph, prev) = levels.last().unwrap();
            let (pw, ph) = (*pw, *ph);
            if pw <= 8 || ph <= 8 {
                break;
            }
            let (nw, nh) = (pw / 2, ph / 2);
            let mut out = vec![[0u8; 4]; nw * nh];
            for y in 0..nh {
                for x in 0..nw {
                    let mut acc = [0u32; 4];
                    for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)] {
                        let c = prev[(y * 2 + dy) * pw + x * 2 + dx];
                        for k in 0..4 {
                            acc[k] += c[k] as u32;
                        }
                    }
                    out[y * nw + x] = [(acc[0] / 4) as u8, (acc[1] / 4) as u8, (acc[2] / 4) as u8, (acc[3] / 4) as u8];
                }
            }
            levels.push((nw, nh, out));
        }
        Texture { levels }
    }

    /// Çift doğrusal örnek (sarmalı), seviye `lod` (0 = en ayrıntılı)
    #[inline]
    pub fn sample(&self, lod: usize, u: f32, v: f32) -> [f32; 3] {
        let (w, h, px) = &self.levels[lod.min(self.levels.len() - 1)];
        let (w, h) = (*w, *h);
        let x = u.rem_euclid(1.0) * w as f32 - 0.5;
        let y = v.rem_euclid(1.0) * h as f32 - 0.5;
        let x0 = x.floor();
        let y0 = y.floor();
        let fx = x - x0;
        let fy = y - y0;
        let xi = (x0 as i32).rem_euclid(w as i32) as usize;
        let yi = (y0 as i32).rem_euclid(h as i32) as usize;
        let xj = (xi + 1) % w;
        let yj = (yi + 1) % h;
        let a = px[yi * w + xi];
        let b = px[yi * w + xj];
        let c = px[yj * w + xi];
        let d = px[yj * w + xj];
        let mut o = [0.0f32; 3];
        for k in 0..3 {
            let top = a[k] as f32 + (b[k] as f32 - a[k] as f32) * fx;
            let bot = c[k] as f32 + (d[k] as f32 - c[k] as f32) * fx;
            o[k] = top + (bot - top) * fy;
        }
        o
    }

    /// En yakın örnek (küçültmede mip zaten süzülmüş)
    #[inline]
    pub fn nearest(&self, lod: usize, u: f32, v: f32) -> [f32; 3] {
        let (w, h, px) = &self.levels[lod.min(self.levels.len() - 1)];
        let x = ((u.clamp(0.0, 0.99999)) * *w as f32) as usize;
        let y = ((v.clamp(0.0, 0.99999)) * *h as f32) as usize;
        let c = px[y * *w + x];
        [c[0] as f32, c[1] as f32, c[2] as f32]
    }

    pub fn size(&self) -> f32 {
        self.levels[0].0 as f32
    }
}

pub struct Model {
    pub meta: Meta,
    pub verts: Vec<Vertex>,
    pub tris: Vec<[u16; 3]>,
    pub tex: Option<Texture>,
}

impl Model {
    pub fn parse(bytes: &[u8]) -> Result<Model, String> {
        if bytes.len() < 8 || &bytes[0..4] != b"ZXM1" {
            return Err("ZXM1 değil".into());
        }
        let jl = u32::from_le_bytes(bytes[4..8].try_into().unwrap()) as usize;
        let meta: Meta = serde_json::from_slice(&bytes[8..8 + jl]).map_err(|e| e.to_string())?;
        let mut off = 8 + jl;
        let need = off + meta.nverts * meta.vstride + meta.ntris * 6;
        if bytes.len() < need {
            return Err(format!("kısa dosya: {} < {}", bytes.len(), need));
        }
        let lo = V3::from(meta.lo);
        let span = V3::from(meta.hi) - lo;
        let u16a = |b: &[u8], o: usize| u16::from_le_bytes([b[o], b[o + 1]]) as f32;
        let mut verts = Vec::with_capacity(meta.nverts);
        for _ in 0..meta.nverts {
            let r = &bytes[off..off + meta.vstride];
            let p = v3(
                lo.x + u16a(r, 0) / 65535.0 * span.x,
                lo.y + u16a(r, 2) / 65535.0 * span.y,
                lo.z + u16a(r, 4) / 65535.0 * span.z,
            );
            let n = v3(r[6] as i8 as f32, r[7] as i8 as f32, r[8] as i8 as f32).norm();
            let uv = [u16a(r, 10) / 65535.0, u16a(r, 12) / 65535.0];
            verts.push(Vertex { p, n, uv, b: [r[14], r[15]], w: r[16] as f32 / 255.0 });
            off += meta.vstride;
        }
        let mut tris = Vec::with_capacity(meta.ntris);
        for _ in 0..meta.ntris {
            let r = &bytes[off..off + 6];
            tris.push([u16a(r, 0) as u16, u16a(r, 2) as u16, u16a(r, 4) as u16]);
            off += 6;
        }
        Ok(Model { meta, verts, tris, tex: None })
    }

    pub fn part(&self, name: &str) -> Option<&PartDef> {
        self.meta.parts.iter().find(|p| p.name == name)
    }

    pub fn bone(&self, name: &str) -> Option<usize> {
        self.meta.bones.iter().position(|b| b.name == name)
    }
}
