//! Zemin parçası çizimi: karo malzeme haritasını dokularla piksel piksel harmanlar.
//! Kenarlar alan bükme (domain warp) ile organik, büyük ölçekli ton dalgası tekrar hissini kırar.
use crate::rng::value_noise;
use crate::world::{ground, World, Zone, MAP_PX, MAP_TILES, TILE};

#[derive(Default)]
pub struct Textures {
    pub size: usize,
    /// her malzeme için ARGB pikseller (size*size); 14-17 bulunulan bölgenin dokuları (yoksa ortak dokuya düşer)
    pub tex: Vec<Vec<u32>>,
    /// bölge dokularının ait olduğu bölge (areas::AREAS indeksi); dışında kalan yer "boşluk" dokusuyla çizilir
    pub area: Option<usize>,
}

impl Textures {
    pub fn ready(&self) -> bool {
        self.size > 0 && self.tex.len() >= ground::BASE_COUNT
    }
}

/// dokunun ortalama rengi (küçük ölçekli harita için)
fn mean_color(t: &[u32]) -> u32 {
    if t.is_empty() {
        return 0xFF4F8A3A;
    }
    let (mut r, mut g, mut b) = (0u64, 0u64, 0u64);
    for &c in t {
        r += ((c >> 16) & 255) as u64;
        g += ((c >> 8) & 255) as u64;
        b += (c & 255) as u64;
    }
    let n = t.len() as u64;
    0xFF00_0000 | (((r / n) as u32) << 16) | (((g / n) as u32) << 8) | (b / n) as u32
}

/// bölge dokusu yüklenmemişse yerine geçen ortak doku
fn fallback(m: u8) -> u8 {
    match m {
        ground::R_MAIN => ground::GRASS,
        ground::R_ALT => ground::DARK_GRASS,
        ground::R_PATH => ground::DIRT,
        ground::R_VOID => ground::DARK_GRASS,
        _ => m,
    }
}

/// V13 bölge küçük harita renkleri (dokuların ortalaması): [ana, ikinci, yol, boşluk]
pub const AREA_COL: [[u32; 4]; 8] = [
    [0xFF587441, 0xFF3A3A1C, 0xFF6B4E35, 0xFF0A1A12],
    [0xFF3D4344, 0xFF808177, 0xFF555758, 0xFF151719],
    [0xFF2E2E30, 0xFF3B372F, 0xFF6D6A62, 0xFF131211],
    [0xFFEA974A, 0xFF813123, 0xFF927A5C, 0xFF0A0202],
    [0xFF373822, 0xFF0E3232, 0xFF4A4330, 0xFF0B0C0E],
    [0xFFBBCED6, 0xFFA0BED0, 0xFF8EA5B2, 0xFF16263C],
    [0xFF3E3424, 0xFF5B4E2B, 0xFF5A4A39, 0xFF212323],
    [0xFF417339, 0xFF417339, 0xFF7B7E53, 0xFF0A1A12],
];

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
    render_scaled(world, tex, x0, y0, 1.0, size, size, out);
}

/// V15 gerçek harita: aynı zemin çizimi küçük ölçekte (bir çıktı pikseli `step` dünya pikseli, `w`x`h`).
/// Büyük adımda doku pikseli yerine dokunun ortalama rengi alınır (titreşimsiz, karonun gerçek tonu).
pub fn render_scaled(world: &World, tex: &Textures, x0: f32, y0: f32, step: f32, w: usize, h: usize, out: &mut [u32]) {
    let ts = tex.size.max(1);
    let coarse = step > 2.0;
    let means: Vec<u32> = if coarse { tex.tex.iter().map(|t| mean_color(t)).collect() } else { Vec::new() };
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
    for py in 0..h {
        let wy = y0 + py as f32 * step;
        for px in 0..w {
            let wx = x0 + px as f32 * step;
            let idx = py * w + px;
            if wx < 0.0 || wy < 0.0 || wx >= MAP_PX || wy >= MAP_PX {
                out[idx] = 0xFF1E3A22;
                continue;
            }
            let nx = warp_noise(seed, wx / 90.0, wy / 90.0) * 2.0 - 1.0;
            let ny = warp_noise(seed ^ 0x99, wx / 90.0, wy / 90.0) * 2.0 - 1.0;
            let mut m = mat_at(wx + nx * warp, wy + ny * warp);
            let mut m2 = mat_at(wx + nx * warp + 7.0, wy + ny * warp + 5.0);
            // V13: bulunulan bölgenin dışı (sınırın ötesi) bölgenin boşluk dokusu
            if let Some(a) = tex.area {
                if crate::areas::area_exact(wx + nx * warp * 1.4, wy + ny * warp * 1.4) != Some(a) {
                    m = ground::R_VOID;
                    m2 = ground::R_VOID;
                }
            }
            let sample = |mm: u8| -> u32 {
                if !tex.ready() {
                    return 0xFF4F8A3A;
                }
                let mm = if (mm as usize) < tex.tex.len() && !tex.tex[mm as usize].is_empty() { mm } else { fallback(mm) };
                if coarse {
                    return means.get(mm as usize).copied().unwrap_or(0xFF4F8A3A);
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
            // yol şeritleri (yalnız tam ölçekte)
            if m == ground::ASPHALT && !coarse {
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
