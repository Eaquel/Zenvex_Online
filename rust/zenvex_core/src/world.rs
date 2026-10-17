//! Düz dünya üretimi: zemin karoları, ağaçlar, çalılar, kayalar, otlar.
use crate::rng::{fbm, value_noise, Rng};

pub const TILE: f32 = 32.0;
pub const MAP_TILES: usize = 128;
pub const MAP_PX: f32 = TILE * MAP_TILES as f32;

/// Kotlin tarafı bu isimleri manifest.json'daki görsellerle eşler. Sıra değişmemeli.
pub const STATIC_NAMES: [&str; 33] = [
    "oak0",
    "oak1",
    "oak2",
    "pine0",
    "pine1",
    "pine2",
    "birch0",
    "birch1",
    "dead0",
    "dead1", // 0-9
    "bush0",
    "bush1",
    "bush2",
    "bush3",
    "bush4", // 10-14
    "rock_big0",
    "rock_big1",
    "rock_big2",
    "rock_small0",
    "rock_small1",
    "rock_small2", // 15-20
    "stump",
    "log", // 21-22
    "tuft0",
    "tuft1",
    "tuft2",
    "tuft3",
    "tuft4", // 23-27
    "flower_daisy",
    "flower_poppy",
    "flower_corn",
    "flower_butter",
    "flower_lavender", // 28-32
];
pub const STUMP_SPRITE: u16 = 21;

/// Zemin karo indeksleri (manifest tileset.names ile aynı sıra).
pub mod tiles {
    pub const GRASS: u8 = 0; // 0-3
    pub const FLOWERS: u8 = 4; // 4-7
    pub const DARK: u8 = 8; // 8-9
    pub const DIRT: u8 = 10; // 10-12
    pub const PATH: u8 = 13; // 13-15
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    Tree,
    Bush,
    Rock,
    Deco,
}

#[derive(Clone, Debug)]
pub struct Static {
    pub sprite: u16,
    pub kind: Kind,
    pub x: f32,
    pub y: f32,
    /// çarpışma yarıçapı (0 = içinden geçilir)
    pub radius: f32,
    pub hp: f32,
    pub max_hp: f32,
    pub felled: bool,
    pub regrow: f32,
    pub shake: f32,
    /// rüzgâr fazı / görsel çeşitlilik
    pub phase: f32,
}

#[derive(Clone, Debug)]
pub struct GroundDecal {
    pub name: &'static str,
    pub x: f32,
    pub y: f32,
}

pub struct World {
    pub seed: u64,
    pub tiles: Vec<u8>,
    pub statics: Vec<Static>,
    pub decals: Vec<GroundDecal>,
    /// statik nesneler için kaba ızgara (her hücre 128 px) -> indeksler
    pub grid: Vec<Vec<u32>>,
}

pub const GRID_CELL: f32 = 128.0;
pub const GRID_N: usize = (MAP_PX / GRID_CELL) as usize;

pub fn spawn_point() -> (f32, f32) {
    (MAP_PX * 0.5, MAP_PX * 0.5)
}

fn tree_hp(sprite: u16) -> f32 {
    match sprite {
        0..=2 => 60.0,
        3..=5 => 45.0,
        6..=7 => 40.0,
        _ => 25.0,
    }
}

impl World {
    pub fn generate(seed: u64) -> World {
        let mut rng = Rng::new(seed);
        let n = MAP_TILES;
        let mut tiles = vec![0u8; n * n];
        let (cx, cy) = (n as f32 / 2.0, n as f32 / 2.0);

        // --- zemin
        for ty in 0..n {
            for tx in 0..n {
                let fx = tx as f32;
                let fy = ty as f32;
                let dirt = fbm(seed ^ 0xD1, fx / 14.0, fy / 14.0);
                let dark = fbm(seed ^ 0xDA, fx / 10.0, fy / 10.0);
                let t = if dirt > 0.72 {
                    tiles::DIRT + rng.below(3) as u8
                } else if dark > 0.66 {
                    tiles::DARK + rng.below(2) as u8
                } else if rng.chance(0.07) {
                    tiles::FLOWERS + rng.below(4) as u8
                } else {
                    tiles::GRASS + rng.below(4) as u8
                };
                tiles[ty * n + tx] = t;
            }
        }
        // patikalar: merkezden dışarı kıvrılan yollar
        for p in 0..4 {
            let mut ang = p as f32 * std::f32::consts::FRAC_PI_2 + rng.range(-0.4, 0.4);
            let (mut x, mut y) = (cx, cy);
            for _ in 0..90 {
                ang += rng.range(-0.25, 0.25);
                x += ang.cos();
                y += ang.sin();
                for (ox, oy) in [(0, 0), (1, 0), (0, 1), (1, 1)] {
                    let tx = x as i32 + ox;
                    let ty = y as i32 + oy;
                    if tx >= 2 && ty >= 2 && (tx as usize) < n - 2 && (ty as usize) < n - 2 {
                        tiles[ty as usize * n + tx as usize] = tiles::PATH + rng.below(3) as u8;
                    }
                }
            }
        }

        let mut decals = Vec::new();
        for i in 0..160 {
            decals.push(GroundDecal {
                name: ["dirt_patch0", "dirt_patch1", "dirt_patch2"][i % 3],
                x: rng.range(64.0, MAP_PX - 64.0),
                y: rng.range(64.0, MAP_PX - 64.0),
            });
        }

        // --- nesneler (jitter'lı ızgara örnekleme)
        let mut statics: Vec<Static> = Vec::new();
        let is_path = |tiles: &Vec<u8>, x: f32, y: f32| {
            let tx = (x / TILE) as usize;
            let ty = (y / TILE) as usize;
            tx < n && ty < n && tiles[ty * n + tx] >= tiles::PATH
        };
        let cell = 44.0;
        let cells = (MAP_PX / cell) as i32;
        let (spx, spy) = spawn_point();
        for gy in 0..cells {
            for gx in 0..cells {
                let x = (gx as f32 + rng.range(0.15, 0.85)) * cell;
                let y = (gy as f32 + rng.range(0.15, 0.85)) * cell;
                let border = x < 96.0 || y < 96.0 || x > MAP_PX - 96.0 || y > MAP_PX - 96.0;
                let d_spawn = ((x - spx).powi(2) + (y - spy).powi(2)).sqrt();
                let forest = fbm(seed ^ 0xF0, x / 520.0, y / 520.0);
                let pine_zone = value_noise(seed ^ 0x91, x / 700.0, y / 700.0);
                let on_path = is_path(&tiles, x, y);
                let clearing = d_spawn < 260.0;
                let p_tree = if border {
                    0.9
                } else if clearing || on_path {
                    0.0
                } else {
                    ((forest - 0.38) * 1.6).clamp(0.02, 0.62)
                };
                let r = rng.f32();
                if r < p_tree {
                    let sprite: u16 = if rng.chance(0.06) {
                        8 + rng.below(2) as u16
                    } else if pine_zone > 0.55 {
                        if rng.chance(0.8) {
                            3 + rng.below(3) as u16
                        } else {
                            rng.below(3) as u16
                        }
                    } else if rng.chance(0.22) {
                        6 + rng.below(2) as u16
                    } else {
                        rng.below(3) as u16
                    };
                    let hp = tree_hp(sprite);
                    statics.push(Static {
                        sprite,
                        kind: Kind::Tree,
                        x,
                        y,
                        radius: 11.0,
                        hp,
                        max_hp: hp,
                        felled: false,
                        regrow: 0.0,
                        shake: 0.0,
                        phase: rng.range(0.0, std::f32::consts::TAU),
                    });
                } else if r < p_tree + 0.06 && !clearing && !on_path {
                    let sprite = 10 + rng.below(5) as u16;
                    statics.push(Static {
                        sprite,
                        kind: Kind::Bush,
                        x,
                        y,
                        radius: 0.0,
                        hp: 1.0,
                        max_hp: 1.0,
                        felled: false,
                        regrow: 0.0,
                        shake: 0.0,
                        phase: rng.range(0.0, std::f32::consts::TAU),
                    });
                } else if r < p_tree + 0.075 && d_spawn > 140.0 && !on_path {
                    let (sprite, radius, kind) = if rng.chance(0.45) {
                        (15 + rng.below(3) as u16, 22.0, Kind::Rock)
                    } else if rng.chance(0.25) {
                        (22, 18.0, Kind::Rock)
                    } else {
                        (18 + rng.below(3) as u16, 0.0, Kind::Deco)
                    };
                    statics.push(Static {
                        sprite,
                        kind,
                        x,
                        y,
                        radius,
                        hp: 1.0,
                        max_hp: 1.0,
                        felled: false,
                        regrow: 0.0,
                        shake: 0.0,
                        phase: 0.0,
                    });
                }
                // otlar ve çiçekler (dekor, çarpışmasız)
                let deco_n = if on_path { 0 } else { 2 + rng.below(3) };
                for _ in 0..deco_n {
                    let dx = x + rng.range(-cell * 0.5, cell * 0.5);
                    let dy = y + rng.range(-cell * 0.5, cell * 0.5);
                    if dx < 8.0 || dy < 8.0 || dx > MAP_PX - 8.0 || dy > MAP_PX - 8.0 {
                        continue;
                    }
                    let sprite = if rng.chance(0.28) {
                        28 + rng.below(5) as u16
                    } else {
                        23 + rng.below(5) as u16
                    };
                    statics.push(Static {
                        sprite,
                        kind: Kind::Deco,
                        x: dx,
                        y: dy,
                        radius: 0.0,
                        hp: 1.0,
                        max_hp: 1.0,
                        felled: false,
                        regrow: 0.0,
                        shake: 0.0,
                        phase: rng.range(0.0, std::f32::consts::TAU),
                    });
                }
            }
        }

        let mut grid = vec![Vec::new(); GRID_N * GRID_N];
        for (i, s) in statics.iter().enumerate() {
            let gx = ((s.x / GRID_CELL) as usize).min(GRID_N - 1);
            let gy = ((s.y / GRID_CELL) as usize).min(GRID_N - 1);
            grid[gy * GRID_N + gx].push(i as u32);
        }
        World {
            seed,
            tiles,
            statics,
            decals,
            grid,
        }
    }

    /// (x0,y0)-(x1,y1) dikdörtgenine değebilecek statik nesnelerin indeksleri.
    pub fn query(&self, x0: f32, y0: f32, x1: f32, y1: f32, out: &mut Vec<u32>) {
        out.clear();
        let gx0 = ((x0 / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        let gy0 = ((y0 / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        let gx1 = ((x1 / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        let gy1 = ((y1 / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        for gy in gy0..=gy1 {
            for gx in gx0..=gx1 {
                out.extend_from_slice(&self.grid[gy * GRID_N + gx]);
            }
        }
    }

    /// Daire (x,y,r) katı nesnelerle çakışıyorsa dışarı iter.
    pub fn resolve_collision(&self, x: &mut f32, y: &mut f32, r: f32, scratch: &mut Vec<u32>) {
        self.query(*x - 64.0, *y - 64.0, *x + 64.0, *y + 64.0, scratch);
        for &i in scratch.iter() {
            let s = &self.statics[i as usize];
            if s.radius <= 0.0 || (s.kind == Kind::Tree && s.felled) {
                continue;
            }
            let dx = *x - s.x;
            // nesnenin tabanı basık elips gibi davranır
            let dy = (*y - s.y) * 1.6;
            let d2 = dx * dx + dy * dy;
            let min = s.radius + r;
            if d2 < min * min {
                let d = d2.sqrt().max(0.001);
                let push = min - d;
                *x += dx / d * push;
                *y += dy / d * push / 1.6;
            }
        }
        *x = x.clamp(48.0, MAP_PX - 48.0);
        *y = y.clamp(48.0, MAP_PX - 48.0);
    }

    pub fn blocked(&self, x: f32, y: f32, r: f32, scratch: &mut Vec<u32>) -> bool {
        self.query(x - 64.0, y - 64.0, x + 64.0, y + 64.0, scratch);
        scratch.iter().any(|&i| {
            let s = &self.statics[i as usize];
            s.radius > 0.0
                && !(s.kind == Kind::Tree && s.felled)
                && ((x - s.x).powi(2) + ((y - s.y) * 1.6).powi(2)) < (s.radius + r).powi(2)
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generation_is_deterministic() {
        let a = World::generate(5);
        let b = World::generate(5);
        assert_eq!(a.tiles, b.tiles);
        assert_eq!(a.statics.len(), b.statics.len());
    }

    #[test]
    fn spawn_is_clear() {
        let w = World::generate(1);
        let (x, y) = spawn_point();
        let mut s = Vec::new();
        assert!(!w.blocked(x, y, 10.0, &mut s));
        let trees = w.statics.iter().filter(|s| s.kind == Kind::Tree).count();
        assert!(trees > 500, "orman çok seyrek: {trees}");
    }

    #[test]
    fn static_sprites_valid() {
        let w = World::generate(9);
        assert!(w
            .statics
            .iter()
            .all(|s| (s.sprite as usize) < STATIC_NAMES.len()));
    }
}
