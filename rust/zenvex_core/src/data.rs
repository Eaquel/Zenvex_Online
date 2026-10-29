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
    /// V8: yüzük (armor = hasar %, bonus = kritik şansı)
    Ring,
    /// V8: kolye (armor = düz can, bonus = can yenilenmesi %)
    Necklace,
    /// V8: kişisel üsse kurulan sandık (armor = kapasite)
    Chest,
    /// V9: pantolon (armor = zırh, bonus = hız %)
    Pants,
    /// V11: alet (balta / kazma: çantada durması yeter, armor = ağaç/taş başına vuruş gücü) ve araç anahtarı (kullanınca araç gelir)
    Tool,
    /// V12.1: kurulabilir tezgâh (kişisel üste kalıcı, başka yerde 10 dakika)
    Bench,
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
    item!("wood", "Odun", Material, Common, 50, 2, "Ağaç kesince düşer. Balta, kazma, sandık ve tezgâh yapımında kullanılır."),
    item!("stone", "Taş", Material, Common, 50, 2, "Kaya kırınca düşer. Taş balta, taş kazma ve tezgâh yapımında kullanılır."),
    item!("cloth", "Kumaş", Material, Common, 50, 4, "Zombilerden ve sandıklardan düşer. Bandaj, yelek, pantolon ve çanta yapımında kullanılır."),
    item!("glue", "Yapıştırıcı", Material, Common, 50, 6, "Reçineden üretilir. Birçok tarifte gerekir."),
    item!("resin", "Reçine", Material, Common, 50, 3, "Çam ağaçlarından damlar. Bandaj, Can İksiri ve Panzehir yapımında kullanılır."),
    item!("leather", "Deri", Material, Common, 50, 6, "Köpeklerden ve sandıklardan düşer. Zırh, çanta, bıçak ve tekerlek yapımında kullanılır."),
    item!("scrap", "Paslı Metal", Material, Common, 50, 5, "Enkazlardan, sandıklardan ve tank zombilerden düşer. Silah, kask, alet, araç parçası ve sandık yapımında kullanılır."),
    item!("screw", "Vida", Material, Common, 50, 3, "Sandık ve tezgâhlarda bulunur."),
    item!("powder", "Barut", Material, Rare, 50, 8, "Mermi üretimi için."),
    item!("electronics", "Elektronik Parça", Material, Rare, 50, 14, "Yıkık Şehir'de düşer. Araç motoru yapımında kullanılır."),
    item!("tooth", "Zombi Dişi", Material, Common, 50, 3, "Zombilerden düşer."),
    item!("toxic", "Toksik Öz", Material, Rare, 50, 12, "Toksik zombilerden düşer. Panzehir yapımında kullanılır."),
    item!("dark_crystal", "Karanlık Kristal", Material, Epic, 50, 40, "Karanlık zombilerden nadiren düşer. Katana, Titan zırhı ve Karanlık Kolye yapımında kullanılır."),
    item!("titan_shard", "Titan Parçası", Material, Legendary, 50, 120, "Yalnız Titan'dan düşer. Titan zırhı ve Karanlık Yüzük yapımında kullanılır."),
    item!("fuel", "Benzin", Material, Common, 10, 10, "Araçların yakıtı; aracın yanında kullanınca depoyu doldurur. Motor yapımında da kullanılır."),
    // tüketilebilir (15-19)  bonus = can
    item!("bandage", "Bandaj", Consumable, Common, 10, 8, "Kullanınca hemen 25 can verir.", 0.0, 25.0),
    item!("medkit", "İlk Yardım Kiti", Consumable, Rare, 5, 30, "Kullanınca hemen 60 can verir.", 0.0, 60.0),
    item!("food", "Konserve", Consumable, Common, 10, 6, "10 saniye boyunca her saniye 3 can verir.", 0.0, 30.0),
    item!("energy", "Enerji İçeceği", Consumable, Common, 10, 10, "Dayanıklılığı doldurur ve 10 saniye boyunca %20 hızlandırır.", 0.0, 0.0),
    item!("antidote", "Panzehir", Consumable, Rare, 5, 16, "Zehri hemen temizler.", 0.0, 0.0),
    // mermiler (20-24)
    item!("ammo_9mm", "9 mm Mermi", Ammo, Common, 240, 1, "Glock için."),
    item!("ammo_762", "7,62 mm Mermi", Ammo, Common, 240, 2, "AK-47 için."),
    item!("ammo_556", "5,56 mm Mermi", Ammo, Common, 240, 2, "M4 için."),
    item!("ammo_308", ".308 Mermi", Ammo, Rare, 60, 6, "Keskin nişancı tüfeği için."),
    item!("ammo_rocket", "Roket", Ammo, Epic, 10, 60, "RPG için."),
    // silahlar (25-31)
    item!("w_bat", "Tahta Sopa", Weapon, Common, 1, 20, "Çivili ağır meşe sopa. Yavaş ama sersemletir."),
    item!("w_baton", "Polis Copu", Weapon, Rare, 1, 60, "%15 sersemletme şansı."),
    item!("w_glock", "Tabanca", Weapon, Rare, 1, 150, "Hafif yarı otomatik tabanca. 15 mermilik şarjör; yedek mermi gerekmez, yalnız şarjör değiştirilir."),
    item!("w_ak47", "AK-47", Weapon, Epic, 1, 600, "Güçlü otomatik tüfek. 30 mermilik şarjör; yedek mermi gerekmez, yalnız şarjör değiştirilir."),
    item!("w_m4", "M4", Weapon, Epic, 1, 650, "İsabetli otomatik tüfek."),
    item!("w_sniper", "Keskin Nişancı Tüfeği", Weapon, Legendary, 1, 1200, "Uzak menzil, üç düşmanı delip geçen ağır mermi. 5 mermilik şarjör."),
    item!("w_rpg", "RPG-7", Weapon, Legendary, 1, 1500, "Alan hasarlı roketatar. Her atıştan sonra yeni roket takılır."),
    // kasklar (32-35) armor, bonus = can %
    item!("helm_basic", "Basit Kask", Helmet, Common, 1, 30, "+4 zırh.", 4.0, 0.0),
    item!("helm_police", "Polis Kaskı", Helmet, Rare, 1, 120, "+8 zırh.", 8.0, 0.0),
    item!("helm_military", "Askerî Kask", Helmet, Epic, 1, 300, "+12 zırh.", 12.0, 0.0),
    item!("helm_titan", "Titan Kaskı", Helmet, Legendary, 1, 2000, "+20 zırh, +%10 can.", 20.0, 0.10),
    // ceketler (36-39)
    item!("jacket_basic", "Basit Yelek", Jacket, Common, 1, 40, "+5 zırh.", 5.0, 0.0),
    item!("jacket_police", "Polis Yeleği", Jacket, Rare, 1, 150, "+10 zırh.", 10.0, 0.0),
    item!("jacket_military", "Askerî Ceket", Jacket, Epic, 1, 350, "+15 zırh.", 15.0, 0.0),
    item!("jacket_titan", "Titan Ceketi", Jacket, Legendary, 1, 2500, "+24 zırh, +%8 hasar direnci.", 24.0, 0.08),
    // botlar (40-43) bonus = hız
    item!("boots_basic", "Basit Bot", Boots, Common, 1, 30, "+2 zırh, +%2 hız.", 2.0, 0.02),
    item!("boots_police", "Polis Botu", Boots, Rare, 1, 110, "+4 zırh, +%3 hız.", 4.0, 0.03),
    item!("boots_military", "Askerî Bot", Boots, Epic, 1, 280, "+7 zırh, +%5 hız.", 7.0, 0.05),
    item!("boots_titan", "Titan Botu", Boots, Legendary, 1, 1800, "+10 zırh, +%8 hız.", 10.0, 0.08),
    // çantalar (44-46) armor = ek bölme başı slot, bonus = ek bölme sayısı
    item!("bag_basic", "Basit Çanta", Bag, Common, 1, 50, "Çantana 1 ek bölme açar (12 slot).", 12.0, 1.0),
    item!("bag_police", "Polis Çantası", Bag, Rare, 1, 180, "Çantana 2 ek bölme açar (12'şer slot).", 12.0, 2.0),
    item!("bag_military", "Askerî Çanta", Bag, Epic, 1, 400, "Çantana 3 ek bölme açar (16'şar slot).", 16.0, 3.0),
    // pelerin (47)
    item!("cloak", "Tanrı Pelerini", Cloak, Seasonal, 1, 0,
          "Sezon 1'e özel. Can %20'nin altına düşünce 3 sn hasar almazsın, +%20 hız, +%25 hasar (60 sn bekleme)."),
    // para (48-49)
    item!("coin", "Altın", Currency, Common, 9999, 1, "Tüccarda, köpek eğitmeninde, büyücüde ve karakter kartlarında harcanır."),
    item!("gem", "Elmas", Currency, Epic, 9999, 0, "Yetenek ağacında ve Elmas Sandığı'nda harcanır; Mağaza'dan alınır."),
    // V4 iksirler (50-52) — kayıtlar dizin sakladığı için yeni eşyalar hep sona eklenir
    item!("potion_heal", "Can İksiri", Consumable, Rare, 10, 40, "10 saniye boyunca her saniye 5 can verir. Yeniden kullanmak için 30 saniye bekle."),
    item!("potion_speed", "Hız İksiri", Consumable, Rare, 10, 60, "30 saniye boyunca %25 hızlandırır. Yeniden kullanmak için 60 saniye bekle."),
    item!("potion_power", "Güç İksiri", Consumable, Epic, 10, 90, "20 saniye boyunca hasarını %20 artırır. Yeniden kullanmak için 60 saniye bekle."),
    // V6 malzemeler ve silahlar (53+)
    item!("tape", "Bant", Material, Common, 50, 4, "Sargı ve tamir için. Kumaş ve yapıştırıcıdan da yapılır."),
    item!("copper", "Bakır Tel", Material, Common, 50, 6, "Elektronik çöplerden ve araç enkazından."),
    item!("nails", "Çivi", Material, Common, 50, 3, "Yıkık evlerde ve sandıklarda."),
    item!("rope", "İp", Material, Common, 50, 4, "Ormanda ve sandıklarda bulunur. Balta, kazma, çanta, tekerlek ve tezgâh yapımında kullanılır."),
    item!("battery", "Pil", Material, Rare, 20, 12, "Yıkık Şehir'de düşer. Polis Çantası ve araba gövdesi yapımında kullanılır."),
    item!("plastic", "Plastik", Material, Common, 50, 3, "Şehir çöplerinden."),
    item!("gear", "Dişli", Material, Rare, 50, 10, "Yıkık Şehir'de düşer. Demir aletler, Polis Kaskı ve araç parçaları yapımında kullanılır."),
    item!("spring", "Yay", Material, Rare, 50, 9, "Eski mobilya ve araçlardan."),
    item!("bone", "Kemik", Material, Common, 50, 3, "Zombilerden ve köpeklerden düşer. Kemik Yüzük ve kolye yapımında kullanılır."),
    item!("w_knife", "Bıçak", Weapon, Rare, 1, 60, "Hızlı ve sessiz. Kısa menzil, yüksek saldırı hızı."),
    item!("w_katana", "Katana", Weapon, Epic, 1, 900, "Geniş yay çizen ağır kesik. Önündeki birden çok düşmana vurur."),
    // V8 yüzükler (armor = hasar %, bonus = kritik şansı), kolyeler (armor = düz can, bonus = can yenilenmesi %), sandıklar (armor = kapasite)
    item!("ring_copper", "Kemik Yüzük", Ring, Common, 1, 60, "+%4 hasar.", 0.04, 0.0),
    item!("ring_hunter", "Rün Yüzüğü", Ring, Rare, 1, 220, "+%3 hasar, +%4 kritik şansı.", 0.03, 0.04),
    item!("ring_titan", "Karanlık Yüzük", Ring, Legendary, 1, 2200, "+%8 hasar, +%6 kritik şansı.", 0.08, 0.06),
    item!("neck_bone", "Kemik Kolye", Necklace, Common, 1, 50, "+15 can.", 15.0, 0.0),
    item!("neck_amulet", "Koruyucu Muska", Necklace, Rare, 1, 240, "+30 can, +%10 can yenilenmesi.", 30.0, 0.10),
    item!("neck_dark", "Karanlık Kolye", Necklace, Epic, 1, 700, "+50 can, +%20 can yenilenmesi.", 50.0, 0.20),
    item!("chest_wood", "Ahşap Sandık", Chest, Common, 5, 40, "Kişisel üssüne kurulur; içinde 16 eşya yuvası var.", 16.0, 0.0),
    item!("chest_iron", "Demir Sandık", Chest, Rare, 5, 160, "Kişisel üssüne kurulur; içinde 24 eşya yuvası var.", 24.0, 0.0),
    item!("chest_military", "Askerî Sandık", Chest, Epic, 5, 450, "Kişisel üssüne kurulur; içinde 32 eşya yuvası var.", 32.0, 0.0),    // V9 pantolonlar (armor = zırh, bonus = hız %)
    item!("pants_basic", "Kargo Pantolon", Pants, Common, 1, 35, "+3 zırh, +%1 hız.", 3.0, 0.01),
    item!("pants_police", "Dizlikli Pantolon", Pants, Rare, 1, 130, "+6 zırh, +%2 hız.", 6.0, 0.02),
    item!("pants_military", "Askerî Pantolon", Pants, Epic, 1, 320, "+10 zırh, +%3 hız.", 10.0, 0.03),
    item!("pants_titan", "Titan Pantolonu", Pants, Legendary, 1, 2100, "+16 zırh, +%5 hız.", 16.0, 0.05),
    // V9 etkinlik ödülleri (1., 2., 3. sıra): takas edilemez, yere bırakılamaz, 30 gün sonra kaybolur
    item!("ev_blood_ring", "Kan Ayı Kralı Yüzüğü", Ring, Seasonal, 1, 0, "Kan Ayı etkinliğinde 1. sırada bitirerek elde edilir. +%25 hasar, +%15 kritik şansı. Sınırlı sürelidir (30 gün), takas edilemez.", 0.25, 0.15),
    item!("ev_blood_neck", "Kan Ayı Şampiyonu Kolyesi", Necklace, Seasonal, 1, 0, "Kan Ayı etkinliğinde 2. sırada bitirerek elde edilir. +250 can, +%40 can yenilenmesi. Sınırlı sürelidir (30 gün), takas edilemez.", 250.0, 0.40),
    item!("ev_blood_helm", "Kan Ayı Muhafız Kaskı", Helmet, Seasonal, 1, 0, "Kan Ayı etkinliğinde 3. sırada bitirerek elde edilir. +40 zırh, +%15 can. Sınırlı sürelidir (30 gün), takas edilemez.", 40.0, 0.15),
    item!("ev_titan_ring", "Titan Avı Kralı Yüzüğü", Ring, Seasonal, 1, 0, "Titan Avı etkinliğinde 1. sırada bitirerek elde edilir. +%25 hasar, +%15 kritik şansı. Sınırlı sürelidir (30 gün), takas edilemez.", 0.25, 0.15),
    item!("ev_titan_neck", "Titan Avı Şampiyonu Kolyesi", Necklace, Seasonal, 1, 0, "Titan Avı etkinliğinde 2. sırada bitirerek elde edilir. +250 can, +%40 can yenilenmesi. Sınırlı sürelidir (30 gün), takas edilemez.", 250.0, 0.40),
    item!("ev_titan_helm", "Titan Avı Muhafız Kaskı", Helmet, Seasonal, 1, 0, "Titan Avı etkinliğinde 3. sırada bitirerek elde edilir. +40 zırh, +%15 can. Sınırlı sürelidir (30 gün), takas edilemez.", 40.0, 0.15),
    item!("ev_base_ring", "Üs Fırtınası Kralı Yüzüğü", Ring, Seasonal, 1, 0, "Üs Fırtınası etkinliğinde 1. sırada bitirerek elde edilir. +%25 hasar, +%15 kritik şansı. Sınırlı sürelidir (30 gün), takas edilemez.", 0.25, 0.15),
    item!("ev_base_neck", "Üs Fırtınası Şampiyonu Kolyesi", Necklace, Seasonal, 1, 0, "Üs Fırtınası etkinliğinde 2. sırada bitirerek elde edilir. +250 can, +%40 can yenilenmesi. Sınırlı sürelidir (30 gün), takas edilemez.", 250.0, 0.40),
    item!("ev_base_helm", "Üs Fırtınası Muhafız Kaskı", Helmet, Seasonal, 1, 0, "Üs Fırtınası etkinliğinde 3. sırada bitirerek elde edilir. +40 zırh, +%15 can. Sınırlı sürelidir (30 gün), takas edilemez.", 40.0, 0.15),
    item!("ev_scrap_ring", "Hurdacılar Kralı Yüzüğü", Ring, Seasonal, 1, 0, "Hurdacılar etkinliğinde 1. sırada bitirerek elde edilir. +%25 hasar, +%15 kritik şansı. Sınırlı sürelidir (30 gün), takas edilemez.", 0.25, 0.15),
    item!("ev_scrap_neck", "Hurdacılar Şampiyonu Kolyesi", Necklace, Seasonal, 1, 0, "Hurdacılar etkinliğinde 2. sırada bitirerek elde edilir. +250 can, +%40 can yenilenmesi. Sınırlı sürelidir (30 gün), takas edilemez.", 250.0, 0.40),
    item!("ev_scrap_helm", "Hurdacılar Muhafız Kaskı", Helmet, Seasonal, 1, 0, "Hurdacılar etkinliğinde 3. sırada bitirerek elde edilir. +40 zırh, +%15 can. Sınırlı sürelidir (30 gün), takas edilemez.", 40.0, 0.15),
    item!("ev_pack_ring", "Sürü Lideri Kralı Yüzüğü", Ring, Seasonal, 1, 0, "Sürü Lideri etkinliğinde 1. sırada bitirerek elde edilir. +%25 hasar, +%15 kritik şansı. Sınırlı sürelidir (30 gün), takas edilemez.", 0.25, 0.15),
    item!("ev_pack_neck", "Sürü Lideri Şampiyonu Kolyesi", Necklace, Seasonal, 1, 0, "Sürü Lideri etkinliğinde 2. sırada bitirerek elde edilir. +250 can, +%40 can yenilenmesi. Sınırlı sürelidir (30 gün), takas edilemez.", 250.0, 0.40),
    item!("ev_pack_helm", "Sürü Lideri Muhafız Kaskı", Helmet, Seasonal, 1, 0, "Sürü Lideri etkinliğinde 3. sırada bitirerek elde edilir. +40 zırh, +%15 can. Sınırlı sürelidir (30 gün), takas edilemez.", 40.0, 0.15),
    item!("ev_breath_ring", "Son Nefes Kralı Yüzüğü", Ring, Seasonal, 1, 0, "Son Nefes etkinliğinde 1. sırada bitirerek elde edilir. +%25 hasar, +%15 kritik şansı. Sınırlı sürelidir (30 gün), takas edilemez.", 0.25, 0.15),
    item!("ev_breath_neck", "Son Nefes Şampiyonu Kolyesi", Necklace, Seasonal, 1, 0, "Son Nefes etkinliğinde 2. sırada bitirerek elde edilir. +250 can, +%40 can yenilenmesi. Sınırlı sürelidir (30 gün), takas edilemez.", 250.0, 0.40),
    item!("ev_breath_helm", "Son Nefes Muhafız Kaskı", Helmet, Seasonal, 1, 0, "Son Nefes etkinliğinde 3. sırada bitirerek elde edilir. +40 zırh, +%15 can. Sınırlı sürelidir (30 gün), takas edilemez.", 40.0, 0.15),
    // V9 bölgeye özel eşyalar: yalnız kendi bölgesindeki zombilerden düşer (combat.rs region_drop), tariflerde kullanılır
    item!("frozen_fish", "Donmuş Balık", Consumable, Rare, 10, 40, "Donmuş Liman'dan. 14 saniye boyunca her saniye 3 can verir.", 0.0, 0.0),
    item!("ice_shard", "Buz Kristali", Material, Rare, 30, 60, "Donmuş Liman'dan. Askerî zırh, askerî çanta ve muska yapımında kullanılır."),
    item!("scorched_resin", "Yanık Reçine", Material, Rare, 30, 45, "Yanık Orman'dan. Hız İksiri yapımında kullanılır."),
    item!("charcoal", "Kömür", Material, Common, 30, 30, "Yanık Orman'dan. Güç İksiri yapımında kullanılır."),
    item!("scorpion_venom", "Akrep Zehri", Material, Rare, 20, 70, "Çöl Otoyolu'ndan. Hız İksiri yapımında kullanılır."),
    item!("dried_hide", "Kurutulmuş Deri", Material, Common, 30, 35, "Çöl Otoyolu'ndan. İlk Yardım Kiti yapımında kullanılır."),
    item!("swamp_moss", "Bataklık Yosunu", Material, Common, 30, 30, "Kara Bataklık'tan. İlk Yardım Kiti ve Koruyucu Muska yapımında kullanılır."),
    item!("bog_iron", "Bataklık Demiri", Material, Rare, 30, 65, "Kara Bataklık'tan. Katana, Rün Yüzüğü, askerî zırh, demir sandık ve araba gövdesi yapımında kullanılır."),
    item!("circuit_board", "Askerî Devre Kartı", Material, Epic, 20, 120, "Cephe Hattı'ndaki askerlerden. Askerî takım ve sandık yapımında kullanılır."),
    item!("powder_pouch", "Barut Kesesi", Material, Rare, 20, 80, "Cephe Hattı'ndaki askerlerden. Güç İksiri yapımında kullanılır."),
    item!("gold_watch", "Altın Saat", Material, Epic, 10, 250, "Eski şehirden. Rün Yüzüğü yapımında kullanılır ya da tüccara iyi fiyata satılır."),
    item!("rusty_key", "Paslı Anahtar", Material, Rare, 10, 90, "Eski şehirden. Askerî Sandık yapımında kullanılır."),
    // aletler: yalnız elde tutulunca işe yarar (taş 50, demir 100 vuruş; yumruk 25) ve gerçek alet olarak görünür
    // (armor = ağaç/kaya başına vuruş gücü; yumruk 25)
    item!("t_axe_stone", "Taş Balta", Weapon, Common, 1, 30, "Elinde tutunca ağaca her vuruşta 50 can götürür (yumruk 25), zombiye de vurur. Çantada dururken işe yaramaz.", 50.0, 0.0),
    item!("t_axe_iron", "Demir Balta", Weapon, Rare, 1, 120, "Elinde tutunca ağaca her vuruşta 100 can götürür (yumruk 25), zombiye de vurur. Çantada dururken işe yaramaz.", 100.0, 0.0),
    item!("t_pick_stone", "Taş Kazma", Weapon, Common, 1, 30, "Elinde tutunca kayaya her vuruşta 50 can götürür (yumruk 25), zombiye de vurur. Çantada dururken işe yaramaz.", 50.0, 0.0),
    item!("t_pick_iron", "Demir Kazma", Weapon, Rare, 1, 120, "Elinde tutunca kayaya her vuruşta 100 can götürür (yumruk 25), zombiye de vurur. Çantada dururken işe yaramaz.", 100.0, 0.0),
    // V11 araç parçaları: Tamirci Viktor'a verilir, araç belirli sürede üretilip postayla gelir
    item!("v_wheel", "Tekerlek", Material, Rare, 8, 60, "Araç parçası. Motosiklete 2, arabaya 4 gerekir."),
    item!("v_engine", "Motor", Material, Epic, 2, 250, "Araç parçası. Her araca 1 gerekir."),
    item!("v_frame_moto", "Motosiklet Gövdesi", Material, Rare, 1, 150, "Araç parçası: motosikletin gövdesi. Parçaları Tamirci Viktor'a götür, aracını o yapar."),
    item!("v_frame_car", "Araba Gövdesi", Material, Epic, 1, 400, "Araç parçası: arabanın gövdesi. Parçaları Tamirci Viktor'a götür, aracını o yapar."),
    // V11 araç anahtarları: kullanınca aracın yanına gelir (eskisi kaybolur); dünyada artık sahipsiz araç yok
    item!("veh_moto", "Motosiklet", Tool, Epic, 1, 0, "Kullanınca motosikletin yanına gelir; araç yuvasına takarsan çantanın yanındaki tuşla binersin. 200 can; sürerken 30 sn'de %1 aşınır, hasarın %70'ini o alır. Patlarsa 60 sn sonra yeniden binilir."),
    item!("veh_car", "Araba", Tool, Legendary, 1, 0, "Kullanınca araban yanına gelir; araç yuvasına takarsan çantanın yanındaki tuşla binersin. 500 can; sürerken 60 sn'de %1 aşınır, hasarın hepsini o alır. Patlarsa 60 sn sonra yeniden binilir."),
    // V11 etkinlik silahları (3. sıra ödülü; kaskların yerine)
    item!("ev_blood_weapon", "Kan Ayı Orağı", Weapon, Seasonal, 1, 0, "Kan Ayı etkinliğinde 3. sırada bitirerek elde edilir. Geniş kızıl kesik, birden çok düşmana vurur. Sınırlı sürelidir (30 gün), takas edilemez."),
    item!("ev_titan_weapon", "Titan Kırıcı Balyoz", Weapon, Seasonal, 1, 0, "Titan Avı etkinliğinde 3. sırada bitirerek elde edilir. Yavaş ama sersemleten ağır darbe. Sınırlı sürelidir (30 gün), takas edilemez."),
    item!("ev_base_weapon", "Fırtına Baltası", Weapon, Seasonal, 1, 0, "Üs Fırtınası etkinliğinde 3. sırada bitirerek elde edilir. Dengeli, sert vuruşlu savaş baltası. Sınırlı sürelidir (30 gün), takas edilemez."),
    item!("ev_scrap_weapon", "Hurdacı Testere Kılıcı", Weapon, Seasonal, 1, 0, "Hurdacılar etkinliğinde 3. sırada bitirerek elde edilir. Hızlı dönen dişli bıçak. Sınırlı sürelidir (30 gün), takas edilemez."),
    item!("ev_pack_weapon", "Sürü Lideri Pençesi", Weapon, Seasonal, 1, 0, "Sürü Lideri etkinliğinde 3. sırada bitirerek elde edilir. Çok hızlı pençe darbeleri. Sınırlı sürelidir (30 gün), takas edilemez."),
    item!("ev_breath_weapon", "Son Nefes Tırpanı", Weapon, Seasonal, 1, 0, "Son Nefes etkinliğinde 3. sırada bitirerek elde edilir. Uzun menzilli, önündeki her şeyi biçer. Sınırlı sürelidir (30 gün), takas edilemez."),
    // V12.1 (sona eklenir: kayıtlar eşyayı sırasıyla tutar)
    item!("workbench", "Tezgâh", Bench, Rare, 3, 80, "Kullan: bulunduğun yere kurulur. Kişisel üssünde kalıcıdır, başka yerde 10 dakika durur. Gelişmiş tarifler tezgâhta üretilir."),
    // V15 karakter sistemi
    item!("char_card", "Karakter Kartı", Consumable, Epic, 99, 0, "Kullanınca 4 karakterden birinin kartı çıkar (Sıradan %55, Nadir %28, Destansı %13, Efsanevi %4). İlk kart karakteri açar, her 25 kart seviye atlatır."),
    // V16 3B silahlar (sona eklenir)
    item!("w_uzi", "Uzi", Weapon, Epic, 1, 700, "Çok hızlı ateş eden kısa namlulu hafif makineli. Yakın mesafede yıkıcı, uzakta dağılır. 32 mermilik şarjör."),
    item!("w_hatchet", "El Baltası", Weapon, Common, 1, 40, "Tek elle savrulan hafif balta. Hızlı ve isabetli kısa darbeler."),
    item!("w_handaxe", "Savaş Baltası", Weapon, Rare, 1, 180, "Çift ağızlı ağır savaş baltası. Güçlü, geniş açılı yarma."),
    item!("w_effigy", "Lanetli Taş Tokmak", Weapon, Epic, 1, 800, "Eski bir putun taşından yontulmuş ağır tokmak. Sersemletir, vurduğu yerde ruh alevleri saçar."),
];

/// Malzemenin nereden çıktığı (üretim ekranında malzemenin altında yazar).
pub fn item_source(id: &str) -> &'static str {
    match id {
        "wood" => "Ağaç kes (balta ile hızlı)",
        "stone" => "Kaya kır (kazma ile hızlı)",
        "cloth" => "Zombiler, sandıklar, tüccar",
        "leather" => "Köpek zombiler, sandıklar",
        "scrap" => "Tank zombiler, kayalar, sandıklar",
        "bone" => "Zombiler",
        "resin" | "rope" => "Başlangıç Ormanı (zombiler, sandıklar, çamlar)",
        "electronics" | "gear" | "battery" | "gold_watch" | "rusty_key" => "Yıkık Şehir (zombiler, sandıklar)",
        "charcoal" | "scorched_resin" => "Yanık Orman (zombiler, sandıklar)",
        "dried_hide" | "fuel" | "scorpion_venom" => "Çöl Otoyolu (zombiler, sandıklar)",
        "swamp_moss" | "toxic" | "bog_iron" => "Kara Bataklık (zombiler, sandıklar)",
        "frozen_fish" | "ice_shard" | "dark_crystal" => "Donmuş Liman (zombiler, sandıklar)",
        "powder_pouch" | "circuit_board" => "Cephe Hattı (askerler, üs sandıkları)",
        "titan_shard" => "Titan (her 15 dakikada başka bir bölgede uyanır)",
        "bandage" | "energy" | "food" => "Sandıklar, tüccar",
        "medkit" => "Tezgâh, sandıklar, tüccar",
        "v_wheel" | "v_engine" | "v_frame_moto" | "v_frame_car" => "Tezgâh (Araç sekmesi)",
        _ => "Üret ya da sandıklar",
    }
}

/// Üretim sekmesi: 0 şifa, 1 iksir, 2 silah ve alet, 3 ekipman, 4 takı, 5 malzeme, 6 araç, 7 üs
pub fn recipe_tab(out: &str) -> u8 {
    match out {
        "bandage" | "medkit" | "potion_heal" | "antidote" => 0,
        "potion_speed" | "potion_power" => 1,
        o if o.starts_with("w_") || o.starts_with("t_") => 2,
        o if ["helm_", "jacket_", "boots_", "bag_", "pants_"].iter().any(|p| o.starts_with(p)) => 3,
        o if o.starts_with("ring_") || o.starts_with("neck_") => 4,
        o if o.starts_with("v_") => 6,
        o if o.starts_with("chest_") || o == "workbench" => 7,
        _ => 5,
    }
}

/// Tamirci Viktor'un araç üretimi: (anahtar, ad, parçalar, süre dk)
pub const VEHICLE_BUILDS: &[(&str, &str, &[(&str, u32)], u32)] = &[
    ("veh_moto", "Motosiklet", &[("v_frame_moto", 1), ("v_wheel", 2), ("v_engine", 1)], 10),
    ("veh_car", "Araba", &[("v_frame_car", 1), ("v_wheel", 4), ("v_engine", 1), ("battery", 2)], 30),
];

/// Oyundan kaldırılan eşyalar (ateşli silahlar, mermiler): hile panelinde de listelenmez.
/// V8: kask, ceket, bot, çanta ve pelerin çanta ekipman slotlarıyla geri döndü.
/// V11: vida motor üretiminde kullanılır (sandıklardan); pelerin slotu kalktığı için pelerin listede yok.
/// V16: 3B silahlarla tabanca, AK-47, keskin nişancı, roketatar ve sopa geri geldi (yedek mermisiz; yalnız şarjör).
pub const RETIRED_ITEMS: &[&str] = &["w_baton", "w_m4", "ammo_", "powder", "cloak",
    // V12.1 eşya temizliği: tekrarlı ara malzemeler (RETIRE_INTO ile dönüşür)
    "screw", "glue", "tape", "nails", "plastic", "spring", "copper", "tooth",
    // etkinlik kaskları yerine etkinlik silahları geldi (elde olanlar süresi dolana dek çalışır)
    "ev_blood_helm", "ev_titan_helm", "ev_base_helm", "ev_scrap_helm", "ev_pack_helm", "ev_breath_helm",
    // V17: El Baltası kalktı (Taş Balta ile karışıyordu); elde olan bıçağa döner (RETIRE_INTO)
    "w_hatchet"];

/// V15: oyundan kalkan eşyanın altın karşılığı (değerinin 2 katı; değeri olmayan etkinlik kaskı ve pelerin sabit)
pub fn retired_refund(id: &str) -> u32 {
    if id.starts_with("ev_") && id.ends_with("_helm") {
        return 2000;
    }
    if id == "cloak" {
        return 150;
    }
    item_index(id).map_or(0, |i| (ITEMS[i].value * 2).max(1))
}

/// Eşya şu an oyunda elde edilebilir mi
pub fn item_in_game(id: &str) -> bool {
    !RETIRED_ITEMS.iter().any(|r| if r.ends_with('_') { id.starts_with(r) } else { id == *r })
}

/// Hesaba bağlı eşya (etkinlik ödülleri): takas edilemez, yere bırakılamaz
pub fn item_bound(id: &str) -> bool {
    id.starts_with("ev_")
}

/// Elle yazılmış eşya kimliğini çözer (yönetici postası: "Medkit", "ilk yardım kiti", " medkit "): önce birebir,
/// sonra küçük harf / boşluk→alt çizgi, en son görünen adla (büyük-küçük harf duyarsız).
pub fn resolve_item_id(raw: &str) -> Option<&'static str> {
    let t = raw.trim();
    if let Some(i) = item_index(t) {
        return Some(ITEMS[i].id);
    }
    let low = t.to_lowercase().replace(' ', "_");
    if let Some(i) = item_index(&low) {
        return Some(ITEMS[i].id);
    }
    let name = t.to_lowercase();
    ITEMS.iter().find(|d| d.name.to_lowercase() == name).map(|d| d.id)
}

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
        stun: 0.15,
        sprite: "club",
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
        damage: 13.0,
        rate: 3.5,
        range_m: 12.0,
        ammo: "",
        mag: 15,
        reload: 1.1,
        spread: 0.05,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "glock",
    },
    WeaponDef {
        item: "w_ak47",
        class: WeaponClass::Rifle,
        damage: 13.0,
        rate: 6.5,
        range_m: 17.0,
        ammo: "",
        mag: 30,
        reload: 2.0,
        spread: 0.08,
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
        damage: 85.0,
        rate: 0.7,
        range_m: 32.0,
        ammo: "",
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
        damage: 150.0,
        rate: 0.35,
        range_m: 24.0,
        ammo: "",
        mag: 1,
        reload: 2.6,
        spread: 0.0,
        pierce: 0,
        splash_m: 3.0,
        stun: 0.0,
        sprite: "rpg",
    },
    WeaponDef {
        item: "w_knife",
        class: WeaponClass::Melee,
        // V19: savuruş animasyonu görünür olsun diye hız 2,8 → 1,8; saniyedeki hasar aynı kalsın diye vuruş 17 → 26
        damage: 26.0,
        rate: 1.8,
        range_m: 1.5,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "knife",
    },
    WeaponDef {
        item: "w_katana",
        class: WeaponClass::Melee,
        damage: 33.0,
        rate: 1.5,
        range_m: 2.3,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 2,
        splash_m: 0.0,
        stun: 0.05,
        sprite: "katana",
    },
    // etkinlik silahları: görsel gelene dek katana / bıçak çizimiyle (istemler: docs/28_GORSEL_PROMPTLARI_V19.md)
    WeaponDef {
        item: "ev_blood_weapon",
        class: WeaponClass::Melee,
        damage: 38.0,
        rate: 1.7,
        range_m: 2.5,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 2,
        splash_m: 0.0,
        stun: 0.05,
        sprite: "katana",
    },
    WeaponDef {
        item: "ev_titan_weapon",
        class: WeaponClass::Melee,
        damage: 54.0,
        rate: 1.1,
        range_m: 2.2,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 2,
        splash_m: 0.0,
        stun: 0.3,
        sprite: "katana",
    },
    WeaponDef {
        item: "ev_base_weapon",
        class: WeaponClass::Melee,
        damage: 44.0,
        rate: 1.4,
        range_m: 2.2,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 2,
        splash_m: 0.0,
        stun: 0.1,
        sprite: "katana",
    },
    WeaponDef {
        item: "ev_scrap_weapon",
        class: WeaponClass::Melee,
        damage: 24.0,
        rate: 3.0,
        range_m: 1.8,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 1,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "knife",
    },
    WeaponDef {
        item: "ev_pack_weapon",
        class: WeaponClass::Melee,
        damage: 21.0,
        rate: 3.6,
        range_m: 1.6,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 1,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "knife",
    },
    WeaponDef {
        item: "ev_breath_weapon",
        class: WeaponClass::Melee,
        damage: 40.0,
        rate: 1.4,
        range_m: 2.9,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 3,
        splash_m: 0.0,
        stun: 0.05,
        sprite: "katana",
    },
    // V16 3B silahlar (sona eklenir: çizim kodu dizini kullanır)
    WeaponDef {
        item: "w_uzi",
        class: WeaponClass::Rifle,
        damage: 8.0,
        rate: 11.0,
        range_m: 10.0,
        ammo: "",
        mag: 32,
        reload: 1.5,
        spread: 0.14,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "uzi",
    },
    WeaponDef {
        item: "w_hatchet",
        class: WeaponClass::Melee,
        damage: 20.0,
        rate: 2.4,
        range_m: 1.7,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "hatchet",
    },
    WeaponDef {
        item: "w_handaxe",
        class: WeaponClass::Melee,
        damage: 27.0,
        rate: 1.8,
        range_m: 1.9,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 1,
        splash_m: 0.0,
        stun: 0.05,
        sprite: "handaxe",
    },
    WeaponDef {
        item: "w_effigy",
        class: WeaponClass::Melee,
        damage: 36.0,
        rate: 1.2,
        range_m: 2.0,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 1,
        splash_m: 0.0,
        stun: 0.3,
        sprite: "effigy",
    },
    WeaponDef {
        item: "t_axe_stone",
        class: WeaponClass::Melee,
        damage: 16.0,
        rate: 1.9,
        range_m: 1.7,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.0,
        sprite: "hatchet",
    },
    WeaponDef {
        item: "t_axe_iron",
        class: WeaponClass::Melee,
        damage: 24.0,
        rate: 1.7,
        range_m: 1.8,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.04,
        sprite: "handaxe",
    },
    WeaponDef {
        item: "t_pick_stone",
        class: WeaponClass::Melee,
        damage: 18.0,
        rate: 1.4,
        range_m: 1.8,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.06,
        sprite: "pickaxe",
    },
    WeaponDef {
        item: "t_pick_iron",
        class: WeaponClass::Melee,
        damage: 26.0,
        rate: 1.25,
        range_m: 1.9,
        ammo: "",
        mag: 0,
        reload: 0.0,
        spread: 0.0,
        pierce: 0,
        splash_m: 0.0,
        stun: 0.1,
        sprite: "pickaxe",
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

/// V13 bölge yaratıkları: temel türün davranışını (yetenekler, sürü, zehir…) kullanır, kendi görseli ve çarpanları var.
pub struct Species {
    pub id: &'static str,
    pub name: &'static str,
    pub base: ZKind,
    /// areas::AREAS kimliği
    pub area: &'static str,
    pub hp: f32,
    pub dmg: f32,
    pub speed: f32,
    pub xp: f32,
}

macro_rules! sp {
    ($id:expr, $name:expr, $base:ident, $area:expr, $hp:expr, $dmg:expr, $speed:expr, $xp:expr) => {
        Species { id: $id, name: $name, base: ZKind::$base, area: $area, hp: $hp, dmg: $dmg, speed: $speed, xp: $xp }
    };
}

/// Sıra kayıt/ağ biçiminin parçası (zombinin `species` alanı = indeks + 1): yalnız sona eklenir.
pub const SPECIES: &[Species] = &[
    sp!("lumberjack", "Oduncu Zombi", Walker, "forest", 1.25, 1.15, 0.95, 1.2),
    sp!("wolf", "Kuduz Kurt", Dog, "forest", 1.0, 1.0, 1.05, 1.0),
    sp!("shroom", "Mantar Zombi", Toxic, "forest", 1.1, 0.9, 0.85, 1.1),
    sp!("office", "Ofis Zombisi", Walker, "city", 1.0, 1.0, 1.0, 1.0),
    sp!("riot", "Çevik Kuvvet Zombisi", Tank, "city", 0.85, 0.9, 1.1, 0.9),
    sp!("crawler", "Sürüngen", Runner, "city", 0.9, 1.1, 1.1, 1.1),
    sp!("charred", "Kömürleşmiş Zombi", Walker, "burnt", 1.3, 1.25, 1.0, 1.3),
    sp!("emberhound", "Kor Tazısı", Dog, "burnt", 1.2, 1.25, 1.1, 1.3),
    sp!("ashwraith", "Kül Hayaleti", Dark, "burnt", 1.0, 1.15, 1.05, 1.2),
    sp!("mummy", "Mumya", Walker, "desert", 1.45, 1.2, 0.85, 1.35),
    sp!("scorpion", "Dev Akrep", Runner, "desert", 1.2, 1.3, 1.0, 1.3),
    sp!("biker", "Motorcu Zombi", Runner, "desert", 1.35, 1.2, 1.05, 1.35),
    sp!("bloater", "Şişkin", Toxic, "bswamp", 1.5, 1.1, 0.8, 1.4),
    sp!("boggiant", "Bataklık Devi", Tank, "bswamp", 1.3, 1.25, 0.9, 1.5),
    sp!("witch", "Bataklık Cadısı", Dark, "bswamp", 1.1, 1.35, 1.0, 1.5),
    sp!("sailor", "Donmuş Denizci", Walker, "harbor", 1.5, 1.3, 1.0, 1.5),
    sp!("icecrab", "Buz Yengeci", Runner, "harbor", 1.5, 1.2, 0.95, 1.5),
    sp!("dockbrute", "Rıhtım Canavarı", Tank, "harbor", 1.45, 1.35, 0.95, 1.7),
];

/// V17: bölge başına iki imza yaratık kalır (oyuncu her bölgede az ama akılda kalan düşman görür); kalkanlar artık
/// doğmaz, kayıt/ağ biçimi bozulmasın diye tabloda durur
pub const RETIRED_SPECIES: &[&str] = &["shroom", "crawler", "ashwraith", "biker", "witch", "dockbrute"];

/// Tür hâlâ doğuyor mu
pub fn species_active(s: &Species) -> bool {
    !RETIRED_SPECIES.contains(&s.id)
}

/// zombinin tür çarpanları (species 0: klasik zombi)
pub fn species(s: u8) -> Option<&'static Species> {
    if s == 0 { None } else { SPECIES.get(s as usize - 1) }
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

/// V12.1: her tarif tek (aynı eşyanın ikinci tarifi yok). El üretimi (bench: false) yalnız temel malzemeler ve
/// Başlangıç Ormanı eşyalarıyla; tezgâh tarifleri (bench: true) bölgelere özel malzemeler ister. El üretimi ekranında
/// tezgâh tarifleri görünmez, tezgâhta da el tarifleri.
macro_rules! r {
    ($out:expr, $n:expr, [$(($i:expr, $c:expr)),*], $bench:expr) => {
        Recipe { out: $out, count: $n, inputs: &[$(($i, $c)),*], bench: $bench }
    };
}

pub const RECIPES: &[Recipe] = &[
    // ---- el üretimi: temel (odun, taş, kumaş, deri, hurda, kemik) + orman (reçine, ip)
    r!("bandage", 1, [("cloth", 2), ("resin", 1)], false),
    r!("potion_heal", 1, [("bandage", 1), ("resin", 2)], false),
    r!("w_knife", 1, [("scrap", 3), ("leather", 1), ("rope", 1)], false),
    r!("w_bat", 1, [("wood", 8), ("scrap", 2)], false),
    r!("t_axe_stone", 1, [("wood", 6), ("stone", 4), ("rope", 1)], false),
    r!("t_pick_stone", 1, [("wood", 6), ("stone", 6), ("rope", 1)], false),
    r!("helm_basic", 1, [("scrap", 4), ("leather", 1), ("cloth", 1)], false),
    r!("jacket_basic", 1, [("cloth", 6), ("leather", 2)], false),
    r!("pants_basic", 1, [("cloth", 5), ("leather", 1)], false),
    r!("boots_basic", 1, [("leather", 3), ("cloth", 2)], false),
    r!("bag_basic", 1, [("cloth", 6), ("leather", 2), ("rope", 2)], false),
    r!("ring_copper", 1, [("scrap", 3), ("bone", 2)], false),
    r!("neck_bone", 1, [("bone", 6), ("rope", 1)], false),
    r!("workbench", 1, [("wood", 20), ("stone", 10), ("scrap", 4), ("rope", 2)], false),
    // ---- tezgâh: şifa ve iksir
    r!("medkit", 2, [("bandage", 2), ("swamp_moss", 1), ("dried_hide", 1)], true),
    r!("antidote", 1, [("toxic", 2), ("resin", 1)], true),
    r!("potion_speed", 2, [("scorpion_venom", 1), ("scorched_resin", 2)], true),
    r!("potion_power", 2, [("charcoal", 3), ("powder_pouch", 1)], true),
    // ---- tezgâh: silah ve alet
    r!("w_katana", 1, [("scrap", 10), ("leather", 2), ("bog_iron", 2), ("dark_crystal", 1)], true),
    r!("w_handaxe", 1, [("wood", 4), ("scrap", 10), ("leather", 2), ("gear", 1)], true),
    r!("w_effigy", 1, [("stone", 30), ("dark_crystal", 2), ("bone", 10)], true),
    r!("w_glock", 1, [("scrap", 12), ("gear", 2), ("leather", 1)], true),
    r!("t_axe_iron", 1, [("wood", 4), ("scrap", 8), ("leather", 1), ("gear", 1)], true),
    r!("t_pick_iron", 1, [("wood", 4), ("scrap", 10), ("leather", 1), ("gear", 1)], true),
    // ---- tezgâh: polis takımı (şehir + çöl)
    r!("helm_police", 1, [("scrap", 8), ("leather", 2), ("gear", 1)], true),
    r!("jacket_police", 1, [("cloth", 10), ("leather", 4), ("dried_hide", 2)], true),
    r!("pants_police", 1, [("cloth", 8), ("leather", 3), ("dried_hide", 1)], true),
    r!("boots_police", 1, [("leather", 6), ("dried_hide", 2), ("scrap", 2)], true),
    r!("bag_police", 1, [("cloth", 10), ("leather", 6), ("rope", 4), ("battery", 1)], true),
    // ---- tezgâh: askerî takım (bataklık + liman + cephe)
    r!("helm_military", 1, [("scrap", 14), ("bog_iron", 3), ("circuit_board", 1)], true),
    r!("jacket_military", 1, [("cloth", 14), ("scrap", 8), ("ice_shard", 2), ("circuit_board", 1)], true),
    r!("pants_military", 1, [("cloth", 12), ("bog_iron", 2), ("ice_shard", 1)], true),
    r!("boots_military", 1, [("leather", 8), ("bog_iron", 2), ("scorched_resin", 2)], true),
    r!("bag_military", 1, [("cloth", 16), ("leather", 10), ("rope", 6), ("ice_shard", 2)], true),
    // ---- tezgâh: Titan takımı (Titan + liman)
    r!("helm_titan", 1, [("titan_shard", 3), ("scrap", 20), ("dark_crystal", 2)], true),
    r!("jacket_titan", 1, [("titan_shard", 4), ("leather", 12), ("dark_crystal", 2)], true),
    r!("pants_titan", 1, [("titan_shard", 3), ("cloth", 12), ("dark_crystal", 1)], true),
    r!("boots_titan", 1, [("titan_shard", 2), ("leather", 10), ("dark_crystal", 1)], true),
    // ---- tezgâh: takı
    r!("ring_hunter", 1, [("gold_watch", 1), ("bog_iron", 2)], true),
    r!("ring_titan", 1, [("titan_shard", 2), ("dark_crystal", 2), ("gold_watch", 1)], true),
    r!("neck_amulet", 1, [("ice_shard", 3), ("swamp_moss", 2)], true),
    r!("neck_dark", 1, [("dark_crystal", 3), ("bone", 10), ("ice_shard", 2)], true),
    // ---- tezgâh: araç parçaları
    r!("v_wheel", 1, [("scrap", 4), ("leather", 2), ("rope", 1)], true),
    r!("v_engine", 1, [("scrap", 10), ("gear", 4), ("electronics", 2), ("fuel", 2)], true),
    r!("v_frame_moto", 1, [("scrap", 12), ("gear", 1), ("leather", 2)], true),
    r!("v_frame_car", 1, [("scrap", 30), ("gear", 3), ("battery", 1), ("bog_iron", 2)], true),
    // ---- tezgâh: üs
    r!("chest_wood", 1, [("wood", 30), ("rope", 2)], true),
    r!("chest_iron", 1, [("scrap", 16), ("wood", 10), ("bog_iron", 2)], true),
    r!("chest_military", 1, [("scrap", 20), ("rusty_key", 1), ("circuit_board", 1)], true),
];

/// Bölgeye özel malzemeler (zombi ve sandık ganimeti; ağırlık): her bölgeden yalnız kendi eşyaları çıkar.
/// Temel malzemeler (odun, taş, kumaş, deri, hurda, kemik) her yerde.
pub fn region_items(zone: crate::world::Zone) -> &'static [(&'static str, u32)] {
    use crate::world::Zone;
    match zone {
        Zone::Camp | Zone::Forest | Zone::DeepForest | Zone::Swamp => &[("resin", 3), ("rope", 2)],
        Zone::SafeCity | Zone::Suburb | Zone::Downtown | Zone::Industrial | Zone::BossArena =>
            &[("electronics", 3), ("gear", 3), ("battery", 2), ("rusty_key", 1), ("gold_watch", 1)],
        Zone::BurntForest => &[("charcoal", 3), ("scorched_resin", 2)],
        Zone::Desert => &[("dried_hide", 3), ("fuel", 2), ("scorpion_venom", 2)],
        Zone::BlackSwamp => &[("swamp_moss", 3), ("toxic", 3), ("bog_iron", 2)],
        Zone::Harbor => &[("frozen_fish", 3), ("ice_shard", 2), ("dark_crystal", 1)],
        // Cephe Hattı: askerler ve üs sandıkları
        Zone::Front | Zone::Military => &[("powder_pouch", 2), ("circuit_board", 1)],
        _ => &[],
    }
}

/// V12.1'de oyundan kalkan malzemeler eski kayıtlarda bunlara dönüşür (1'e 1)
pub const RETIRE_INTO: &[(&str, &str)] = &[
    ("w_hatchet", "w_knife"),
    ("screw", "scrap"), ("nails", "scrap"), ("spring", "scrap"), ("copper", "scrap"), ("plastic", "scrap"),
    ("glue", "resin"), ("tape", "cloth"), ("tooth", "bone"), ("powder", "charcoal"),
];

// --------------------------------------------------------------------------- tüccar
/// (eşya, fiyat, gereken seviye)
pub const MERCHANT: &[(&str, u32, u32)] = &[
    ("bandage", 15, 1),
    ("food", 10, 1),
    ("energy", 18, 1),
    ("cloth", 6, 1),
    ("rope", 8, 1),
    ("w_knife", 120, 1),
    ("w_bat", 60, 1),
    ("medkit", 60, 5),
    ("antidote", 35, 6),
    ("potion_heal", 80, 3),
    ("potion_speed", 120, 6),
    ("potion_power", 180, 12),
    ("fuel", 25, 8),
    ("w_katana", 1800, 10),
    ("w_glock", 450, 5),
    ("w_handaxe", 700, 8),
    ("w_uzi", 1500, 12),
    ("w_effigy", 2400, 15),
    ("w_ak47", 2600, 18),
    ("w_sniper", 4200, 25),
    ("w_rpg", 6500, 32),
];

// --------------------------------------------------------------------------- karakterler
pub struct CharDef {
    pub id: &'static str,
    pub name: &'static str,
    pub class: &'static str,
    /// 0 Sıradan, 1 Nadir, 2 Destansı, 3 Efsanevi
    pub rarity: u8,
    pub desc: &'static str,
    pub unlock_gold: u32,
    pub unlock_level: u32,
    pub ability: &'static str,
    pub ability_desc: &'static str,
    /// seviye 1-10 özellikleri (1. seviye karakter açılınca)
    pub perks: [&'static str; 10],
}

/// V15: 4 karakter; kartla açılır ve gelişir (her 25 kart +1 seviye, en çok 10). Savaşçı başlangıçta açık.
/// V16: 3B modeller (kael, lyra, vex, morvath); kimlikler kayıtlarla uyum için eski kalır.
pub const CHARACTERS: &[CharDef] = &[
    CharDef { id: "kaan", name: "Kael", class: "Gölge Gezgin", rarity: 0,
              desc: "Sürgün gölge suikastçısı. Çevik ve sessiz; yumruk ve kılıçla dövüşür, taklayla kaçar.", unlock_gold: 0, unlock_level: 1,
              ability: "Takla", ability_desc: "Bakılan yöne yuvarlanır, yuvarlanırken hasar almaz. 5 sn bekleme.",
              perks: ["Takla", "Yumruk hasarı +%10", "Hasardan kaçınma +%5", "Hareket hızı +%10", "Takladan sonra 1 sn hasar almama",
                      "Can +50", "Yumruk hasarı +%15", "Takla bekleme −%25", "Öldürünce canın %5'i yenilenir", "Karanlık Aura: yakındaki düşmanlara saniyede 20 hasar"] },
    CharDef { id: "tracker", name: "Lyra", class: "Engerek Silahşor", rarity: 1,
              desc: "Çorak toprakların hızlı avcısı. Bıçak ve ateşli silahlarda usta, kritik vuruşlarla düşmanın arkasına geçer.", unlock_gold: 0, unlock_level: 1,
              ability: "Gölge Atılımı", ability_desc: "İleri atılır; 3 sn boyunca vuruşları %40 daha sert. 7 sn bekleme.",
              perks: ["Gölge Atılımı", "Kılıç/bıçak hasarı +%10", "Kritik şansı +%3", "Hareket hızı +%8", "Atılım bekleme −%20",
                      "Can +40", "Kritik hasarı +%25", "Saldırı hızı +%10", "Hasardan kaçınma +%5", "Avcı İçgüdüsü: kritik hasarı +%50"] },
    CharDef { id: "guardian", name: "Vex", class: "Demir Dev", rarity: 2,
              desc: "Ağır zırhlı dev. Hasarı göğüsler; çekici ve baltasıyla çevresindekileri sersemletir.", unlock_gold: 0, unlock_level: 1,
              ability: "Kalkan Duvarı", ability_desc: "Canının %30'u kadar kalkan (4 sn), çevredeki düşmanları sersemletir. 20 sn bekleme.",
              perks: ["Kalkan Duvarı", "Can +60", "Alınan hasar −%5", "Hasar yansıtma +%5", "Patlama direnci +%20",
                      "Can +100", "Saniyede +1 can yenilenmesi", "Alınan hasar −%5", "Sersemletme şansı +%10", "Demir İrade: can +250"] },
    CharDef { id: "shadow", name: "Morvath", class: "Veba Simyacısı", rarity: 3,
              desc: "Buz, kan ve boşluk büyüsüyle dövüşen gizemli simyacı. Tırpanıyla kombo yapar, vurduğu her darbeyle can çalar.", unlock_gold: 0, unlock_level: 1,
              ability: "Kan Yemini", ability_desc: "6 sn boyunca vuruşlar hasarın %15'i kadar can çalar, saldırı hızı +%30. 25 sn bekleme.",
              perks: ["Kan Yemini", "Kılıç hasarı +%12", "Can çalma +%2", "Saldırı hızı +%8", "Kritik şansı +%4",
                      "Can +50", "Kılıç hasarı +%15", "Can çalma +%3", "Hareket hızı +%8", "Kan Gölgesi: kritik hasarı +%40, can çalma +%3"] },
];

/// Karakter kartı: her 25 kart bir seviye; ilk kart karakteri açar
pub const CHAR_CARDS_PER_LEVEL: u32 = 25;
pub const CHAR_MAX_CARDS: u32 = 1 + 9 * CHAR_CARDS_PER_LEVEL;
/// Kart açma (altın): 1 kart, 10 kart (%20 indirimli)
pub const CHAR_PULL_ONE: u32 = 2500;
pub const CHAR_PULL_TEN: u32 = 20000;
/// Nadirliğe göre çıkma şansı (%) ve karakter en üst seviyedeyken kartın elmas karşılığı
pub const CHAR_ODDS: [u32; 4] = [55, 28, 13, 4];
pub const CHAR_DUPE_GEMS: [u32; 4] = [1, 3, 8, 20];
/// Karakter görevi: o karakterle günde bu kadar zombi → o karakterin 1 kartı
pub const CHAR_DAILY_KILLS: u32 = 40;

pub fn char_level_of(cards: u32) -> u32 {
    if cards == 0 {
        0
    } else {
        (1 + (cards - 1) / CHAR_CARDS_PER_LEVEL).min(10)
    }
}

/// Nadirlik zarı (0-99) → karakter
pub fn char_roll(r: u32) -> usize {
    let mut acc = 0;
    for (i, p) in CHAR_ODDS.iter().enumerate() {
        acc += p;
        if r < acc {
            return i;
        }
    }
    0
}

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

/// Kendi aracın: sürerken aşınma (her `VEH_WEAR_S` saniyede azami canın %1'i) ve patladıktan sonra yeniden çağırma
/// beklemesi (oyun süresi, sn). Sıra: [motosiklet, araba].
pub const VEH_WEAR_S: [f32; 2] = [30.0, 60.0];
pub const VEH_RECALL_S: [f64; 2] = [60.0, 60.0];
/// Sürücüye gelen hasarın araca giden payı (motosiklette sürücü açıkta)
pub const VEH_ABSORB: [f32; 2] = [0.7, 1.0];

/// Araç türü (VEHICLES indeksi) → kayıttaki can/bekleme yuvası (motosiklet 0, araba 1)
pub fn veh_slot(kind: usize) -> Option<usize> {
    match kind {
        1 => Some(0),
        2 => Some(1),
        _ => None,
    }
}

/// Araçlar. 0. kayıt (bisiklet) ve 3. kayıt (SUV) oyundan kaldırıldı: hiçbir yerde doğmaz, hileyle de gelmez;
/// yalnız ağ/çizim indeksleri (motor 1, araba 2) değişmesin diye yer tutucu olarak durur.
pub const REMOVED_VEHICLE: usize = 0;
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
        hp: 200.0,
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
    /// devlet üssü askeri etkisiz bırak
    Soldier,
    /// devlet üssü ele geçir
    BaseCap,
    /// kaya kır (kazma)
    Mine,
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
    QuestDef {
        id: "d_mine",
        title: "12 kaya kır",
        kind: QuestKind::Mine,
        goal: 12,
        gold: 90,
        xp: 150,
        pass_xp: 200,
    },
];

// --------------------------------------------------------------------------- sezon / pass
pub const PASS_TIERS: u32 = 30;
/// Kademe başı XP: 1-10 → 1.000, 11-20 → 2.000, 21-30 → 5.000 (toplam 80.000)
pub fn pass_tier_xp(tier: u32) -> u32 {
    match tier {
        0..=10 => 1000,
        11..=20 => 2000,
        _ => 5000,
    }
}
/// Bu kademeye ulaşmak için gereken toplam XP
pub fn pass_xp_for(tier: u32) -> u32 {
    (1..=tier.min(PASS_TIERS)).map(pass_tier_xp).sum()
}
/// Premium bilet: anında bu kadar kademe XP'si
pub const PASS_PREMIUM_XP: u32 = 10_000;

/// (kademe, ücretsiz ödül, premium ödül) — ödül biçimi: "eşya:adet" | "gold:n" | "gem:n" | "cos:kozmetik"
/// Elmas ödülleri sunucudan onaylanır (supabase _zx_pass_gems ile aynı tablo). Ücretsiz yol 150, premium 900 elmas;
/// premium yolda 30 karakter kartı, 25'te Kanlı Ay Kılıcı görünümü, 30'da Sezon 1 Kanatları.
pub const PASS_REWARDS: &[(u32, &str, &str)] = &[
    (1, "gold:500", "char_card:4"),
    (2, "bandage:5", "char_card:3"),
    (3, "food:5", "gem:40"),
    (4, "char_card:1", "potion_heal:5"),
    (5, "gem:15", "chest_iron:2"),
    (6, "gold:1000", "char_card:3"),
    (7, "medkit:3", "gem:60"),
    (8, "char_card:1", "potion_speed:5"),
    (9, "gold:1500", "char_card:4"),
    (10, "gem:25", "chest_military:1"),
    (11, "potion_heal:3", "gem:80"),
    (12, "char_card:1", "potion_power:5"),
    (13, "gold:2000", "char_card:4"),
    (14, "medkit:5", "gem:90"),
    (15, "gem:30", "chest_military:2"),
    (16, "char_card:2", "titan_shard:2"),
    (17, "gold:2500", "gem:100"),
    (18, "potion_speed:3", "char_card:4"),
    (19, "char_card:2", "cos:camo_s1"),
    (20, "gem:35", "chest_military:3"),
    (21, "gold:3000", "gem:120"),
    (22, "potion_power:3", "char_card:4"),
    (23, "char_card:2", "titan_shard:3"),
    (24, "gold:3500", "gem:130"),
    (25, "gem:45", "cos:skin_w_bloodmoon"),
    (26, "titan_shard:2", "char_card:4"),
    (27, "gold:4000", "gem:140"),
    (28, "char_card:3", "ring_titan:1"),
    (29, "medkit:5", "gem:140"),
    (30, "chest_military:3", "gem:200"),
];

/// Haftalık havuz: her hafta 4 tanesi seçilir.
pub const WEEKLY_POOL: &[QuestDef] = &[
    QuestDef { id: "w_kill", title: "300 Zombi Öldür", kind: QuestKind::Kill, goal: 300, gold: 800, xp: 1500, pass_xp: 1200 },
    QuestDef { id: "w_dark", title: "40 Karanlık Zombi Öldür", kind: QuestKind::KillType(4), goal: 40, gold: 900, xp: 1600, pass_xp: 1200 },
    QuestDef { id: "w_dog", title: "60 Zombi Köpek Öldür", kind: QuestKind::KillType(2), goal: 60, gold: 800, xp: 1400, pass_xp: 1000 },
    QuestDef { id: "w_soldier", title: "25 Asker Etkisiz Bırak", kind: QuestKind::Soldier, goal: 25, gold: 1200, xp: 2000, pass_xp: 1500 },
    QuestDef { id: "w_base", title: "2 Devlet Üssü Ele Geçir", kind: QuestKind::BaseCap, goal: 2, gold: 1500, xp: 2500, pass_xp: 2000 },
    QuestDef { id: "w_crate", title: "30 Sandık Aç", kind: QuestKind::Crate, goal: 30, gold: 700, xp: 1200, pass_xp: 1000 },
    QuestDef { id: "w_craft", title: "25 Eşya Üret", kind: QuestKind::Craft, goal: 25, gold: 700, xp: 1200, pass_xp: 1000 },
    QuestDef { id: "w_titan", title: "1 Titan Yen", kind: QuestKind::KillType(7), goal: 1, gold: 2000, xp: 3000, pass_xp: 2000 },
];

/// Sezon görevleri: sezon boyunca hepsi açık.
pub const SEASON_QUESTS: &[QuestDef] = &[
    QuestDef { id: "s_kill", title: "3.000 Zombi Öldür", kind: QuestKind::Kill, goal: 3000, gold: 3000, xp: 8000, pass_xp: 4000 },
    QuestDef { id: "s_titan", title: "10 Titan Yen", kind: QuestKind::KillType(7), goal: 10, gold: 3000, xp: 10000, pass_xp: 5000 },
    QuestDef { id: "s_base", title: "15 Devlet Üssü Ele Geçir", kind: QuestKind::BaseCap, goal: 15, gold: 3000, xp: 9000, pass_xp: 5000 },
    QuestDef { id: "s_soldier", title: "300 Asker Etkisiz Bırak", kind: QuestKind::Soldier, goal: 300, gold: 3000, xp: 9000, pass_xp: 4000 },
    QuestDef { id: "s_craft", title: "200 Eşya Üret", kind: QuestKind::Craft, goal: 200, gold: 3000, xp: 6000, pass_xp: 3000 },
    QuestDef { id: "s_crate", title: "300 Sandık Aç", kind: QuestKind::Crate, goal: 300, gold: 3000, xp: 6000, pass_xp: 3000 },
    QuestDef { id: "s_chop", title: "1.000 Ağaç Kes", kind: QuestKind::Chop, goal: 1000, gold: 3000, xp: 6000, pass_xp: 3000 },
    QuestDef { id: "s_level", title: "Seviye 30'a Ulaş", kind: QuestKind::Level, goal: 30, gold: 3000, xp: 0, pass_xp: 4000 },
];

/// Elmas Sandığı kaplamaları (kind: "char" karakter, "vehicle" araç; rarity 2 destansı, 3 efsanevi).
/// supabase zx_crate_open ile aynı liste.
pub struct SkinDef {
    pub id: &'static str,
    pub name: &'static str,
    pub kind: &'static str,
    pub rarity: u8,
    pub desc: &'static str,
}

pub const SKINS: &[SkinDef] = &[
    SkinDef { id: "sk_night", name: "Gece Avcısı", kind: "char", rarity: 3, desc: "Gece mavisi zırh, yıldız tozu izi bırakır." },
    SkinDef { id: "sk_blood", name: "Kan Ayı Savaşçısı", kind: "char", rarity: 3, desc: "Kızıl ayın lanetiyle kaplı zırh, kor parçacıkları saçar." },
    SkinDef { id: "sk_frost", name: "Buz Kurdu", kind: "char", rarity: 3, desc: "Buzul rüzgârıyla dövülmüş zırh, kar taneleri döker." },
    SkinDef { id: "sk_gold", name: "Altın Savaşçı", kind: "char", rarity: 3, desc: "Saf altın kaplama, her adımda ışıldar." },
    SkinDef { id: "sk_toxic", name: "Zehir Uzmanı", kind: "char", rarity: 2, desc: "Toksik bölgede hayatta kalanların yeşil zırhı." },
    SkinDef { id: "sk_shadow", name: "Gölge", kind: "char", rarity: 2, desc: "Karanlıkta kaybolan kömür siyahı zırh." },
    SkinDef { id: "vs_gold", name: "Altın Kaplama", kind: "vehicle", rarity: 3, desc: "Motor ve araban altın kaplanır, sürerken parıldar." },
    SkinDef { id: "vs_flame", name: "Alev Desenli", kind: "vehicle", rarity: 3, desc: "Kızgın alev boyası, egzozdan kıvılcım saçar." },
    SkinDef { id: "vs_camo", name: "Kamuflaj", kind: "vehicle", rarity: 2, desc: "Askerî kamuflaj boyası." },
    SkinDef { id: "vs_neon", name: "Neon", kind: "vehicle", rarity: 2, desc: "Gece parlayan neon boya." },
];

pub fn skin(id: &str) -> Option<&'static SkinDef> {
    SKINS.iter().find(|s| s.id == id)
}

/// Elmas Sandığı fiyatı (1 ve 10 açılış)
pub const CRATE_PRICE: u32 = 120;
pub const CRATE_PRICE10: u32 = 1080;

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
    // V15 sezon görünümleri; kanatlar oyuncuya seçtirilmez, yalnız yönetici paneli takar (V18: VIP kaldırıldı)
    Cosmetic { id: "skin_w_bloodmoon", name: "Kanlı Ay Kılıcı", kind: "weapon_skin" },
    Cosmetic { id: "camo_s1", name: "Sezon 1 Kamuflajı", kind: "camo" },
    Cosmetic { id: "wings_s1", name: "Sezon 1 Kanatları", kind: "wings" },
    Cosmetic { id: "wings_ash", name: "Kor Kanatları", kind: "wings" },
    Cosmetic { id: "wings_dragon", name: "Ejder Kanatları", kind: "wings" },
    Cosmetic { id: "wings_titan", name: "Titan Kanatları", kind: "wings" },
];

#[cfg(test)]
mod tests {
    #[allow(unused_imports)]
    use crate::Hyp;
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
                    "cos" => assert!(COSMETICS.iter().any(|c| c.id == r.split_once(':').unwrap().1), "{r}"),
                    _ => {
                        assert!(item_index(k).is_some(), "{r}");
                        assert!(!matches!(ITEMS[item_index(k).unwrap()].kind, ItemKind::Ammo | ItemKind::Cloak), "{r}");
                    }
                }
            }
        }
    }
}
