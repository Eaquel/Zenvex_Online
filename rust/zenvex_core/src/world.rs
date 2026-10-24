//! V2 dünyası: orman + şehir, bölgeler, zemin malzemeleri, statik nesneler, çarpışma, doğuş noktaları.
use crate::data::ZKind;
use crate::rng::{fbm, value_noise, Rng};
use crate::bases::{base_defs, BASE_HALF};

pub const TILE: f32 = 64.0;
/// V8: harita iki eksende 2 kat (alan 4 kat): eski harita sol üst çeyrekte aynı koordinatlarda kalır.
/// V11: doğu ve güney kenara 180 karoluk Cephe Hattı şeridi eklendi (üsler orada; yalnız Büyücü Aldric ile girilir)
pub const MAP_TILES: usize = 900;
/// V8-V10 haritası (ana dünya; karantina duvarıyla çevrili)
pub const WORLD_TILES: usize = 720;
pub const WORLD_PX: f32 = TILE * WORLD_TILES as f32;
/// V8 öncesi harita kenarı (eski kayıtlar bu karede geçerli)
pub const OLD_TILES: usize = 360;
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
    // V8 bölge dokuları (art/v8u, tools/pixel/ground_v8.py)
    pub const SNOW_DOCK: u8 = 9;
    pub const ASH: u8 = 10;
    pub const DESERT_ROAD: u8 = 11;
    pub const SWAMP: u8 = 12;
    pub const BASE_DIRT: u8 = 13;
    /// V13 bulunulan bölgenin dokuları (ışınlanınca Kotlin yenilerini yükler): ana, ikinci, yol, sınır ötesi boşluk
    pub const R_MAIN: u8 = 14;
    pub const R_ALT: u8 = 15;
    pub const R_PATH: u8 = 16;
    pub const R_VOID: u8 = 17;
    pub const BASE_COUNT: usize = 14;
    pub const COUNT: usize = 18;
    /// kendi rengini taşıyan (bölge tonu uygulanmayan) dokular
    pub fn own_color(m: u8) -> bool {
        m >= SNOW_DOCK
    }
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
    "quest_board",
    "med_station",
    "kennel",
    "fuel_pump",
    "anvil",
    "mailbox",
    "barricade",
    "charger", // 65-72 (V6 kamp nesneleri)
    "med_tent",
    "mystery_tree",
    "gas_station", // 73-75 (V7 piksel yer işaretleri)
    "portal", // 76 (V8 kişisel üs dönüş kapısı)
    // 77+ V8 kullanıcı görselleri (art/v8/props): bölge simge yapıları, üs yapıları, kişisel üs eşyaları
    "burnt_tree", "burnt_stump", "desert_wreck", "swamp_shack", "harbor_containers", "hangar", "bunker", "watchtower",
    "helipad", "supply_crate", "bed", "stove", "fence_wood", "base_gate", "weapon_rack", "rain_barrel",
    // 93+ V10 bölge dekorları (art/v10/props): donmuş liman, yanık orman, çöl, kara bataklık, askerî bölge, eski şehir, kamp çadırları
    "snow_pine", "ice_boulder", "frozen_boat", "anchor_snow", "snow_barrels",
    "burnt_pine", "smolder_log", "charred_log", "deer_skeleton", "ember_rocks",
    "cactus", "cow_skull", "road_sign", "cone_pile", "gas_can",
    "moss_tree", "mushroom_log", "rowboat", "reeds", "toxic_puddle",
    "mil_tent", "jeep_wreck", "radar_dish", "ammo_stack", "jeep_wreck2", "sandbag_nest",
    "street_lamp", "bus_stop", "burnt_car", "dumpster", "billboard",
    "tent_patched", "tent_olive", "tent_tarp",
    // V13 (V11 paketi): cephe ve karargâh, eğitim üssü, karantina sınırı
    "front_trench", "front_crater", "front_barbed", "front_tank_wreck",
    "hq_command_tent", "hq_radio_mast", "hq_flag", "hq_sign",
    "train_dummy", "train_sign", "train_target",
    "quarantine_wall", "quarantine_post", "quarantine_sign", "border_searchlight", "border_bones",
    // V13 bölge paketi: sınır parçaları (b), dekorlar (d), simge yapılar (l); areas::AREAS sırasıyla
    "b_forest_0", "b_forest_1", "b_forest_2", "b_forest_3", "b_forest_4", "b_forest_5", "d_forest_0", "d_forest_1", "d_forest_2", "d_forest_3", "d_forest_4", "d_forest_5", "d_forest_6", "d_forest_7", "l_forest_1", "l_forest_2",
    "b_city_0", "b_city_1", "b_city_2", "b_city_3", "b_city_4", "b_city_5", "d_city_0", "d_city_1", "d_city_2", "d_city_3", "d_city_4", "d_city_5", "d_city_6", "d_city_7", "l_city_1", "l_city_2",
    "b_burnt_0", "b_burnt_1", "b_burnt_2", "b_burnt_3", "b_burnt_4", "b_burnt_5", "d_burnt_0", "d_burnt_1", "d_burnt_2", "d_burnt_3", "d_burnt_4", "d_burnt_5", "d_burnt_6", "d_burnt_7", "l_burnt_1", "l_burnt_2",
    "b_desert_0", "b_desert_1", "b_desert_2", "b_desert_3", "b_desert_4", "b_desert_5", "d_desert_0", "d_desert_1", "d_desert_2", "d_desert_3", "d_desert_4", "d_desert_5", "d_desert_6", "d_desert_7", "l_desert_1", "l_desert_2",
    "b_bswamp_0", "b_bswamp_1", "b_bswamp_2", "b_bswamp_3", "b_bswamp_4", "b_bswamp_5", "d_bswamp_0", "d_bswamp_1", "d_bswamp_2", "d_bswamp_3", "d_bswamp_4", "d_bswamp_5", "d_bswamp_6", "d_bswamp_7", "l_bswamp_1", "l_bswamp_2",
    "b_harbor_0", "b_harbor_1", "b_harbor_2", "b_harbor_3", "b_harbor_4", "b_harbor_5", "d_harbor_0", "d_harbor_1", "d_harbor_2", "d_harbor_3", "d_harbor_4", "d_harbor_5", "d_harbor_6", "d_harbor_7", "l_harbor_1", "l_harbor_2",
    "b_front_0", "b_front_1", "b_front_2", "b_front_3", "b_front_4", "b_front_5", "d_front_0", "d_front_1", "d_front_2", "d_front_3", "d_front_4", "d_front_5", "d_front_6", "d_front_7", "l_front_1", "l_front_2",
    "b_home_0", "b_home_1", "b_home_2", "b_home_3", "b_home_4", "b_home_5", "d_home_0", "d_home_1", "d_home_2", "d_home_3", "d_home_4", "d_home_5", "d_home_6", "d_home_7",
];
pub fn sprite(name: &str) -> u16 {
    STATIC_NAMES.iter().position(|n| *n == name).unwrap_or(0) as u16
}
pub const STUMP: &str = "stump";
/// ağaç ve kaya canı (yumruk 25, taş alet 50, demir alet 100 vurur)
pub const TREE_HP: f32 = 500.0;

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
            (-1.0, _) => camp_spawn(),
            (-2.0, _) => city_spawn(),
            (-3.0, _) => (BOSS.0 - 600.0, BOSS.1 + 400.0),
            (x, y) => (x * TILE, y * TILE),
        }
    }
}

/// Haritadan hızlı seyahat noktaları: hepsi güvenli bir karakola ya da kampa çıkar (karakolların yanına, 2 kare güneye).
pub const TRAVEL: &[Travel] = &[
    Travel { id: "camp", name: "Kamp", tile: (-1.0, -1.0), level: 1, cost: 0 },
    Travel { id: "deep", name: "Derin Orman Karakolu", tile: (300.0, 252.0), level: 5, cost: 25 },
    Travel { id: "swamp", name: "Bataklık Karakolu", tile: (45.0, 282.0), level: 8, cost: 40 },
    Travel { id: "city", name: "Şehir Güvenli Bölgesi", tile: (-2.0, -2.0), level: 10, cost: 60 },
    Travel { id: "burnt", name: "Kül Karakolu", tile: (452.0, 272.0), level: 15, cost: 80 },
    Travel { id: "downtown", name: "Merkez Sığınağı", tile: (44.0, 70.0), level: 20, cost: 100 },
    Travel { id: "desert", name: "Çöl Karakolu", tile: (262.0, 472.0), level: 20, cost: 110 },
    Travel { id: "bswamp", name: "Kara Bataklık Karakolu", tile: (90.0, 472.0), level: 25, cost: 130 },
    Travel { id: "military", name: "Kuzey Kül Karakolu", tile: (388.0, 132.0), level: 15, cost: 80 },
    Travel { id: "harbor", name: "Liman Karakolu", tile: (470.0, 422.0), level: 40, cost: 180 },
    Travel { id: "boss", name: "Arena Kampı", tile: (275.0, 50.0), level: 45, cost: 200 },
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
    /// kamp dışındaki güvenli karakollar (sahra hastanesi, barınak, benzinlik)
    Outpost,
    // ---- V8 yeni bölgeler (sıra Kotlin "zones" dizisiyle aynı: sona eklenir)
    BurntForest,
    Desert,
    BlackSwamp,
    Military,
    Harbor,
    /// kişisel üs (büyücüyle gelinir, duvarla kapalı, güvenli)
    Home,
    /// V11 Cephe Hattı: tüm üsler burada (doğu ve güney şerit; Büyücü Aldric ile Sv 10'da gelinir)
    Front,
}

/// Haritadaki adlı yer: görev hedefi ya da güvenli karakol (kare koordinatı, yarıçap kare cinsinden).
pub struct Landmark {
    pub id: &'static str,
    pub name: &'static str,
    pub tx: f32,
    pub ty: f32,
    pub r: f32,
    pub safe: bool,
}

impl Landmark {
    pub fn pos(&self) -> (f32, f32) {
        (self.tx * TILE, self.ty * TILE)
    }
}

pub const LANDMARKS: &[Landmark] = &[
    // kamptan uzakta, patikaların üstünde (doktor batıda, eğitmen doğuda, tamirci kuzeyde)
    Landmark { id: "clinic", name: "Sahra Hastanesi", tx: 118.0, ty: 289.0, r: 5.0, safe: true },
    Landmark { id: "kennel", name: "Köpek Barınağı", tx: 245.0, ty: 313.0, r: 5.0, safe: true },
    Landmark { id: "gas", name: "Benzin İstasyonu", tx: 192.0, ty: 205.0, r: 6.0, safe: true },
    Landmark { id: "tree", name: "Gizemli Ağaç", tx: 232.0, ty: 262.0, r: 3.0, safe: false },
    Landmark { id: "spring", name: "Zehirli Kaynak", tx: 58.0, ty: 302.0, r: 7.0, safe: false },
    // V8 güvenli karakollar (seyahat ve büyücü ışınlanması buralara çıkar)
    Landmark { id: "deep_post", name: "Derin Orman Karakolu", tx: 300.0, ty: 250.0, r: 5.0, safe: true },
    Landmark { id: "swamp_post", name: "Bataklık Karakolu", tx: 45.0, ty: 280.0, r: 5.0, safe: true },
    Landmark { id: "downtown_post", name: "Merkez Sığınağı", tx: 44.0, ty: 68.0, r: 5.0, safe: true },
    Landmark { id: "arena_post", name: "Arena Kampı", tx: 275.0, ty: 48.0, r: 5.0, safe: true },
    Landmark { id: "burnt_post", name: "Kül Karakolu", tx: 452.0, ty: 270.0, r: 6.0, safe: true },
    Landmark { id: "desert_post", name: "Çöl Karakolu", tx: 262.0, ty: 470.0, r: 6.0, safe: true },
    Landmark { id: "bswamp_post", name: "Kara Bataklık Karakolu", tx: 90.0, ty: 470.0, r: 6.0, safe: true },
    Landmark { id: "military_post", name: "Kuzey Kül Karakolu", tx: 388.0, ty: 130.0, r: 6.0, safe: true },
    Landmark { id: "harbor_post", name: "Liman Karakolu", tx: 470.0, ty: 420.0, r: 6.0, safe: true },
    // V11 Cephe Karargâhı (Cephe Hattı'nın güneydoğu köşesi; Büyücü Aldric buraya ışınlar, geri götürür)
    Landmark { id: "front_hq", name: "Cephe Karargâhı", tx: 810.0, ty: 810.0, r: 9.0, safe: true },
];

/// V8 karakolları (place_posts bunlara çadır, ateş, kum torbası koyar)
pub const POSTS: &[&str] = &[
    "deep_post", "swamp_post", "downtown_post", "arena_post", "burnt_post", "desert_post", "bswamp_post", "military_post", "harbor_post",
    "front_hq",
];

pub fn landmark(id: &str) -> &'static Landmark {
    LANDMARKS.iter().find(|l| l.id == id).unwrap_or(&LANDMARKS[0])
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
            Zone::Outpost => "Karakol (Güvenli)",
            Zone::BurntForest => "Yanık Orman",
            Zone::Desert => "Çöl Otoyolu",
            Zone::BlackSwamp => "Kara Bataklık",
            Zone::Military => "Askeri Bölge",
            Zone::Harbor => "Donmuş Liman",
            Zone::Home => "Kişisel Üs (Güvenli)",
            Zone::Front => "Cephe Hattı",
        }
    }
    pub fn level_range(self) -> (u32, u32) {
        match self {
            Zone::Camp | Zone::SafeCity | Zone::Wall | Zone::Outpost | Zone::Home => (1, 1),
            Zone::BurntForest => (15, 25),
            Zone::Desert => (20, 30),
            Zone::BlackSwamp => (25, 35),
            Zone::Military => (35, 50),
            Zone::Harbor => (40, 50),
            Zone::Front => (30, 50),
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
        matches!(self, Zone::Camp | Zone::SafeCity | Zone::Outpost | Zone::Home)
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
            Zone::BurntForest => &[(ZKind::Walker, 35), (ZKind::Runner, 30), (ZKind::Dark, 20), (ZKind::Dog, 15)],
            Zone::Desert => &[(ZKind::Runner, 35), (ZKind::Walker, 25), (ZKind::Tank, 20), (ZKind::Dog, 20)],
            Zone::BlackSwamp => &[(ZKind::Toxic, 50), (ZKind::Dark, 25), (ZKind::Walker, 20), (ZKind::Mystery, 5)],
            Zone::Military => &[(ZKind::Tank, 30), (ZKind::Dark, 25), (ZKind::Runner, 25), (ZKind::Toxic, 10), (ZKind::Mystery, 5), (ZKind::Dog, 5)],
            Zone::Harbor => &[(ZKind::Dark, 35), (ZKind::Tank, 25), (ZKind::Runner, 20), (ZKind::Toxic, 10), (ZKind::Mystery, 10)],
            // V12: üslerde ve çevresinde zombi yok, yalnız üs askerleri (botlar)
            Zone::Front => &[],
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
/// Cephe Karargâhı ışınlanma noktası (büyücünün önü)
pub fn front_spawn() -> (f32, f32) {
    let l = landmark("front_hq");
    (l.tx * TILE, (l.ty + 3.0) * TILE)
}
pub fn camp_spawn() -> (f32, f32) {
    (CAMP.0, CAMP.1 + 2.0 * TILE)
}

fn road_x(tx: usize) -> bool {
    (8..OLD_TILES).contains(&tx) && (tx - 8) % 34 < 4
}
fn road_y(ty: usize) -> bool {
    (6..WALL_Y).contains(&ty) && (ty - 6) % 30 < 4
}

// ---- V8: kişisel üs (karo, duvar dahil) — haritanın güneydoğu köşesinde, duvarla kapalı; yalnız büyücüyle girilir
pub const HOME_X0: usize = 684;
pub const HOME_X1: usize = 712;
pub const HOME_Y0: usize = 684;
pub const HOME_Y1: usize = 712;
pub fn in_home_tile(tx: usize, ty: usize) -> bool {
    (HOME_X0..=HOME_X1).contains(&tx) && (HOME_Y0..=HOME_Y1).contains(&ty)
}
/// Kişisel üsse ışınlanma / doğma noktası (dünya pikseli)
pub fn home_spawn() -> (f32, f32) {
    (((HOME_X0 + HOME_X1) as f32 / 2.0) * TILE, ((HOME_Y0 + HOME_Y1) as f32 / 2.0 + 3.0) * TILE)
}

/// V8 bölgeleri: doğu yarısı (kuzey Askeri Bölge, güney Yanık Orman), güney şerit (batı Kara Bataklık, doğu Çöl Otoyolu),
/// güneydoğu çeyrek Donmuş Liman. V12: bölgeler ayrı haritalar (areas.rs), sınırlar düz.
fn region_zone(tx: usize, ty: usize) -> Zone {
    let half = OLD_TILES / 2;
    if tx >= OLD_TILES {
        // V12.1: Askeri Bölge kalktı; kuzeydoğu da Yanık Orman
        if ty >= OLD_TILES {
            Zone::Harbor
        } else {
            Zone::BurntForest
        }
    } else if tx < half {
        Zone::BlackSwamp
    } else {
        Zone::Desert
    }
}

/// Yeni bölgelerin yolları: (yolda mı, dikey orta çizgi, yatay orta çizgi)
pub fn region_road(tx: usize, ty: usize) -> (bool, bool, bool) {
    if tx < OLD_TILES && ty < OLD_TILES {
        return (false, false, false);
    }
    let band = |v: usize, at: &[usize]| at.iter().find(|&&a| v >= a && v < a + 4).map(|&a| v - a == 2);
    let (cols, rows): (&[usize], &[usize]) = if ty >= OLD_TILES && tx < OLD_TILES {
        (&[268], &[538])
    } else if tx >= OLD_TILES && ty < OLD_TILES / 2 {
        (&[420, 540, 660], &[60, 122])
    } else if tx >= OLD_TILES && ty >= OLD_TILES {
        (&[540], &[450, 600])
    } else {
        (&[], &[])
    };
    let v = band(tx, cols);
    let h = band(ty, rows);
    (v.is_some() || h.is_some(), v == Some(true) && h.is_none(), h == Some(true) && v.is_none())
}

/// V10 bölge dekorları: eski nesnenin yerine bölgesine ve konumuna göre yeni görsel (rastgele sayı dizisi değişmez,
/// böylece ağaç/bina yerleşimi aynı kalır). Büyük yapılara uygun çarpışma alanı verilir.
fn v10_variant(name: &str, x: f32, y: f32, shape: Shape) -> (&str, Shape) {
    let t = TILE;
    let (tx, ty) = ((x / t).max(0.0) as usize, (y / t).max(0.0) as usize);
    let h = ((x as i64).wrapping_mul(73_856_093) ^ (y as i64).wrapping_mul(19_349_663)).unsigned_abs() >> 3;
    let pick = |opts: &[&'static str]| opts[(h % opts.len() as u64) as usize];
    let z = zone_at_tile(tx.min(MAP_TILES - 1), ty.min(MAP_TILES - 1));
    let city = matches!(z, Zone::Suburb | Zone::Downtown | Zone::Industrial | Zone::SafeCity);
    let out: &str = match (name, z) {
        ("tent", _) => pick(&["tent_olive", "tent_patched", "tent_tarp"]),
        ("pine0" | "pine1" | "pine2", Zone::Harbor) => "snow_pine",
        ("barrel", Zone::Harbor) => "snow_barrels",
        ("trash", Zone::Harbor) => pick(&["anchor_snow", "ice_boulder", "frozen_boat", "trash"]),
        ("burnt_tree", _) => pick(&["burnt_tree", "burnt_pine"]),
        ("log", Zone::BurntForest) => pick(&["smolder_log", "charred_log"]),
        ("burnt_stump", _) => pick(&["burnt_stump", "deer_skeleton", "ember_rocks", "burnt_stump"]),
        ("dead0" | "dead1", Zone::BlackSwamp) => pick(&["moss_tree", "dead0", "moss_tree"]),
        ("reeds0" | "reeds1", Zone::BlackSwamp) => pick(&["reeds", name_static(name)]),
        ("barrel_tox", Zone::BlackSwamp) => pick(&["toxic_puddle", "mushroom_log", "rowboat", "barrel_tox"]),
        ("dead1", Zone::Desert) => "cactus",
        ("rock_small0" | "rock_small1" | "rock_small2" | "grass2", Zone::Desert) => {
            pick(&["cow_skull", "cone_pile", "gas_can", "road_sign", name_static(name), name_static(name)])
        }
        ("wreck0" | "wreck1", Zone::Military | Zone::Front) => pick(&["jeep_wreck", "jeep_wreck2"]),
        ("sandbags", Zone::Military | Zone::Front) => pick(&["sandbag_nest", "sandbags"]),
        ("barricade", Zone::Military | Zone::Front) => "ammo_stack",
        ("barrel", Zone::Military | Zone::Front) => pick(&["radar_dish", "mil_tent", "ammo_stack"]),
        ("wreck0" | "wreck1", _) if city => pick(&["burnt_car", name_static(name)]),
        ("trash", _) if city => pick(&["dumpster", "bus_stop", "billboard", "trash", "street_lamp"]),
        _ => name_static(name),
    };
    let shape = match out {
        "frozen_boat" => Shape::Rect(1.8 * t, 0.7 * t),
        "ice_boulder" => Shape::Circle(0.7 * t),
        "anchor_snow" => Shape::Circle(0.5 * t),
        "rowboat" => Shape::Rect(1.1 * t, 0.4 * t),
        "toxic_puddle" | "deer_skeleton" | "ember_rocks" | "cow_skull" | "cone_pile" | "reeds" => Shape::None,
        "mil_tent" => Shape::Rect(1.4 * t, 0.9 * t),
        "radar_dish" => Shape::Circle(0.6 * t),
        "ammo_stack" => Shape::Rect(0.7 * t, 0.5 * t),
        "sandbag_nest" => Shape::Circle(0.9 * t),
        "jeep_wreck" | "jeep_wreck2" | "burnt_car" => Shape::Rect(1.4 * t, 0.7 * t),
        "bus_stop" => Shape::Rect(1.5 * t, 0.5 * t),
        "billboard" => Shape::Rect(1.4 * t, 0.4 * t),
        "dumpster" => Shape::Rect(0.9 * t, 0.5 * t),
        "street_lamp" => Shape::Circle(0.2 * t),
        "gas_can" => Shape::Circle(0.25 * t),
        "road_sign" => Shape::Circle(0.15 * t),
        _ => shape,
    };
    (out, shape)
}

/// Görsel adını 'static ömre çevirir (STATIC_NAMES içindeki eşi; yoksa ilk ad).
fn name_static(name: &str) -> &'static str {
    STATIC_NAMES.iter().find(|n| **n == name).copied().unwrap_or(STATIC_NAMES[0])
}

pub fn zone_at_tile(tx: usize, ty: usize) -> Zone {
    let x = tx as f32 * TILE + TILE / 2.0;
    let y = ty as f32 * TILE + TILE / 2.0;
    if in_home_tile(tx, ty) {
        return Zone::Home;
    }
    if tx < OLD_TILES && (WALL_Y..=WALL_Y + 1).contains(&ty) {
        return Zone::Wall;
    }
    // güvenli karakollar her bölgede önce gelir (seyahat ve ışınlanma noktaları)
    for l in LANDMARKS.iter().filter(|l| l.safe) {
        let (lx, ly) = l.pos();
        if (x - lx).hypot(y - ly) < l.r * TILE {
            return Zone::Outpost;
        }
    }
    if tx >= WORLD_TILES || ty >= WORLD_TILES {
        return Zone::Front;
    }
    if tx >= OLD_TILES || ty >= OLD_TILES {
        return region_zone(tx, ty);
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

    /// Tüccarın çevresindeki NPC'lerin yanına iş nesneleri: görev panosu, sağlık masası, köpek kulübesi, benzin pompası.
    /// (NPC'ler game.rs'de tüccara göre metre cinsinden yerleşir: görevci +2.6/+1.2, doktor -2.6/+1.2, tamirci +4.8/+2.8,
    /// eğitmen -4.8/+2.8; nesneler onların arkasında.)
    fn camp_props(&mut self, mx: f32, my: f32) {
        let u = crate::data::PX_PER_M;
        for (name, ox, oy, w, h) in [
            ("quest_board", 2.6, -0.2, 0.6, 0.2),
            ("med_station", -2.6, -0.1, 0.4, 0.25),
            ("kennel", -5.6, 1.9, 0.55, 0.4),
            ("fuel_pump", 5.8, 1.9, 0.3, 0.3),
            ("charger", 4.0, 1.7, 0.35, 0.25),
            ("mailbox", 1.2, 2.6, 0.12, 0.12),
        ] {
            self.push(name, Kind::Prop, mx + ox * u, my + oy * u, Shape::Rect(w * u, h * u), 0.0, 0.0);
        }
    }

    fn push(&mut self, name: &str, kind: Kind, x: f32, y: f32, shape: Shape, hp: f32, phase: f32) {
        let (name, shape) = v10_variant(name, x, y, shape);
        // V11: ağaç ve kaya 500 can (vuruş gücü alete bağlı, bkz. Game::hit_static)
        let hp = if matches!(kind, Kind::Tree | Kind::Rock) { TREE_HP } else { hp };
        let sort_off = match shape {
            // zemine yatık yapılar (helikopter pisti) her şeyin altında çizilir
            _ if name == "helipad" => -1.0e7,
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
                    Zone::Outpost => {
                        let near = LANDMARKS.iter().filter(|l| l.safe).map(|l| (fx - l.tx).hypot(fy - l.ty)).fold(99.0f32, f32::min);
                        if near < 3.0 { ground::GRAVEL } else { ground::DIRT }
                    }
                    Zone::BurntForest => {
                        // kül zemin; aralarda kurumuş çimen ve toprak lekeleri
                        let d = fbm(seed ^ 0xB1, fx / 10.0, fy / 10.0);
                        if d > 0.64 {
                            ground::DIRT
                        } else if d < 0.26 {
                            ground::DRY_GRASS
                        } else {
                            ground::ASH
                        }
                    }
                    Zone::Desert if region_road(tx, ty).0 => ground::DESERT_ROAD,
                    Zone::Military | Zone::Harbor if region_road(tx, ty).0 => ground::ASPHALT,
                    Zone::Desert => {
                        let d = fbm(seed ^ 0xDE5, fx / 12.0, fy / 12.0);
                        if d > 0.62 {
                            ground::DIRT
                        } else if d < 0.3 {
                            ground::GRAVEL
                        } else {
                            ground::DRY_GRASS
                        }
                    }
                    Zone::BlackSwamp => {
                        let m = fbm(seed ^ 0xB5, fx / 8.0, fy / 8.0);
                        if m > 0.4 {
                            ground::SWAMP
                        } else if m > 0.3 {
                            ground::MUD
                        } else {
                            ground::DARK_GRASS
                        }
                    }
                    Zone::Military => {
                        let d = fbm(seed ^ 0x3171, fx / 9.0, fy / 9.0);
                        if d > 0.62 {
                            ground::BASE_DIRT
                        } else if d > 0.42 {
                            ground::GRAVEL
                        } else {
                            ground::DRY_GRASS
                        }
                    }
                    Zone::Front => {
                        // savaş alanı: çamurlu toprak, çakıl, yanık çimen
                        let d = fbm(seed ^ 0xF407, fx / 10.0, fy / 10.0);
                        if d > 0.6 {
                            ground::BASE_DIRT
                        } else if d > 0.45 {
                            ground::MUD
                        } else if d > 0.3 {
                            ground::GRAVEL
                        } else {
                            ground::DRY_GRASS
                        }
                    }
                    Zone::Harbor => {
                        let d = fbm(seed ^ 0x4A2, fx / 9.0, fy / 9.0);
                        if d > 0.3 {
                            ground::SNOW_DOCK
                        } else {
                            ground::GRAVEL
                        }
                    }
                    Zone::Home => {
                        let (cx, cy) = ((HOME_X0 + HOME_X1) as f32 / 2.0, (HOME_Y0 + HOME_Y1) as f32 / 2.0);
                        if (fx - cx).abs() < 9.0 && (fy - cy).abs() < 9.0 { ground::BASE_DIRT } else { ground::GRASS }
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
        w.place_regions(&mut rng);
        w.place_posts();
        w.place_home();
        w.place_bases();
        // V11 (sona eklenir: önceki nesnelerin sıra numaraları değişmez)
        w.place_border();
        w.place_front();
        w.regionize_ground();
        w.place_region_art();
        // SUV oyundan kaldırıldı: doğduğu yerlerde iki kişilik araba durur (indeks 3 yalnız yer tutucu)
        for v in w.vehicle_spawns.iter_mut() {
            if v.0 == 3 {
                v.0 = 2;
            }
        }
        w.build_grid();
        w
    }

    fn place_city(&mut self, rng: &mut Rng) {
        let t = TILE;
        // sur duvarı (kapı boşluklu)
        let mut x = 2.0;
        while x < OLD_TILES as f32 - 2.0 {
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
        self.camp_props(172.0 * t, 129.5 * t);
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
            while bx + 34 <= OLD_TILES {
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
            while x < OLD_TILES - 4 {
                if road_y(y) && x.is_multiple_of(12) && zone_at_tile(x, y) != Zone::SafeCity {
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

    /// Karakollar ve görev yerleri: sahra hastanesi, köpek barınağı, benzin istasyonu, gizemli ağaç, zehirli kaynak.
    fn place_landmarks(&mut self) {
        let t = TILE;
        let at = |id: &str, ox: f32, oy: f32| {
            let (x, y) = landmark(id).pos();
            (x + ox * t, y + oy * t)
        };
        // sahra hastanesi: sağlık istasyonu, çadırlar, kum torbaları
        let (x, y) = at("clinic", 0.0, -1.2);
        self.push("med_station", Kind::Prop, x, y, Shape::Rect(0.4 * t, 0.25 * t), 0.0, 0.0);
        for (ox, oy) in [(-3.2, -1.5), (3.2, -1.5)] {
            let (x, y) = at("clinic", ox, oy);
            self.push("med_tent", Kind::Prop, x, y, Shape::Rect(1.1 * t, 1.1 * t), 0.0, 0.0);
        }
        for ox in [-2.0, 2.0] {
            let (x, y) = at("clinic", ox, 3.4);
            self.push("sandbags", Kind::Prop, x, y, Shape::Rect(0.9 * t, 0.3 * t), 0.0, 0.0);
        }
        let (x, y) = at("clinic", 0.0, 1.8);
        self.push("campfire", Kind::Fire, x, y, Shape::Circle(0.4 * t), 0.0, 0.0);
        // köpek barınağı: kulübeler, çit
        for (ox, oy) in [(-1.8, -1.0), (1.8, -1.0)] {
            let (x, y) = at("kennel", ox, oy);
            self.push("kennel", Kind::Prop, x, y, Shape::Rect(0.55 * t, 0.4 * t), 0.0, 0.0);
        }
        for ox in [-3.5, -1.2, 1.2, 3.5] {
            let (x, y) = at("kennel", ox, -3.0);
            self.push("fence", Kind::Prop, x, y, Shape::Rect(1.1 * t, 0.15 * t), 0.0, 0.0);
        }
        // benzin istasyonu: bina, pompalar, enkaz araçlar, barikat
        let (x, y) = at("gas", 0.0, -2.6);
        self.push("gas_station", Kind::Building, x, y, Shape::Rect(1.5 * t, 1.0 * t), 0.0, 0.0);
        for ox in [-2.6, -0.9, 0.9, 2.6] {
            let (x, y) = at("gas", ox, 1.0);
            self.push("fuel_pump", Kind::Prop, x, y, Shape::Rect(0.3 * t, 0.3 * t), 0.0, 0.0);
        }
        for (ox, oy, w) in [(-4.6, 2.4, "wreck0"), (4.4, -0.4, "wreck1")] {
            let (x, y) = at("gas", ox, oy);
            self.push(w, Kind::Prop, x, y, Shape::Rect(0.9 * t, 2.0 * t), 0.0, 0.0);
        }
        for (ox, oy) in [(-3.0, 4.2), (3.0, 4.2)] {
            let (x, y) = at("gas", ox, oy);
            self.push("barricade", Kind::Prop, x, y, Shape::Rect(0.8 * t, 0.2 * t), 0.0, 0.0);
        }
        let (x, y) = at("gas", 3.4, 2.6);
        self.push("charger", Kind::Prop, x, y, Shape::Rect(0.35 * t, 0.25 * t), 0.0, 0.0);
        let (x, y) = at("gas", 1.5, 3.2);
        self.vehicle_spawns.push((1, x, y, 0.0));
        // gizemli ağaç: güz meşesi + mantar halkası
        let (x, y) = at("tree", 0.0, 0.0);
        self.push("mystery_tree", Kind::Deco, x, y, Shape::Circle(0.5 * t), 0.0, 0.0);
        for k in 0..8 {
            let a = k as f32 * std::f32::consts::TAU / 8.0;
            self.push(["mushrooms0", "mushrooms1"][k % 2], Kind::Deco, x + a.cos() * 1.9 * t, y + a.sin() * 1.2 * t + 0.4 * t, Shape::None, 0.0, 0.0);
        }
        // zehirli kaynak: zehir varilleri ve sazlar
        for (ox, oy) in [(-1.5, -0.8), (1.2, 0.6), (0.0, 2.0)] {
            let (x, y) = at("spring", ox, oy);
            self.push("barrel_tox", Kind::Prop, x, y, Shape::Circle(0.35 * t), 0.0, 0.0);
        }
        for k in 0..6 {
            let a = k as f32 * 1.1;
            let (x, y) = at("spring", a.cos() * 3.5, a.sin() * 2.6);
            self.push(["reeds0", "reeds1"][k % 2], Kind::Deco, x, y, Shape::None, 0.0, 0.0);
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
        self.camp_props(cx - 4.0 * t, cy - 1.6 * t);
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
        self.place_landmarks();

        // orman: jitter'lı ızgara
        // (V11: döngüler ana dünyanın boyunda kalır; rastgele sayı dizisi ve yerleşim V10 ile aynı)
        let cell = 2.6 * t;
        let n = (WORLD_PX / cell) as i32;
        for gy in 0..n {
            for gx in 0..n {
                let x = (gx as f32 + rng.range(0.15, 0.85)) * cell;
                let y = (gy as f32 + rng.range(0.15, 0.85)) * cell;
                let tx = (x / t) as usize;
                let ty = (y / t) as usize;
                if tx >= WORLD_TILES || ty >= WORLD_TILES {
                    continue;
                }
                let z = zone_at_tile(tx, ty);
                let gmat = self.ground[ty * MAP_TILES + tx];
                let path = matches!(gmat, ground::GRAVEL | ground::ASPHALT);
                let border = tx < 3 || ty < 3 || tx > WORLD_TILES - 4 || ty > WORLD_TILES - 4;
                if !(matches!(z, Zone::Forest | Zone::DeepForest | Zone::Swamp)
                    || border && ty > WALL_Y)
                {
                    continue;
                }
                if LANDMARKS.iter().any(|l| !l.safe && l.id == "tree" && (x - l.tx * t).hypot(y - l.ty * t) < l.r * t) {
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

    /// Statikleri bir dikdörtgenden (karo) temizler (karakol, üs, kişisel üs alanı açmak için).
    fn clear_tiles(&mut self, x0: f32, y0: f32, x1: f32, y1: f32) {
        let t = TILE;
        self.statics.retain(|s| !(s.x >= x0 * t && s.x <= x1 * t && s.y >= y0 * t && s.y <= y1 * t));
    }

    /// V8 bölgeleri: yanık orman (kömürleşmiş ağaçlar), kara bataklık, çöl otoyolu (enkazlar), askeri bölge, donmuş liman.
    fn place_regions(&mut self, rng: &mut Rng) {
        let t = TILE;
        let cell = 2.8 * t;
        let n = (WORLD_PX / cell) as i32;
        let tau = std::f32::consts::TAU;
        let avoid = |x: f32, y: f32| -> bool {
            let (tx, ty) = ((x / t) as usize, (y / t) as usize);
            if tx >= WORLD_TILES || ty >= WORLD_TILES || in_home_tile(tx, ty) {
                return true;
            }
            if base_defs().iter().any(|b| (x / t - b.tx).abs() < BASE_HALF + 3.0 && (y / t - b.ty).abs() < BASE_HALF + 3.0) {
                return true;
            }
            LANDMARKS.iter().filter(|l| l.safe).any(|l| (x / t - l.tx).hypot(y / t - l.ty) < l.r + 2.0)
        };
        for gy in 0..n {
            for gx in 0..n {
                let x = (gx as f32 + rng.range(0.15, 0.85)) * cell;
                let y = (gy as f32 + rng.range(0.15, 0.85)) * cell;
                let (tx, ty) = ((x / t) as usize, (y / t) as usize);
                if tx >= WORLD_TILES || ty >= WORLD_TILES || (tx < OLD_TILES && ty < OLD_TILES) {
                    continue;
                }
                let border = tx < 3 || ty < 3 || tx > WORLD_TILES - 4 || ty > WORLD_TILES - 4;
                if border || avoid(x, y) || region_road(tx, ty).0 {
                    continue;
                }
                let z = zone_at_tile(tx, ty);
                let r = rng.f32();
                let ph = rng.range(0.0, tau);
                match z {
                    Zone::BurntForest => {
                        let dens = fbm(self.seed ^ 0xB2, x / 800.0, y / 800.0);
                        let p_tree = ((dens - 0.3) * 1.1).clamp(0.08, 0.5);
                        if r < p_tree {
                            self.push(["burnt_tree", "dead1"][rng.below(2) as usize], Kind::Tree, x, y, Shape::Circle(0.22 * t), 25.0, ph);
                        } else if r < p_tree + 0.05 {
                            self.push("log", Kind::Prop, x, y, Shape::Rect(1.0 * t, 0.25 * t), 0.0, 0.0);
                        } else if r < p_tree + 0.08 {
                            self.push(["rock_big0", "rock_big1"][rng.below(2) as usize], Kind::Rock, x, y, Shape::Circle(0.55 * t), 60.0, 0.0);
                        } else if r < p_tree + 0.14 {
                            self.push("burnt_stump", Kind::Deco, x, y, Shape::None, 0.0, ph);
                        } else if r < p_tree + 0.2 {
                            self.push(["rock_small0", "rock_small1", "grass2"][rng.below(3) as usize], Kind::Deco, x, y, Shape::None, 0.0, ph);
                        } else if r < p_tree + 0.21 {
                            self.push("crate", Kind::Crate, x, y, Shape::Circle(0.45 * t), 30.0, 0.0);
                        }
                    }
                    Zone::BlackSwamp => {
                        if r < 0.2 {
                            let name = if rng.chance(0.55) { ["dead0", "dead1"][rng.below(2) as usize] } else { ["birch1", "oak2"][rng.below(2) as usize] };
                            let hp = if name.starts_with("dead") { 25.0 } else { 65.0 };
                            self.push(name, Kind::Tree, x, y, Shape::Circle(0.24 * t), hp, ph);
                        } else if r < 0.36 {
                            for _ in 0..rng.below(3) + 1 {
                                let (dx, dy) = (x + rng.range(-cell * 0.4, cell * 0.4), y + rng.range(-cell * 0.4, cell * 0.4));
                                self.push(["reeds0", "reeds1", "mushrooms0", "mushrooms1"][rng.below(4) as usize], Kind::Deco, dx, dy, Shape::None, 0.0, ph);
                            }
                        } else if r < 0.39 {
                            self.push(["bush2", "bush3"][rng.below(2) as usize], Kind::Bush, x, y, Shape::None, 1.0, ph);
                        } else if r < 0.4 {
                            if (gx + gy) % 3 == 0 {
                                self.push("swamp_shack", Kind::Building, x, y, Shape::Rect(2.0 * t, 1.5 * t), 0.0, 0.0);
                            } else {
                                self.push("barrel_tox", Kind::Prop, x, y, Shape::Circle(0.3 * t), 0.0, 0.0);
                            }
                        } else if r < 0.41 {
                            self.push("crate", Kind::Crate, x, y, Shape::Circle(0.45 * t), 30.0, 0.0);
                        }
                    }
                    Zone::Desert => {
                        if r < 0.03 {
                            let nm = ["desert_wreck", "rock_big1", "rock_big2"][rng.below(3) as usize];
                            if nm == "desert_wreck" {
                                self.push(nm, Kind::Prop, x, y, Shape::Rect(2.0 * t, 0.9 * t), 0.0, 0.0);
                            } else {
                                self.push(nm, Kind::Rock, x, y, Shape::Circle(0.55 * t), 60.0, 0.0);
                            }
                        } else if r < 0.1 {
                            self.push(["rock_small0", "rock_small1", "rock_small2", "grass2"][rng.below(4) as usize], Kind::Deco, x, y, Shape::None, 0.0, ph);
                        } else if r < 0.12 {
                            self.push("dead1", Kind::Tree, x, y, Shape::Circle(0.22 * t), 25.0, ph);
                        } else if r < 0.135 {
                            self.push("bush4", Kind::Bush, x, y, Shape::None, 1.0, ph);
                        } else if r < 0.142 {
                            self.push("crate", Kind::Crate, x, y, Shape::Circle(0.45 * t), 30.0, 0.0);
                        }
                    }
                    Zone::Military => {
                        if r < 0.018 {
                            self.push("crate_mil", Kind::Crate, x, y, Shape::Circle(0.45 * t), 40.0, 0.0);
                        } else if r < 0.04 {
                            self.push("sandbags", Kind::Prop, x, y, Shape::Rect(1.4 * t, 0.35 * t), 0.0, 0.0);
                        } else if r < 0.05 {
                            self.push("barricade", Kind::Prop, x, y, Shape::Rect(0.8 * t, 0.2 * t), 0.0, 0.0);
                        } else if r < 0.06 {
                            self.push(["wreck0", "wreck1"][rng.below(2) as usize], Kind::Prop, x, y, Shape::Rect(0.9 * t, 2.0 * t), 0.0, 0.0);
                        } else if r < 0.075 {
                            self.push(["cone", "rock_small0", "grass2"][rng.below(3) as usize], Kind::Deco, x, y, Shape::None, 0.0, ph);
                        } else if r < 0.085 {
                            self.push("dead0", Kind::Tree, x, y, Shape::Circle(0.22 * t), 25.0, ph);
                        }
                    }
                    Zone::Harbor => {
                        if r < 0.03 {
                            self.push(["pine0", "pine1", "dead0"][rng.below(3) as usize], Kind::Tree, x, y, Shape::Circle(0.28 * t), 55.0, ph);
                        } else if r < 0.045 {
                            self.push("crate_mil", Kind::Crate, x, y, Shape::Circle(0.45 * t), 40.0, 0.0);
                        } else if r < 0.065 {
                            self.push("barrel", Kind::Prop, x, y, Shape::Circle(0.3 * t), 0.0, 0.0);
                        } else if r < 0.075 {
                            let _ = rng.below(2);
                            self.push("harbor_containers", Kind::Building, x, y, Shape::Rect(3.0 * t, 1.4 * t), 0.0, 0.0);
                        } else if r < 0.085 {
                            self.push("trash", Kind::Prop, x, y, Shape::Circle(0.4 * t), 0.0, 0.0);
                        } else if r < 0.1 {
                            self.push(["rock_small1", "rock_small2"][rng.below(2) as usize], Kind::Deco, x, y, Shape::None, 0.0, ph);
                        }
                    }
                    _ => {}
                }
            }
        }
        // binalar: askeri bölgede kışla/hangar, limanda depolar (kaba ızgara, yollardan uzak)
        let mut by = 12usize;
        while by < WORLD_TILES - 12 {
            let mut bx = 12usize;
            while bx < WORLD_TILES - 12 {
                let (cx, cy) = (bx as f32 + rng.range(-3.0, 3.0), by as f32 + rng.range(-3.0, 3.0));
                let z = zone_at_tile(cx as usize, cy as usize);
                let near_road = (-6..=6).any(|d: i32| {
                    let (a, b) = ((cx as i32 + d).max(0) as usize, (cy as i32 + d).max(0) as usize);
                    region_road(a, cy as usize).0 || region_road(cx as usize, b).0
                });
                if matches!(z, Zone::Military | Zone::Harbor) && !near_road && !avoid(cx * t, cy * t) && rng.chance(0.55) {
                    let (name, hw, hh) = match (z, rng.below(3)) {
                        (Zone::Military, 0) => ("bunker", 2.6, 2.0),
                        (Zone::Military, 1) => ("watchtower", 1.2, 1.2),
                        (Zone::Military, _) => ("hangar", 4.2, 2.4),
                        (_, 0) => ("shop0", 3.7, 2.9),
                        (_, 1) => ("harbor_containers", 3.0, 1.4),
                        _ => ("apartment1", 4.9, 3.5),
                    };
                    let kind = if name == "watchtower" { Kind::Prop } else { Kind::Building };
                    self.push(name, kind, cx * t, cy * t, Shape::Rect(hw * t, hh * t), 0.0, 0.0);
                    for k in 0..3 {
                        let (ox, oy) = (rng.range(-hw - 2.5, hw + 2.5), hh + 1.5 + k as f32 * 0.8);
                        let pr = if z == Zone::Military { ["sandbags", "crate_mil", "barrel"][k] } else { ["barrel", "crate_mil", "trash"][k] };
                        match pr {
                            "sandbags" => self.push(pr, Kind::Prop, (cx + ox) * t, (cy + oy) * t, Shape::Rect(1.4 * t, 0.35 * t), 0.0, 0.0),
                            "crate_mil" => self.push(pr, Kind::Crate, (cx + ox) * t, (cy + oy) * t, Shape::Circle(0.45 * t), 40.0, 0.0),
                            _ => self.push(pr, Kind::Prop, (cx + ox) * t, (cy + oy) * t, Shape::Circle(0.35 * t), 0.0, 0.0),
                        }
                    }
                }
                bx += 30;
            }
            by += 30;
        }
        // otoyol enkazları ve yol lambaları
        for _ in 0..140 {
            let (tx, ty) = (rng.range(4.0, WORLD_TILES as f32 - 4.0), rng.range(4.0, WORLD_TILES as f32 - 4.0));
            if !region_road(tx as usize, ty as usize).0 || avoid(tx * t, ty * t) {
                continue;
            }
            let a = rng.range(0.0, tau);
            self.push(["wreck0", "wreck1"][rng.below(2) as usize], Kind::Prop, tx * t, ty * t, Shape::Rect(0.9 * t, 2.0 * t), 0.0, a);
            if rng.chance(0.5) {
                self.push(["cone", "barrel"][rng.below(2) as usize], Kind::Deco, (tx + rng.range(-2.0, 2.0)) * t, (ty + 2.5) * t, Shape::None, 0.0, 0.0);
            }
        }
        // araçlar yeni bölgelerde de
        for (k, n) in [(1usize, 4), (2, 4), (3, 3)] {
            for _ in 0..n {
                let (tx, ty) = (rng.range(380.0, 700.0), rng.range(20.0, 700.0));
                if !avoid(tx * t, ty * t) {
                    self.vehicle_spawns.push((k, tx * t, ty * t, rng.range(0.0, tau)));
                }
            }
        }
    }

    /// V8 güvenli karakollar: çadırlar, ateş, kum torbaları, lambalar (seyahat noktası güneyde boş kalır).
    fn place_posts(&mut self) {
        let t = TILE;
        for id in POSTS {
            let l = landmark(id);
            let (cx, cy) = (l.tx, l.ty);
            self.clear_tiles(cx - l.r, cy - l.r, cx + l.r, cy + l.r);
            let at = |ox: f32, oy: f32| ((cx + ox) * t, (cy + oy) * t);
            for ox in [-3.5f32, 3.5] {
                let (x, y) = at(ox, -2.0);
                self.push("tent", Kind::Prop, x, y, Shape::Rect(1.1 * t, 1.1 * t), 0.0, 0.0);
            }
            let (x, y) = at(0.0, -0.5);
            self.push("campfire", Kind::Fire, x, y, Shape::Circle(0.5 * t), 0.0, 0.0);
            for ox in [-3.0f32, 3.0] {
                let (x, y) = at(ox, 3.6);
                self.push("sandbags", Kind::Prop, x, y, Shape::Rect(0.9 * t, 0.3 * t), 0.0, 0.0);
            }
            for ox in [-1.8f32, 1.8] {
                let (x, y) = at(ox, -4.0);
                self.push("lamp", Kind::Lamp, x, y, Shape::Circle(0.15 * t), 0.0, 0.0);
            }
            let (x, y) = at(5.0, 0.5);
            self.push("crate", Kind::Prop, x, y, Shape::Circle(0.4 * t), 0.0, 0.0);
        }
    }

    /// Kişisel üs: dört yanı duvarla kapalı bahçe; ortada ateş, çadır (yatak), tezgâh ve dönüş kapısı (büyücü yanında).
    fn place_home(&mut self) {
        let t = TILE;
        let (x0, x1, y0, y1) = (HOME_X0 as f32, HOME_X1 as f32 + 1.0, HOME_Y0 as f32, HOME_Y1 as f32 + 1.0);
        self.clear_tiles(x0 - 3.0, y0 - 3.0, x1 + 3.0, y1 + 3.0);
        let mut x = x0;
        while x < x1 {
            // V13: ahşap çit (b_home_0); dikey kenarlarda eski duvar
            self.push("b_home_0", Kind::Prop, (x + 2.0) * t, y0 * t, Shape::Rect(2.0 * t, 0.4 * t), 0.0, 0.0);
            self.push("b_home_0", Kind::Prop, (x + 2.0) * t, y1 * t, Shape::Rect(2.0 * t, 0.4 * t), 0.0, 0.0);
            x += 3.4;
        }
        let mut y = y0;
        while y < y1 {
            self.push("wall_z", Kind::Prop, x0 * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
            self.push("wall_z", Kind::Prop, x1 * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
            y += 4.0;
        }
        let (cx, cy) = ((x0 + x1) / 2.0, (y0 + y1) / 2.0);
        self.push("portal", Kind::Prop, cx * t, (cy - 8.0) * t, Shape::Rect(1.0 * t, 0.4 * t), 0.0, 0.0);
        self.push("campfire", Kind::Fire, cx * t, (cy + 0.5) * t, Shape::Circle(0.5 * t), 0.0, 0.0);
        self.push("bed", Kind::Prop, (cx - 6.0) * t, (cy - 2.0) * t, Shape::Rect(1.0 * t, 0.5 * t), 0.0, 0.0);
        self.push("stove", Kind::Prop, (cx - 3.0) * t, (cy - 3.0) * t, Shape::Rect(0.5 * t, 0.35 * t), 0.0, 0.0);
        self.push("weapon_rack", Kind::Prop, (cx + 3.0) * t, (cy - 6.0) * t, Shape::Rect(0.9 * t, 0.3 * t), 0.0, 0.0);
        self.push("rain_barrel", Kind::Prop, (cx + 8.5) * t, (cy + 4.0) * t, Shape::Circle(0.35 * t), 0.0, 0.0);
        self.push("workbench", Kind::Bench, (cx + 6.0) * t, (cy - 2.0) * t, Shape::Rect(0.9 * t, 0.5 * t), 0.0, 0.0);
        self.benches.push(((cx + 6.0) * t, (cy - 1.0) * t));
        for (ox, oy) in [(-9.0, -9.0), (9.0, -9.0), (-9.0, 9.0), (9.0, 9.0)] {
            self.push("lamp", Kind::Lamp, (cx + ox) * t, (cy + oy) * t, Shape::Circle(0.15 * t), 0.0, 0.0);
        }
        // V13: köşe kuleleri, iç kenarlarda çam, kaya, odun yığını, çit; bahçe, kuyu, bank, çamaşır ipi …
        for (ox, oy) in [(-13.0f32, -13.0f32), (13.0, -13.0), (-13.0, 13.0), (13.0, 13.0)] {
            self.push("b_home_1", Kind::Prop, (cx + ox) * t, (cy + oy) * t, Shape::Rect(1.0 * t, 0.6 * t), 0.0, 0.0);
        }
        for (name, ox, oy) in [("b_home_2", -12.5f32, -4.0f32), ("b_home_3", 12.5, -9.5), ("b_home_4", 12.0, 9.0), ("b_home_5", -12.0, 8.5),
            ("b_home_2", 4.0, -12.5), ("b_home_5", -6.0, 12.6)] {
            self.push(name, Kind::Prop, (cx + ox) * t, (cy + oy) * t, Shape::Rect(1.4 * t, 0.45 * t), 0.0, 0.0);
        }
        for (k, (ox, oy)) in [(-7.5f32, 6.0f32), (-3.0, 7.5), (8.5, 1.0), (4.0, 6.5), (-9.5, 2.0), (-1.0, -5.5), (2.0, 3.0), (-5.0, -6.0)].iter().enumerate() {
            self.push(&format!("d_home_{k}"), Kind::Deco, (cx + ox) * t, (cy + oy) * t, Shape::None, 0.0, 0.0);
        }
        for k in 0..10 {
            let a = k as f32 * 0.63;
            self.push(["flower_daisy", "flower_poppy", "grass1"][k % 3], Kind::Deco, (cx + a.cos() * 11.0) * t, (cy + a.sin() * 11.0) * t, Shape::None, 0.0, a);
        }
    }

    /// Devlet üsleri: duvarlı yerleşke (güney kapısı + kuzey açıklığı), köşelerde kule, içeride kum torbası, çadır,
    /// askerî sandıklar; ortada ele geçirme alanı ve helikopter pisti (zemin beton).
    fn place_bases(&mut self) {
        let t = TILE;
        let h = BASE_HALF;
        for b in base_defs() {
            let (cx, cy) = (b.tx, b.ty);
            self.clear_tiles(cx - h - 2.0, cy - h - 2.0, cx + h + 2.0, cy + h + 3.0);
            for ty in (cy - h) as usize..=(cy + h) as usize {
                for tx in (cx - h) as usize..=(cx + h) as usize {
                    let (dx, dy) = (tx as f32 - cx, ty as f32 - cy);
                    let g = if dx.hypot(dy + 5.0) < 3.2 || dx.hypot(dy) < 2.2 { ground::CONCRETE } else if dx.abs() < h - 1.5 && dy.abs() < h - 1.5 { ground::GRAVEL } else { ground::DIRT };
                    if let Some(c) = self.ground.get_mut(ty * MAP_TILES + tx) {
                        *c = g;
                    }
                }
            }
            let (x0, x1, y0, y1) = (cx - h, cx + h, cy - h, cy + h);
            let mut x = x0;
            while x < x1 {
                let mid = x + 2.0;
                if (mid - cx).abs() > 3.5 {
                    self.push("wall_x", Kind::Prop, mid * t, y1 * t, Shape::Rect(2.0 * t, 0.4 * t), 0.0, 0.0);
                }
                if (mid - cx).abs() > 2.5 {
                    self.push("wall_x", Kind::Prop, mid * t, y0 * t, Shape::Rect(2.0 * t, 0.4 * t), 0.0, 0.0);
                }
                x += 4.0;
            }
            let mut y = y0;
            while y < y1 {
                self.push("wall_z", Kind::Prop, x0 * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
                self.push("wall_z", Kind::Prop, x1 * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
                y += 4.0;
            }
            for (ox, oy) in [(-1.0, -1.0), (1.0, -1.0), (-1.0, 1.0), (1.0, 1.0)] {
                self.push("watchtower", Kind::Tower, (cx + ox * (h - 1.6)) * t, (cy + oy * (h - 1.6)) * t, Shape::Rect(1.2 * t, 1.2 * t), 0.0, 0.0);
            }
            for (ox, oy) in [(-4.5, 3.0), (4.5, 3.0), (0.0, 6.5), (-7.0, -1.0), (7.0, -1.0)] {
                self.push("sandbags", Kind::Prop, (cx + ox) * t, (cy + oy) * t, Shape::Rect(1.4 * t, 0.35 * t), 0.0, 0.0);
            }
            // komuta sığınağı, ödül sandığı ve helikopter pisti (zemin betonunun üstünde)
            self.push("bunker", Kind::Building, (cx - 6.5) * t, (cy - 6.5) * t, Shape::Rect(2.6 * t, 1.8 * t), 0.0, 0.0);
            self.push("supply_crate", Kind::Prop, (cx + 6.5) * t, (cy - 6.5) * t, Shape::Rect(0.8 * t, 0.45 * t), 0.0, 0.0);
            self.push("helipad", Kind::Deco, cx * t, (cy - 5.0) * t, Shape::None, 0.0, 0.0);
            for (ox, oy) in [(-8.0, 5.5), (8.0, 5.5), (-3.0, -8.5)] {
                self.push("crate_mil", Kind::Crate, (cx + ox) * t, (cy + oy) * t, Shape::Circle(0.45 * t), 40.0, 0.0);
            }
            for ox in [-4.0f32, 4.0] {
                self.push("barricade", Kind::Prop, (cx + ox) * t, (y1 + 1.6) * t, Shape::Rect(0.8 * t, 0.2 * t), 0.0, 0.0);
                self.push("lamp", Kind::Lamp, (cx + ox * 0.75) * t, (y1 - 1.5) * t, Shape::Circle(0.15 * t), 0.0, 0.0);
            }
            // eğitim üssü: kapıda tabela, içeride hedef işaretleri ve saman mankenler
            if b.id as usize == crate::bases::TRAIN_BASE {
                self.push("train_sign", Kind::Prop, (cx + 6.5) * t, (y1 + 2.8) * t, Shape::Rect(0.9 * t, 0.2 * t), 0.0, 0.0);
                for (ox, oy) in [(-5.5f32, 1.5f32), (5.5, 1.5)] {
                    self.push("train_target", Kind::Deco, (cx + ox) * t, (cy + oy) * t, Shape::None, 0.0, 0.0);
                }
                for (ox, oy) in [(-8.5f32, 1.0f32), (8.5, 1.0), (-2.5, 8.0), (2.5, 8.0)] {
                    self.push("train_dummy", Kind::Prop, (cx + ox) * t, (cy + oy) * t, Shape::Circle(0.2 * t), 0.0, 0.0);
                }
            }
        }
    }

    /// V11 dünya sınırı ve şehir surunun açıkları:
    ///  - şehir suru harita kenarından 2 karo içeride başlıyordu (kenardan sıyrılıp şehre girilebiliyordu) → batı ucu kapatılır
    ///  - şehrin doğusu (Askerî Bölge tarafı) açıktı → doğu suru (x = OLD_TILES), kuzey kenardan sura kadar
    ///  - harita kenarı boştu → çepeçevre karantina duvarı, aralıklı gözetleme kuleleri, lambalar ve uyarı tabelaları
    ///
    /// V13: her bölgenin kendi sınır parçaları kenar boyunca dizilir (öteki bölge görünmesin), dekorlar serpilir,
    /// iki simge yapı (biri giriş karakolunun yanında, biri bölgenin içinde) kurulur.
    fn place_region_art(&mut self) {
        use crate::areas::{self, AREAS};
        let t = TILE;
        let n = MAP_TILES as f32;
        let mut rng = Rng::new(self.seed ^ 0x13_A2EA);
        // büyük nesneler için kaba ızgara (4 karo)
        let cell = |x: f32, y: f32| ((x / (4.0 * t)) as i32, (y / (4.0 * t)) as i32);
        let mut grid: std::collections::HashMap<(i32, i32), Vec<(f32, f32, f32)>> = std::collections::HashMap::new();
        for st in &self.statics {
            let r = match st.shape {
                Shape::None => continue,
                Shape::Circle(r) => r,
                Shape::Rect(hw, hh) => hw.max(hh),
            };
            grid.entry(cell(st.x, st.y)).or_default().push((st.x, st.y, r));
        }
        let free = |grid: &std::collections::HashMap<(i32, i32), Vec<(f32, f32, f32)>>, x: f32, y: f32, r: f32| -> bool {
            let (cx, cy) = cell(x, y);
            for dy in -1..=1 {
                for dx in -1..=1 {
                    if let Some(v) = grid.get(&(cx + dx, cy + dy)) {
                        if v.iter().any(|&(sx, sy, sr)| (sx - x).hypot(sy - y) < sr + r) {
                            return false;
                        }
                    }
                }
            }
            true
        };
        let busy_base = |x: f32, y: f32| base_defs().iter().any(|b| (x / t - b.tx).abs() < BASE_HALF + 4.0 && (y / t - b.ty).abs() < BASE_HALF + 4.5);
        for (ai, a) in AREAS.iter().enumerate() {
            let id = a.id;
            let (hx, hy) = areas::hub(ai);
            let near_hub = |x: f32, y: f32| (x - hx).hypot(y - hy) < 14.0 * t;
            // ---- sınır: kenar boyunca, içeriden; harita kenarında karantina duvarının iç yanına
            if id != "home" {
                for &(x0, y0, x1, y1) in a.rects {
                    let edges = [
                        ((x0, y0), (x1, y0), (0.0f32, -1.0f32)),
                        ((x0, y1), (x1, y1), (0.0, 1.0)),
                        ((x0, y0), (x0, y1), (-1.0, 0.0)),
                        ((x1, y0), (x1, y1), (1.0, 0.0)),
                    ];
                    for ((ax, ay), (bx, by), (ox, oy)) in edges {
                        let len = (bx - ax).abs().max((by - ay).abs());
                        let mut d = 2.0;
                        while d < len - 1.0 {
                            let f = d / len;
                            let (ex, ey) = (ax + (bx - ax) * f, ay + (by - ay) * f);
                            let outer = ex + ox < 0.5 || ey + oy < 0.5 || ex + ox > n - 0.5 || ey + oy > n - 0.5;
                            let inset = if outer { 4.8 } else { 1.3 };
                            let (px, py) = ((ex - ox * inset) * t, (ey - oy * inset) * t);
                            let other = outer || areas::area_at((ex + ox * 1.5) * t, (ey + oy * 1.5) * t) != ai;
                            d += 2.9 + rng.range(0.0, 0.5);
                            if !other || areas::area_at(px, py) != ai || near_hub(px, py) || busy_base(px, py) || !free(&grid, px, py, 0.9 * t) {
                                continue;
                            }
                            let k = rng.below(6);
                            let name = format!("b_{id}_{k}");
                            self.push(&name, Kind::Prop, px, py, Shape::Rect(1.45 * t, 0.45 * t), 0.0, rng.range(0.0, std::f32::consts::TAU));
                            grid.entry(cell(px, py)).or_default().push((px, py, 1.45 * t));
                        }
                    }
                }
            }
            // ---- simge yapılar
            if id != "home" {
                let spots = [(hx + 13.0 * t, hy - 5.0 * t), {
                    let r = a.rects[0];
                    (((r.0 + r.2) / 2.0 + 18.0) * t, ((r.1 + r.3) / 2.0 - 10.0) * t)
                }];
                for (li, &(sx, sy)) in spots.iter().enumerate() {
                    // en yakın boş yer (sarmal arama)
                    let mut at = None;
                    'search: for ring in 0..24 {
                        let rr = ring as f32 * 2.0 * t;
                        let steps = (ring * 6).max(1);
                        for s in 0..steps {
                            let ang = s as f32 / steps as f32 * std::f32::consts::TAU;
                            let (x, y) = (sx + ang.cos() * rr, sy + ang.sin() * rr);
                            if areas::area_at(x, y) == ai && free(&grid, x, y, 3.2 * t) && !busy_base(x, y) && (li == 0 || !near_hub(x, y)) {
                                at = Some((x, y));
                                break 'search;
                            }
                        }
                    }
                    if let Some((x, y)) = at {
                        let name = format!("l_{id}_{}", li + 1);
                        self.push(&name, Kind::Building, x, y, Shape::Rect(2.0 * t, 0.9 * t), 0.0, 0.0);
                        grid.entry(cell(x, y)).or_default().push((x, y, 3.0 * t));
                    }
                }
            }
            // ---- dekor: bölgeye serpilir (kişisel üste sabit birkaç tane)
            let (mut area_t, mut tries) = (0.0f32, 0usize);
            for r in a.rects {
                area_t += (r.2 - r.0) * (r.3 - r.1);
            }
            let want = if id == "home" { 8 } else { ((area_t / 700.0) as usize).clamp(40, 220) };
            let mut placed = 0;
            while placed < want && tries < want * 12 {
                tries += 1;
                let r = a.rects[rng.below(a.rects.len() as u32) as usize];
                let (x, y) = (rng.range(r.0 + 2.0, r.2 - 2.0) * t, rng.range(r.1 + 2.0, r.3 - 2.0) * t);
                if areas::area_at(x, y) != ai || (id != "home" && near_hub(x, y)) || busy_base(x, y) || !free(&grid, x, y, 0.6 * t) {
                    continue;
                }
                if id == "home" && (x - hx).hypot(y - hy) < 3.0 * t {
                    continue;
                }
                let name = format!("d_{id}_{}", rng.below(8));
                self.push(&name, Kind::Deco, x, y, Shape::None, 0.0, rng.range(0.0, std::f32::consts::TAU));
                grid.entry(cell(x, y)).or_default().push((x, y, 0.5 * t));
                placed += 1;
            }
        }
    }

    /// V13: her karonun malzemesi bulunduğu bölgenin kendi dokusuna çevrilir (crate::areas::region_ground)
    fn regionize_ground(&mut self) {
        for ty in 0..MAP_TILES {
            for tx in 0..MAP_TILES {
                let (x, y) = ((tx as f32 + 0.5) * TILE, (ty as f32 + 0.5) * TILE);
                let a = crate::areas::area_at(x, y);
                let n = crate::rng::value_noise(self.seed ^ 0xA17, tx as f32 / 7.0, ty as f32 / 7.0);
                let i = ty * MAP_TILES + tx;
                if let Some(c) = self.ground.get_mut(i) {
                    *c = crate::areas::region_ground(a, *c, n);
                }
            }
        }
    }

    fn place_border(&mut self) {
        let t = TILE;
        let n = MAP_TILES as f32;
        // şehir surunun batı ucu (kenardan ilk sur parçasına kadar)
        self.push("wall_x", Kind::Prop, 1.0 * t, (WALL_Y as f32 + 1.0) * t, Shape::Rect(1.2 * t, 0.4 * t), 0.0, 0.0);
        // şehrin doğu suru: kuzey kenardan güney sura kadar kesintisiz
        let ex = OLD_TILES as f32;
        let mut y = 0.0;
        while y < WALL_Y as f32 + 1.0 {
            self.push("wall_z", Kind::Prop, ex * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
            y += 4.0;
        }
        self.push("tower", Kind::Tower, (ex - 1.5) * t, (WALL_Y as f32 - 1.0) * t, Shape::Rect(1.2 * t, 1.2 * t), 0.0, 0.0);
        // kapı ile yan kuleler arasındaki dar aralık (iki taraftan itilen oyuncu sıyrılabiliyordu): görünmez dolgu
        for gx in [GATE_X0 as f32 - 1.0, GATE_X1 as f32 + 1.0] {
            self.push("grass0", Kind::Deco, gx * t, (WALL_Y as f32 + 1.0) * t, Shape::Rect(1.0 * t, 0.5 * t), 0.0, 0.0);
        }
        // karantina duvarı: ana dünyanın çevresi (kenardan 1,5 karo içeride) ve haritanın dış kenarı (Cephe Hattı'nın dışı).
        // Ana dünya ile Cephe Hattı arasında geçit yok: yalnız Büyücü Aldric ışınlar.
        let e0 = 1.5f32;
        let w1 = WORLD_TILES as f32 - 1.5;
        let e1 = n - 1.5;
        let mut x = 0.0;
        while x < n {
            // V13: beton karantina duvarı (dikenli tel, tehlike şeritleri); dikey kenarlarda eski sur
            self.push("quarantine_wall", Kind::Prop, (x + 2.0) * t, e0 * t, Shape::Rect(2.0 * t, 0.4 * t), 0.0, 0.0);
            self.push("quarantine_wall", Kind::Prop, (x + 2.0) * t, e1 * t, Shape::Rect(2.0 * t, 0.4 * t), 0.0, 0.0);
            if x < WORLD_TILES as f32 {
                self.push("quarantine_wall", Kind::Prop, (x + 2.0) * t, w1 * t, Shape::Rect(2.0 * t, 0.4 * t), 0.0, 0.0);
            }
            x += 4.0;
        }
        let mut y = 0.0;
        while y < n {
            self.push("wall_z", Kind::Prop, e0 * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
            self.push("wall_z", Kind::Prop, e1 * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
            if y < WORLD_TILES as f32 {
                self.push("wall_z", Kind::Prop, w1 * t, (y + 2.0) * t, Shape::Rect(0.4 * t, 2.0 * t), 0.0, 0.0);
            }
            y += 4.0;
        }
        let n = WORLD_TILES as f32;
        let e1 = w1;
        // duvarın iç yüzü: her 36 karoda gözetleme kulesi, aralarda lamba, tabela ve barikat (bölgeye göre görsel değişir)
        let mut k = 0usize;
        let mut d = 18.0;
        while d < n - 18.0 {
            for (px, py) in [(d, e0 + 2.6), (d, e1 - 2.6), (e0 + 2.6, d), (e1 - 2.6, d)] {
                let (tx, ty) = (px as usize, py as usize);
                // kişisel üs ve güvenli bölgelerin içine bir şey koyma
                if in_home_tile(tx, ty) || matches!(zone_at_tile(tx.min(MAP_TILES - 1), ty.min(MAP_TILES - 1)), Zone::Outpost | Zone::SafeCity | Zone::Camp) {
                    continue;
                }
                match k % 3 {
                    0 => self.push("watchtower", Kind::Tower, px * t, py * t, Shape::Rect(1.2 * t, 1.2 * t), 0.0, 0.0),
                    1 => {
                        self.push("border_searchlight", Kind::Lamp, px * t, py * t, Shape::Circle(0.35 * t), 0.0, 0.0);
                        self.push("quarantine_sign", Kind::Prop, (px + 2.0) * t, py * t, Shape::Circle(0.15 * t), 0.0, 0.0);
                    }
                    _ => {
                        self.push("quarantine_post", Kind::Prop, px * t, py * t, Shape::Rect(0.5 * t, 0.4 * t), 0.0, 0.0);
                        self.push("border_bones", Kind::Deco, (px - 2.2) * t, py * t, Shape::None, 0.0, 0.0);
                    }
                }
            }
            k += 1;
            d += 12.0;
        }
    }

    /// V11 Cephe Hattı: savaş alanı dekoru (siper, kum torbası, enkaz, kurumuş ağaç, askerî sandık) ve güneydoğudaki
    /// Cephe Karargâhı (place_posts çadır/ateş koyar; burada çevresine sur ve kule). Üsler place_bases ile gelir.
    fn place_front(&mut self) {
        let t = TILE;
        let mut rng = Rng::new(self.seed ^ 0xF20_7000);
        let w0 = WORLD_TILES as f32;
        let n = MAP_TILES as f32;
        let hq = landmark("front_hq");
        let busy = |tx: f32, ty: f32| -> bool {
            base_defs().iter().any(|b| (tx - b.tx).abs() < BASE_HALF + 3.0 && (ty - b.ty).abs() < BASE_HALF + 3.5)
                || (tx - hq.tx).hypot(ty - hq.ty) < hq.r + 6.0
        };
        let tau = std::f32::consts::TAU;
        let mut y = 4.0;
        while y < n - 4.0 {
            let mut x = 4.0;
            while x < n - 4.0 {
                if x < w0 + 1.0 && y < w0 + 1.0 {
                    x += 3.2;
                    continue;
                }
                let (px, py) = (x + rng.range(-1.2, 1.2), y + rng.range(-1.2, 1.2));
                let r = rng.f32();
                let ph = rng.range(0.0, tau);
                if busy(px, py) || px < w0 + 2.0 && py < w0 + 2.0 {
                    x += 3.2;
                    continue;
                }
                let (wx, wy) = (px * t, py * t);
                if r < 0.008 {
                    self.push("front_trench", Kind::Prop, wx, wy, Shape::Rect(2.2 * t, 0.6 * t), 0.0, 0.0);
                } else if r < 0.016 {
                    self.push("front_crater", Kind::Deco, wx, wy, Shape::None, 0.0, ph);
                } else if r < 0.022 {
                    self.push("front_barbed", Kind::Prop, wx, wy, Shape::Rect(1.6 * t, 0.25 * t), 0.0, 0.0);
                } else if r < 0.0245 {
                    self.push("front_tank_wreck", Kind::Prop, wx, wy, Shape::Rect(2.3 * t, 0.9 * t), 0.0, 0.0);
                } else if r < 0.03 {
                    self.push("sandbags", Kind::Prop, wx, wy, Shape::Rect(1.4 * t, 0.35 * t), 0.0, 0.0);
                } else if r < 0.04 {
                    self.push("barricade", Kind::Prop, wx, wy, Shape::Rect(0.8 * t, 0.2 * t), 0.0, 0.0);
                } else if r < 0.05 {
                    self.push(["jeep_wreck", "burnt_car", "wreck0"][rng.below(3) as usize], Kind::Prop, wx, wy, Shape::Rect(1.4 * t, 0.7 * t), 0.0, ph);
                } else if r < 0.07 {
                    self.push(["burnt_tree", "dead0", "dead1"][rng.below(3) as usize], Kind::Tree, wx, wy, Shape::Circle(0.22 * t), 25.0, ph);
                } else if r < 0.078 {
                    self.push("crate_mil", Kind::Crate, wx, wy, Shape::Circle(0.45 * t), 40.0, 0.0);
                } else if r < 0.1 {
                    self.push(["ember_rocks", "rock_small1", "grass2", "cone"][rng.below(4) as usize], Kind::Deco, wx, wy, Shape::None, 0.0, ph);
                } else if r < 0.105 {
                    self.push(["rock_big0", "rock_big1"][rng.below(2) as usize], Kind::Rock, wx, wy, Shape::Circle(0.55 * t), 60.0, 0.0);
                }
                x += 3.2;
            }
            y += 3.2;
        }
        // karargâh pazarı: tüccar, görevci, doktor, tamirci, eğitmen (Game::new tüccarın çevresine dizer)
        self.merchants.push((hq.tx * t, (hq.ty - 2.0) * t));
        // karargâh çevresi: dört köşede kule, lambalar
        for (ox, oy) in [(-1.0f32, -1.0f32), (1.0, -1.0), (-1.0, 1.0), (1.0, 1.0)] {
            self.push("watchtower", Kind::Tower, (hq.tx + ox * (hq.r + 2.5)) * t, (hq.ty + oy * (hq.r + 2.5)) * t, Shape::Rect(1.2 * t, 1.2 * t), 0.0, 0.0);
            self.push("lamp", Kind::Lamp, (hq.tx + ox * (hq.r - 1.0)) * t, (hq.ty + oy * (hq.r - 1.0)) * t, Shape::Circle(0.15 * t), 0.0, 0.0);
        }
        // V12.1 (Askeri Bölge kalktı): karargâhın dışında komuta çadırı, telsiz direği, radar ve askerî çadırlar
        let (rx, ry) = (hq.tx - hq.r - 7.0, hq.ty - 3.0);
        self.push("hq_radio_mast", Kind::Prop, rx * t, (ry - 4.0) * t, Shape::Circle(0.4 * t), 0.0, 0.0);
        self.push("radar_dish", Kind::Prop, (rx + 4.5) * t, (ry - 3.0) * t, Shape::Circle(0.6 * t), 0.0, 0.0);
        self.push("hq_command_tent", Kind::Prop, (rx + 1.5) * t, (ry + 5.0) * t, Shape::Rect(2.2 * t, 1.1 * t), 0.0, 0.0);
        for k in 0..2 {
            self.push("mil_tent", Kind::Prop, (rx + 5.5 + k as f32 * 3.2) * t, (ry + 5.0) * t, Shape::Rect(1.4 * t, 0.9 * t), 0.0, 0.0);
        }
        // karargâh bayrağı ve giriş tabelası
        self.push("hq_flag", Kind::Prop, (hq.tx + hq.r - 1.5) * t, (hq.ty - hq.r + 1.5) * t, Shape::Circle(0.3 * t), 0.0, 0.0);
        self.push("hq_flag", Kind::Prop, (hq.tx - hq.r + 1.5) * t, (hq.ty - hq.r + 1.5) * t, Shape::Circle(0.3 * t), 0.0, 0.0);
        self.push("hq_sign", Kind::Prop, (hq.tx + 3.0) * t, (hq.ty + hq.r + 1.2) * t, Shape::Circle(0.15 * t), 0.0, 0.0);
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

    /// Hareketi küçük adımlara bölerek çözer: hızlı araç ya da takılan kare (büyük dt) ince duvarın içinden geçemez.
    #[allow(clippy::too_many_arguments)]
    pub fn resolve_move(&self, ox: f32, oy: f32, x: &mut f32, y: &mut f32, r: f32, gate_open: bool, scratch: &mut Vec<u32>) -> bool {
        let (dx, dy) = (*x - ox, *y - oy);
        let step = (r * 0.6).max(6.0);
        let n = ((dx.hypot(dy) / step).ceil() as usize).clamp(1, 48);
        let (sx, sy) = (dx / n as f32, dy / n as f32);
        let (mut cx, mut cy) = (ox, oy);
        let mut hit = false;
        for _ in 0..n {
            let (px, py) = (cx, cy);
            cx += sx;
            cy += sy;
            hit |= self.resolve(&mut cx, &mut cy, r, gate_open, scratch);
            // V12: bölge sınırı görünmez duvar
            hit |= crate::areas::clamp(px, py, &mut cx, &mut cy);
        }
        *x = cx;
        *y = cy;
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
            "snow_dock",
            "ash",
            "desert_road",
            "swamp",
            "base_dirt",
            "r_main",
            "r_alt",
            "r_path",
            "r_void",
        ]
    }

    pub fn is_road_center(tx: usize, ty: usize) -> (bool, bool) {
        if tx >= OLD_TILES || ty >= OLD_TILES {
            let (_, v, h) = region_road(tx, ty);
            return (v, h);
        }
        let cx = tx >= 8 && (tx - 8) % 34 == 2;
        let cy = (6..WALL_Y).contains(&ty) && (ty - 6) % 30 == 2;
        (cx, cy)
    }
}

pub const ZONES_ALL: [Zone; 18] = [
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
    Zone::Outpost,
    Zone::BurntForest,
    Zone::Desert,
    Zone::BlackSwamp,
    Zone::Military,
    Zone::Harbor,
    Zone::Home,
    Zone::Front,
];

#[cfg(test)]
mod tests {
    use super::*;

    /// V10 bölge dekorlarının her biri dünyada en az bir kez yer alır; çadırlar üç görsele dağılır.
    #[test]
    fn v10_decor_placed() {
        let w = World::generate(7);
        let mut n = std::collections::HashMap::new();
        for s in &w.statics {
            *n.entry(STATIC_NAMES[s.sprite as usize]).or_insert(0u32) += 1;
        }
        let first = STATIC_NAMES.iter().position(|s| *s == "snow_pine").unwrap();
        let missing: Vec<_> = STATIC_NAMES[first..].iter().filter(|k| n.get(*k).copied().unwrap_or(0) == 0).collect();
        assert!(missing.is_empty(), "yerleşmeyen: {missing:?}");
        assert_eq!(n.get("tent").copied().unwrap_or(0), 0);
    }

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

    /// Telefonda bellek/süre bütçesi: 4 kat alanda statik sayısı makul kalmalı
    #[test]
    fn generation_budget() {
        let t0 = std::time::Instant::now();
        let w = World::generate(7);
        let n = w.statics.len();
        println!("statik: {n}, üretim: {:?}", t0.elapsed());
        assert!(n < 260_000, "{n}");
        // bölgelerin hepsi var (V12.1: Askeri Bölge kalktı, askerî dekor Cephe Hattı'nda)
        for z in [Zone::BurntForest, Zone::Desert, Zone::BlackSwamp, Zone::Harbor, Zone::Front, Zone::Home] {
            let k = ZONES_ALL.iter().position(|q| *q == z).unwrap() as u8;
            assert!(w.zones.contains(&k), "{z:?}");
        }
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

    /// Şehre yalnız kapıdan girilir: harita kenarından ve doğudaki Askerî Bölge'den yürüyerek (ya da hızlı araçla) geçilemez
    #[test]
    fn city_sealed_from_outside() {
        let w = World::generate(7);
        let mut s = Vec::new();
        // dışarıdan şehre doğru yürüyüş (yol boyunca küçük adımlar; büyük adım da tünel açmamalı)
        let walk = |from: (f32, f32), to: (f32, f32), step: f32, s: &mut Vec<u32>| {
            let (mut x, mut y) = from;
            for _ in 0..4000 {
                let (dx, dy) = (to.0 - x, to.1 - y);
                let d = dx.hypot(dy);
                if d < 1.0 {
                    break;
                }
                let k = step.min(d) / d;
                let (ox, oy) = (x, y);
                let (mut nx, mut ny) = (x + dx * k, y + dy * k);
                w.resolve_move(ox, oy, &mut nx, &mut ny, 14.0, false, s);
                x = nx;
                y = ny;
            }
            (x, y)
        };
        let inside = |p: (f32, f32)| p.0 < OLD_TILES as f32 * TILE - TILE && p.1 < WALL_Y as f32 * TILE;
        // batı kenarı boyunca kuzeye (eski açık: sur kenardan 2 karo içeride başlıyordu)
        for step in [6.0, 120.0] {
            let end = walk((1.2 * TILE, (WALL_Y as f32 + 6.0) * TILE), (1.2 * TILE, 100.0 * TILE), step, &mut s);
            assert!(!inside(end), "batı kenarından şehre girildi {end:?} (adım {step})");
            let end = walk(((OLD_TILES as f32 + 6.0) * TILE, 100.0 * TILE), (300.0 * TILE, 100.0 * TILE), step, &mut s);
            assert!(!inside(end), "doğudan şehre girildi {end:?} (adım {step})");
        }
        // harita kenarı duvarı: dışarı çıkılamaz, doğma noktaları boş
        let (x, y) = home_spawn();
        assert!(!w.blocked(x, y, 14.0, &mut s));
    }

    /// Sur boyunca her noktadan kuzeye yürüyüş: kapı kapalıyken hiçbir aralıktan şehre geçilemez
    #[test]
    fn city_wall_has_no_gaps() {
        let w = World::generate(7);
        let mut s = Vec::new();
        let mut leaks = Vec::new();
        let mut tx = 0.6;
        while tx < OLD_TILES as f32 + 4.0 {
            let (mut x, mut y) = (tx * TILE, (WALL_Y as f32 + 5.0) * TILE);
            for _ in 0..400 {
                let (ox, oy) = (x, y);
                let (mut nx, mut ny) = (x, y - 8.0);
                w.resolve_move(ox, oy, &mut nx, &mut ny, 14.0, false, &mut s);
                x = nx;
                y = ny;
            }
            if y < (WALL_Y as f32 - 1.0) * TILE && x < OLD_TILES as f32 * TILE {
                leaks.push((tx, x / TILE, y / TILE));
            }
            tx += 0.5;
        }
        assert!(leaks.is_empty(), "surdan sızma: {leaks:?}");
    }
}
