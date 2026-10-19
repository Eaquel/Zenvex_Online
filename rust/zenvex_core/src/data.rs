//! Oyun verileri: eşyalar, silahlar, zırhlar, zombiler, tarifler, karakterler, araçlar, görevler, pass.
//! Tek kaynak burasıdır; Kotlin arayüzü bu verileri JSON olarak alır.

/// Dünya birimi: 1 m = 62.5 px (sprite çözünürlüğü 0.016 m/px).
pub const PX_PER_M: f32 = 62.5;

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub enum ItemKind {
    Material,
    Consumable,
    Ammo,
    Weapon,
    Helmet,
    Jacket,
    Boots,
    Bag,
    Cloak,
    Currency,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, serde::Serialize)]
pub enum Rarity {
    Common = 0,
    Rare = 1,
    Epic = 2,
    Legendary = 3,
    Seasonal = 4,
}

pub struct ItemDef {
    pub id: &'static str,
    pub name: &'static str,
    pub kind: ItemKind,
    pub rarity: Rarity,
    pub stack: u16,
    pub value: u32,
    pub desc: &'static str,
    /// zırh / can bonusu (%) / çanta slotu / iyileştirme miktarı (türüne göre)
    pub armor: f32,
    pub bonus: f32,
}

macro_rules! item {
    ($id:expr, $name:expr, $kind:ident, $rar:ident, $stack:expr, $val:expr, $desc:expr) => {
        ItemDef {
            id: $id,
            name: $name,
            kind: ItemKind::$kind,
            rarity: Rarity::$rar,
            stack: $stack,
            value: $val,
            desc: $desc,
            armor: 0.0,
            bonus: 0.0,
        }
    };
    ($id:expr, $name:expr, $kind:ident, $rar:ident, $stack:expr, $val:expr, $desc:expr, $armor:expr, $bonus:expr) => {
        ItemDef {
            id: $id,
            name: $name,
            kind: ItemKind::$kind,
            rarity: Rarity::$rar,
            stack: $stack,
            value: $val,
            desc: $desc,
            armor: $armor,
            bonus: $bonus,
        }
    };
}

pub const ITEMS: &[ItemDef] = &[
    // malzemeler (0-14)
    item!("wood", "Odun", Material, Common, 50, 2, "Ağaçlardan toplanır. Sopa ve inşa için."),
    item!("stone", "Taş", Material, Common, 50, 2, "Kayalardan toplanır."),
    item!("cloth", "Kumaş", Material, Common, 50, 4, "Zombi kıyafetlerinden. Bandaj, çanta ve yelek yapımında."),
    item!("glue", "Yapıştırıcı", Material, Common, 50, 6, "Reçineden üretilir. Birçok tarifte gerekir."),
    item!("resin", "Reçine", Material, Common, 50, 3, "Çam ağaçlarından damlar."),
    item!("leather", "Deri", Material, Common, 50, 6, "Köpeklerden ve sandıklardan."),
    item!("scrap", "Metal Hurda", Material, Common, 50, 5, "Araç enkazı ve sandıklardan."),
    item!("screw", "Vida", Material, Common, 50, 3, "Sandık ve tezgâhlarda bulunur."),
    item!("powder", "Barut", Material, Rare, 50, 8, "Mermi üretimi için."),
    item!("electronics", "Elektronik Parça", Material, Rare, 50, 14, "Şehirdeki binalarda."),
    item!("tooth", "Zombi Dişi", Material, Common, 50, 3, "Zombilerden düşer."),
    item!("toxic", "Toksik Öz", Material, Rare, 50, 12, "Toksik zombilerden."),
    item!("dark_crystal", "Karanlık Kristal", Material, Epic, 50, 40, "Karanlık zombilerden nadiren düşer."),
    item!("titan_shard", "Titan Parçası", Material, Legendary, 50, 120, "Yalnızca Titan'dan düşer."),
    item!("fuel", "Benzin", Material, Common, 10, 10, "Araçlara yakıt."),
    // tüketilebilir (15-19)  bonus = can
    item!("bandage", "Bandaj", Consumable, Common, 10, 8, "+25 can.", 0.0, 25.0),
    item!("medkit", "İlk Yardım Kiti", Consumable, Rare, 5, 30, "+60 can.", 0.0, 60.0),
    item!("food", "Konserve", Consumable, Common, 10, 6, "10 sn boyunca saniyede +3 can.", 0.0, 30.0),
    item!("energy", "Enerji İçeceği", Consumable, Common, 10, 10, "Dayanıklılık dolar, 10 sn +%20 hız.", 0.0, 0.0),
    item!("antidote", "Panzehir", Consumable, Rare, 5, 16, "Zehri temizler.", 0.0, 0.0),
    // mermiler (20-24)
    item!("ammo_9mm", "9 mm Mermi", Ammo, Common, 240, 1, "Glock için."),
    item!("ammo_762", "7,62 mm Mermi", Ammo, Common, 240, 2, "AK-47 için."),
    item!("ammo_556", "5,56 mm Mermi", Ammo, Common, 240, 2, "M4 için."),
    item!("ammo_308", ".308 Mermi", Ammo, Rare, 60, 6, "Keskin nişancı tüfeği için."),
    item!("ammo_rocket", "Roket", Ammo, Epic, 10, 60, "RPG için."),
    // silahlar (25-31)
    item!("w_bat", "Sopa", Weapon, Common, 1, 20, "Basit yakın dövüş silahı."),
    item!("w_baton", "Polis Copu", Weapon, Rare, 1, 60, "%15 sersemletme şansı."),
    item!("w_glock", "Glock", Weapon, Rare, 1, 150, "Hafif tabanca."),
    item!("w_ak47", "AK-47", Weapon, Epic, 1, 600, "Güçlü otomatik tüfek."),
    item!("w_m4", "M4", Weapon, Epic, 1, 650, "İsabetli otomatik tüfek."),
    item!("w_sniper", "Keskin Nişancı", Weapon, Legendary, 1, 1200, "Delip geçen ağır mermi."),
    item!("w_rpg", "RPG", Weapon, Legendary, 1, 1500, "Alan hasarlı roketatar."),
    // kasklar (32-35) armor, bonus = can %
    item!("helm_basic", "Basit Kask", Helmet, Common, 1, 30, "+5 zırh.", 5.0, 0.0),
    item!("helm_police", "Polis Kaskı", Helmet, Rare, 1, 120, "+12 zırh.", 12.0, 0.0),
    item!("helm_military", "Askerî Kask", Helmet, Epic, 1, 300, "+20 zırh.", 20.0, 0.0),
    item!("helm_titan", "Titan Kaskı", Helmet, Legendary, 1, 2000, "+35 zırh, +%10 can.", 35.0, 0.10),
    // ceketler (36-39)
    item!("jacket_basic", "Basit Yelek", Jacket, Common, 1, 40, "+6 zırh.", 6.0, 0.0),
    item!("jacket_police", "Polis Yeleği", Jacket, Rare, 1, 150, "+15 zırh.", 15.0, 0.0),
    item!("jacket_military", "Askerî Ceket", Jacket, Epic, 1, 350, "+24 zırh.", 24.0, 0.0),
    item!("jacket_titan", "Titan Ceketi", Jacket, Legendary, 1, 2500, "+40 zırh, +%10 hasar direnci.", 40.0, 0.10),
    // botlar (40-43) bonus = hız
    item!("boots_basic", "Basit Bot", Boots, Common, 1, 30, "+3 zırh, +%2 hız.", 3.0, 0.02),
    item!("boots_police", "Polis Botu", Boots, Rare, 1, 110, "+7 zırh, +%4 hız.", 7.0, 0.04),
    item!("boots_military", "Askerî Bot", Boots, Epic, 1, 280, "+12 zırh, +%6 hız.", 12.0, 0.06),
    item!("boots_titan", "Titan Botu", Boots, Legendary, 1, 1800, "+20 zırh, +%10 hız.", 20.0, 0.10),
    // çantalar (44-46) bonus = slot
    item!("bag_basic", "Basit Çanta", Bag, Common, 1, 50, "+8 envanter slotu.", 0.0, 8.0),
    item!("bag_police", "Polis Çantası", Bag, Rare, 1, 180, "+16 envanter slotu.", 0.0, 16.0),
    item!("bag_military", "Askerî Çanta", Bag, Epic, 1, 400, "+24 envanter slotu.", 0.0, 24.0),
    // pelerin (47)
    item!("cloak", "Tanrı Pelerini", Cloak, Seasonal, 1, 0,
          "Sezon 1'e özel. Can %20'nin altına düşünce 3 sn hasar almazsın, +%20 hız, +%25 hasar (60 sn bekleme)."),
    // para (48-49)
    item!("coin", "Altın", Currency, Common, 9999, 1, "Tüccarla alışveriş için."),
    item!("gem", "Elmas", Currency, Epic, 9999, 0, "Mağaza parası."),
    // V4 iksirler (50-52) — kayıtlar dizin sakladığı için yeni eşyalar hep sona eklenir
    item!("potion_heal", "Can İksiri", Consumable, Rare, 10, 40, "10 sn boyunca saniyede +5 can. 30 sn bekleme."),
    item!("potion_speed", "Hız İksiri", Consumable, Rare, 10, 60, "30 sn boyunca +%25 hız. 60 sn bekleme."),
    item!("potion_power", "Güç İksiri", Consumable, Epic, 10, 90, "20 sn boyunca +%20 hasar. 60 sn bekleme."),
];

pub fn item_index(id: &str) -> Option<usize> {
    ITEMS.iter().position(|i| i.id == id)
}

pub fn item(id: &str) -> &'static ItemDef {
    &ITEMS[item_index(id).unwrap_or(0)]
}

// --------------------------------------------------------------------------- silahlar
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub enum WeaponClass {
    Fist,
    Melee,
    Pistol,
    Rifle,
    Sniper,
    Heavy,
}

pub struct WeaponDef {
    pub item: &'static str,
    pub class: WeaponClass,
    pub damage: f32,
    pub rate: f32,
    pub range_m: f32,
    pub ammo: &'static str,
    pub mag: u16,
    pub reload: f32,
    pub spread: f32,
    pub pierce: u8,
    pub splash_m: f32,
    pub stun: f32,
    /// çizim için silah görseli kimliği (Kotlin: weapons/<sprite>)
    pub sprite: &'static str,
}

pub const WEAPONS: &[WeaponDef] = &[
    WeaponDef {
        item: "",
        class: WeaponClass::Fist,
        damage: 8.0,
        rate: 3.5,
        range_m: 1.4,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "",
    },
    WeaponDef {
        item: "w_bat",
        class: WeaponClass::Melee,
        damage: 16.0,
        rate: 2.0,
        range_m: 1.8,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "bat",
    },
    WeaponDef {
        item: "w_baton",
        class: WeaponClass::Melee,
        damage: 22.0,
        rate: 2.2,
        range_m: 1.8,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.15,
        sprite: "baton",
    },
    WeaponDef {
        item: "w_glock",
        class: WeaponClass::Pistol,
        damage: 14.0,
        rate: 4.0,
        range_m: 12.0,
        ammo: "ammo_9mm",
        mag: 17,
        reload: 1.2,
        spread: 0.06,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "glock",
    },
    WeaponDef {
        item: "w_ak47",
        class: WeaponClass::Rifle,
        damage: 22.0,
        rate: 8.0,
        range_m: 18.0,
        ammo: "ammo_762",
        mag: 30,
        reload: 2.0,
        spread: 0.1,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "ak47",
    },
    WeaponDef {
        item: "w_m4",
        class: WeaponClass::Rifle,
        damage: 19.0,
        rate: 10.0,
        range_m: 20.0,
        ammo: "ammo_556",
        mag: 30,
        reload: 1.8,
        spread: 0.05,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "m4",
    },
    WeaponDef {
        item: "w_sniper",
        class: WeaponClass::Sniper,
        damage: 95.0,
        rate: 0.8,
        range_m: 35.0,
        ammo: "ammo_308",
        mag: 5,
        reload: 2.6,
        spread: 0.0,
        pierce: 3,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "sniper",
    },
    WeaponDef {
        item: "w_rpg",
        class: WeaponClass::Heavy,
        damage: 220.0,
        rate: 0.4,
        range_m: 25.0,
        ammo: "ammo_rocket",
        mag: 1,
        reload: 3.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 3.0,
        stun: 0.0,
        sprite: "rpg",
    },
];

pub fn weapon_for(item_id: &str) -> usize {
    WEAPONS.iter().position(|w| w.item == item_id).unwrap_or(0)
}

// --------------------------------------------------------------------------- zombiler
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub enum ZKind {
    Walker = 0,
    Runner = 1,
    Dog = 2,
    Toxic = 3,
    Dark = 4,
    Tank = 5,
    Mystery = 6,
    Titan = 7,
}

pub struct ZombieDef {
    pub kind: ZKind,
    pub name: &'static str,
    pub sprite: &'static str,
    pub hp: f32,
    pub hp_lv: f32,
    pub dmg: f32,
    pub dmg_lv: f32,
    pub speed: f32,
    pub chase: f32,
    pub radius: f32,
    pub xp: u32,
    pub gold: (u32, u32),
    pub color: u32,
}

pub const ZOMBIES: &[ZombieDef] = &[
    ZombieDef {
        kind: ZKind::Walker,
        name: "Zombi",
        sprite: "walker",
        hp: 40.0,
        hp_lv: 8.0,
        dmg: 6.0,
        dmg_lv: 1.4,
        speed: 0.5,
        chase: 2.4,
        radius: 14.0,
        xp: 15,
        gold: (1, 4),
        color: 0xFFE8E8E8,
    },
    ZombieDef {
        kind: ZKind::Runner,
        name: "Koşucu",
        sprite: "runner",
        hp: 25.0,
        hp_lv: 5.0,
        dmg: 4.0,
        dmg_lv: 1.0,
        speed: 0.8,
        chase: 4.4,
        radius: 13.0,
        xp: 18,
        gold: (1, 5),
        color: 0xFFFF8A6A,
    },
    ZombieDef {
        kind: ZKind::Dog,
        name: "Zombi Köpek",
        sprite: "dog",
        hp: 20.0,
        hp_lv: 4.0,
        dmg: 5.0,
        dmg_lv: 1.0,
        speed: 1.0,
        chase: 4.8,
        radius: 13.0,
        xp: 12,
        gold: (0, 3),
        color: 0xFFD8B48A,
    },
    ZombieDef {
        kind: ZKind::Toxic,
        name: "Toksik Zombi",
        sprite: "toxic",
        hp: 50.0,
        hp_lv: 9.0,
        dmg: 5.0,
        dmg_lv: 1.0,
        speed: 0.45,
        chase: 2.0,
        radius: 15.0,
        xp: 26,
        gold: (2, 7),
        color: 0xFFA8FF4A,
    },
    ZombieDef {
        kind: ZKind::Dark,
        name: "Karanlık Zombi",
        sprite: "dark",
        hp: 45.0,
        hp_lv: 8.0,
        dmg: 8.0,
        dmg_lv: 1.6,
        speed: 0.6,
        chase: 3.0,
        radius: 14.0,
        xp: 32,
        gold: (3, 9),
        color: 0xFFC87AFF,
    },
    ZombieDef {
        kind: ZKind::Tank,
        name: "Tank Zombi",
        sprite: "tank",
        hp: 160.0,
        hp_lv: 20.0,
        dmg: 14.0,
        dmg_lv: 2.0,
        speed: 0.4,
        chase: 1.7,
        radius: 22.0,
        xp: 60,
        gold: (6, 16),
        color: 0xFFFFB84A,
    },
    ZombieDef {
        kind: ZKind::Mystery,
        name: "Gizemli Maskeli",
        sprite: "mystery",
        hp: 120.0,
        hp_lv: 15.0,
        dmg: 10.0,
        dmg_lv: 2.0,
        speed: 0.7,
        chase: 3.2,
        radius: 15.0,
        xp: 120,
        gold: (15, 40),
        color: 0xFFFFFFFF,
    },
    ZombieDef {
        kind: ZKind::Titan,
        name: "TİTAN",
        sprite: "titan",
        hp: 6000.0,
        hp_lv: 0.0,
        dmg: 40.0,
        dmg_lv: 0.0,
        speed: 0.5,
        chase: 1.9,
        radius: 46.0,
        xp: 2500,
        gold: (400, 800),
        color: 0xFFFF4A3A,
    },
];

// --------------------------------------------------------------------------- tarifler
pub struct Recipe {
    pub out: &'static str,
    pub count: u16,
    pub inputs: &'static [(&'static str, u16)],
    pub bench: bool,
}

pub const RECIPES: &[Recipe] = &[
    Recipe {
        out: "potion_heal",
        count: 1,
        inputs: &[("bandage", 1), ("resin", 2)],
        bench: false,
    },
    Recipe {
        out: "potion_speed",
        count: 1,
        inputs: &[("energy", 1), ("toxic", 1)],
        bench: true,
    },
    Recipe {
        out: "glue",
        count: 1,
        inputs: &[("resin", 2)],
        bench: false,
    },
    Recipe {
        out: "bandage",
        count: 1,
        inputs: &[("cloth", 2), ("glue", 1)],
        bench: false,
    },
    Recipe {
        out: "w_bat",
        count: 1,
        inputs: &[("wood", 4), ("screw", 2)],
        bench: false,
    },
    Recipe {
        out: "medkit",
        count: 1,
        inputs: &[("bandage", 2), ("toxic", 1)],
        bench: true,
    },
    Recipe {
        out: "antidote",
        count: 1,
        inputs: &[("toxic", 2), ("glue", 1)],
        bench: true,
    },
    Recipe {
        out: "ammo_9mm",
        count: 15,
        inputs: &[("scrap", 1), ("powder", 1)],
        bench: true,
    },
    Recipe {
        out: "ammo_762",
        count: 15,
        inputs: &[("scrap", 2), ("powder", 2)],
        bench: true,
    },
    Recipe {
        out: "ammo_556",
        count: 15,
        inputs: &[("scrap", 2), ("powder", 2)],
        bench: true,
    },
    Recipe {
        out: "ammo_308",
        count: 5,
        inputs: &[("scrap", 2), ("powder", 3)],
        bench: true,
    },
    Recipe {
        out: "ammo_rocket",
        count: 1,
        inputs: &[("scrap", 3), ("powder", 5), ("electronics", 2)],
        bench: true,
    },
    Recipe {
        out: "w_glock",
        count: 1,
        inputs: &[("scrap", 8), ("screw", 4), ("electronics", 2)],
        bench: true,
    },
];

// --------------------------------------------------------------------------- tüccar
/// (eşya, fiyat, gereken seviye)
pub const MERCHANT: &[(&str, u32, u32)] = &[
    ("bandage", 15, 1),
    ("food", 10, 1),
    ("energy", 18, 1),
    ("ammo_9mm", 2, 1),
    ("glue", 10, 1),
    ("cloth", 6, 1),
    ("w_bat", 40, 1),
    ("w_glock", 400, 5),
    ("medkit", 60, 5),
    ("antidote", 35, 6),
    ("potion_heal", 80, 3),
    ("potion_speed", 120, 6),
    ("potion_power", 180, 12),
    ("fuel", 25, 8),
    ("ammo_762", 3, 10),
    ("ammo_556", 3, 10),
    ("w_baton", 180, 10),
    ("w_ak47", 1600, 15),
    ("w_m4", 1800, 18),
    ("ammo_308", 10, 20),
    ("w_sniper", 4000, 28),
    ("ammo_rocket", 120, 30),
    ("w_rpg", 6000, 32),
];

// --------------------------------------------------------------------------- karakterler
pub struct CharDef {
    pub id: &'static str,
    pub name: &'static str,
    pub class: &'static str,
    pub desc: &'static str,
    pub unlock_gold: u32,
    pub unlock_level: u32,
    pub ability: &'static str,
    pub ability_desc: &'static str,
}

/// V4: tek kahraman. (Görseller "kaan" kimliğiyle paketli.)
pub const CHARACTERS: &[CharDef] = &[
    CharDef { id: "kaan", name: "Savaşçı", class: "Yumruk Dövüşçüsü", desc: "Siyah kapüşonlu hayatta kalan. Yumruklarıyla dövüşür, taklayla kaçar.",
              unlock_gold: 0, unlock_level: 1,
              ability: "Takla", ability_desc: "Bakılan yöne yuvarlanır, yuvarlanırken hasar almaz. 5 sn bekleme." },
];

pub fn char_xp_to_next(level: u32) -> u32 {
    (200.0 * (level as f32).powf(1.5)).round() as u32
}

// --------------------------------------------------------------------------- araçlar
pub struct VehicleDef {
    pub id: &'static str,
    pub name: &'static str,
    pub seats: u8,
    pub speed_m: f32,
    pub fuel: bool,
    pub hp: f32,
    pub radius: f32,
    pub length: f32,
}

pub const VEHICLES: &[VehicleDef] = &[
    VehicleDef {
        id: "bike",
        name: "Bisiklet",
        seats: 1,
        speed_m: 4.2,
        fuel: false,
        hp: 120.0,
        radius: 18.0,
        length: 40.0,
    },
    VehicleDef {
        id: "moto",
        name: "Motosiklet",
        seats: 1,
        speed_m: 8.5,
        fuel: true,
        hp: 220.0,
        radius: 20.0,
        length: 55.0,
    },
    VehicleDef {
        id: "car",
        name: "Spor Araba",
        seats: 2,
        speed_m: 10.0,
        fuel: true,
        hp: 500.0,
        radius: 46.0,
        length: 120.0,
    },
    VehicleDef {
        id: "suv",
        name: "SUV",
        seats: 4,
        speed_m: 9.0,
        fuel: true,
        hp: 700.0,
        radius: 50.0,
        length: 135.0,
    },
];

// --------------------------------------------------------------------------- görevler
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum QuestKind {
    Kill,
    KillType(u8),
    Chop,
    Craft,
    Crate,
    Gold,
    Level,
    EnterCity,
    Boss,
}

pub struct QuestDef {
    pub id: &'static str,
    pub title: &'static str,
    pub kind: QuestKind,
    pub goal: u32,
    pub gold: u32,
    pub xp: u32,
    pub pass_xp: u32,
}

pub const MAIN_QUESTS: &[QuestDef] = &[
    QuestDef {
        id: "m1",
        title: "İlk Kan: 5 zombi öldür",
        kind: QuestKind::Kill,
        goal: 5,
        gold: 50,
        xp: 60,
        pass_xp: 100,
    },
    QuestDef {
        id: "m2",
        title: "Oduncu: 10 ağaç kes",
        kind: QuestKind::Chop,
        goal: 10,
        gold: 60,
        xp: 80,
        pass_xp: 100,
    },
    QuestDef {
        id: "m3",
        title: "Zanaatkâr: 3 eşya üret",
        kind: QuestKind::Craft,
        goal: 3,
        gold: 80,
        xp: 100,
        pass_xp: 150,
    },
    QuestDef {
        id: "m4",
        title: "Hazine Avcısı: 5 sandık aç",
        kind: QuestKind::Crate,
        goal: 5,
        gold: 120,
        xp: 150,
        pass_xp: 150,
    },
    QuestDef {
        id: "m5",
        title: "Hazırlık: 10. seviyeye ulaş",
        kind: QuestKind::Level,
        goal: 10,
        gold: 300,
        xp: 0,
        pass_xp: 300,
    },
    QuestDef {
        id: "m6",
        title: "Yeni Ufuk: Şehre gir",
        kind: QuestKind::EnterCity,
        goal: 1,
        gold: 200,
        xp: 400,
        pass_xp: 300,
    },
    QuestDef {
        id: "m7",
        title: "Temizlik: 100 zombi öldür",
        kind: QuestKind::Kill,
        goal: 100,
        gold: 500,
        xp: 1200,
        pass_xp: 500,
    },
    QuestDef {
        id: "m8",
        title: "Efsane: Titan'ı yen",
        kind: QuestKind::Boss,
        goal: 1,
        gold: 3000,
        xp: 5000,
        pass_xp: 2000,
    },
];

pub const DAILY_POOL: &[QuestDef] = &[
    QuestDef {
        id: "d_kill",
        title: "30 zombi öldür",
        kind: QuestKind::Kill,
        goal: 30,
        gold: 120,
        xp: 200,
        pass_xp: 250,
    },
    QuestDef {
        id: "d_toxic",
        title: "8 Toksik Zombi öldür",
        kind: QuestKind::KillType(3),
        goal: 8,
        gold: 160,
        xp: 260,
        pass_xp: 300,
    },
    QuestDef {
        id: "d_dark",
        title: "6 Karanlık Zombi öldür",
        kind: QuestKind::KillType(4),
        goal: 6,
        gold: 200,
        xp: 320,
        pass_xp: 300,
    },
    QuestDef {
        id: "d_dog",
        title: "10 Zombi Köpek öldür",
        kind: QuestKind::KillType(2),
        goal: 10,
        gold: 120,
        xp: 180,
        pass_xp: 250,
    },
    QuestDef {
        id: "d_chop",
        title: "15 ağaç kes",
        kind: QuestKind::Chop,
        goal: 15,
        gold: 90,
        xp: 150,
        pass_xp: 200,
    },
    QuestDef {
        id: "d_craft",
        title: "5 eşya üret",
        kind: QuestKind::Craft,
        goal: 5,
        gold: 110,
        xp: 160,
        pass_xp: 200,
    },
    QuestDef {
        id: "d_crate",
        title: "6 sandık aç",
        kind: QuestKind::Crate,
        goal: 6,
        gold: 140,
        xp: 200,
        pass_xp: 250,
    },
    QuestDef {
        id: "d_gold",
        title: "500 altın kazan",
        kind: QuestKind::Gold,
        goal: 500,
        gold: 100,
        xp: 200,
        pass_xp: 250,
    },
];

// --------------------------------------------------------------------------- sezon / pass
pub const PASS_TIERS: u32 = 30;
pub const PASS_XP_PER_TIER: u32 = 1000;
pub const PASS_PRICE_NORMAL: &str = "49,99 TL";
pub const PASS_PRICE_ELITE: &str = "99,99 TL";

/// (kademe, ücretsiz ödül, premium ödül) — ödül biçimi: "eşya:adet" | "gold:n" | "gem:n" | "cos:kimlik"
pub const PASS_REWARDS: &[(u32, &str, &str)] = &[
    (1, "gold:100", "cos:frame_bronze"),
    (2, "bandage:5", "gem:20"),
    (3, "gold:150", "cos:tag_survivor"),
    (4, "ammo_9mm:60", "cos:skin_glock_camo"),
    (5, "cloth:20", "gold:500"),
    (6, "gold:200", "cos:kaan_night"),
    (7, "glue:10", "gem:30"),
    (8, "food:5", "medkit:3"),
    (9, "gold:250", "cos:skin_ak_gold"),
    (10, "bag_basic:1", "cos:frame_silver"),
    (11, "gold:300", "gem:40"),
    (12, "scrap:20", "cos:nova_neon"),
    (13, "gold:300", "ammo_762:120"),
    (14, "energy:5", "cos:tag_hunter"),
    (15, "gold:400", "helm_police:1"),
    (16, "powder:15", "gem:50"),
    (17, "gold:400", "cos:rady_crimson"),
    (18, "medkit:2", "cos:skin_m4_neon"),
    (19, "gold:500", "gem:60"),
    (20, "boots_police:1", "cos:frame_gold"),
    (21, "gold:500", "cos:elif_snow"),
    (22, "electronics:10", "gem:70"),
    (23, "gold:600", "cos:skin_sniper_titan"),
    (24, "antidote:3", "jacket_military:1"),
    (25, "gold:700", "cos:tag_legend"),
    (26, "dark_crystal:2", "gem:100"),
    (27, "gold:800", "cos:skin_rpg_gold"),
    (28, "medkit:5", "titan_shard:3"),
    (29, "gold:1000", "gem:150"),
    (30, "cos:frame_s1", "cloak:1"),
];

pub struct Cosmetic {
    pub id: &'static str,
    pub name: &'static str,
    pub kind: &'static str,
}

pub const COSMETICS: &[Cosmetic] = &[
    Cosmetic {
        id: "frame_bronze",
        name: "Bronz Çerçeve",
        kind: "frame",
    },
    Cosmetic {
        id: "frame_silver",
        name: "Gümüş Çerçeve",
        kind: "frame",
    },
    Cosmetic {
        id: "frame_gold",
        name: "Altın Çerçeve",
        kind: "frame",
    },
    Cosmetic {
        id: "frame_s1",
        name: "Sezon 1 Çerçevesi",
        kind: "frame",
    },
    Cosmetic {
        id: "tag_survivor",
        name: "[Hayatta Kalan]",
        kind: "tag",
    },
    Cosmetic {
        id: "tag_hunter",
        name: "[Avcı]",
        kind: "tag",
    },
    Cosmetic {
        id: "tag_legend",
        name: "[Sezon 1 Efsanesi]",
        kind: "tag",
    },
    Cosmetic {
        id: "kaan_night",
        name: "Kaan: Gece Avcısı",
        kind: "costume",
    },
    Cosmetic {
        id: "nova_neon",
        name: "Nova: Neon",
        kind: "costume",
    },
    Cosmetic {
        id: "rady_crimson",
        name: "Rady: Kızıl Büyü",
        kind: "costume",
    },
    Cosmetic {
        id: "elif_snow",
        name: "Elif: Kar Beyazı",
        kind: "costume",
    },
    Cosmetic {
        id: "skin_glock_camo",
        name: "Glock: Kamuflaj",
        kind: "weapon_skin",
    },
    Cosmetic {
        id: "skin_ak_gold",
        name: "AK-47: Altın",
        kind: "weapon_skin",
    },
    Cosmetic {
        id: "skin_m4_neon",
        name: "M4: Neon",
        kind: "weapon_skin",
    },
    Cosmetic {
        id: "skin_sniper_titan",
        name: "Keskin Nişancı: Titan",
        kind: "weapon_skin",
    },
    Cosmetic {
        id: "skin_rpg_gold",
        name: "RPG: Altın",
        kind: "weapon_skin",
    },
];

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ids_unique_and_resolvable() {
        for (i, a) in ITEMS.iter().enumerate() {
            for b in &ITEMS[i + 1..] {
                assert_ne!(a.id, b.id);
            }
        }
        for r in RECIPES {
            assert!(item_index(r.out).is_some(), "{}", r.out);
            for (i, _) in r.inputs {
                assert!(item_index(i).is_some(), "{i}");
            }
        }
        for (i, _, _) in MERCHANT {
            assert!(item_index(i).is_some(), "{i}");
        }
        for w in &WEAPONS[1..] {
            assert!(item_index(w.item).is_some());
            assert!(item_index(w.ammo).is_some() || w.ammo.is_empty());
        }
    }

    #[test]
    fn pass_rewards_valid() {
        assert_eq!(PASS_REWARDS.len() as u32, PASS_TIERS);
        for (_, a, b) in PASS_REWARDS {
            for r in [a, b] {
                let (k, _) = r.split_once(':').unwrap();
                match k {
                    "gold" | "gem" => {}
                    "cos" => assert!(
                        COSMETICS
                            .iter()
                            .any(|c| c.id == r.split(':').nth(1).unwrap()),
                        "{r}"
                    ),
                    _ => assert!(item_index(k).is_some(), "{r}"),
                }
            }
        }
    }
}
