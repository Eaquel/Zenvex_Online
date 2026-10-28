//! Hikâye: NPC konuşmaları, rehberin öğretici görev zinciri (Sv 5'e kadar) ve görevcinin ana görev zinciri.
//! Görev bir NPC'den konuşarak alınır, hedef tamamlanınca aynı NPC'ye dönülüp teslim edilir.
use crate::data::{self, ZKind};
use crate::game::{Event, Game};
use crate::world::{self, Zone};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

/// NPC türleri (game::Npc.kind)
pub const N_MERCHANT: u8 = 1;
pub const N_QUEST: u8 = 2;
pub const N_DOCTOR: u8 = 3;
pub const N_MECHANIC: u8 = 4;
pub const N_TRAINER: u8 = 5;
pub const N_GUIDE: u8 = 6;
/// V8: büyücü (kampta ve kişisel üste; ışınlanma)
pub const N_WIZARD: u8 = 7;
/// V11: şehir güvenli bölgesindeki komutan (Sv 10 şehir öğreticisi: arena, eğitim üssü)
pub const N_COMMANDER: u8 = 8;

/// NPC adı ve unvanı
pub fn npc_name(kind: u8) -> (&'static str, &'static str) {
    match kind {
        N_MERCHANT => ("Tüccar Marco", "Tüccar"),
        N_QUEST => ("Yaşlı Silas", "Görevci"),
        N_DOCTOR => ("Doktor Elena", "Doktor"),
        N_MECHANIC => ("Tamirci Viktor", "Tamirci"),
        N_TRAINER => ("Eğitmen Jack", "Köpek Eğitmeni"),
        N_GUIDE => ("Öğretici", "İlk Adımlar"),
        N_WIZARD => ("Büyücü Aldric", "Büyücü"),
        N_COMMANDER => ("Komutan Diana", "Komutan"),
        _ => ("Kule Askeri", "Muhafız"),
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Goal {
    /// bir NPC ile konuş
    Talk(u8),
    /// zombi öldür (bölge şartı isteğe bağlı)
    Kill(Option<Zone>),
    KillKind(ZKind),
    Chop,
    Craft(&'static str),
    Use(&'static str),
    /// cebe bir eşya koy
    Pocket,
    Roll,
    /// yerden eşya topla
    Loot,
    /// bir yere git (LANDMARKS kimliği)
    Reach(&'static str),
    /// bir yerin çevresinde zehirli yaratık öldür
    Cleanse(&'static str),
    Level,
    Dog,
    /// V11: eğitim üssünü ele geçir (bombalanıp şehirde uyanınca tamamlanır)
    Train,
    /// V16: kaya kır (kazma ya da yumruk)
    Mine,
    /// V16: elindeki silah/alet bu kimlikle başlıyorsa tamamlanır (canlı okunur: zaten kuşanılıysa hemen biter)
    Equip(&'static str),
    /// V18: üstündeki zırh parçası sayısı (kask, ceket, pantolon, bot; canlı okunur)
    Wear,
    /// V18: arayüz ve sistem olayı (ışınlanma, harita, yetenek ağacı, sohbet, tezgâh kurma, satış, araç siparişi…)
    Ping(&'static str),
}

pub struct Quest {
    pub id: &'static str,
    pub giver: u8,
    pub title: &'static str,
    /// takipçide görünen kısa yönerge
    pub hint: &'static str,
    /// görev verilirken NPC'nin söyledikleri
    pub talk: &'static [&'static str],
    /// teslimde söyledikleri
    pub done: &'static str,
    pub goal: Goal,
    pub count: u32,
    pub min_level: u32,
    pub gold: u32,
    pub xp: u32,
    pub items: &'static [(&'static str, u32)],
    /// kabulde verilen malzeme (ör. üretim öğretirken)
    pub start_items: &'static [(&'static str, u32)],
}

macro_rules! q {
    ($id:expr, $giver:expr, $title:expr, $hint:expr, [$($t:expr),*], $done:expr, $goal:expr, $count:expr, $lvl:expr, $gold:expr, $xp:expr, $items:expr, $start:expr) => {
        Quest { id: $id, giver: $giver, title: $title, hint: $hint, talk: &[$($t),*], done: $done, goal: $goal, count: $count,
                min_level: $lvl, gold: $gold, xp: $xp, items: $items, start_items: $start }
    };
}

/// Öğretici zinciri (kendi kendine ilerler, NPC'si yok): zombi öldürmekten üretime kadar her şeyi öğretir.
pub const GUIDE: &[Quest] = &[
    q!("g1", N_GUIDE, "İlk Adımlar", "Kamptan Çık, 3 Zombi Öldür",
       ["Uyandın. Dünya çöktü ama bu kamp hâlâ ayakta.",
        "Sol joystick ile yürürsün; sonuna kadar itersen koşarsın. Sağ joystick saldırıdır: çevirdiğin yöne vurursun.",
        "Kampın dışındaki ormanda aylak zombiler dolaşıyor. 3 tanesini indir."],
       "İşte bu! Yumrukların iş görüyor. Ama bir zombi sürüsü seni kolayca köşeye sıkıştırır…",
       Goal::Kill(None), 3, 1, 20, 40, &[("bandage", 2)], &[]),
    q!("g2", N_GUIDE, "Takla At", "Takla Tuşuyla 2 Kez Yuvarlan",
       ["Kuşatıldığında kaçmayı bilmelisin. Sağdaki takla tuşu seni baktığın yöne yuvarlar.",
        "Yuvarlanırken hiçbir saldırı sana işlemez. 2 kez takla at."],
       "Çevik bir hayatta kalan! Takla 5 saniyede bir dolar, iyi zamanla.",
       Goal::Roll, 2, 1, 10, 30, &[], &[]),
    q!("g3", N_GUIDE, "Ganimet", "Yerden 3 Eşya Topla",
       ["Ölen zombilerden ve sandıklardan malzeme düşer: kumaş, kemik, reçine…",
        "Üstünden geçmen yeter, çantana girer. 3 eşya topla."],
       "Malzeme her şeydir. Ne kadar çok toplarsan o kadar çok üretirsin.",
       Goal::Loot, 3, 1, 15, 40, &[], &[]),
    q!("g4", N_GUIDE, "İlk Üretim", "Çantadan Üret: 1 Bandaj",
       ["Şimdi üretmeyi öğrenelim. Çantana 2 kumaş ve 1 reçine kondu.",
        "Çantadaki Üret tuşundan Bandaj'ı seç ve üret. Bandaj her yerde üretilir; gelişmiş tarifler tezgâh ister."],
       "Harika! Kendi bandajını yapan biri kolay kolay ölmez.",
       Goal::Craft("bandage"), 1, 1, 20, 50, &[], &[("cloth", 2), ("resin", 1)]),
    q!("g4b", N_GUIDE, "Taş Balta", "Üret: 1 Taş Balta (Silah Ve Alet Sekmesi)",
       ["Ağaçlar sağlamdır: her birinin 500 canı var. Yumrukla 20 kez vurman gerekir.",
        "Çantana odun, taş ve ip kondu. Üretimdeki Silah Ve Alet sekmesinden Taş Balta yap.",
        "Baltayı elinde tutunca ağaca her vuruşun 50 can götürür (yumrukla 25). Demir balta 100 götürür, onu tezgâhta yaparsın."],
       "İşte gerçek bir hayatta kalan aleti! Kazmayı da aynı yerden yaparsın; kayalara işler.",
       Goal::Craft("t_axe_stone"), 1, 1, 15, 40, &[], &[("wood", 6), ("stone", 4), ("rope", 1)]),
    q!("g4c", N_GUIDE, "Baltayı Kuşan", "Çantadan Taş Baltayı Kuşan",
       ["Çantayı aç, Taş Balta'ya dokun ve Kuşan'ı seç. Balta yalnız elindeyken ağacı hızlı keser; zombiye de vurabilirsin."],
       "Elinde balta olan biri kolay kolay yenilmez. Artık ağaç keselim.",
       Goal::Equip("t_axe_"), 1, 1, 10, 30, &[], &[]),
    q!("g4a", N_GUIDE, "Odun Topla", "1 Ağaç Kes",
       ["Şimdi baltanı dene. Kampın çevresindeki ağaçlardan birine yürü ve vurmaya devam et.",
        "Ağaç devrilince odun düşer; çamlardan reçine de çıkar. Odun üretimin temelidir."],
       "Gördün mü? Balta olunca iş hızlanıyor. Odun ve taş her zaman lazım olacak.",
       Goal::Chop, 1, 1, 15, 40, &[], &[]),
    q!("g4d", N_GUIDE, "Taş Kazma", "Üret: 1 Taş Kazma",
       ["Çantana odun, taş ve ip kondu. Aynı sekmeden Taş Kazma yap."],
       "Kazma kayaya, balta ağaca. Basit ve etkili.",
       Goal::Craft("t_pick_stone"), 1, 1, 15, 40, &[], &[("wood", 6), ("stone", 6), ("rope", 1)]),
    q!("g4e", N_GUIDE, "Kaya Kır", "1 Kaya Kır",
       ["Kayalar da odun kadar önemli: taş, tezgâh ve alet demek. Hepsinin 500 canı var.",
        "Kazmayı çantanda tut (ya da kuşan) ve bir kayaya yürüyüp vurmaya devam et.",
        "Kaya parçalanınca taş düşer; ara sıra hurda da çıkar. Hurda silah üretiminin temelidir."],
       "Taş ve hurda: tezgâhın yakıtı. Sakın sıkılma, hepsi lazım olacak.",
       Goal::Mine, 1, 1, 15, 40, &[], &[]),
    q!("g4f", N_GUIDE, "İlk Silah", "Üret: 1 Bıçak",
       ["Yumruk ilk günler için yeter ama zombiler yenilenir. Gerçek bir silaha ihtiyacın var.",
        "Çantana hurda, deri ve ip kondu. Silah Ve Alet sekmesinden Bıçak yap: kısa ama çok hızlı vurur."],
       "Güzel iş! Daha iyi silahlar tezgâhta ve tüccarda: katana, tabanca, tüfek…",
       Goal::Craft("w_knife"), 1, 1, 20, 50, &[], &[("scrap", 3), ("leather", 1), ("rope", 1)]),
    q!("g4g", N_GUIDE, "Silahını Kuşan", "Çantadan Bıçağı Kuşan",
       ["Silahını Çanta'dan Kuşan'la elinde tut. Savaşta sağ joystick silahınla vurur; ateşli silahlarda mermi şarjörden gelir."],
       "Silahın elinde. Artık zombi sürüsü seni o kadar korkutmaz.",
       Goal::Equip("w_knife"), 1, 1, 10, 30, &[], &[]),
    q!("g4h", N_GUIDE, "Zırh Giy", "Çantadan Kaskı Ve Ceketi Kuşan",
       ["Zırh gelen hasarı azaltır. Kask, ceket, pantolon ve bot ayrı yuvalardadır.",
        "Çantana basit bir kask ve ceket kondu. Çantayı aç, eşyaya dokun ve Kuşan'a bas; eşya sol taraftaki ekipman yuvasına oturur."],
       "Zırhın seni korur. Giydiğin eşyalar kullandıkça yıpranır; tezgâhta ya da Tamirci Viktor'da onarılır.",
       Goal::Wear, 2, 1, 15, 40, &[], &[("helm_basic", 1), ("jacket_basic", 1)]),
    q!("g4i", N_GUIDE, "Tezgâh Kur", "Çantadaki Tezgâhı Yere Kur",
       ["Gelişmiş eşyalar tezgâhta yapılır. Kampta bir tezgâh var ama kendi tezgâhını da kurabilirsin.",
        "Çantana bir tezgâh kondu. Çantada tezgâha dokun, Kur'a bas ve yerde yeşil çıkan bir noktaya yerleştir."],
       "Tezgâhın hazır. Yakınına gidince üretim ekranı açılır; çantaya geri alabilirsin.",
       Goal::Ping("bench"), 1, 1, 15, 40, &[], &[("workbench", 1)]),
    q!("g5", N_GUIDE, "Cebini Hazırla", "Çantada Bir Eşyayı Cebe Sürükle",
       ["Savaşın ortasında çantayı açmaya vaktin olmaz.",
        "Çantayı aç, bir bandaja ya da iksire basılı tut ve soldaki ceplerden birine sürükle.",
        "Cepteki eşyalar ekranın altında tuş olur: tek dokunuşla kullanırsın."],
       "Artık hazırlıklısın. Ceplerin bekleme süresini de gösterir.",
       Goal::Pocket, 1, 1, 10, 30, &[("potion_heal", 1)], &[]),
    q!("g5b", N_GUIDE, "Cepten Kullan", "Cebindeki Bandajı Kullan",
       ["Cepteki eşyaya dokununca kullanılır: yiyecekler ve iksirler canını yeniler, bandaj yarayı sarar.",
        "Çantana bir bandaj kondu. Cebine koyup ekranın altından dokun."],
       "İşte hızlı müdahale! Kullanım sonrası eşya bekleme süresine girer.",
       Goal::Use("bandage"), 1, 1, 10, 30, &[], &[("bandage", 1)]),
    q!("g6", N_GUIDE, "Tüccarla Tanış", "Tüccar Marco ile Konuş",
       ["Marco gezgin tüccardır: kamyonetiyle bölgenin duraklarını dolaşır, her gelişinde başka mallar getirir.",
        "Haritada altın kese işaretini bul, onunla bir konuş. Fazla malzemeni de ona satarsın."],
       "Marco'yla anlaştın mı? Fiyatlarına dikkat et, kurnaz adamdır.",
       Goal::Talk(N_MERCHANT), 1, 1, 10, 30, &[], &[]),
    q!("g6s", N_GUIDE, "Eşya Sat", "Marco'ya Bir Eşya Sat",
       ["Marco fazla malzemeni satın alır. Onunla konuşurken Satış bölümünü aç, bir eşyaya dokun ve Sat'a bas.",
        "Çantana satacak birkaç kumaş kondu."],
       "Altın kazandın! Silah ve zırhı satmadan önce düşün; onarıp kullanmak çoğu zaman daha kârlıdır.",
       Goal::Ping("sell"), 1, 1, 10, 30, &[], &[("cloth", 3)]),
    q!("g6b", N_GUIDE, "Büyücüyle Tanış", "Büyücü Aldric ile Konuş",
       ["Ateşin doğusunda mor cüppeli biri durur: Büyücü Aldric.",
        "Bölgeler ayrı haritalardır; aralarında yalnız o ışınlar. Kişisel üssüne de o götürür, 10. seviyede şehre ve Cephe Hattı'na da.",
        "Git, kendini tanıt."],
       "Aldric tuhaf adamdır ama sözünü tutar. Bölgeden bölgeye onunla geçersin.",
       Goal::Talk(N_WIZARD), 1, 1, 10, 30, &[], &[]),
    q!("g6c", N_GUIDE, "Işınlan", "Aldric'le Kişisel Üssüne Işınlan Ve Geri Dön",
       ["Aldric'le konuşunca bölge listesi açılır. Kişisel Üssüm'ü seç: orası yalnız senindir, duvarla çevrilidir ve ücretsizdir.",
        "Önce üsse ışınlan, sonra üssündeki Aldric'ten Başlangıç Ormanı'na geri dön. Büyü her kullanımdan sonra kısa süre dinlenir."],
       "Işınlanmayı öğrendin! Üssün güvenli bir depodur: sandıklarını orada kurarsın. Yeni bölgelerde Aldric yine yolun açar.",
       Goal::Ping("tp"), 2, 1, 20, 50, &[], &[]),
    q!("g6d", N_GUIDE, "Haritayı Aç", "Mini Haritaya Dokun",
       ["Sağ üstteki yuvarlak mini harita çevreni gösterir: kırmızı noktalar zombi, turuncu ünlemler görev, mavi elmaslar önemli yerlerdir.",
        "Haritaya dokun: büyük harita açılır, iki parmakla yakınlaştırırsın."],
       "Harita yolunu bulmanın anahtarı. Karakterler ve yerler adlarıyla işaretlidir.",
       Goal::Ping("map"), 1, 1, 10, 30, &[], &[]),
    q!("g6e", N_GUIDE, "Yetenek Ağacı", "Yetenek Ağacını Aç",
       ["Her seviye yeni güç getirir ama asıl gelişim yetenek ağacındadır. Sol üstteki mor ağaç tuşundan aç.",
        "Düğümler elmasla açılır; ortadan başlayıp bağlı olanlara ilerlersin. Dört dal var: hasar, silah, can ve çeviklik."],
       "Ağaçta ilerledikçe oyun tarzın şekillenir. Fikrini değiştirirsen harcadığın elmas kredi olarak geri döner.",
       Goal::Ping("skills"), 1, 1, 10, 30, &[], &[]),
    q!("g6f", N_GUIDE, "Posta Kutusu", "Posta Kutunu Aç",
       ["Ödüller, destek yanıtları ve klan davetleri postana gelir. Sağ üstteki zarf tuşuna dokun.",
        "Üstünde kırmızı nokta varsa okunmamış posta vardır; ödülleri oradan alırsın."],
       "Postanı düzenli kontrol et: etkinlik ödülleri ve araç siparişleri oraya düşer.",
       Goal::Ping("mail"), 1, 1, 10, 30, &[], &[]),
    q!("g7", N_GUIDE, "Ayakta Kal", "Seviye 5'e Ulaş",
       ["Temel bilgiler bu kadar. Gerisi tecrübe.",
        "Yaşlı Silas'ın görevlerini yap, ormanda zombi avla ve 5. seviyeye ulaş.",
        "5. seviyede sohbet açılır; diğer hayatta kalanlarla konuşabilirsin."],
       "Öğretici bitti. Sohbet açıldı; yalnız değilsin. Yolun açık olsun!",
       Goal::Level, 5, 1, 100, 0, &[("potion_power", 1)], &[]),
    q!("g7b", N_GUIDE, "Sohbet", "Genel Sohbete Bir Mesaj Yaz",
       ["5. seviyede sohbet açıldı. Ekranın altındaki Genel Sohbet çubuğuna dokun ve bir selam yaz.",
        "Klan sohbeti ve özel mesajlar da orada. Küfür ve reklam yasak; kurallara uyarsan sohbet seninle kalır."],
       "Artık yalnız değilsin! Klanlar ve takaslar da sohbetten başlar.",
       Goal::Ping("chat"), 1, 5, 30, 80, &[], &[]),
    // V11 şehir öğreticisi (Sv 10): şehrin güvenli bölgesindeki Komutan Diana
    q!("g8", N_COMMANDER, "Şehre Hoş Geldin", "Eski Arena'ya Git",
       ["Şehre hoş geldin asker. Ben Komutan Diana; bu surların ardında hayatta kalanları ben toplarım.",
        "Şehir üç kuşaktır: banliyö, merkez ve sanayi. Kuzeydoğudaki eski arena artık boş; Titan her çeyrek saatte başka bir bölgede uyanıyor.",
        "Eski Arena'ya kadar git, şehri tanı. Haritanda işaretledim. Titan'ın nerede olduğunu Aldric'in haritasında görürsün."],
       "Şehri tanıdın. Titan büyük ganimet bırakır ama klansız gidene mezar olur.",
       Goal::Reach("arena_post"), 1, 10, 150, 200, &[("medkit", 2)], &[]),
    q!("g9", N_COMMANDER, "Eğitim Üssü", "Eğitim Üssündeki Askerleri Yen Ve Üssü Ele Geçir",
       ["Asıl savaş Cephe Hattı'nda: devlet üsleri ve klanların kapıştığı yer. Oraya Büyücü Aldric götürür.",
        "Ama önce eğitim. Seni Cephe Karargâhı'nın yanındaki eğitim üssüne gönderiyorum; yalnız sen göreceksin.",
        "İçerideki askerleri indir, sonra ortadaki bayrak halkasında 10 saniye dur. Üs senin olacak… bakalım ne kadar."],
       "Hava saldırısını sağ atlattın! Gerçek üslerde de olur: tuttuğun üssü klanınla savunmalısın. Artık hazırsın asker.",
       Goal::Train, 1, 10, 300, 300, &[("medkit", 3), ("potion_power", 1)], &[]),
];

/// V10 kayıtlarındaki rehber sırası (g1..g7, 7 = bitti) → V11 sırası (g4b, g4a, g6b eklendi; şehir adımları sonda)
pub fn migrate_guide_index(old: usize) -> usize {
    match old {
        0..=3 => old,
        4 | 5 => old + 2,
        _ => old + 3,
    }
}

/// V11 sırasındaki kayıtlar (rehber_v 1: g4a 5. sırada, 12 = bitti) → V16 sırası (g4c, g4d, g4e, g4f, g4g eklendi)
pub fn migrate_guide_v2(old: usize) -> usize {
    match old {
        0..=4 => old,
        5 => 6,
        _ => old + 5,
    }
}

/// Görevcinin ana zinciri (sıradan ve sıra dışı görevler).
pub const MAIN: &[Quest] = &[
    q!("q1", N_QUEST, "Ormanı Temizle", "Başlangıç Ormanında 5 Zombi Öldür",
       ["Gel evlat, gel. Kampın çevresi zombi kaynıyor; geceleri uyuyamıyoruz.",
        "Başlangıç Ormanında 5 zombi öldür. Karşılığında altın ve tecrübe var."],
       "Sağ ol evlat. Bu gece biraz rahat uyuruz.",
       Goal::Kill(Some(Zone::Forest)), 5, 1, 60, 80, &[], &[]),
    q!("q2", N_QUEST, "Sahra Hastanesi", "1 İlk Yardım Kiti Üret",
       ["Doktor Elena batıdaki sahra hastanesinde yaralılara bakıyor; malzemesi bitmek üzere.",
        "Sana gereken malzemeyi veriyorum: bandaj, bataklık yosunu ve kurutulmuş deri. Tezgâhta İlk Yardım Kiti üret."],
       "Bu kit bir hayat kurtaracak. Elena'ya selamımı söyle.",
       Goal::Craft("medkit"), 1, 2, 80, 100, &[], &[("bandage", 2), ("swamp_moss", 1), ("dried_hide", 1)]),
    q!("q3", N_QUEST, "Gizemli Ağacı Keşfet", "Haritadaki Gizemli Ağaca Git",
       ["Ormanın doğusunda, yaprakları hiç dökülmeyen bir ağaç varmış. Çevresinde mantarlar halka çizermiş.",
        "Zombiler ona yaklaşmıyormuş… Haritanda işaretledim. Git, gör ve bana anlat."],
       "Demek gerçekmiş! O ağacın altında bir şey var, eminim. Bunu kimseye söyleme.",
       Goal::Reach("tree"), 1, 3, 90, 150, &[("potion_speed", 1)], &[]),
    q!("q4", N_QUEST, "Oduncu", "8 Ağaç Kes",
       ["Kışa hazırlanmalıyız. Kamp ateşi sönmemeli.",
        "Saldırı tuşuyla ağaçlara vurursan devrilir. 8 ağaç kes."],
       "Bu odun bize bir hafta yeter. Ellerine sağlık.",
       Goal::Chop, 8, 3, 70, 120, &[("rope", 2)], &[]),
    q!("q5", N_QUEST, "Uluyan Sürü", "5 Zombi Köpek Öldür",
       ["Geceleri uluyan o yaratıklar… Bir zamanlar birinin köpeğiydiler.",
        "5 zombi köpeği huzura kavuştur. Barınaktaki Jack'in köpekleri de rahat etsin."],
       "Uluma kesildi. Jack sana teşekkür etmek isteyebilir.",
       Goal::KillKind(ZKind::Dog), 5, 4, 100, 160, &[("bone", 3)], &[]),
    q!("q6", N_QUEST, "Benzin Kokusu", "Benzin İstasyonundaki Tamirci Viktor'u Bul",
       ["Kuzeydeki eski benzin istasyonunda Viktor diye bir tamirci kalıyormuş.",
        "Araçlarımızı o tamir edebilir. Git, onunla konuş."],
       "Viktor hâlâ hayatta demek! Artık araçlarımız yolda kalmaz.",
       Goal::Talk(N_MECHANIC), 1, 5, 80, 180, &[("fuel", 2)], &[]),
    q!("q7", N_QUEST, "Derin Ormanın Sessizliği", "Derin Ormanda 15 Zombi Öldür",
       ["Derin ormanda koşucular türedi. Hızlılar ve aç gözlüler.",
        "15 tanesini temizle; kampa ulaşmadan durdurmalıyız."],
       "Derin orman artık biraz daha güvenli. Sen gerçek bir avcısın.",
       Goal::Kill(Some(Zone::DeepForest)), 15, 6, 160, 300, &[("potion_power", 1)], &[]),
    q!("q8", N_QUEST, "Zehirden Arındır", "Zehirli Kaynakta 6 Zehirli Zombi Öldür",
       ["Batıdaki bataklıkta bir kaynak var; suyu yeşile döndü.",
        "Zehirli zombiler oradan çıkıyor. Kaynağın çevresinde 6 tanesini yok et ki su arınsın.",
        "Panzehir taşımayı unutma, evlat."],
       "Kaynak duruldu! Kurbağalar bile geri döndü. Bunu hak ettin.",
       Goal::Cleanse("spring"), 6, 8, 220, 420, &[("antidote", 3), ("potion_heal", 2)], &[]),
    q!("q9", N_QUEST, "Şehre Yolculuk", "Seviye 10'a Ulaş",
       ["Şehir ancak güçlü olanları kabul eder. Oraya yürüyerek gidilmez; Büyücü Aldric götürür.",
        "10. seviyeye ulaş, sonra Aldric'ten seni şehre götürmesini iste. Orada daha büyük tehlikeler ve hazineler var."],
       "10. seviyedesin! Aldric seni şehre götürmeye hazır. Artık bu kampın hikâyesi seninle anlatılacak.",
       Goal::Level, 10, 8, 300, 0, &[("medkit", 2)], &[]),
    // V18: araç kurulumu (parçaları tezgâhta yap, Viktor'a ver; motosiklet postaya gelir)
    q!("q9a", N_QUEST, "Motosiklet: Gövde", "Tezgâhta 1 Motosiklet Gövdesi Üret",
       ["Şehre gidecek biri tek ayakla yetmez, evlat. Motosiklet işini görür ama kendin kuracaksın.",
        "Önce gövde. Gereken hurda, dişli ve deriyi verdim; tezgâhın Araç sekmesinden Motosiklet Gövdesi üret."],
       "Gövde sağlam. Sıra tekerlekte.",
       Goal::Craft("v_frame_moto"), 1, 10, 100, 150, &[], &[("scrap", 12), ("gear", 1), ("leather", 2)]),
    q!("q9b", N_QUEST, "Motosiklet: Tekerlekler", "Tezgâhta 2 Tekerlek Üret",
       ["Motosiklete iki tekerlek gerekir. Malzemesi bende, tezgâhta üretmek sende."],
       "İki tekerlek de tamam. Geriye motor kaldı.",
       Goal::Craft("v_wheel"), 2, 10, 100, 150, &[], &[("scrap", 8), ("leather", 4), ("rope", 2)]),
    q!("q9c", N_QUEST, "Motosiklet: Motor", "Tezgâhta 1 Motor Üret",
       ["Motor işin en zor kısmı: elektronik parça ve yakıt ister. Hepsini verdim.",
        "Tezgâhın Araç sekmesinden Motor üret."],
       "Motoru yaptın! Artık parçaların tamam; Viktor'un işi başlıyor.",
       Goal::Craft("v_engine"), 1, 10, 150, 250, &[], &[("scrap", 10), ("gear", 4), ("electronics", 2), ("fuel", 2)]),
    q!("q9d", N_QUEST, "Motosiklet: Sipariş", "Parçaları Tamirci Viktor'a Teslim Et",
       ["Parçaları kampın tamircisi Viktor'a götür. Konuşurken Araç Üretimi bölümünden Motosiklet'i seç.",
        "Viktor parçaları alır, 10 dakika sonra Motosiklet Anahtarı postana gelir. Anahtarı kullanınca motorun yanına gelir; sür ve zombileri geride bırak."],
       "Sipariş verildi! Postanı kontrol et; anahtar gelince çantandan kullan, motosiklete bin. Sürerken yakıt ve can çubuğuna dikkat.",
       Goal::Ping("order"), 1, 10, 200, 300, &[("fuel", 2)], &[]),
    // V16: Sv 40'a kadar bölge zinciri: her bölgenin karakoluna git (Aldric götürür), bölgeyi avla; sonunda Titan
    q!("q10", N_QUEST, "Kül Karakolu'na Git", "Kül Karakolu'na Git",
       ["Şehrin doğusunda bir zamanlar ormandı; şimdi yalnız kül ve kor. Orada bir karakol kalmış.",
        "Büyücü Aldric seni oraya götürür. Karakola ulaş, güvende olduğunu bana bildir."],
       "Kül Karakolu hâlâ ayakta! Orada kor tazıları ve kömürleşmiş zombiler dolaşır; dikkatli ol.",
       Goal::Reach("burnt_post"), 1, 12, 200, 400, &[("medkit", 2)], &[]),
    q!("q11", N_QUEST, "Kor Avı", "Yanık Ormanda 20 Zombi Öldür",
       ["Yanık Orman'daki yaratıklar ateşe dayanıklı. Kömürleşmiş zombiler yaklaşınca etrafı tutuşturur.",
        "20 tanesini temizle. Yanıklara karşı bandaj ve iksir taşı."],
       "Yanık Orman biraz daha nefes alıyor. Külden bir şey doğacak belki.",
       Goal::Kill(Some(Zone::BurntForest)), 20, 15, 350, 800, &[("potion_heal", 3), ("bandage", 5)], &[]),
    q!("q12", N_QUEST, "Çöl Karakolu'na Git", "Çöl Karakolu'na Git",
       ["Güneyde otoyol kumlara gömülmüş. Mumyalar, akrepler ve motorcu zombiler orada.",
        "Aldric'le Çöl Karakolu'na git. Susuzluk yok ama kum her yere işliyor."],
       "Çöl Karakolu seni gördü, iyi. Orada hız iksiri ve araç işe yarar.",
       Goal::Reach("desert_post"), 1, 18, 300, 700, &[("potion_speed", 2)], &[]),
    q!("q13", N_QUEST, "Kum Fırtınası", "Çöl Otoyolunda 25 Zombi Öldür",
       ["Otoyolu açık tutmak istiyorum: ticaret yoluydu.",
        "Çöl Otoyolu'nda 25 zombi öldür. Akrep zehrine dikkat, panzehir şart."],
       "Yol açıldı! Tüccar Marco'nun kamyoneti artık oradan geçebilir.",
       Goal::Kill(Some(Zone::Desert)), 25, 20, 600, 1400, &[("potion_power", 2), ("medkit", 2)], &[]),
    q!("q14", N_QUEST, "Kara Bataklık Karakolu'na Git", "Kara Bataklık Karakolu'na Git",
       ["Kara Bataklık Karakolu'na kadar git. Panzehirsiz gitme.",
        "Bataklığın kara kısmında zehirli sis var; cadılar ve şişkinler orada yaşar."],
       "Karakola ulaştın. Çıkışta mutlaka gözlerini aç; sis her yerde.",
       Goal::Reach("bswamp_post"), 1, 24, 500, 1200, &[("antidote", 3)], &[]),
    q!("q15", N_QUEST, "Bataklığın Dibi", "Kara Bataklıkta 25 Zombi Öldür",
       ["Kara Bataklık'ta 25 yaratık öldür; sürüyü seyrelt.",
        "Bataklık Devi geceleri çamurdan çıkıp karakolu eziyor."],
       "Bataklık sustu. Güçlendiğini hissediyorum evlat.",
       Goal::Kill(Some(Zone::BlackSwamp)), 25, 26, 900, 2200, &[("medkit", 3), ("potion_heal", 3)], &[]),
    q!("q16", N_QUEST, "Cephe Karargâhı'na Git", "Cephe Karargâhı'na Git",
       ["Doğuda, devlet üslerinin ve klanların kapıştığı yer: Cephe Hattı.",
        "Aldric seni oraya götürür. Karargâhta durum raporu al, sonra bana dön."],
       "Cephe sıcak demek. Klanınla gitmeden üs almaya kalkma.",
       Goal::Reach("front_hq"), 1, 30, 800, 2000, &[("potion_power", 2)], &[]),
    q!("q17", N_QUEST, "Askeri Bölge", "Askeri Bölgede 30 Zombi Öldür",
       ["Askeri Bölge'de çevik kuvvet zombileri ve botlar var; çoğu hâlâ görev başında.",
        "30 tanesini indir. Ağır zırh ve iyi bir silah şart."],
       "Askeri Bölge'yi sarstın! Oradan düşen devre kartları işine yarar.",
       Goal::Kill(Some(Zone::Military)), 30, 34, 1400, 3500, &[("medkit", 4), ("potion_power", 2)], &[]),
    q!("q18", N_QUEST, "Liman Karakolu'na Git", "Liman Karakolu'na Git",
       ["En uzakta, buzla kaplı bir liman var. Donmuş denizciler ve rıhtım canavarları orada.",
        "Liman Karakolu'na git; sıcak giyin."],
       "Liman Karakolu'ndasın! Burası dünyanın en soğuk köşesi.",
       Goal::Reach("harbor_post"), 1, 38, 1100, 3000, &[("potion_heal", 5)], &[]),
    q!("q19", N_QUEST, "Buz Altında", "Donmuş Limanda 30 Zombi Öldür",
       ["Rıhtım canavarları buzu kıran bir kuvvetle vurur; ama ateşe karşı zayıftırlar.",
        "30 yaratığı temizle ve donmuş balık topla, sıcak yemek iyileştirir."],
       "Liman temiz! Sen artık bir efsanesin, evlat.",
       Goal::Kill(Some(Zone::Harbor)), 30, 40, 2000, 5000, &[("medkit", 5), ("potion_power", 3)], &[]),
    q!("q20", N_QUEST, "Titan'ın Sonu", "Bir Titan'ı Yen",
       ["Titan her çeyrek saatte bir bölgede uyanır; haritada nerede olduğunu Aldric gösterir.",
        "Tek başına gidilmez. Klanınla git, ağır silah ve bol iksir al. Titan'ı yenersen senin adın destan olur."],
       "TİTAN'I YENDİN! Duvarlar bunu yüzyıllarca anlatır. Ganimeti hak ettin.",
       Goal::KillKind(ZKind::Titan), 1, 40, 5000, 10000, &[("titan_shard", 3)], &[]),
];

const OLD_GUIDE_V2: [&str; 17] = ["g1", "g2", "g3", "g4", "g4b", "g4c", "g4a", "g4d", "g4e", "g4f", "g4g", "g5", "g6", "g6b", "g7", "g8", "g9"];
const OLD_MAIN_V0: [&str; 20] = ["q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", "q10", "q11", "q12", "q13", "q14", "q15", "q16", "q17", "q18", "q19", "q20"];

/// Eski zincirdeki sıradan (kimlikle) yeni zincirdeki sıraya; bitmiş zincir yeni zincirin sonu olur
fn remap(old: &[&str], new: &[Quest], idx: usize) -> usize {
    match old.get(idx) {
        Some(id) => new.iter().position(|q| q.id == *id).unwrap_or(new.len()),
        None => new.len(),
    }
}

/// Test yardımcısı: V16 rehber sırasını güncel zincire taşır
#[cfg(test)]
pub fn remap_v2_guide(idx: usize) -> usize {
    remap(&OLD_GUIDE_V2, GUIDE, idx)
}

/// Kaydedilen hikâye durumu
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct StoryState {
    /// sıradaki rehber görevi (GUIDE indeksi)
    pub guide: usize,
    pub guide_on: bool,
    pub guide_n: u32,
    /// sıradaki ana görev (MAIN indeksi)
    pub main: usize,
    pub main_on: bool,
    pub main_n: u32,
    /// rehber zinciri sürümü (V11: 1, V16: 2)
    pub guide_v: u32,
    /// ana zincir sürümü (V18: 1; görev sırası kimliğe göre taşınır)
    pub main_v: u32,
}

impl StoryState {
    /// Eski kayıtların rehber sırasını yeni zincire taşır
    pub fn migrate(&mut self) {
        if self.guide_v < 1 {
            self.guide = migrate_guide_index(self.guide);
            self.guide_v = 1;
        }
        if self.guide_v < 2 {
            self.guide = migrate_guide_v2(self.guide);
            self.guide_v = 2;
        }
        // V18: zincirlere yeni adımlar eklendi; sıra eski sıranın kimliğinden yeni konuma taşınır
        if self.guide_v < 3 {
            self.guide = remap(&OLD_GUIDE_V2, GUIDE, self.guide);
            self.guide_v = 3;
        }
        if self.main_v < 1 {
            self.main = remap(&OLD_MAIN_V0, MAIN, self.main);
            self.main_v = 1;
        }
    }
}

/// Hikâye ilerleme olayı
#[derive(Clone, Copy, Debug)]
pub enum Ev {
    Kill { kind: ZKind, x: f32, y: f32 },
    Chop,
    Craft(&'static str),
    Use(&'static str),
    Pocket,
    Roll,
    Loot,
    Talk(u8),
    Dog,
    Train,
    Mine,
    /// V18: arayüz/sistem olayı adı (bkz. [`ping_name`])
    Ping(&'static str),
}

/// İstemcinin bildirebildiği olay adları (hile ve yanlış ad engellenir)
pub fn ping_name(s: &str) -> Option<&'static str> {
    ["tp", "map", "skills", "mail", "chat", "sell", "bench", "order", "drive", "unlock", "repair", "shop", "quests"]
        .iter()
        .find(|n| **n == s)
        .copied()
}

fn chain(which: u8) -> &'static [Quest] {
    if which == 0 {
        GUIDE
    } else {
        MAIN
    }
}

impl Game {
    fn story_slot(&mut self, which: u8) -> (usize, bool, u32) {
        let s = &self.progress.story;
        if which == 0 {
            (s.guide, s.guide_on, s.guide_n)
        } else {
            (s.main, s.main_on, s.main_n)
        }
    }

    fn story_set(&mut self, which: u8, idx: usize, on: bool, n: u32) {
        let s = &mut self.progress.story;
        if which == 0 {
            s.guide = idx;
            s.guide_on = on;
            s.guide_n = n;
        } else {
            s.main = idx;
            s.main_on = on;
            s.main_n = n;
        }
    }

    /// Görevin anlık ilerlemesi (seviye görevleri canlı okunur)
    /// Üstündeki zırh parçası sayısı (kask, ceket, pantolon, bot)
    fn armor_worn(&self) -> u32 {
        let e = &self.progress.eq;
        [&e.helmet, &e.jacket, &e.pants, &e.boots].iter().filter(|x| !x.is_empty()).count() as u32
    }

    fn quest_progress(&self, q: &Quest, n: u32) -> u32 {
        match q.goal {
            Goal::Level => self.progress.level.min(q.count),
            Goal::Dog => u32::from(self.progress.dog_owned),
            Goal::Wear => self.armor_worn().min(q.count),
            _ => n.min(q.count),
        }
    }

    /// Olayı etkin görevlere işler; hedef tamamlanınca bir kez haber verir.
    pub fn story_event(&mut self, ev: Ev) {
        for which in 0..2u8 {
            let (idx, on, n) = self.story_slot(which);
            let Some(q) = chain(which).get(idx) else { continue };
            if !on || n >= q.count {
                continue;
            }
            let hit = match (q.goal, ev) {
                (Goal::Kill(zone), Ev::Kill { x, y, .. }) => zone.is_none_or(|z| self.world.zone(x, y) == z),
                (Goal::KillKind(k), Ev::Kill { kind, .. }) => k == kind,
                (Goal::Cleanse(id), Ev::Kill { kind, x, y }) => {
                    let l = world::landmark(id);
                    let (lx, ly) = l.pos();
                    kind == ZKind::Toxic && (x - lx).hypot(y - ly) < (l.r + 6.0) * world::TILE
                }
                (Goal::Chop, Ev::Chop) | (Goal::Pocket, Ev::Pocket) | (Goal::Roll, Ev::Roll) | (Goal::Loot, Ev::Loot) => true,
                (Goal::Craft(a), Ev::Craft(b)) | (Goal::Use(a), Ev::Use(b)) => a == b,
                (Goal::Talk(a), Ev::Talk(b)) => a == b,
                (Goal::Dog, Ev::Dog) | (Goal::Train, Ev::Train) | (Goal::Mine, Ev::Mine) => true,
                (Goal::Ping(a), Ev::Ping(b)) => a == b,
                _ => false,
            };
            if hit {
                let n = n + 1;
                self.story_set(which, idx, true, n);
                if n >= q.count {
                    self.story_ready(q);
                }
            }
        }
    }

    fn story_ready(&mut self, q: &Quest) {
        // Nova'nın adımları kendiliğinden teslim edilir (auto_guide)
        if q.giver == N_GUIDE {
            return;
        }
        let (who, _) = npc_name(q.giver);
        self.events.push(Event::Quest { title: format!("{} · {who} ile konuş", q.title) });
    }

    /// Görevi başlatır: başlangıç malzemesi verilir (verilenlerin listesi döner).
    fn quest_accept(&mut self, w: u8, idx: usize) -> Vec<String> {
        let Some(q) = chain(w).get(idx) else { return Vec::new() };
        self.story_set(w, idx, true, 0);
        let mut given = Vec::new();
        for &(id, c) in q.start_items {
            let left = self.progress.inv.add_id(id, c);
            if left > 0 {
                let (x, y) = (self.player.x, self.player.y);
                self.spawn_drop_id(id, left, x, y + 30.0);
            }
            if let Some(i) = data::item_index(id) {
                given.push(format!("{c}× {}", data::ITEMS[i].name));
            }
        }
        self.events.push(Event::Quest { title: format!("Yeni Görev: {}", q.title) });
        self.story_goals();
        given
    }

    /// Görevi teslim eder: ödüller verilir (listesi döner), zincir ilerler.
    fn quest_turnin(&mut self, w: u8, idx: usize) -> Vec<String> {
        let Some(q) = chain(w).get(idx) else { return Vec::new() };
        self.progress.add_gold(q.gold);
        self.progress.add_xp(q.xp);
        let mut got = vec![format!("+{} altın", q.gold)];
        if q.xp > 0 {
            got.push(format!("+{} XP", q.xp));
        }
        for &(id, c) in q.items {
            let left = self.progress.inv.add_id(id, c);
            if left > 0 {
                let (x, y) = (self.player.x, self.player.y);
                self.spawn_drop_id(id, left, x, y + 30.0);
            }
            if let Some(i) = data::item_index(id) {
                got.push(format!("{c}× {}", data::ITEMS[i].name));
            }
        }
        self.events.push(Event::Quest { title: format!("Görev Teslim Edildi: {}", q.title) });
        self.story_set(w, idx + 1, false, 0);
        self.check_level_quests();
        got
    }

    /// İlk adımlar öğreticisi (dünyada NPC'si yok) otomatik: kabul ve teslim için konuşmak gerekmez. Seviye yetince adım kendiliğinden
    /// başlar, hedef tamamlanınca ödül verilir ve sıradaki adım açılır. (Komutan Diana'nın şehir adımları elle alınır.)
    fn auto_guide(&mut self) {
        let (idx, on, n) = self.story_slot(0);
        let Some(q) = GUIDE.get(idx) else { return };
        if q.giver != N_GUIDE {
            return;
        }
        if !on {
            if self.progress.level >= q.min_level {
                self.quest_accept(0, idx);
            }
        } else if self.quest_progress(q, n) >= q.count {
            let got = self.quest_turnin(0, idx);
            self.msg(format!("{} ({})", q.done, got.join(", ")));
        }
    }

    /// Her saniye: yer ve seviye hedefleri, otomatik rehber
    pub fn story_tick(&mut self) {
        self.story_goals();
        self.auto_guide();
    }

    fn story_goals(&mut self) {
        for which in 0..2u8 {
            let (idx, on, n) = self.story_slot(which);
            let Some(q) = chain(which).get(idx) else { continue };
            if !on || n >= q.count {
                continue;
            }
            match q.goal {
                Goal::Reach(id) => {
                    let l = world::landmark(id);
                    let (lx, ly) = l.pos();
                    if (self.player.x - lx).hypot(self.player.y - ly) < (l.r + 1.5) * world::TILE {
                        self.story_set(which, idx, true, q.count);
                        self.story_ready(q);
                    }
                }
                Goal::Wear if self.armor_worn() >= q.count => {
                    self.story_set(which, idx, true, q.count);
                    self.story_ready(q);
                }
                Goal::Equip(prefix) if self.progress.eq.weapon.starts_with(prefix) => {
                    self.story_set(which, idx, true, q.count);
                    self.story_ready(q);
                }
                Goal::Level | Goal::Dog
                    if self.quest_progress(q, n) >= q.count => {
                        self.story_set(which, idx, true, q.count);
                        self.story_ready(q);
                    }
                _ => {}
            }
        }
    }

    /// Görev hedefinin dünyadaki yeri (harita/mini harita işareti): teslimde veren NPC, yer görevinde o yer.
    fn quest_target(&self, q: &Quest, ready: bool) -> Option<(f32, f32)> {
        // aynı bölgedeki NPC öncelikli (bölgeler ayrı harita)
        let npc = |k: u8| {
            let (px, py) = (self.player.x, self.player.y);
            let here = crate::areas::area_at(px, py);
            self.npcs
                .iter()
                .filter(|n| n.kind == k)
                .min_by_key(|n| (crate::areas::area_at(n.x, n.y) != here, (n.x - px).hypot(n.y - py) as u32))
                .map(|n| (n.x, n.y))
        };
        if ready {
            return npc(q.giver);
        }
        match q.goal {
            Goal::Reach(id) | Goal::Cleanse(id) => Some(world::landmark(id).pos()),
            Goal::Talk(k) => npc(k),
            _ => None,
        }
    }

    /// Mini harita için görev hedefleri (etkin ya da alınabilir görevler)
    pub(crate) fn story_targets(&self) -> Vec<(f32, f32)> {
        let j = self.story_json();
        j.as_array()
            .map(|a| a.iter().filter_map(|q| Some((q["x"].as_f64()? as f32, q["y"].as_f64()? as f32))).collect())
            .unwrap_or_default()
    }

    /// Takipçi ve harita için etkin görevler
    pub fn story_json(&self) -> Value {
        let mut list = Vec::new();
        for which in 0..2u8 {
            let s = &self.progress.story;
            let (idx, on, n) = if which == 0 { (s.guide, s.guide_on, s.guide_n) } else { (s.main, s.main_on, s.main_n) };
            let Some(q) = chain(which).get(idx) else { continue };
            let have = self.quest_progress(q, n);
            let ready = on && have >= q.count;
            let mut v = json!({
                "id": q.id, "title": q.title, "hint": q.hint, "n": have, "goal": q.count, "on": on, "ready": ready,
                "giver": npc_name(q.giver).0, "giver_kind": q.giver, "guide": which == 0, "min_level": q.min_level,
            });
            // kabul edilmemiş görev: veren NPC işaretlenir (seviye yetiyorsa)
            let target = if on { self.quest_target(q, ready) } else if self.progress.level >= q.min_level { self.quest_target(q, true) } else { None };
            if let Some((mut x, mut y)) = target {
                // hedef başka bölgede: bu bölgedeki en yakın büyücü gösterilir ("via": gidilecek bölge)
                let (px, py) = (self.player.x, self.player.y);
                let (here, there) = (crate::areas::area_at(px, py), crate::areas::area_at(x, y));
                if here != there {
                    v["via"] = json!(crate::areas::AREAS[there].name);
                    if let Some(w) = self
                        .npcs
                        .iter()
                        .filter(|n| n.kind == N_WIZARD && crate::areas::area_at(n.x, n.y) == here)
                        .min_by_key(|n| (n.x - px).hypot(n.y - py) as u32)
                    {
                        (x, y) = (w.x, w.y);
                    }
                }
                v["x"] = json!(x);
                v["y"] = json!(y);
            }
            list.push(v);
        }
        json!(list)
    }

    /// Bir NPC ile konuşma: satırlar + seçenekler. `opt`: seçilen seçenek (boşsa açılış).
    ///
    /// Akış: açılış → [görev kabul/teslim, hizmet, Sohbet Et, Hoşça Kal]. Kabulden sonra NPC kısa bir onay söyler ve
    /// yalnız "Yola Çık" (kapatır) kalır; teslimden sonra ödül + "Başka İşin Var Mı?" / "Tamam".
    /// "chat" konu menüsünü açar, "t:<konu>" konuyu anlatır, "back" ana menüye döner. "ok"/"bye" Kotlin'de kapatır.
    pub fn talk(&mut self, kind: u8, opt: &str) -> Value {
        let (name, role) = npc_name(kind);
        let mut lines: Vec<String> = Vec::new();
        let mut opts: Vec<(String, String)> = Vec::new();
        let mut open = "";
        if opt.is_empty() {
            self.story_event(Ev::Talk(kind));
        }
        // bu NPC'nin verdiği görev (rehber: 0, görevci: 1)
        let which = match kind {
            N_COMMANDER => Some(0u8),
            N_QUEST => Some(1u8),
            _ => None,
        }
        // zincirin sıradaki görevi bu NPC'nin değilse (ör. rehber zincirinde şehir adımları komutandadır) görev seçeneği yok
        .filter(|&w| chain(w).get(self.story_slot(w).0).is_some_and(|q| q.giver == kind));
        // menü türü: ana menü, konu menüsü ya da tek çıkış
        enum Menu {
            Main,
            Topics,
            Leave,
            AfterTurnin,
            Teleport,
        }
        let mut menu = Menu::Main;
        match opt {
            "accept" | "turnin" if which.is_some() => {
                let w = which.unwrap();
                let (idx, on, n) = self.story_slot(w);
                if let Some(q) = chain(w).get(idx) {
                    if opt == "accept" && !on && self.progress.level >= q.min_level {
                        let given = self.quest_accept(w, idx);
                        lines.push(format!("Anlaştık! {}.", q.hint));
                        if !given.is_empty() {
                            lines.push(format!("Al bunları, işine yarar: {}.", given.join(", ")));
                        }
                        lines.push("Hedefin haritada işaretli, Görevler tuşunda da yazıyor. İşin bitince bana dön.".into());
                        if q.goal == Goal::Train {
                            self.start_training();
                            lines.push("Kapıyı açıyorum… Bol şans asker!".into());
                        }
                        menu = Menu::Leave;
                    } else if opt == "turnin" && on && self.quest_progress(q, n) >= q.count {
                        lines.push(q.done.to_string());
                        let got = self.quest_turnin(w, idx);
                        lines.push(format!("Ödülün: {}", got.join(", ")));
                        menu = Menu::AfterTurnin;
                    }
                }
            }
            "chat" => {
                lines.push(crate::talk::chat_intro(kind).into());
                menu = Menu::Topics;
            }
            "t:todo" => {
                lines.extend(self.todo_lines(kind));
                menu = Menu::Topics;
            }
            t if t.starts_with("t:") => {
                let seed = (self.time as usize).wrapping_add(self.progress.level as usize);
                let area = crate::areas::AREAS[crate::areas::area_at(self.player.x, self.player.y)].id;
                lines.extend(crate::talk::topic_lines(kind, &t[2..], seed, area).into_iter().map(String::from));
                menu = Menu::Topics;
            }
            "back" => lines.push(match kind {
                N_MERCHANT => "Başka? Kamyonet kalkmadan acele et, dostum.",
                N_QUEST => "Başka bir şey sormak ister misin evlat?",
                _ => "Başka bir şey var mı?",
            }
            .into()),
            // V12: bölgeler ayrı harita; büyücünün haritası açılır (bölgeye dokun → bilgi → ışınlan)
            "tp" => open = "wizard_map",
            t if t.starts_with("tp:") => match self.wizard_teleport(&t[3..]) {
                Ok(m) => {
                    lines.push(format!("Gözlerini kapa… {m}."));
                    menu = Menu::Leave;
                }
                Err(e) => {
                    lines.push(e);
                    menu = Menu::Teleport;
                }
            },
            "homespawn" => {
                self.progress.home_spawn = !self.progress.home_spawn;
                lines.push(if self.progress.home_spawn {
                    "Tamam: düşersen ruhunu buraya, üssüne çağırırım.".into()
                } else {
                    "Peki: düşersen yine kampta uyanırsın.".into()
                });
            }
            "shop" => open = "merchant",
            "garage" => open = "garage",
            "dailies" => open = "quests",
            "dog" => open = "dog",
            "heal" => match self.npc_doctor() {
                Ok(m) | Err(m) => lines.push(m),
            },
            "repair" => match self.npc_mechanic() {
                Ok(m) | Err(m) => lines.push(m),
            },
            _ => {}
        }
        // açılış (ve "Başka İşin Var Mı?"): görev durumu ya da selam
        if opt.is_empty() || opt == "more" {
            let mut said = false;
            if let Some(w) = which {
                let (idx, on, n) = self.story_slot(w);
                match chain(w).get(idx) {
                    Some(q) if !on && self.progress.level >= q.min_level => {
                        lines.extend(q.talk.iter().map(|s| s.to_string()));
                        said = true;
                    }
                    Some(q) if on && self.quest_progress(q, n) >= q.count => {
                        lines.push("Döndün! Anlat bakalım, işi hallettin mi?".into());
                        said = true;
                    }
                    Some(q) if on => {
                        lines.push(format!("{} Şu an {} / {}. Acele etme, ama oyalanma da.", q.hint, self.quest_progress(q, n), q.count));
                        said = true;
                    }
                    Some(q) => {
                        lines.push(format!("Senin için bir işim var ama önce biraz güçlen. {}. seviyede gel.", q.min_level));
                        said = true;
                    }
                    None => {}
                }
            }
            if !said {
                lines.push(if opt == "more" { "Şimdilik başka işim yok. Kendine iyi bak.".into() } else { greeting(kind).to_string() });
            }
        }
        // seçenekler
        match menu {
            Menu::Leave => {
                // kabul: hedef anında tamamlandıysa (ör. seviye görevi) teslim de sunulur
                if let Some(w) = which {
                    let (idx, on, n) = self.story_slot(w);
                    if let Some(q) = chain(w).get(idx) {
                        if on && self.quest_progress(q, n) >= q.count {
                            opts.push(("turnin".into(), "Görevi Teslim Et".into()));
                        }
                    }
                }
                opts.push(("ok".into(), "Yola Çık".into()));
            }
            Menu::AfterTurnin => {
                if let Some(w) = which {
                    let (idx, _, _) = self.story_slot(w);
                    if chain(w).get(idx).is_some_and(|q| self.progress.level >= q.min_level) {
                        opts.push(("more".into(), "Başka İşin Var Mı?".into()));
                    }
                }
                opts.push(("ok".into(), "Tamam".into()));
            }
            Menu::Teleport => {
                for d in self.wizard_dests() {
                    let label = if d.cost > 0 { format!("{} ({} Altın)", d.name, d.cost) } else { d.name.to_string() };
                    opts.push((format!("tp:{}", d.id), label));
                }
                opts.push(("back".into(), "Geri".into()));
            }
            Menu::Topics => {
                for (id, label) in crate::talk::topics(kind) {
                    opts.push((format!("t:{id}"), label.to_string()));
                }
                opts.push(("back".into(), "Geri".into()));
            }
            Menu::Main => {
                if let Some(w) = which {
                    let (idx, on, n) = self.story_slot(w);
                    if let Some(q) = chain(w).get(idx) {
                        if !on && self.progress.level >= q.min_level {
                            opts.push(("accept".into(), "Görevi Kabul Et".into()));
                        } else if on && self.quest_progress(q, n) >= q.count {
                            opts.push(("turnin".into(), "Görevi Teslim Et".into()));
                        }
                    }
                }
                match kind {
                    N_MERCHANT => opts.push(("shop".into(), "Alışveriş Yap".into())),
                    N_QUEST => opts.push(("dailies".into(), "Günlük Görevler".into())),
                    N_DOCTOR => opts.push(("heal".into(), format!("Tedavi Ol ({} Altın)", crate::ui::DOCTOR_PRICE))),
                    N_MECHANIC => {
                        opts.push(("garage".into(), "Araç Üret".into()));
                        opts.push(("repair".into(), format!("Aracımı Onar ({} Altın)", crate::ui::MECHANIC_PRICE)));
                    }
                    N_TRAINER => opts.push(("dog".into(), if self.progress.dog_owned { "Köpeğimi Geliştir".into() } else { "Köpek Sahiplen".into() })),
                    N_WIZARD => {
                        opts.push(("tp".into(), "Işınlan".into()));
                        if self.at_home() {
                            opts.push(("homespawn".into(), if self.progress.home_spawn { "Kampta Doğ".into() } else { "Burada Doğ".into() }));
                        }
                    }
                    _ => {}
                }
                opts.push(("chat".into(), "Sohbet Et".into()));
                opts.push(("bye".into(), "Hoşça Kal".into()));
            }
        }
        json!({
            "npc": kind, "name": name, "role": role, "lines": lines, "open": open,
            "opts": opts.iter().map(|(id, l)| json!({"id": id, "label": l})).collect::<Vec<_>>(),
        })
    }

    /// "Ne Yapmalıyım?": oyuncunun durumuna göre yol gösterir (etkin görev, seviye, köpek).
    fn todo_lines(&self, kind: u8) -> Vec<String> {
        let mut out = Vec::new();
        let s = &self.progress.story;
        let lv = self.progress.level;
        if let Some(q) = GUIDE.get(s.guide) {
            let (who, _) = npc_name(q.giver);
            if s.guide_on {
                out.push(format!("{who}'nin verdiği işi bitir: {}.", q.hint));
            } else if lv >= q.min_level && q.giver == N_COMMANDER {
                out.push("Komutan Diana seni şehrin güvenli bölgesinde bekliyor: şehri ve üsleri o anlatır.".into());
            }
        }
        if let Some(q) = MAIN.get(s.main) {
            if s.main_on {
                out.push(format!("Yaşlı Silas'ın görevi: {}.", q.hint));
            } else if lv >= q.min_level {
                out.push(format!("Yaşlı Silas'ın sana yeni bir işi var: \"{}\". Kampta onu bul.", q.title));
            } else {
                out.push(format!("Silas'ın sıradaki işi için {}. seviyeye ulaşmalısın. Ormanda zombi avla, ağaç kes, tecrübe topla.", q.min_level));
            }
        }
        if !self.progress.dog_owned && lv >= 3 {
            out.push("Henüz köpeğin yok. Barınaktaki Eğitmen Jack'ten bir yol arkadaşı alabilirsin.".into());
        }
        if lv < 10 {
            out.push(format!("Şehrin kapısı 10. seviyede açılır. Sen şu an {lv}. seviyedesin."));
        } else {
            out.push("Şehir sana açık. Banliyö ve şehir merkezi daha tehlikeli ama ganimeti de bol.".into());
        }
        if kind == N_DOCTOR && self.player.hp < self.progress.max_hp() * 0.6 {
            out.push("Ama önce şu yaralarına bakalım; böyle dolaşamazsın.".into());
        }
        out
    }

    /// Seviye görevleri teslimden hemen sonra da hazır olabilir
    fn check_level_quests(&mut self) {
        self.story_tick();
    }
}

fn greeting(kind: u8) -> &'static str {
    match kind {
        N_MERCHANT => "Hoş geldin yolcu! Kamyonet bu sefer dolu: her gelişimde başka mallar getiririm, fırsatı kaçırma. Ne lazım?",
        N_QUEST => "Otur evlat, otur. Yaşlı Silas'ın dizleri tutmuyor ama aklı hâlâ yerinde.",
        N_DOCTOR => "Yaralı mısın? Otur şuraya, bir bakayım. Zehirlendiysen de temizlerim.",
        N_MECHANIC => "Motor sesi duydum sandım… Aracın mı bozuldu? Viktor'un elinden kurtulan araç yoktur.",
        N_TRAINER => "Köpekler insanı kokusundan tanır. Seninki iyi bir koku, sana güvenirler.",
        N_GUIDE => "Bir şey sormak istersen buradayım. Kampın etrafında dikkatli ol.",
        N_WIZARD => "Rüzgâr senin geleceğini fısıldadı yolcu. Haritam açık: bölgeden bölgeye yalnız benim kapılarımla geçilir.",
        _ => "Kuleden her şeyi görüyorum. Zombileri mızrağımla indiririm, sen içeride güvendesin.",
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::game::Input;
    use crate::progress::Progress;

    fn npc_at(g: &mut Game, kind: u8) {
        let n = g.npcs.iter().rev().find(|n| n.kind == kind).unwrap();
        let (x, y) = (n.x, n.y + 60.0);
        g.player.x = x;
        g.player.y = y;
    }

    /// İlk adımlar öğreticisi otomatik — konuşmadan başlar, hedef bitince ödül verilir ve sıradaki adım açılır
    #[test]
    fn guide_runs_automatically() {
        let mut g = Game::new(5, Progress::default(), 1);
        assert!(!g.progress.story.guide_on);
        g.story_tick();
        assert!(g.progress.story.guide_on, "ilk adım kendiliğinden başlamalı");
        // öğreticinin dünyada NPC'si yok
        assert!(!g.npcs.iter().any(|n| n.kind == N_GUIDE));
        for _ in 0..3 {
            g.story_event(Ev::Kill { kind: ZKind::Walker, x: 0.0, y: 0.0 });
        }
        let gold = g.progress.gold;
        g.story_tick();
        assert!(g.progress.gold > gold, "ödül kendiliğinden verilmeli");
        assert_eq!(g.progress.story.guide, 1);
        g.story_tick();
        assert!(g.progress.story.guide_on);
        // üretim adımı başlayınca malzeme verilir
        g.progress.story.guide = 3;
        g.progress.story.guide_on = false;
        g.story_tick();
        assert!(g.progress.inv.count_id("cloth") >= 2);
        let j = g.story_json();
        assert_eq!(j[0]["id"], "g4");
        assert_eq!(j[1]["id"], "q1");
        // Komutan Diana'nın adımları elle alınır
        g.progress.story.guide = GUIDE.iter().position(|q| q.id == "g8").unwrap();
        g.progress.story.guide_on = false;
        g.progress.level = 12;
        g.story_tick();
        assert!(!g.progress.story.guide_on);
    }

    #[test]
    fn reach_quest_marks_map_and_completes() {
        let mut g = Game::new(5, Progress::default(), 1);
        g.progress.level = 3;
        g.progress.story.main = 2; // gizemli ağaç
        // başka bölgedeki görevciden alınırsa hedef başka bölgede: bu bölgenin büyücüsü gösterilir
        npc_at(&mut g, N_QUEST);
        assert_ne!(crate::areas::area_at(g.player.x, g.player.y), crate::areas::FOREST);
        g.talk(N_QUEST, "accept");
        let j = g.story_json();
        let (tx, ty) = world::landmark("tree").pos();
        assert_eq!(j[1]["via"], "Başlangıç Ormanı");
        let (wx, wy) = (j[1]["x"].as_f64().unwrap() as f32, j[1]["y"].as_f64().unwrap() as f32);
        assert!(g.npcs.iter().any(|n| n.kind == N_WIZARD && n.x == wx && n.y == wy));
        // ormandayken ağacın kendisi
        let (cx, cy) = world::camp_spawn();
        g.player.x = cx;
        g.player.y = cy;
        let j = g.story_json();
        assert!(j[1].get("via").is_none());
        assert_eq!(j[1]["x"].as_f64().unwrap() as f32, tx);
        g.player.x = tx;
        g.player.y = ty + 50.0;
        g.step(1.0 / 30.0, Input::default());
        g.story_tick();
        assert_eq!(g.story_json()[1]["ready"], true);
    }

    #[test]
    fn accept_closes_with_single_leave_option() {
        // Silas: görev konuşarak alınır ve teslim edilir
        let mut g = Game::new(5, Progress::default(), 1);
        let n = g.npcs.iter().find(|n| n.kind == N_QUEST).unwrap();
        let (x, y) = (n.x, n.y + 60.0);
        g.player.x = x;
        g.player.y = y;
        let d = g.talk(N_QUEST, "accept");
        let ids: Vec<&str> = d["opts"].as_array().unwrap().iter().map(|o| o["id"].as_str().unwrap()).collect();
        assert_eq!(ids, ["ok"], "kabulden sonra yalnız Yola Çık kalmalı");
        assert!(d["lines"].as_array().unwrap().iter().all(|l| !l.as_str().unwrap().contains("Tamamlandı")));
        assert!(g.progress.story.main_on);
        for _ in 0..5 {
            let (cx, cy) = world::camp_spawn();
            g.story_event(Ev::Kill { kind: ZKind::Walker, x: cx + 25.0 * world::TILE, y: cy });
        }
        let d = g.talk(N_QUEST, "");
        assert!(d["opts"].as_array().unwrap().iter().any(|o| o["id"] == "turnin"), "{d}");
        let d = g.talk(N_QUEST, "turnin");
        assert!(d["opts"].as_array().unwrap().iter().any(|o| o["id"] == "ok"));
    }

    #[test]
    fn chat_topics_menu() {
        let mut g = Game::new(5, Progress::default(), 1);
        for k in [N_MERCHANT, N_QUEST, N_DOCTOR, N_MECHANIC, N_TRAINER] {
            npc_at(&mut g, k);
            let d = g.talk(k, "chat");
            let opts = d["opts"].as_array().unwrap();
            assert!(opts.len() >= 6 && opts.iter().any(|o| o["id"] == "back"));
            for o in opts {
                let id = o["id"].as_str().unwrap();
                if id.starts_with("t:") {
                    let d = g.talk(k, id);
                    assert!(!d["lines"].as_array().unwrap().is_empty(), "{k} {id}");
                }
            }
            let d = g.talk(k, "back");
            assert!(d["opts"].as_array().unwrap().iter().any(|o| o["id"] == "bye"));
        }
    }
}
