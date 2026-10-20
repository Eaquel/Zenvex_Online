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

/// NPC adı ve unvanı
pub fn npc_name(kind: u8) -> (&'static str, &'static str) {
    match kind {
        N_MERCHANT => ("Tüccar Selim", "Tüccar"),
        N_QUEST => ("Yaşlı Hüseyin", "Görevci"),
        N_DOCTOR => ("Doktor Leyla", "Doktor"),
        N_MECHANIC => ("Tamirci Rıza", "Tamirci"),
        N_TRAINER => ("Eğitmen Kerem", "Köpek Eğitmeni"),
        N_GUIDE => ("Rehber Deniz", "Rehber"),
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

/// Rehberin öğretici zinciri: zombi öldürmekten üretime kadar her şeyi öğretir.
pub const GUIDE: &[Quest] = &[
    q!("g1", N_GUIDE, "İlk Adımlar", "Kamptan Çık, 3 Zombi Öldür",
       ["Uyandın demek! Ben Deniz, bu kampın rehberiyim. Dünya çöktü ama biz hâlâ ayaktayız.",
        "Sol joystick ile yürürsün; sonuna kadar itersen koşarsın. Sağ joystick saldırıdır: çevirdiğin yöne vurursun.",
        "Kampın dışındaki ormanda aylak zombiler dolaşıyor. Git, 3 tanesini indir ve bana dön."],
       "İşte bu! Kılıcını iyi kullanıyorsun. Ama bir zombi sürüsü seni kolayca köşeye sıkıştırır…",
       Goal::Kill(None), 3, 1, 20, 40, &[("bandage", 2)], &[]),
    q!("g2", N_GUIDE, "Takla At", "Takla Tuşuyla 2 Kez Yuvarlan",
       ["Kuşatıldığında kaçmayı bilmelisin. Sağdaki takla tuşu seni baktığın yöne yuvarlar.",
        "Yuvarlanırken hiçbir saldırı sana işlemez. 2 kez takla at, göreyim."],
       "Çevik bir hayatta kalan! Takla 5 saniyede bir dolar, iyi zamanla.",
       Goal::Roll, 2, 1, 10, 30, &[], &[]),
    q!("g3", N_GUIDE, "Ganimet", "Yerden 3 Eşya Topla",
       ["Ölen zombilerden ve sandıklardan malzeme düşer: kumaş, bant, kemik…",
        "Üstünden geçmen yeter, çantana girer. 3 eşya topla."],
       "Malzeme her şeydir. Ne kadar çok toplarsan o kadar çok üretirsin.",
       Goal::Loot, 3, 1, 15, 40, &[], &[]),
    q!("g4", N_GUIDE, "İlk Üretim", "Çantadan Üret: 1 Bandaj",
       ["Şimdi üretmeyi öğrenelim. Sana 2 kumaş ve 1 bant veriyorum.",
        "Çantadaki Üretim bölümünden Bandaj'ı seç ve üret. Bandaj her yerde üretilir; bazı tarifler tezgâh ister."],
       "Harika! Kendi bandajını yapan biri kolay kolay ölmez.",
       Goal::Craft("bandage"), 1, 1, 20, 50, &[], &[("cloth", 2), ("tape", 1)]),
    q!("g5", N_GUIDE, "Cebini Hazırla", "Çantada Bir Eşyayı Cebe Sürükle",
       ["Savaşın ortasında çantayı açmaya vaktin olmaz.",
        "Çantayı aç, bir bandaja ya da iksire basılı tut ve soldaki ceplerden birine sürükle.",
        "Cepteki eşyalar ekranın altında tuş olur: tek dokunuşla kullanırsın."],
       "Artık hazırlıklısın. Ceplerin bekleme süresini de gösterir.",
       Goal::Pocket, 1, 1, 10, 30, &[("potion_heal", 1)], &[]),
    q!("g6", N_GUIDE, "Tüccarla Tanış", "Tüccar Selim ile Konuş",
       ["Selim kampın tüccarıdır. Fazla malzemeni satar, ihtiyacın olanı alırsın.",
        "Git, onunla bir konuş."],
       "Selim'le anlaştın mı? Fiyatlarına dikkat et, kurnaz adamdır.",
       Goal::Talk(N_MERCHANT), 1, 1, 10, 30, &[], &[]),
    q!("g7", N_GUIDE, "Ayakta Kal", "Seviye 5'e Ulaş",
       ["Sana öğreteceklerim bu kadar. Gerisi tecrübe.",
        "Yaşlı Hüseyin'in görevlerini yap, ormanda zombi avla. 5. seviyeye ulaşınca gel, seni uğurlayayım.",
        "5. seviyede sohbet de açılır; diğer hayatta kalanlarla konuşabilirsin."],
       "Artık benim yardımıma ihtiyacın yok. Sohbet açıldı; yalnız değilsin. Yolun açık olsun!",
       Goal::Level, 5, 1, 100, 0, &[("potion_power", 1)], &[]),
];

/// Görevcinin ana zinciri (sıradan ve sıra dışı görevler).
pub const MAIN: &[Quest] = &[
    q!("q1", N_QUEST, "Ormanı Temizle", "Başlangıç Ormanında 5 Zombi Öldür",
       ["Gel evlat, gel. Kampın çevresi zombi kaynıyor; geceleri uyuyamıyoruz.",
        "Başlangıç Ormanında 5 zombi öldür. Karşılığında altın ve tecrübe var."],
       "Sağ ol evlat. Bu gece biraz rahat uyuruz.",
       Goal::Kill(Some(Zone::Forest)), 5, 1, 60, 80, &[], &[]),
    q!("q2", N_QUEST, "Sahra Hastanesi", "1 İlk Yardım Kiti Üret",
       ["Doktor Leyla batıdaki sahra hastanesinde yaralılara bakıyor; malzemesi bitmek üzere.",
        "Sana gereken malzemeyi veriyorum: bandaj, bant ve zehir bezi. Tezgâhta 1 İlk Yardım Kiti üret."],
       "Bu kit bir hayat kurtaracak. Leyla'ya selamımı söyle.",
       Goal::Craft("medkit"), 1, 2, 80, 100, &[], &[("bandage", 2), ("tape", 1), ("toxic", 1)]),
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
        "5 zombi köpeği huzura kavuştur. Barınaktaki Kerem'in köpekleri de rahat etsin."],
       "Uluma kesildi. Kerem sana teşekkür etmek isteyebilir.",
       Goal::KillKind(ZKind::Dog), 5, 4, 100, 160, &[("bone", 3)], &[]),
    q!("q6", N_QUEST, "Benzin Kokusu", "Benzin İstasyonundaki Tamirci Rıza'yı Bul",
       ["Kuzeydeki eski benzin istasyonunda Rıza diye bir tamirci kalıyormuş.",
        "Araçlarımızı o tamir edebilir. Git, onunla konuş."],
       "Rıza hâlâ hayatta demek! Artık araçlarımız yolda kalmaz.",
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
    q!("q9", N_QUEST, "Şehrin Kapısı", "Seviye 10'a Ulaş ve Şehre Gir",
       ["Kuzeydeki şehir kapısı ancak güçlü olanlara açılıyor.",
        "10. seviyeye ulaş ve şehre adım at. Orada daha büyük tehlikeler ve hazineler var."],
       "Şehre girdin! Artık bu kampın hikâyesi seninle anlatılacak.",
       Goal::Level, 10, 8, 300, 0, &[("medkit", 2)], &[]),
];

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
    fn quest_progress(&self, q: &Quest, n: u32) -> u32 {
        match q.goal {
            Goal::Level => self.progress.level.min(q.count),
            Goal::Dog => u32::from(self.progress.dog_owned),
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
                (Goal::Dog, Ev::Dog) => true,
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
        let (who, _) = npc_name(q.giver);
        self.events.push(Event::Quest { title: format!("{} · {who} ile konuş", q.title) });
    }

    /// Her saniye: yer ve seviye hedefleri
    pub fn story_tick(&mut self) {
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
                Goal::Level | Goal::Dog => {
                    if self.quest_progress(q, n) >= q.count {
                        self.story_set(which, idx, true, q.count);
                        self.story_ready(q);
                    }
                }
                _ => {}
            }
        }
    }

    /// Görev hedefinin dünyadaki yeri (harita/mini harita işareti): teslimde veren NPC, yer görevinde o yer.
    fn quest_target(&self, q: &Quest, ready: bool) -> Option<(f32, f32)> {
        let npc = |k: u8| {
            let (px, py) = (self.player.x, self.player.y);
            self.npcs
                .iter()
                .filter(|n| n.kind == k)
                .min_by(|a, b| (a.x - px).hypot(a.y - py).total_cmp(&(b.x - px).hypot(b.y - py)))
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
            if let Some((x, y)) = target {
                v["x"] = json!(x);
                v["y"] = json!(y);
            }
            list.push(v);
        }
        json!(list)
    }

    /// Bir NPC ile konuşma: satırlar + seçenekler. `opt`: seçilen seçenek (boşsa açılış).
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
            N_GUIDE => Some(0u8),
            N_QUEST => Some(1u8),
            _ => None,
        };
        match opt {
            "accept" | "turnin" if which.is_some() => {
                let w = which.unwrap();
                let (idx, on, n) = self.story_slot(w);
                if let Some(q) = chain(w).get(idx) {
                    if opt == "accept" && !on && self.progress.level >= q.min_level {
                        self.story_set(w, idx, true, 0);
                        for &(id, c) in q.start_items {
                            let left = self.progress.inv.add_id(id, c);
                            if left > 0 {
                                let (x, y) = (self.player.x, self.player.y);
                                self.spawn_drop_id(id, left, x, y + 30.0);
                            }
                        }
                        lines.push(format!("Güzel. {}", q.hint));
                        self.events.push(Event::Quest { title: format!("Yeni görev: {}", q.title) });
                        self.story_tick();
                    } else if opt == "turnin" && on && self.quest_progress(q, n) >= q.count {
                        lines.push(q.done.to_string());
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
                        lines.push(format!("Ödül: {}", got.join(", ")));
                        self.events.push(Event::Quest { title: format!("Tamamlandı: {}", q.title) });
                        self.story_set(w, idx + 1, false, 0);
                        self.check_level_quests();
                    }
                }
            }
            "chat" => lines.extend(chatter(kind, self.time as usize).iter().map(|s| s.to_string())),
            "shop" => open = "merchant",
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
        // açılışta: görev durumu ya da selam
        if opt.is_empty() {
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
                        lines.push(format!("{} — {} / {}", q.hint, self.quest_progress(q, n), q.count));
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
                lines.push(greeting(kind).to_string());
            }
        }
        // seçenekler
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
            N_MECHANIC => opts.push(("repair".into(), format!("Aracımı Onar ({} Altın)", crate::ui::MECHANIC_PRICE))),
            N_TRAINER => opts.push(("dog".into(), if self.progress.dog_owned { "Köpeğimi Geliştir".into() } else { "Köpek Sahiplen".into() })),
            _ => {}
        }
        opts.push(("chat".into(), "Sohbet Et".into()));
        opts.push(("bye".into(), "Hoşça Kal".into()));
        json!({
            "npc": kind, "name": name, "role": role, "lines": lines, "open": open,
            "opts": opts.iter().map(|(id, l)| json!({"id": id, "label": l})).collect::<Vec<_>>(),
        })
    }

    /// Seviye görevleri teslimden hemen sonra da hazır olabilir
    fn check_level_quests(&mut self) {
        self.story_tick();
    }
}

fn greeting(kind: u8) -> &'static str {
    match kind {
        N_MERCHANT => "Hoş geldin yolcu! Selim'in tezgâhında her şey var: iksir, malzeme, yiyecek. Ne lazım?",
        N_QUEST => "Otur evlat, otur. Yaşlı Hüseyin'in dizleri tutmuyor ama aklı hâlâ yerinde.",
        N_DOCTOR => "Yaralı mısın? Otur şuraya, bir bakayım. Zehirlendiysen de temizlerim.",
        N_MECHANIC => "Motor sesi duydum sandım… Aracın mı bozuldu? Rıza'nın elinden kurtulan araç yoktur.",
        N_TRAINER => "Köpekler insanı kokusundan tanır. Seninki iyi bir koku, sana güvenirler.",
        N_GUIDE => "Bir şey sormak istersen buradayım. Kampın etrafında dikkatli ol.",
        _ => "Kuleden her şeyi görüyorum. Zombileri mızrağımla indiririm, sen içeride güvendesin.",
    }
}

/// NPC başına ara sıra değişen sohbet satırları
fn chatter(kind: u8, seed: usize) -> &'static [&'static str] {
    let all: &[&[&str]] = match kind {
        N_MERCHANT => &[
            &["Eskiden kasabada bir dükkânım vardı. Şimdi bu tezgâh ve bir çadır.", "Ama ticaret ölmez, dostum. İnsanlar hep bir şeye ihtiyaç duyar."],
            &["Kemik toplarsan satın alırım. Köpek eğitmeni bayılıyor onlara.", "Zehir bezi mi? Bataklıktan getirebilirsen iyi para eder."],
        ],
        N_QUEST => &[
            &["Bu kamp ilk kurulduğunda üç kişiydik. Şimdi bak, bir köy olduk.", "Her gelen bir umut getiriyor."],
            &["Şehrin kapısını Titan'ın yıktığını söylüyorlar.", "Ben inanmıyorum… Ama kuzeyden gelen sesleri sen de duydun, değil mi?"],
        ],
        N_DOCTOR => &[
            &["Isırık yaraları kolay iyileşmez. Bandajları sık değiştir.", "Zehirli zombilere yakın durma; panzehir her zaman yanında olsun."],
            &["Bu hastaneyi bir çadırla kurdum. Şimdi on yatağımız var.", "Malzeme getirirsen daha çok insan kurtarırız."],
        ],
        N_MECHANIC => &[
            &["Bu istasyonun son depolarında hâlâ biraz benzin var.", "Bisiklet en sessizidir; zombiler motor sesine koşar, bunu unutma."],
            &["Bir SUV'u tamir etmek için üç gün uğraştım.", "Sonra bir tank zombi devirdi. Hayat işte."],
        ],
        N_TRAINER => &[
            &["Köpeğin seninle birlikte savaşır, zombiler de ona saldırır.", "Canı azalırsa bayılır, biraz dinlenince geri gelir."],
            &["Kemikle eğitilen köpek daha güçlü ısırır.", "Burada sahipsiz kalan köpekleri topluyorum."],
        ],
        N_GUIDE => &[
            &["Gece zombiler daha cesur olur. Fenerin yoksa kampa yakın dur.", "Güvenli bölgeye kaçarsan peşindeki yaratık seni bırakır; tanrılar bazen onları çarpar bile."],
            &["Takla, cepler, üretim… Hepsini öğrendin.", "Bir de haritaya bak: keşfetmediğin yerler bulutla kaplı."],
        ],
        _ => &[&["Mızrağım hiç ıskalamaz.", "Yine de sen dikkatli ol."]],
    };
    all[seed % all.len()]
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

    #[test]
    fn guide_chain_flows() {
        let mut g = Game::new(5, Progress::default(), 1);
        npc_at(&mut g, N_GUIDE);
        let d = g.talk(N_GUIDE, "");
        assert!(d["lines"].as_array().unwrap().len() >= 2);
        assert!(d["opts"].as_array().unwrap().iter().any(|o| o["id"] == "accept"));
        g.talk(N_GUIDE, "accept");
        assert!(g.progress.story.guide_on);
        for _ in 0..3 {
            g.story_event(Ev::Kill { kind: ZKind::Walker, x: 0.0, y: 0.0 });
        }
        let d = g.talk(N_GUIDE, "");
        assert!(d["opts"].as_array().unwrap().iter().any(|o| o["id"] == "turnin"));
        let gold = g.progress.gold;
        g.talk(N_GUIDE, "turnin");
        assert!(g.progress.gold > gold);
        assert_eq!(g.progress.story.guide, 1);
        // üretim görevi kabulde malzeme verir
        g.progress.story.guide = 3;
        g.talk(N_GUIDE, "accept");
        assert!(g.progress.inv.count_id("cloth") >= 2);
        let j = g.story_json();
        assert_eq!(j[0]["id"], "g4");
        assert_eq!(j[1]["id"], "q1");
    }

    #[test]
    fn reach_quest_marks_map_and_completes() {
        let mut g = Game::new(5, Progress::default(), 1);
        g.progress.level = 3;
        g.progress.story.main = 2; // gizemli ağaç
        npc_at(&mut g, N_QUEST);
        g.talk(N_QUEST, "accept");
        let j = g.story_json();
        let (tx, ty) = world::landmark("tree").pos();
        assert_eq!(j[1]["x"].as_f64().unwrap() as f32, tx);
        g.player.x = tx;
        g.player.y = ty + 50.0;
        g.step(1.0 / 30.0, Input::default());
        g.story_tick();
        assert_eq!(g.story_json()[1]["ready"], true);
    }
}
