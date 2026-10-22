//! NPC sohbet konuları: her NPC'nin kendi bölgesi, geçmişi, söylentileri ve oyun ipuçları.
//! Satırlar normal cümledir; seçenek etiketleri Baş Harfi Büyük yazılır.
use crate::story::{N_DOCTOR, N_GUIDE, N_MECHANIC, N_MERCHANT, N_QUEST, N_TRAINER, N_WIZARD};

/// Sohbet menüsü konuları (kimlik, etiket). "todo" oyuncunun durumuna göre story.rs'de üretilir.
pub fn topics(kind: u8) -> &'static [(&'static str, &'static str)] {
    match kind {
        N_MERCHANT => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Ticaret Taktikleri"), ("rumor", "Söylenti Var Mı?")],
        N_QUEST => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Eski Günler"), ("rumor", "Söylenti Var Mı?")],
        N_DOCTOR => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Sağlık Tavsiyesi"), ("rumor", "Söylenti Var Mı?")],
        N_MECHANIC => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Araçlar Hakkında"), ("rumor", "Söylenti Var Mı?")],
        N_TRAINER => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Köpekler Hakkında"), ("rumor", "Söylenti Var Mı?")],
        N_GUIDE => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Temel Bilgiler"), ("rumor", "Söylenti Var Mı?")],
        N_WIZARD => &[("todo", "Ne Yapmalıyım?"), ("area", "Kişisel Üs Nedir?"), ("self", "Kendinden Bahset"), ("tip", "Devlet Üsleri"), ("rumor", "Söylenti Var Mı?")],
        _ => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Kuleler Hakkında"), ("rumor", "Söylenti Var Mı?")],
    }
}

/// Sohbet menüsü açılınca NPC'nin söylediği giriş satırı
pub fn chat_intro(kind: u8) -> &'static str {
    match kind {
        N_MERCHANT => "Konuşmak bedava, dostum. Ama lafı uzatırsan bir şey almak zorunda kalırsın, haha!",
        N_QUEST => "Otur şu kütüğe evlat. Yaşlı bir adamın anlatacak çok şeyi vardır.",
        N_DOCTOR => "Hastalarım şimdilik uyuyor, birkaç dakikam var. Ne merak ediyorsun?",
        N_MECHANIC => "Elim yağlı ama kulağım sende. Sor bakalım.",
        N_TRAINER => "Köpekler dinlenirken ben de biraz soluklanayım. Ne konuşalım?",
        N_GUIDE => "Sorularını sevdim; soru soran hayatta kalır. Ne öğrenmek istersin?",
        N_WIZARD => "Otur, ateş sıcak. Sorularının cevabı bende; ama bazı cevaplar soru sormaktan daha ağırdır.",
        _ => "Nöbetteyim ama gözüm ufukta, kulağım sende. Çabuk sor.",
    }
}

/// Sabit konuların satırları; `seed` söylentileri ve kişisel hikâyeleri değiştirir.
pub fn topic_lines(kind: u8, topic: &str, seed: usize) -> Vec<&'static str> {
    let pick = |all: &[&'static [&'static str]]| all[seed % all.len()].to_vec();
    match (kind, topic) {
        // ------------------------------------------------------------------ tüccar
        (N_MERCHANT, "area") => vec![
            "Burası kampın kalbi. Kamp ateşi, tezgâhlar, çadırlar… Zombiler sınırı geçemez; nöbetçiler ve tanrılar buna izin vermez.",
            "Batıda Doktor Elena'nın sahra hastanesi, doğuda Jack'in köpek barınağı, kuzeyde de Viktor'un benzinliği var.",
            "Hepsi güvenli karakoldur. Bir yere sıkışırsan oralardan birine koş.",
        ],
        (N_MERCHANT, "self") => pick(&[
            &["Ben Marco. Salgından önce kasabanın en büyük nalbur dükkânı benimdi.",
              "Çivi, ip, bant… Bugün bunlar altından kıymetli. Ben de onları satarak hayatta kalıyorum.",
              "Kimse beni kahraman sanmasın; ben sadece insanlara ihtiyaçlarını veriyorum."],
            &["Karım ve kızım güneydeki kampa kaçtı. Bir gün yollar açılırsa onları bulacağım.",
              "O güne kadar bu tezgâhtan ayrılmam. Kervan gelirse ilk ben duyarım."],
        ]),
        (N_MERCHANT, "tip") => vec![
            "Malzemeyi çöpe atma; kumaş, bant ve ip neredeyse her tarifte lazım.",
            "Fazla kemiğin, dişin varsa bana sat. Ama zehir bezini satma; panzehir için sakla.",
            "Ticaret dalını geliştirirsen sana daha ucuza satarım. Söz.",
            "Bir de… Gece fiyatlarım aynıdır ama yolların fiyatı canındır. Akşam olmadan dön.",
        ],
        (N_MERCHANT, "rumor") => pick(&[
            &["Bir müşterim şehirde, eski bankanın kasasında asker sandıkları gördüğünü söyledi.", "Doğru mu bilmem; o adam bir kere de beni kandırmıştı."],
            &["Kuzeyden gelen bir yolcu, Titan'ın göğsünde ateş yandığını anlattı.", "Kalbi bir fırın gibiymiş. Ben görmek istemem."],
            &["Gizemli ağacın altında mantar toplayan bir kadın varmış.", "Kimse yüzünü görmemiş. Selamını alanlar zombi görmeden eve dönmüş."],
        ]),
        // ------------------------------------------------------------------ görevci
        (N_QUEST, "area") => vec![
            "Kampın etrafı Başlangıç Ormanı; aylak zombiler dolaşır ama yavaştırlar.",
            "Daha kuzeye gidersen Derin Orman başlar. Orada koşucular avlanır; nefesini iyi ayarla.",
            "Batıda bataklık var, zehirli kaynağın çevresi. En kuzeyde de şehir surları… 10. seviyeye kadar kapı sana açılmaz.",
        ],
        (N_QUEST, "self") => pick(&[
            &["Kırk yıl köy öğretmenliği yaptım evlat. Çocuklara harita okumayı öğretirdim.",
              "Şimdi haritaları kendim çiziyorum; her kâğıt bir hayat kurtarır.",
              "Bacaklarım artık yürümüyor ama aklım hâlâ yolları hatırlıyor."],
            &["Bu kampı üç kişi kurduk: ben, Marco ve bir de asker. Adını söylemem; o kuledeki nöbetinden hiç dönmedi.",
              "Onun mızrağı hâlâ kulede asılı. Askerler onu uğur sayar."],
        ]),
        (N_QUEST, "tip") => vec![
            "Eskiden bu ormanda ayı yoktu ama av vardı. Şimdi avcı biziz, av da onlar.",
            "Görevlerimi bitirdikçe sana daha zor işler veririm. Her biri haritada işaretlidir; takipçiye dokun, nereye gideceğini görürsün.",
            "Günlük görevleri de unutma. Küçük işlerdir ama altını ve tecrübeyi iyi verir.",
        ],
        (N_QUEST, "rumor") => pick(&[
            &["Bataklıkta bir kurbağanın konuştuğunu duydum. Herhalde zehirli suyu içen biri uydurmuştur.", "Ama yine de… Kaynağı temizlersen öğreniriz."],
            &["Şehrin kapısını kimin yıktığını herkes merak ediyor.", "Bence Titan değil; kapı içeriden açıldı. Biri bir şey saklıyor."],
            &["Geceleri ormanda bir ışık dolaşıyormuş.", "Fener mi, ruh mu, bilmiyorum. Bir gece sen de bak."],
        ]),
        // ------------------------------------------------------------------ doktor
        (N_DOCTOR, "area") => vec![
            "Sahra hastanesi kamptan biraz batıda. Çadır eski ama yataklar temiz.",
            "Burası güvenli karakol; zombiler içeri giremez. Yaralıysan buraya kadar koş, gerisini ben hallederim.",
        ],
        (N_DOCTOR, "self") => pick(&[
            &["Şehir hastanesinde acil serviste çalışıyordum. İlk ısırık vakası bana geldi.",
              "Ne olduğunu anlamamız iki gün sürdü. O iki gün her şeyi değiştirdi.",
              "Şimdi tek yapabildiğim, buraya ulaşanları hayatta tutmak."],
            &["Uyumuyorum desem yalan olur, ama az uyuyorum.", "Her gece birileri kapıya gelir. Ben de kapıyı her gece açarım."],
        ]),
        (N_DOCTOR, "tip") => vec![
            "Bandaj küçük yaralar içindir; az iyileştirir ama hemen etki eder.",
            "İlk yardım kiti ağır yaralanmalarda hayat kurtarır. Tezgâhta bandaj, bant ve zehir beziyle üretilir.",
            "Zehirlendiysen canın yavaş yavaş akar. Panzehir iç ya da bana gel.",
            "Sağlık eşyalarını ceplerine koy; çantayı karıştırırken zombi beklemez.",
        ],
        (N_DOCTOR, "rumor") => pick(&[
            &["Bataklıktan gelen yaralıların kanı yeşile çalıyor.", "Zehir orada bir yerden kaynıyor, kaynağı temizlenmeli."],
            &["Karanlık zombilerin gözleri mor parlıyor. Işıktan kaçmıyorlar, ışığa koşuyorlar.", "Fenerini gece dikkatli kullan."],
        ]),
        // ------------------------------------------------------------------ tamirci
        (N_MECHANIC, "area") => vec![
            "Bu benzinlik kampın kuzeyinde, yolun kıyısında. Depolarda hâlâ biraz yakıt var.",
            "Etrafta hurda araçlar dolu; parça sökmeye çalışma, alarmı öter, sürü gelir.",
            "Burası da güvenli karakol. Aracın bozulursa yanıma getir.",
        ],
        (N_MECHANIC, "self") => pick(&[
            &["Otuz yıl kamyon tamir ettim. Motorun sesinden derdini anlarım.",
              "Salgın başladığında herkes kaçıyordu; ben kaçanların arabalarını tamir ediyordum.",
              "Sonunda kaçacak kimse kalmadı, ben de buraya yerleştim."],
            &["Bir arabayı tamir etmek için üç gün uğraştım.", "Sonra bir tank zombi devirdi. Hayat işte."],
        ]),
        (N_MECHANIC, "tip") => vec![
            "Motor tek kişiliktir ama çevik. Araba iki kişiliktir; yanına bir arkadaşını al, dışarıdan ikiniz de görünürsünüz.",
            "Yakıt biterse araç durur. Yakıt bidonu ya da benim pompam lazım.",
            "Zombilere çarpmak onları ezer ama aracın da hasar alır. Duman çıkıyorsa yanıma getir.",
        ],
        (N_MECHANIC, "rumor") => pick(&[
            &["Şehirdeki sanayi bölgesinde çalışan bir jeneratör sesi duyuluyormuş.", "Biri orada yaşıyor olmalı. Ya da bir şey."],
            &["Askerî kamyonların konvoyu son kez kuzeye giderken görülmüş.", "İçlerinde ne vardı, kimse bilmiyor."],
        ]),
        // ------------------------------------------------------------------ köpek eğitmeni
        (N_TRAINER, "area") => vec![
            "Barınak kampın doğusunda. Sahipsiz kalan köpekleri burada topluyorum.",
            "Çevredeki zombi köpekler bir zamanlar bu köpeklerin kardeşiydi. Onlara acı ama dikkatli ol; sürüyle saldırırlar.",
        ],
        (N_TRAINER, "self") => pick(&[
            &["Ben Jack. Eskiden polis köpeklerini eğitirdim.",
              "Salgın gecesi köpeklerim beni uyandırdı; havlamasalar şimdi burada olmazdım.",
              "O yüzden onlara borçluyum. Her sahipsiz köpeğe bir yuva bulacağım."],
            &["İlk köpeğimin adı Karabaş'tı.", "Şimdi bu barınaktaki her yavru onun torunu sayılır."],
        ]),
        (N_TRAINER, "tip") => vec![
            "Köpek yalnızca benden, bu barınaktan alınır. Başka kimseye güvenme.",
            "Köpeğin seni takip eder, yakındaki düşmana saldırır. Zombiler de ona saldırır, onu korumayı unutma.",
            "Canı biterse bayılır ve 30 saniye sonra kendine gelir.",
            "Isırık, can ve iyileşme olmak üzere üç yönü var; altınla burada geliştiririm.",
        ],
        (N_TRAINER, "rumor") => pick(&[
            &["Ormanda beyaz bir kurt görülmüş. Zombi değilmiş.", "Gözleri insan gibi bakıyormuş. Bulursan bana söyle."],
            &["Zombi köpekler geceleri uluyunca barınaktakiler susuyor.", "Korkudan değil; dinliyorlar sanki."],
        ]),
        // ------------------------------------------------------------------ rehber
        (N_GUIDE, "area") => vec![
            "Bu kamp güvenli bölge: içeride kimse sana saldıramaz, peşindeki zombi sınırda durur.",
            "Kampın çevresi Başlangıç Ormanı. Seviye arttıkça kuzeye, derin ormana ve bataklığa açılırsın.",
            "Haritayı açınca keşfetmediğin yerler bulutla kaplı görünür; gezdikçe açılır.",
        ],
        (N_GUIDE, "self") => pick(&[
            &["Benim adım Nova. Bu kampa ilk gelenlere yol gösteriyorum.",
              "Ben de bir zamanlar senin gibi uyandım; kimse bana bir şey anlatmadı.",
              "Kimse aynı korkuyu yaşamasın diye buradayım."],
            &["Üniversitede coğrafya okuyordum. Haritayı ezbere bilirim.", "Ama haritada olmayan şeyler de var; onları sen keşfedeceksin."],
        ]),
        (N_GUIDE, "tip") => vec![
            "Yürümek ve koşmak yorulmaz; ama yumruk, kılıç savurmak ve takla dayanıklılık harcar. Sarı çubuk biterse saldırın yavaşlar.",
            "Takla atarken hiçbir saldırı sana işlemez; kuşatıldığında baktığın yöne yuvarlan.",
            "Ağaç kesmek emek ister; birkaç vuruşta değil, sabırla devrilir. Odun, tarifler için şart.",
            "Çantada bir eşyaya basılı tutup ceplere sürükle; cepteki eşyalar ekranın altında tek dokunuşla kullanılır.",
        ],
        (N_GUIDE, "rumor") => pick(&[
            &["Gizemli ağacın çevresinde zombilerin durup baktığını gördüm.", "Ağaca değil, gökyüzüne bakıyorlardı."],
            &["Bazı hayatta kalanlar güneydeki eski askerî üslerden bahsediyor.", "Askerler hâlâ oradaymış, ama kimin için savaştıklarını bilen yok."],
        ]),
        // ------------------------------------------------------------------ kule askeri
        (_, "area") => vec![
            "Kuleden her şeyi görürüm: orman, yol, şehrin dumanı.",
            "Kampa yaklaşan her yaratığa mızrağımı atarım. Sen içerideyken rahat ol.",
        ],
        (_, "self") => vec![
            "Bölüğümden geriye ben kaldım. Mızrak kullanmayı burada öğrendim; mermi çoktan bitti.",
            "Nöbet bana iyi geliyor. Düşünecek vakit bırakmıyor.",
        ],
        (_, "tip") => vec![
            "Kuleler güvenli bölgenin sınırını korur. Peşinde zombi varsa kuleye doğru koş.",
            "Zombiler güvenli bölgeye giremez; girmeye kalkanı gökten bir yıldırım çarpar.",
        ],
        (_, "rumor") => pick(&[
            &["Dün gece kuzeyde bir helikopter sesi duydum.", "Kim uçurur bu zamanda? Bir bilen varsa söylesin."],
            &["Titan arenasından gelen kükremeyi kulede bile hissettim.", "Hazır olmadan oraya gitme."],
        ]),
        // ------------------------------------------------------------------ büyücü
        (N_WIZARD, "area") => vec![
            "Kişisel üssün, dünyanın kıyısında, duvarlarla çevrili küçük bir bahçe. Oraya yalnız ben kapı açabilirim.",
            "Kimse göremez, kimse giremez. Sandıklarını orada kur, eşyalarını sakla; çantan hafiflesin.",
            "İstersen öldüğünde orada uyanırsın. Bunu bana üssünde söyle, ayarlarım.",
        ],
        (N_WIZARD, "self") => pick(&[
            &["Adım Aldric. Salgından önce şehrin rasathanesinde yıldızları sayardım.",
              "Gökyüzü değişince ben de değiştim. Artık yıldızlar değil, kapılar sayıyorum.",
              "Her kapı bir yeri hatırlar. Sen bir yeri görmeden ben seni oraya götüremem."],
            &["Bu kapıları bana kim öğretti diye sorma. Rüyamda yaşlı bir kadın vardı; gizemli ağacın altında oturuyordu.",
              "Uyandığımda ellerimde mavi bir ışık vardı. O günden beri yolcuları taşırım."],
        ]),
        (N_WIZARD, "tip") => vec![
            "Haritada elli devlet üssü var. Askerler duvarların ardında bekler: M4'lüler yakına seri atar, keskin nişancılar uzaktan.",
            "Keskin nişancının kırmızı lazerini görürsen yerinden kıpırda ya da takla at; mermi lazerin olduğu yere gider.",
            "Askerleri temizleyip klanınla bayrağın dibinde on saniye durursan üs klanının olur.",
            "Klanının üssü dakikada 60 klan tecrübesi verir; ödül süresi dolunca eşya klan deposuna düşer.",
            "Yeni askerler helikopterle iner ve ilk beş saniye hiçbir şey onlara işlemez. Boşuna vurma, siper al.",
        ],
        (N_WIZARD, "rumor") => pick(&[
            &["Donmuş Liman'dan gelen bir tüccar, buz tutmuş konteynerlerin içinde ışık gördüğünü anlattı.", "Kim bilir, belki asker sandıklarıdır, belki de daha kötüsü."],
            &["Yanık Orman hâlâ tütüyor. Ateşi kimin yaktığını bilen yok; ama küllerin arasında koşan şeyler var."],
            &["Askeri Bölge'nin hangarlarında devlet son birliklerini saklıyormuş. Helikopter sesleri geceleri buraya kadar gelir."],
        ]),
        _ => vec!["Bunu bilmiyorum, kusura bakma."],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_npc_has_rich_topics() {
        for k in [0u8, N_MERCHANT, N_QUEST, N_DOCTOR, N_MECHANIC, N_TRAINER, N_GUIDE] {
            assert!(topics(k).len() >= 5);
            for (id, _) in topics(k) {
                if *id != "todo" {
                    for seed in 0..3 {
                        assert!(topic_lines(k, id, seed).len() >= 2, "{k} {id}");
                    }
                }
            }
        }
    }
}
