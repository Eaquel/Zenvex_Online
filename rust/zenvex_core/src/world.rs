//! V2 dünyası: orman + şehir, bölgeler, zemin malzemeleri, statik nesneler, çarpışma, doğuş noktaları.
use crate::data::ZKind;
use crate::rng::{fbm, value_noise, Rng};

pub const TILE: f32 = 64.0;
pub const MAP_TILES: usize = 360;
pub const MAP_PX: f32 = TILE * MAP_TILES as f32;

// ---- zemin malzemeleri (assetgen/textures.py sırası)
pub mod ground {
    pub const GRASS: u8 = 0;
    pub const DARK_GRASS: u8 = 1;
    pub const DRY_GRASS: u8 = 2;
    pub const DIRT: u8 = 3;
    pub const GRAVEL: u8 = 4;
    pub const ASPHALT: u8 = 5;
    pub const SIDEWALK: u8 = 6;
    pub const CONCRETE: u8 = 7;
    pub const MUD: u8 = 8;
    pub const COUNT: usize = 9;
}

/// Kotlin bu isimleri sprite atlasındaki görsellerle eşler. Sıra değişmemeli.
pub const STATIC_NAMES: &[&str] = &[
    "oak0",
    "oak1",
    "oak2",
    "oak_autumn",
    "pine0",
    "pine1",
    "pine2",
    "birch0",
    "birch1",
    "dead0",
    "dead1", // 0-10
    "bush0",
    "bush1",
    "bush2",
    "bush3",
    "bush4", // 11-15
    "rock_big0",
    "rock_big1",
    "rock_big2",
    "rock_small0",
    "rock_small1",
    "rock_small2", // 16-21
    "stump",
    "log",
    "grass0",
    "grass1",
    "grass2",
    "grass3", // 22-27
    "flower_daisy",
    "flower_poppy",
    "flower_corn",
    "flower_butter",
    "flower_lavender", // 28-32
    "mushrooms0",
    "mushrooms1",
    "reeds0",
    "reeds1", // 33-36
    "house0",
    "house1",
    "house2",
    "apartment0",
    "apartment1",
    "shop0",
    "shop1",
    "police",
    "gas", // 37-45
    "wall_x",
    "wall_z",
    "gate",
    "tower",
    "lamp",
    "wreck0",
    "wreck1",
    "crate",
    "crate_mil",
    "barrel",
    "barrel_tox", // 46-56
    "workbench",
    "campfire",
    "tent",
    "stall",
    "sandbags",
    "fence",
    "trash",
    "cone", // 57-64
];
pub fn sprite(name: &str) -> u16 {
    STATIC_NAMES.iter().position(|n| *n == name).unwrap_or(0) as u16
}
pub const STUMP: &str = "stump";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    Tree,
    Bush,
    Rock,
    Deco,
    Building,
    Prop,
    Crate,
    Lamp,
    Fire,
    Bench,
    Gate,
    Tower,
    Stall,
}

#[derive(Clone, Copy, Debug)]
pub enum Shape {
    None,
    Circle(f32),
    Rect(f32, f32),
}

#[derive(Clone, Debug)]
pub struct Static {
    pub sprite: u16,
    pub kind: Kind,
    pub x: f32,
    pub y: f32,
    pub shape: Shape,
    pub hp: f32,
    pub max_hp: f32,
    pub broken: bool,
    pub respawn: f32,
    pub shake: f32,
    pub phase: f32,
    /// y-sıralama için taban derinliği (binalarda ön yüz)
    pub sort_off: f32,
}

pub struct Travel {
    pub id: &'static str,
    pub name: &'static str,
    /// karo koordinatı; (-1,-1) kamp, (-2,-2) şehir doğma noktası
    pub tile: (f32, f32),
    pub level: u32,
    pub cost: u32,
}

impl Travel {
    pub fn pos(&self) -> (f32, f32) {
        match self.tile {
            (a, _) if a == -1.0 => camp_spawn(),
            (a, _) if a == -2.0 => city_spawn(),
            (a, _) if a == -3.0 => (BOSS.0 - 600.0, BOSS.1 + 400.0),
            (x, y) => (x * TILE, y * TILE),
        }
    }
}

/// Haritadan hızlı seyahat noktaları
pub const TRAVEL: &[Travel] = &[
    Travel { id: "camp", name: "Kamp", tile: (-1.0, -1.0), level: 1, cost: 0 },
    Travel { id: "deep", name: "Derin Orman", tile: (300.0, 250.0), level: 5, cost: 25 },
    Travel { id: "swamp", name: "Bataklık", tile: (50.0, 300.0), level: 8, cost: 40 },
    Travel { id: "city", name: "Şehir Güvenli Bölgesi", tile: (-2.0, -2.0), level: 10, cost: 60 },
    Travel { id: "downtown", name: "Şehir Merkezi", tile: (50.0, 60.0), level: 20, cost: 100 },
    Travel { id: "boss", name: "Titan Arenası", tile: (-3.0, -3.0), level: 45, cost: 200 },
];

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub enum Zone {
    Camp,
    Forest,
    DeepForest,
    Swamp,
    Wall,
    SafeCity,
    Suburb,
    Downtown,
    Industrial,
    BossArena,
}

impl Zone {
    pub fn name(self) -> &'static str {
        match self {
            Zone::Camp => "Kamp (Güvenli)",
            Zone::Forest => "Başlangıç Ormanı",
            Zone::DeepForest => "Derin Orman",
            Zone::Swamp => "Bataklık",
            Zone::Wall => "Şehir Kapısı",
            Zone::SafeCity => "Şehir Güvenli Bölgesi",
            Zone::Suburb => "Banliyö",
            Zone::Downtown => "Şehir Merkezi",
            Zone::Industrial => "Sanayi Bölgesi",
            Zone::BossArena => "Titan Arenası",
        }
    }
    pub fn level_range(self) -> (u32, u32) {
        match self {
            Zone::Camp | Zone::SafeCity | Zone::Wall => (1, 1),
            Zone::Forest => (1, 5),
            Zone::DeepForest => (5, 10),
            Zone::Swamp => (8, 15),
            Zone::Suburb => (10, 20),
            Zone::Downtown => (20, 30),
            Zone::Industrial => (30, 45),
            Zone::BossArena => (45, 50),
        }
    }
    pub fn safe(self) -> bool {
        matches!(self, Zone::Camp | Zone::SafeCity)
    }
    pub fn loot_mult(self) -> f32 {
        let (a, b) = self.level_range();
        1.0 + ((a + b) as f32 / 2.0) / 25.0
    }
    pub fn city(self) -> bool {
        matches!(
            self,
            Zone::SafeCity | Zone::Suburb | Zone::Downtown | Zone::Industrial | Zone::BossArena
        )
    }
    /// (tür, ağırlık)
    pub fn spawns(self) -> &'static [(ZKind, u32)] {
        match self {
            Zone::Forest => &[(ZKind::Walker, 80), (ZKind::Dog, 20)],
            Zone::DeepForest => &[(ZKind::Walker, 55), (ZKind::Runner, 25), (ZKind::Dog, 20)],
            Zone::Swamp => &[(ZKind::Toxic, 60), (ZKind::Walker, 35), (ZKind::Dog, 5)],
            Zone::Suburb => &[
                (ZKind::Walker, 45),
                (ZKind::Runner, 30),
                (ZKind::Tank, 10),
                (ZKind::Dog, 15),
            ],
            Zone::Downtown => &[
                (ZKind::Dark, 35),
                (ZKind::Runner, 25),
                (ZKind::Tank, 15),
                (ZKind::Walker, 15),
                (ZKind::Dog, 8),
                (ZKind::Mystery, 2),
            ],
            Zone::Industrial | Zone::BossArena => &[
                (ZKind::Dark, 30),
                (ZKind::Toxic, 25),
                (ZKind::Tank, 20),
                (ZKind::Runner, 15),
                (ZKind::Mystery, 4),
                (ZKind::Dog, 6),
            ],
            _ => &[],
        }
    }
}

// ---- yerleşim sabitleri (karo cinsinden)
pub const WALL_Y: usize = 150;
pub const GATE_X0: usize = 176;
pub const GATE_X1: usize = 184;
pub const SAFE_X0: usize = 160;
pub const SAFE_X1: usize = 200;
pub const SAFE_Y0: usize = 118;
pub const SAFE_Y1: usize = 146;
pub const CAMP: (f32, f32) = (180.0 * TILE, 300.0 * TILE);
pub const CAMP_R: f32 = 11.0 * TILE;
pub const BOSS: (f32, f32) = (305.0 * TILE, 24.0 * TILE);

pub fn city_spawn() -> (f32, f32) {
    (180.0 * TILE, 138.0 * TILE)
}
pub fn camp_spawn() -> (f32, f32) {
    (CAMP.0, CAMP.1 + 2.0 * TILE)
}

fn road_x(tx: usize) -> bool {
    tx >= 8 && (tx - 8) % 34 < 4
}
fn road_y(ty: usize) -> bool {
    ty < WALL_Y && ty >= 6 && (ty - 6) % 30 < 4
}

pub fn zone_at_tile(tx: usize, ty: usize) -> Zone {
    let x = tx as f32 * TILE + TILE / 2.0;
    let y = ty as f32 * TILE + TILE / 2.0;
    if (WALL_Y..=WALL_Y + 1).contains(&ty) {
        return Zone::Wall;
    }
    if ty < WALL_Y {
        if (SAFE_X0..SAFE_X1).contains(&tx) && (SAFE_Y0..SAFE_Y1).contains(&ty) {
            return Zone::SafeCity;
        }
        if (280..334).contains(&tx) && ty < 44 {
            return Zone::BossArena;
        }
        return if ty >= 90 {
            Zone::Suburb
        } else if ty >= 40 {
            Zone::Downtown
        } else {
            Zone::Industrial
        };
    }
    let d = ((x - CAMP.0).powi(2) + (y - CAMP.1).powi(2)).sqrt();
    if d < CAMP_R {
        return Zone::Camp;
    }
    if tx < 100 && ty > 250 {
        return Zone::Swamp;
    }
    if d < 55.0 * TILE {
        Zone::Forest
    } else {
        Zone::DeepForest
    }
}

pub struct World {
    pub seed: u64,
    pub ground: Vec<u8>,
    pub zones: Vec<u8>,
    pub statics: Vec<Static>,
    pub grid: Vec<Vec<u32>>,
    pub vehicle_spawns: Vec<(usize, f32, f32, f32)>,
    pub merchants: Vec<(f32, f32)>,
    pub benches: Vec<(f32, f32)>,
    pub snipers: Vec<(f32, f32)>,
}

pub const GRID_CELL: f32 = 512.0;
pub const GRID_N: usize = (MAP_PX / GRID_CELL) as usize + 1;

impl World {
    pub fn zone(&self, x: f32, y: f32) -> Zone {
        let tx = ((x / TILE) as usize).min(MAP_TILES - 1);
        let ty = ((y / TILE) as usize).min(MAP_TILES - 1);
        ZONES_ALL[self.zones[ty * MAP_TILES + tx] as usize]
    }

    pub fn ground_at(&self, tx: usize, ty: usize) -> u8 {
        self.ground[ty.min(MAP_TILES - 1) * MAP_TILES + tx.min(MAP_TILES - 1)]
    }

    fn push(&mut self, name: &str, kind: Kind, x: f32, y: f32, shape: Shape, hp: f32, phase: f32) {
        let sort_off = match shape {
            Shape::Rect(_, hh) if kind == Kind::Building => hh,
            _ => 0.0,
        };
        self.statics.push(Static {
            sprite: sprite(name),
            kind,
            x,
            y,
            shape,
            hp,
            max_hp: hp,
            broken: false,
            respawn: 0.0,
            shake: 0.0,
            phase,
            sort_off,
        });
    }

    fn free(&self, x: f32, y: f32, r: f32) -> bool {
        // sadece üretim sırasında: yakın büyük nesne var mı?
        let gx = ((x / GRID_CELL) as usize).min(GRID_N - 1);
        let gy = ((y / GRID_CELL) as usize).min(GRID_N - 1);
        let _ = (gx, gy);
        self.statics.iter().rev().take(400).all(|s| match s.shape {
            Shape::None => true,
            Shape::Circle(sr) => (s.x - x).hypot(s.y - y) > sr + r,
            Shape::Rect(hw, hh) => (x - s.x).abs() > hw + r || (y - s.y).abs() > hh + r,
        })
    }

    pub fn generate(seed: u64) -> World {
        let n = MAP_TILES;
        let mut rng = Rng::new(seed);
        let mut w = World {
            seed,
            ground: vec![0; n * n],
            zones: vec![0; n * n],
            statics: Vec::new(),
            grid: Vec::new(),
            vehicle_spawns: Vec::new(),
            merchants: Vec::new(),
            benches: Vec::new(),
            snipers: Vec::new(),
        };
        // ---- bölgeler ve zemin
        for ty in 0..n {
            for tx in 0..n {
                let z = zone_at_tile(tx, ty);
                w.zones[ty * n + tx] = ZONES_ALL.iter().position(|q| *q == z).unwrap() as u8;
                let fx = tx as f32;
                let fy = ty as f32;
                let g = match z {
                    Zone::Camp => {
                        let d = ((fx - 180.0).powi(2) + (fy - 300.0).powi(2)).sqrt();
                        if d < 6.0 {
                            ground::GRAVEL
                        } else if d < 9.0 {
                            ground::DIRT
                        } else {
                            ground::GRASS
                        }
                    }
                    Zone::Swamp => {
                        let m = fbm(seed ^ 0x5A, fx / 9.0, fy / 9.0);
                        if m > 0.58 {
                            ground::MUD
                        } else if m > 0.45 {
                            ground::DARK_GRASS
                        } else {
                            ground::GRASS
                        }
                    }
                    Zone::Forest | Zone::DeepForest => {
                        let d1 = fbm(seed ^ 0xD1, fx / 16.0, fy / 16.0);
                        let d2 = fbm(seed ^ 0xDA, fx / 11.0, fy / 11.0);
                        if d1 > 0.74 {
                            ground::DIRT
                        } else if d2 > 0.66 {
                            ground::DARK_GRASS
                        } else if d2 < 0.28 {
                            ground::DRY_GRASS
                        } else {
                            ground::GRASS
                        }
                    }
                    Zone::Wall => ground::CONCRETE,
                    Zone::SafeCity => ground::CONCRETE,
                    Zone::BossArena => ground::CONCRETE,
                    Zone::Suburb | Zone::Downtown | Zone::Industrial => {
                        if road_x(tx) || road_y(ty) {
                            ground::ASPHALT
                        } else if (tx >= 6 && road_x(tx - 2))
                            || road_x(tx + 2)
                            || (ty >= 2 && road_y(ty - 2))
                            || road_y(ty + 2)
                            || road_x(tx + 1)
                            || (tx >= 1 && road_x(tx - 1))
                            || road_y(ty + 1)
                            || (ty >= 1 && road_y(ty - 1))
                        {
                            ground::SIDEWALK
                        } else if z == Zone::Industrial {
                            if fbm(seed ^ 0x77, fx / 8.0, fy / 8.0) > 0.6 {
                                ground::DIRT
                            } else {
                                ground::CONCRETE
                            }
                        } else if z == Zone::Downtown {
                            ground::CONCRETE
                        } else if fbm(seed ^ 0x99, fx / 10.0, fy / 10.0) > 0.7 {
                            ground::DRY_GRASS
                        } else {
                            ground::GRASS
                        }
                    }
                };
                w.ground[ty * n + tx] = g;
            }
        }
        // kamp → kapı patikası ve orman patikaları
        let carve = |w: &mut World,
                     x0: f32,
                     y0: f32,
                     x1: f32,
                     y1: f32,
                     wid: i32,
                     mat: u8,
                     rng: &mut Rng| {
            let steps = ((x1 - x0).hypot(y1 - y0) * 1.5) as i32;
            let mut wob = 0.0f32;
            for i in 0..=steps {
                let t = i as f32 / steps.max(1) as f32;
                wob += rng.range(-0.3, 0.3);
                wob *= 0.95;
                let x = x0 + (x1 - x0) * t + wob;
                let y = y0 + (y1 - y0) * t;
                for oy in -wid..=wid {
                    for ox in -wid..=wid {
                        let tx = (x as i32 + ox).clamp(1, n as i32 - 2) as usize;
                        let ty = (y as i32 + oy).clamp(1, n as i32 - 2) as usize;
                        if zone_at_tile(tx, ty) != Zone::Wall {
                            w.ground[ty * n + tx] = mat;
                        }
                    }
                }
            }
        };
        carve(
            &mut w,
            180.0,
            292.0,
            180.0,
            153.0,
            1,
            ground::GRAVEL,
            &mut rng,
        );
        carve(&mut w, 173.0, 300.0, 90.0, 290.0, 1, ground::DIRT, &mut rng);
        carve(
            &mut w,
            187.0,
            300.0,
            290.0,
            320.0,
            1,
            ground::DIRT,
            &mut rng,
        );
        carve(
            &mut w,
            180.0,
            309.0,
            200.0,
            350.0,
            0,
            ground::DIRT,
            &mut rng,
        );
        carve(
            &mut w,
            180.0,
            146.0,
            180.0,
            120.0,
            1,
            ground::ASPHALT,
            &mut rng,
        );

        w.place_city(&mut rng);
        w.place_forest(&mut rng);
        w.build_grid();
        w
    }

    fn place_city(&mut self, rng: &mut Rng) {
        let t = TILE;
        // sur duvarı (kapı boşluklu)
        let mut x = 2.0;
        while x < MAP_TILES as f32 - 2.0 {
            let cx = x + 2.0;
            if !(cx > GATE_X0 as f32 - 1.0 && cx < GATE_X1 as f32 + 1.0) {
                self.push(
                    "wall_x",
                    Kind::Prop,
                    cx * t,
                    (WALL_Y as f32 + 1.0) * t,
                    Shape::Rect(2.0 * t, 0.4 * t),
                    0.0,
                    0.0,
                );
            }
            x += 4.0;
        }
        self.push(
            "gate",
            Kind::Gate,
            180.0 * t,
            (WALL_Y as f32 + 1.0) * t,
            Shape::Rect(4.0 * t, 0.5 * t),
            0.0,
            0.0,
        );
        for gx in [GATE_X0 as f32 - 1.5, GATE_X1 as f32 + 1.5] {
            self.push(
                "tower",
                Kind::Tower,
                gx * t,
                (WALL_Y as f32 + 2.5) * t,
                Shape::Rect(1.2 * t, 1.2 * t),
                0.0,
                0.0,
            );
            self.snipers.push((gx * t, (WALL_Y as f32 + 2.5) * t));
        }
        // güvenli bölge duvarları (orta açıklıklı)
        let (x0, x1, y0, y1) = (
            SAFE_X0 as f32,
            SAFE_X1 as f32,
            SAFE_Y0 as f32,
            SAFE_Y1 as f32,
        );
        let mut x = x0;
        while x < x1 {
            let cx = x + 2.0;
            if (cx - 180.0).abs() > 4.0 {
                self.push(
                    "wall_x",
                    Kind::Prop,
                    cx * t,
                    y0 * t,
                    Shape::Rect(2.0 * t, 0.4 * t),
                    0.0,
                    0.0,
                );
                self.push(
                    "wall_x",
                    Kind::Prop,
                    cx * t,
                    y1 * t,
                    Shape::Rect(2.0 * t, 0.4 * t),
                    0.0,
                    0.0,
                );
            }
            x += 4.0;
        }
        let mut y = y0;
        while y < y1 {
            let cy = y + 2.0;
            if (cy - 132.0).abs() > 4.0 {
                self.push(
                    "wall_z",
                    Kind::Prop,
                    x0 * t,
                    cy * t,
                    Shape::Rect(0.4 * t, 2.0 * t),
                    0.0,
                    0.0,
                );
                self.push(
                    "wall_z",
                    Kind::Prop,
                    x1 * t,
                    cy * t,
                    Shape::Rect(0.4 * t, 2.0 * t),
                    0.0,
                    0.0,
                );
            }
            y += 4.0;
        }
        for (cx, cy) in [
            (x0 + 2.0, y0 + 2.0),
            (x1 - 2.0, y0 + 2.0),
            (x0 + 2.0, y1 - 2.0),
            (x1 - 2.0, y1 - 2.0),
        ] {
            self.push(
                "tower",
                Kind::Tower,
                cx * t,
                cy * t,
                Shape::Rect(1.2 * t, 1.2 * t),
                0.0,
                0.0,
            );
            self.snipers.push((cx * t, cy * t));
        }
        // güvenli bölge içi: tüccar, tezgâh, çadırlar, kum torbaları, lambalar
        self.push(
            "stall",
            Kind::Stall,
            172.0 * t,
            128.0 * t,
            Shape::Rect(1.3 * t, 0.6 * t),
            0.0,
            0.0,
        );
        self.merchants.push((172.0 * t, 129.5 * t));
        self.push(
            "workbench",
            Kind::Bench,
            188.0 * t,
            128.0 * t,
            Shape::Rect(0.9 * t, 0.5 * t),
            0.0,
            0.0,
        );
        self.benches.push((188.0 * t, 129.0 * t));
        for (cx, cy) in [
            (166.0, 138.0),
            (194.0, 138.0),
            (166.0, 124.0),
            (194.0, 124.0),
        ] {
            self.push(
                "tent",
                Kind::Prop,
                cx * t,
                cy * t,
                Shape::Rect(1.1 * t, 1.1 * t),
                0.0,
                0.0,
            );
        }
        for (cx, cy) in [
            (176.0, 136.0),
            (184.0, 136.0),
            (176.0, 122.0),
            (184.0, 122.0),
        ] {
            self.push(
                "lamp",
                Kind::Lamp,
                cx * t,
                cy * t,
                Shape::Circle(0.15 * t),
                0.0,
                0.0,
            );
        }
        self.push(
            "sandbags",
            Kind::Prop,
            180.0 * t,
            143.0 * t,
            Shape::Rect(1.4 * t, 0.35 * t),
            0.0,
            0.0,
        );
        self.vehicle_spawns.push((3, 192.0 * t, 141.0 * t, 0.0));

        // şehir blokları
        let mut by = 6usize;
        while by + 30 <= WALL_Y {
            let mut bx = 8usize;
            while bx + 34 <= MAP_TILES {
                let cx0 = (bx + 6) as f32;
                let cy0 = (by + 6) as f32;
                let cx1 = (bx + 32) as f32;
                let cy1 = (by + 28) as f32;
                let mid_x = (cx0 + cx1) / 2.0;
                let mid_y = (cy0 + cy1) / 2.0;
                let zone = zone_at_tile(mid_x as usize, mid_y as usize);
                if zone == Zone::SafeCity
                    || zone == Zone::Wall
                    || (bx as f32 + 17.0 - 180.0).abs() < 22.0
                        && (by as f32 + 15.0 - 132.0).abs() < 18.0
                {
                    bx += 34;
                    continue;
                }
                match zone {
                    Zone::Suburb => {
                        for (hx, hy) in [
                            (cx0 + 5.0, cy0 + 5.0),
                            (cx1 - 5.0, cy0 + 5.0),
                            (cx0 + 5.0, cy1 - 5.0),
                            (cx1 - 5.0, cy1 - 5.0),
                        ] {
                            if rng.chance(0.85) {
                                let k = rng.below(3);
                                self.push(
                                    ["house0", "house1", "house2"][k as usize],
                                    Kind::Building,
                                    hx * t,
                                    hy * t,
                                    Shape::Rect(3.3 * t, 2.7 * t),
                                    0.0,
                                    0.0,
                                );
                                self.push(
                                    "fence",
                                    Kind::Prop,
                                    hx * t,
                                    (hy + 4.2) * t,
                                    Shape::Rect(1.5 * t, 0.1 * t),
                                    0.0,
                                    0.0,
                                );
                            }
                        }
                        self.scatter_city_props(rng, cx0, cy0, cx1, cy1, zone);
                    }
                    Zone::Downtown => {
                        self.push(
                            if rng.chance(0.5) {
                                "apartment0"
                            } else {
                                "apartment1"
                            },
                            Kind::Building,
                            (cx0 + 7.0) * t,
                            (cy0 + 6.0) * t,
                            Shape::Rect(4.9 * t, 3.5 * t),
                            0.0,
                            0.0,
                        );
                        self.push(
                            if rng.chance(0.5) { "shop0" } else { "shop1" },
                            Kind::Building,
                            (cx1 - 6.0) * t,
                            (cy0 + 6.0) * t,
                            Shape::Rect(3.7 * t, 2.9 * t),
                            0.0,
                            0.0,
                        );
                        self.push(
                            if rng.chance(0.5) {
                                "apartment1"
                            } else {
                                "apartment0"
                            },
                            Kind::Building,
                            mid_x * t,
                            (cy1 - 5.0) * t,
                            Shape::Rect(4.9 * t, 3.5 * t),
                            0.0,
                            0.0,
                        );
                        self.scatter_city_props(rng, cx0, cy0, cx1, cy1, zone);
                    }
                    Zone::Industrial | Zone::BossArena => {
                        if zone == Zone::Industrial {
                            let b = if rng.chance(0.5) { "police" } else { "gas" };
                            self.push(
                                b,
                                Kind::Building,
                                (cx0 + 6.0) * t,
                                (cy0 + 6.0) * t,
                                if b == "gas" {
                                    Shape::Rect(1.5 * t, 1.0 * t)
                                } else {
                                    Shape::Rect(3.7 * t, 2.9 * t)
                                },
                                0.0,
                                0.0,
                            );
                        }
                        self.scatter_city_props(rng, cx0, cy0, cx1, cy1, zone);
                    }
                    _ => {}
                }
                bx += 34;
            }
            by += 30;
        }
        // yol kenarı lambalar
        let mut y = 8usize;
        while y < WALL_Y {
            let mut x = 8usize;
            while x < MAP_TILES - 4 {
                if road_y(y) && (x % 12 == 0) && zone_at_tile(x, y) != Zone::SafeCity {
                    self.push(
                        "lamp",
                        Kind::Lamp,
                        x as f32 * t,
                        (y as f32 - 2.2) * t,
                        Shape::Circle(0.15 * t),
                        0.0,
                        0.0,
                    );
                }
                x += 1;
            }
            y += 30;
        }
        // araç doğuşları
        for _ in 0..4 {
            self.vehicle_spawns.push((
                1,
                rng.range(20.0, 340.0) * t,
                rng.range(95.0, 145.0) * t,
                rng.range(0.0, std::f32::consts::TAU),
            ));
        }
        for _ in 0..4 {
            self.vehicle_spawns.push((
                2,
                rng.range(20.0, 340.0) * t,
                rng.range(45.0, 88.0) * t,
                rng.range(0.0, std::f32::consts::TAU),
            ));
        }
        for _ in 0..3 {
            self.vehicle_spawns.push((
                3,
                rng.range(20.0, 270.0) * t,
                rng.range(8.0, 38.0) * t,
                rng.range(0.0, std::f32::consts::TAU),
            ));
        }
    }

    fn scatter_city_props(
        &mut self,
        rng: &mut Rng,
        x0: f32,
        y0: f32,
        x1: f32,
        y1: f32,
        zone: Zone,
    ) {
        let t = TILE;
        let count = match zone {
            Zone::Suburb => 6,
            Zone::Downtown => 8,
            _ => 12,
        };
        for _ in 0..count {
            let x = rng.range(x0, x1) * t;
            let y = rng.range(y0, y1) * t;
            if !self.free(x, y, 1.2 * t) {
                continue;
            }
            let r = rng.f32();
            match zone {
                Zone::Suburb => {
                    if r < 0.35 {
                        self.push(
                            ["oak0", "oak1", "birch0"][rng.below(3) as usize],
                            Kind::Tree,
                            x,
                            y,
                            Shape::Circle(0.3 * t),
                            50.0,
                            rng.range(0.0, std::f32::consts::TAU),
                        );
                    } else if r < 0.6 {
                        self.push(
                            ["bush0", "bush1", "bush4"][rng.below(3) as usize],
                            Kind::Bush,
                            x,
                            y,
                            Shape::None,
                            1.0,
                            rng.range(0.0, std::f32::consts::TAU),
                        );
                    } else if r < 0.75 {
                        self.push(
                            "crate",
                            Kind::Crate,
                            x,
                            y,
                            Shape::Circle(0.45 * t),
                            30.0,
                            0.0,
                        );
                    } else if r < 0.88 {
                        self.push("trash", Kind::Prop, x, y, Shape::Circle(0.4 * t), 0.0, 0.0);
                    } else {
                        self.push(
                            if rng.chance(0.5) { "wreck0" } else { "wreck1" },
                            Kind::Prop,
                            x,
                            y,
                            Shape::Rect(0.9 * t, 2.0 * t),
                            0.0,
                            0.0,
                        );
                    }
                }
                Zone::Downtown => {
                    if r < 0.3 {
                        self.push(
                            if rng.chance(0.5) { "wreck0" } else { "wreck1" },
                            Kind::Prop,
                            x,
                            y,
                            Shape::Rect(0.9 * t, 2.0 * t),
                            0.0,
                            0.0,
                        );
                    } else if r < 0.5 {
                        self.push(
                            "crate",
                            Kind::Crate,
                            x,
                            y,
                            Shape::Circle(0.45 * t),
                            30.0,
                            0.0,
                        );
                    } else if r < 0.7 {
                        self.push("trash", Kind::Prop, x, y, Shape::Circle(0.4 * t), 0.0, 0.0);
                    } else if r < 0.85 {
                        self.push("cone", Kind::Deco, x, y, Shape::None, 0.0, 0.0);
                    } else {
                        self.push("barrel", Kind::Prop, x, y, Shape::Circle(0.3 * t), 0.0, 0.0);
                    }
                }
                _ => {
                    if r < 0.3 {
                        self.push(
                            "crate_mil",
                            Kind::Crate,
                            x,
                            y,
                            Shape::Circle(0.45 * t),
                            40.0,
                            0.0,
                        );
                    } else if r < 0.5 {
                        self.push(
                            if rng.chance(0.5) {
                                "barrel_tox"
                            } else {
                                "barrel"
                            },
                            Kind::Prop,
                            x,
                            y,
                            Shape::Circle(0.3 * t),
                            0.0,
                            0.0,
                        );
                    } else if r < 0.7 {
                        self.push(
                            if rng.chance(0.5) { "wreck0" } else { "wreck1" },
                            Kind::Prop,
                            x,
                            y,
                            Shape::Rect(0.9 * t, 2.0 * t),
                            0.0,
                            0.0,
                        );
                    } else if r < 0.85 {
                        self.push(
                            "sandbags",
                            Kind::Prop,
                            x,
                            y,
                            Shape::Rect(1.4 * t, 0.35 * t),
                            0.0,
                            0.0,
                        );
                    } else {
                        self.push(
                            "dead0",
                            Kind::Tree,
                            x,
                            y,
                            Shape::Circle(0.25 * t),
                            25.0,
                            0.0,
                        );
                    }
                }
            }
        }
    }

    fn place_forest(&mut self, rng: &mut Rng) {
        let t = TILE;
        // kamp
        let (cx, cy) = CAMP;
        self.push(
            "campfire",
            Kind::Fire,
            cx,
            cy,
            Shape::Circle(0.6 * t),
            0.0,
            0.0,
        );
        self.push(
            "stall",
            Kind::Stall,
            cx - 4.0 * t,
            cy - 3.0 * t,
            Shape::Rect(1.3 * t, 0.6 * t),
            0.0,
            0.0,
        );
        self.merchants.push((cx - 4.0 * t, cy - 1.6 * t));
        self.push(
            "workbench",
            Kind::Bench,
            cx + 4.0 * t,
            cy - 3.0 * t,
            Shape::Rect(0.9 * t, 0.5 * t),
            0.0,
            0.0,
        );
        self.benches.push((cx + 4.0 * t, cy - 2.0 * t));
        for (ox, oy) in [(-6.5, 3.0), (6.5, 3.0), (-3.0, 6.5), (3.5, 6.5)] {
            self.push(
                "tent",
                Kind::Prop,
                cx + ox * t,
                cy + oy * t,
                Shape::Rect(1.1 * t, 1.1 * t),
                0.0,
                0.0,
            );
        }
        self.push(
            "log",
            Kind::Prop,
            cx,
            cy + 2.5 * t,
            Shape::Rect(1.0 * t, 0.25 * t),
            0.0,
            0.0,
        );
        for (ox, oy) in [(-2.0, -6.0), (2.5, -6.0)] {
            self.push(
                "crate",
                Kind::Prop,
                cx + ox * t,
                cy + oy * t,
                Shape::Circle(0.45 * t),
                0.0,
                0.0,
            );
        }
        self.vehicle_spawns
            .push((0, cx + 7.5 * t, cy - 1.0 * t, 1.57));
        self.vehicle_spawns
            .push((0, cx + 8.5 * t, cy + 0.5 * t, 1.57));

        // orman: jitter'lı ızgara
        let cell = 2.6 * t;
        let n = (MAP_PX / cell) as i32;
        for gy in 0..n {
            for gx in 0..n {
                let x = (gx as f32 + rng.range(0.15, 0.85)) * cell;
                let y = (gy as f32 + rng.range(0.15, 0.85)) * cell;
                let tx = (x / t) as usize;
                let ty = (y / t) as usize;
                if tx >= MAP_TILES || ty >= MAP_TILES {
                    continue;
                }
                let z = zone_at_tile(tx, ty);
                let gmat = self.ground[ty * MAP_TILES + tx];
                let path = matches!(gmat, ground::GRAVEL | ground::ASPHALT);
                let border = tx < 3 || ty < 3 || tx > MAP_TILES - 4 || ty > MAP_TILES - 4;
                if !(matches!(z, Zone::Forest | Zone::DeepForest | Zone::Swamp)
                    || border && ty > WALL_Y)
                {
                    continue;
                }
                let dens = fbm(self.seed ^ 0xF0, x / 900.0, y / 900.0);
                let pine_zone = value_noise(self.seed ^ 0x91, x / 1300.0, y / 1300.0);
                let p_tree = if border {
                    0.95
                } else if path || z == Zone::Camp {
                    0.0
                } else {
                    let base = if z == Zone::DeepForest {
                        0.55
                    } else if z == Zone::Swamp {
                        0.25
                    } else {
                        0.38
                    };
                    ((dens - 0.35) * 1.8 * base * 2.0).clamp(0.03, 0.75)
                };
                let r = rng.f32();
                if r < p_tree {
                    let name = if z == Zone::Swamp {
                        if rng.chance(0.4) {
                            ["dead0", "dead1"][rng.below(2) as usize]
                        } else {
                            ["birch0", "birch1", "oak2"][rng.below(3) as usize]
                        }
                    } else if rng.chance(0.04) {
                        ["dead0", "dead1"][rng.below(2) as usize]
                    } else if pine_zone > 0.55 {
                        if rng.chance(0.8) {
                            ["pine0", "pine1", "pine2"][rng.below(3) as usize]
                        } else {
                            ["oak0", "oak1"][rng.below(2) as usize]
                        }
                    } else if rng.chance(0.2) {
                        ["birch0", "birch1"][rng.below(2) as usize]
                    } else if rng.chance(0.06) {
                        "oak_autumn"
                    } else {
                        ["oak0", "oak1", "oak2"][rng.below(3) as usize]
                    };
                    let hp = if name.starts_with("pine") {
                        55.0
                    } else if name.starts_with("dead") {
                        25.0
                    } else {
                        65.0
                    };
                    let rad = if name.starts_with("dead") {
                        0.22
                    } else if name.starts_with("birch") {
                        0.2
                    } else {
                        0.3
                    };
                    self.push(
                        name,
                        Kind::Tree,
                        x,
                        y,
                        Shape::Circle(rad * t),
                        hp,
                        rng.range(0.0, std::f32::consts::TAU),
                    );
                } else if !path && r < p_tree + 0.07 {
                    let name = ["bush0", "bush1", "bush2", "bush3", "bush4"][rng.below(5) as usize];
                    self.push(
                        name,
                        Kind::Bush,
                        x,
                        y,
                        Shape::None,
                        1.0,
                        rng.range(0.0, std::f32::consts::TAU),
                    );
                } else if !path && r < p_tree + 0.085 {
                    if rng.chance(0.4) {
                        self.push(
                            ["rock_big0", "rock_big1", "rock_big2"][rng.below(3) as usize],
                            Kind::Rock,
                            x,
                            y,
                            Shape::Circle(0.55 * t),
                            60.0,
                            0.0,
                        );
                    } else if rng.chance(0.3) {
                        self.push(
                            "log",
                            Kind::Prop,
                            x,
                            y,
                            Shape::Rect(1.0 * t, 0.25 * t),
                            0.0,
                            0.0,
                        );
                    } else {
                        self.push(
                            ["rock_small0", "rock_small1", "rock_small2"][rng.below(3) as usize],
                            Kind::Deco,
                            x,
                            y,
                            Shape::None,
                            0.0,
                            0.0,
                        );
                    }
                } else if !path && r < p_tree + 0.09 && z != Zone::Camp {
                    self.push(
                        "crate",
                        Kind::Crate,
                        x,
                        y,
                        Shape::Circle(0.45 * t),
                        30.0,
                        0.0,
                    );
                }
                // küçük dekor: ot, çiçek (gerçek ölçek, seyrek kümeler), mantar, kamış
                if !path {
                    let k = rng.below(4);
                    for _ in 0..k {
                        let dx = x + rng.range(-cell * 0.5, cell * 0.5);
                        let dy = y + rng.range(-cell * 0.5, cell * 0.5);
                        let rr = rng.f32();
                        let name = if z == Zone::Swamp && rr < 0.3 {
                            ["reeds0", "reeds1"][rng.below(2) as usize]
                        } else if rr < 0.7 {
                            ["grass0", "grass1", "grass2", "grass3"][rng.below(4) as usize]
                        } else if rr < 0.8
                            && value_noise(self.seed ^ 0xF1, dx / 600.0, dy / 600.0) > 0.6
                        {
                            [
                                "flower_daisy",
                                "flower_poppy",
                                "flower_corn",
                                "flower_butter",
                                "flower_lavender",
                            ][rng.below(5) as usize]
                        } else if rr < 0.84 {
                            ["mushrooms0", "mushrooms1"][rng.below(2) as usize]
                        } else {
                            continue;
                        };
                        self.push(
                            name,
                            Kind::Deco,
                            dx,
                            dy,
                            Shape::None,
                            0.0,
                            rng.range(0.0, std::f32::consts::TAU),
                        );
                    }
                }
            }
        }
        // şehir bahçelerine ot
        for _ in 0..4000 {
            let x = rng.range(4.0, 356.0) * t;
            let y = rng.range(4.0, WALL_Y as f32 - 2.0) * t;
            let g = self.ground_at((x / t) as usize, (y / t) as usize);
            if g == ground::GRASS || g == ground::DRY_GRASS {
                self.push(
                    ["grass0", "grass2", "grass3"][rng.below(3) as usize],
                    Kind::Deco,
                    x,
                    y,
                    Shape::None,
                    0.0,
                    rng.range(0.0, std::f32::consts::TAU),
                );
            }
        }
    }

    pub fn build_grid(&mut self) {
        self.grid = vec![Vec::new(); GRID_N * GRID_N];
        for (i, s) in self.statics.iter().enumerate() {
            let gx = ((s.x / GRID_CELL) as usize).min(GRID_N - 1);
            let gy = ((s.y / GRID_CELL) as usize).min(GRID_N - 1);
            self.grid[gy * GRID_N + gx].push(i as u32);
        }
    }

    pub fn query(&self, x0: f32, y0: f32, x1: f32, y1: f32, out: &mut Vec<u32>) {
        out.clear();
        // binalar büyük: komşu hücreleri de tara
        let gx0 = (((x0 - 400.0) / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        let gy0 = (((y0 - 400.0) / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        let gx1 = (((x1 + 400.0) / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        let gy1 = (((y1 + 400.0) / GRID_CELL).floor().max(0.0) as usize).min(GRID_N - 1);
        for gy in gy0..=gy1 {
            for gx in gx0..=gx1 {
                out.extend_from_slice(&self.grid[gy * GRID_N + gx]);
            }
        }
    }

    /// Daireyi katı nesnelerden dışarı iter. `gate_open`: kapıdan geçebilir mi.
    pub fn resolve(
        &self,
        x: &mut f32,
        y: &mut f32,
        r: f32,
        gate_open: bool,
        scratch: &mut Vec<u32>,
    ) -> bool {
        self.query(*x - 64.0, *y - 64.0, *x + 64.0, *y + 64.0, scratch);
        let mut hit = false;
        for &i in scratch.iter() {
            let s = &self.statics[i as usize];
            if s.broken || (s.kind == Kind::Gate && gate_open) {
                continue;
            }
            match s.shape {
                Shape::None => {}
                Shape::Circle(sr) => {
                    let dx = *x - s.x;
                    let dy = *y - s.y;
                    let d = dx.hypot(dy);
                    let min = sr + r;
                    if d < min {
                        let d = d.max(0.01);
                        *x += dx / d * (min - d);
                        *y += dy / d * (min - d);
                        hit = true;
                    }
                }
                Shape::Rect(hw, hh) => {
                    let cx = (*x).clamp(s.x - hw, s.x + hw);
                    let cy = (*y).clamp(s.y - hh, s.y + hh);
                    let dx = *x - cx;
                    let dy = *y - cy;
                    let d2 = dx * dx + dy * dy;
                    if d2 < r * r {
                        if d2 > 1e-4 {
                            let d = d2.sqrt();
                            *x += dx / d * (r - d);
                            *y += dy / d * (r - d);
                        } else {
                            // içinde: en yakın kenara it
                            let pushes = [
                                (s.x - hw - r) - *x,
                                (s.x + hw + r) - *x,
                                (s.y - hh - r) - *y,
                                (s.y + hh + r) - *y,
                            ];
                            let (mut bi, mut bv) = (0, f32::MAX);
                            for (k, p) in pushes.iter().enumerate() {
                                if p.abs() < bv {
                                    bv = p.abs();
                                    bi = k;
                                }
                            }
                            if bi < 2 {
                                *x += pushes[bi]
                            } else {
                                *y += pushes[bi]
                            }
                        }
                        hit = true;
                    }
                }
            }
        }
        *x = x.clamp(TILE, MAP_PX - TILE);
        *y = y.clamp(TILE, MAP_PX - TILE);
        hit
    }

    pub fn blocked(&self, x: f32, y: f32, r: f32, scratch: &mut Vec<u32>) -> bool {
        let (mut ax, mut ay) = (x, y);
        self.resolve(&mut ax, &mut ay, r, false, scratch)
    }

    /// Mermi için: nokta katı bir nesnenin içinde mi (ağaç gövdesi, bina, kaya)?
    pub fn solid_at(&self, x: f32, y: f32, scratch: &mut Vec<u32>) -> Option<usize> {
        self.query(x - 8.0, y - 8.0, x + 8.0, y + 8.0, scratch);
        for &i in scratch.iter() {
            let s = &self.statics[i as usize];
            if s.broken || matches!(s.kind, Kind::Deco | Kind::Bush | Kind::Gate | Kind::Lamp) {
                continue;
            }
            let inside = match s.shape {
                Shape::None => false,
                Shape::Circle(r) => (x - s.x).hypot(y - s.y) < r,
                Shape::Rect(hw, hh) => (x - s.x).abs() < hw && (y - s.y).abs() < hh,
            };
            if inside {
                return Some(i as usize);
            }
        }
        None
    }

    pub fn ground_names() -> [&'static str; ground::COUNT] {
        [
            "grass",
            "dark_grass",
            "dry_grass",
            "dirt",
            "gravel",
            "asphalt",
            "sidewalk",
            "concrete",
            "mud",
        ]
    }

    pub fn is_road_center(tx: usize, ty: usize) -> (bool, bool) {
        let cx = tx >= 8 && (tx - 8) % 34 == 2;
        let cy = ty < WALL_Y && ty >= 6 && (ty - 6) % 30 == 2;
        (cx, cy)
    }
}

pub const ZONES_ALL: [Zone; 10] = [
    Zone::Camp,
    Zone::Forest,
    Zone::DeepForest,
    Zone::Swamp,
    Zone::Wall,
    Zone::SafeCity,
    Zone::Suburb,
    Zone::Downtown,
    Zone::Industrial,
    Zone::BossArena,
];

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn layout() {
        assert_eq!(zone_at_tile(180, 300), Zone::Camp);
        assert_eq!(zone_at_tile(180, 130), Zone::SafeCity);
        assert_eq!(zone_at_tile(180, 100), Zone::Suburb);
        assert_eq!(zone_at_tile(50, 60), Zone::Downtown);
        assert_eq!(zone_at_tile(50, 20), Zone::Industrial);
        assert_eq!(zone_at_tile(300, 20), Zone::BossArena);
        assert_eq!(zone_at_tile(50, 300), Zone::Swamp);
        assert_eq!(zone_at_tile(180, 150), Zone::Wall);
    }

    #[test]
    fn generation_sane() {
        let w = World::generate(7);
        let trees = w.statics.iter().filter(|s| s.kind == Kind::Tree).count();
        let buildings = w
            .statics
            .iter()
            .filter(|s| s.kind == Kind::Building)
            .count();
        assert!(trees > 1500, "ağaç {trees}");
        assert!(buildings > 40, "bina {buildings}");
        let mut s = Vec::new();
        let (x, y) = camp_spawn();
        assert!(!w.blocked(x, y, 14.0, &mut s));
        let (x, y) = city_spawn();
        assert!(!w.blocked(x, y, 14.0, &mut s));
        assert!(w
            .statics
            .iter()
            .all(|s| (s.sprite as usize) < STATIC_NAMES.len()));
        assert!(!w.merchants.is_empty() && !w.benches.is_empty() && w.snipers.len() >= 6);
    }

    #[test]
    fn gate_blocks_until_open() {
        let w = World::generate(7);
        let mut s = Vec::new();
        let (mut x, mut y) = (180.0 * TILE, (WALL_Y as f32 + 1.0) * TILE);
        let moved = w.resolve(&mut x, &mut y, 14.0, false, &mut s);
        assert!(moved);
        let (mut x2, mut y2) = (180.0 * TILE, (WALL_Y as f32 + 1.0) * TILE);
        let moved2 = w.resolve(&mut x2, &mut y2, 14.0, true, &mut s);
        assert!(!moved2);
    }
}
