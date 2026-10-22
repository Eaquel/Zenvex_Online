//! Zemin parçası çizimi: karo malzeme haritasını dokularla piksel piksel harmanlar.
//! Kenarlar alan bükme (domain warp) ile organik, büyük ölçekli ton dalgası tekrar hissini kırar.
use crate::rng::value_noise;
use crate::world::{ground, World, Zone, MAP_PX, MAP_TILES, TILE};

#[derive(Default)]
pub struct Textures {
    pub size: usize,
    /// her malzeme için ARGB pikseller (size*size)
    pub tex: Vec<Vec<u32>>,
}

impl Textures {
    pub fn ready(&self) -> bool {
        self.size > 0 && self.tex.len() >= ground::COUNT
    }
}

#[inline]
fn warp_noise(seed: u64, x: f32, y: f32) -> f32 {
    value_noise(seed, x, y) * 0.65 + value_noise(seed ^ 0x55, x * 2.3, y * 2.3) * 0.35
}

#[inline]
fn shade(c: u32, k: f32) -> u32 {
    let r = (((c >> 16) & 255) as f32 * k).min(255.0) as u32;
    let g = (((c >> 8) & 255) as f32 * k).min(255.0) as u32;
    let b = ((c & 255) as f32 * k).min(255.0) as u32;
    0xFF00_0000 | (r << 16) | (g << 8) | b
}

#[inline]
fn mix(a: u32, b: u32, t: f32) -> u32 {
    let f = |s: u32| (s & 255) as f32;
    let ch = |sh: u32| {
        let va = f(a >> sh);
        let vb = f(b >> sh);
        ((va + (vb - va) * t) as u32 & 255) << sh
    };
    0xFF00_0000 | ch(16) | ch(8) | ch(0)
}

/// V8 bölge renk tonu: yanık orman kül grisi, çöl kum, kara bataklık koyu yosun, liman kar, askeri bölge haki.
pub fn zone_tint(z: Zone, c: u32) -> u32 {
    let gray = |c: u32| {
        let l = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100;
        0xFF00_0000 | (l << 16) | (l << 8) | l
    };
    match z {
        Zone::BurntForest => shade(mix(c, gray(c), 0.7), 0.62),
        Zone::Desert => mix(c, 0xFFD2B67E, 0.42),
        Zone::BlackSwamp => shade(mix(c, 0xFF24331E, 0.3), 0.8),
        Zone::Harbor => mix(shade(gray(c), 1.25), 0xFFE4ECF2, 0.5),
        Zone::Military => mix(c, 0xFF6E6A48, 0.22),
        // Cephe Hattı: is ve barut lekeli, soluk kahverengi
        Zone::Front => shade(mix(c, 0xFF5A4A36, 0.3), 0.85),
        _ => c,
    }
}

/// (x0, y0) dünya pikselinden başlayan `size`x`size` parçayı `out` içine yazar (ARGB).
pub fn render_chunk(world: &World, tex: &Textures, x0: f32, y0: f32, size: usize, out: &mut [u32]) {
    let ts = tex.size.max(1);
    let seed = world.seed;
    let warp = 26.0;
    let mat_at = |wx: f32, wy: f32| -> u8 {
        let tx = (wx / TILE).floor();
        let ty = (wy / TILE).floor();
        if tx < 0.0 || ty < 0.0 || tx >= MAP_TILES as f32 || ty >= MAP_TILES as f32 {
            return ground::DARK_GRASS;
        }
        world.ground_at(tx as usize, ty as usize)
    };
    for py in 0..size {
        let wy = y0 + py as f32;
        for px in 0..size {
            let wx = x0 + px as f32;
            let idx = py * size + px;
            if wx < 0.0 || wy < 0.0 || wx >= MAP_PX || wy >= MAP_PX {
                out[idx] = 0xFF1E3A22;
                continue;
            }
            let nx = warp_noise(seed, wx / 90.0, wy / 90.0) * 2.0 - 1.0;
            let ny = warp_noise(seed ^ 0x99, wx / 90.0, wy / 90.0) * 2.0 - 1.0;
            let m = mat_at(wx + nx * warp, wy + ny * warp);
            let m2 = mat_at(wx + nx * warp + 7.0, wy + ny * warp + 5.0);
            let sample = |mm: u8| -> u32 {
                if !tex.ready() {
                    return 0xFF4F8A3A;
                }
                let t = &tex.tex[mm as usize];
                let u = (wx as usize) % ts;
                let v = (wy as usize) % ts;
                t[v * ts + u]
            };
            let mut c = sample(m);
            if m2 != m {
                // yumuşak geçiş + kenarda hafif gölge (çim üstte, toprak altta hissi)
                c = mix(c, sample(m2), 0.45);
                c = shade(c, 0.9);
            }
            // büyük ölçekli ton dalgası
            let big = value_noise(seed ^ 0x123, wx / 700.0, wy / 700.0) * 0.6
                + value_noise(seed ^ 0x321, wx / 260.0, wy / 260.0) * 0.4;
            let k = 0.9 + big * 0.18;
            c = shade(c, k);
            // bölge tonu (kenarları zeminle aynı bükülmeyle organik)
            let z = world.zone(wx + nx * warp * 1.6, wy + ny * warp * 1.6);
            if !ground::own_color(m) && !matches!(z, Zone::Camp | Zone::Forest | Zone::DeepForest | Zone::Swamp | Zone::Wall | Zone::SafeCity | Zone::Suburb | Zone::Downtown | Zone::Industrial | Zone::BossArena | Zone::Home) {
                c = zone_tint(z, c);
            }
            // yol şeritleri
            if m == ground::ASPHALT {
                let tx = (wx / TILE) as usize;
                let ty = (wy / TILE) as usize;
                let (cx, cy) = World::is_road_center(tx, ty);
                let lx = wx - (tx as f32 * TILE);
                let ly = wy - (ty as f32 * TILE);
                let dash_y = ((wy / 48.0) as i32) % 2 == 0;
                let dash_x = ((wx / 48.0) as i32) % 2 == 0;
                let vert = cx && !cy && (lx - TILE).abs() < 2.5 && dash_y;
                let horiz = cy && !cx && (ly - TILE).abs() < 2.5 && dash_x;
                if vert || horiz {
                    c = 0xFFE8E2C8;
                }
            }
            out[idx] = c;
        }
    }
}
