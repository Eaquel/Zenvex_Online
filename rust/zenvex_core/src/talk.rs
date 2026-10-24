//! NPC sohbet konuları: her NPC'nin kendi bölgesi, geçmişi, söylentileri ve oyun ipuçları.
//! Satırlar normal cümledir; seçenek etiketleri Baş Harfi Büyük yazılır.
use crate::story::{N_COMMANDER, N_DOCTOR, N_MECHANIC, N_MERCHANT, N_QUEST, N_TRAINER, N_WIZARD};

/// Sohbet menüsü konuları (kimlik, etiket). "todo" oyuncunun durumuna göre story.rs'de üretilir.
pub fn topics(kind: u8) -> &'static [(&'static str, &'static str)] {
    match kind {
        N_MERCHANT => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Ticaret Taktikleri"), ("rumor", "Söylenti Var Mı?")],
        N_QUEST => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Eski Günler"), ("rumor", "Söylenti Var Mı?")],
        N_DOCTOR => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Sağlık Tavsiyesi"), ("rumor", "Söylenti Var Mı?")],
        N_MECHANIC => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Araçlar Hakkında"), ("rumor", "Söylenti Var Mı?")],
        N_TRAINER => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Köpekler Hakkında"), ("rumor", "Söylenti Var Mı?")],
        N_WIZARD => &[("todo", "Ne Yapmalıyım?"), ("area", "Kişisel Üs Nedir?"), ("self", "Kendinden Bahset"), ("tip", "Bölgeler ve Üsler"), ("rumor", "Söylenti Var Mı?")],
        N_COMMANDER => &[("todo", "Ne Yapmalıyım?"), ("area", "Bu Bölge Hakkında"), ("self", "Kendinden Bahset"), ("tip", "Devlet Üsleri"), ("rumor", "Söylenti Var Mı?")],
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
        N_WIZARD => "Otur, ateş sıcak. Sorularının cevabı bende; ama bazı cevaplar soru sormaktan daha ağırdır.",
        N_COMMANDER => "Nöbet değişimine kadar vaktim var. Sor asker, ama kısa kes.",
        _ => "Nöbetteyim ama gözüm ufukta, kulağım sende. Çabuk sor.",
    }
}

/// Bulunulan bölgenin tanıtımı (her NPC "Bu Bölge Hakkında" sorulunca bunu anlatır; V15 kare bölgeler)
fn area_lines(area: &str) -> Vec<&'static str> {
    match area {
        "city" => vec![
            "Yıkık Şehir'desin. Güneydeki surun dibinde güvenli meydan var: pazar, doktor, tamirci, eğitmen ve büyücü orada.",
            "Kuzeye çıktıkça önce banliyö, sonra merkez, en tepede sanayi gelir. Her adımda zombiler biraz daha sertleşir.",
            "Kuzeydoğu köşedeki eski arena boş. Titan artık her çeyrek saatte başka bir bölgede uyanıyor.",
            "Elektronik parça, dişli, pil, paslı anahtar ve altın saat en çok bu şehirden çıkar.",
        ],
        "burnt" => vec![
            "Yanık Orman'dasın: kül, duman ve kömürleşmiş ağaçlar. Kül Karakolu güneyde, büyücü de orada.",
            "Koşucular ve karanlık zombiler dumanın içinde avlanır. Uzun süre aynı yerde durma.",
            "Kömür ve yanık reçine bu küllerin arasından çıkar; güç ve hız iksirleri için lazım.",
        ],
        "desert" => vec![
            "Çöl Otoyolu'ndasın. Çöl Karakolu güneyde; büyücü onun yanında bekler.",
            "Tank zombiler yavaştır ama vurdukları yer çöker. Kuduz köpekler sürüyle gelir, kenarda kalma.",
            "Kurutulmuş deri, benzin ve akrep zehri bu kumların malıdır.",
        ],
        "bswamp" => vec![
            "Kara Bataklık: zehirli sis ve kara su. Karakol güneyde, büyücü yanında.",
            "Toksik zombilerin vuruşu zehirler. Yanına panzehir almadan buraya gelme.",
            "Bataklık yosunu, toksik öz ve bataklık demiri burada çıkar.",
        ],
        "harbor" => vec![
            "Donmuş Liman'dasın: buz tutmuş rıhtım, konteynerler ve donmuş gemiler. Liman Karakolu güneyde.",
            "Dünyanın en tehlikeli zombileri burada. Can iksirlerin cebinde olsun.",
            "Donmuş balık, buz kristali ve karanlık kristal yalnız bu limandan çıkar.",
        ],
        "front" => vec![
            "Cephe Hattı'ndasın. Burada zombi yok, askerler var. Karargâh güneyin ortasında.",
            "Kuzeye doğru elli üs ızgara gibi dizili. Her üsün duvarları, kuleleri ve askerleri var.",
            "Üsleri klanınla ele geçirirsin; tuttuğun üs klanına tecrübe ve ödül kazandırır.",
            "Barut kesesi ve askerî devre kartı askerlerden ve üs sandıklarından düşer.",
        ],
        "home" => vec![
            "Burası senin üssün. Kimse göremez, kimse giremez; yalnız büyücü kapı açar.",
            "Sandıklarını ve tezgâhlarını buraya kurarsan kalıcı olur. Dışarıda kurduğun tezgâh on dakika durur.",
        ],
        _ => vec![
            "Başlangıç Ormanı'ndasın. Kamp güneyde: ateş, tüccarın durağı ve Büyücü Aldric orada.",
            "Batıda Doktor Elena'nın sahra hastanesi, doğuda Jack'in köpek barınağı, kuzeyde Viktor'un benzinliği var. Üçü de güvenli.",
            "Doğuya gittikçe ağaçlar sıklaşır ve Derin Orman başlar. Batıdaki bataklıkta zehirli bir kaynak var.",
            "Bu orman bir kare; kenarlarının ötesi boşluk. Başka bölgelere yürüyerek gidilmez, Büyücü Aldric götürür.",
        ],
    }
}

/// Sabit konuların satırları; `seed` söylentileri ve kişisel hikâyeleri değiştirir, `area` bulunulan bölge.
pub fn topic_lines(kind: u8, topic: &str, seed: usize, area: &str) -> Vec<&'static str> {
    let pick = |all: &[&'static [&'static str]]| all[seed % all.len()].to_vec();
    match (kind, topic) {
        (N_WIZARD, "area") => vec![
            "Kişisel üssün, dünyanın kıyısında duvarlarla çevrili küçük bir bahçe. Oraya yalnız ben kapı açabilirim.",
            "Kimse göremez, kimse giremez. Sandıklarını orada kur, eşyalarını sakla; çantan hafiflesin.",
            "İstersen öldüğünde orada uyanırsın. Bunu bana üssünde söyle, ayarlarım.",
        ],
        (_, "area") => area_lines(area),
        // ------------------------------------------------------------------ tüccar
        (N_MERCHANT, "self") => pick(&[
            &["Ben Marco. Salgından önce kasabanın en büyük nalbur dükkânı benimdi.",
              "Şimdi dükkânım bu kamyonet. Karakoldan karakola gider, her durakta birkaç dakika beklerim.",
              "Kimse beni kahraman sanmasın; ben sadece insanlara ihtiyaçlarını getiriyorum."],
            &["Karım ve kızım güneydeki kampa kaçtı. Bir gün yollar açılırsa onları bulacağım.",
              "O güne kadar direksiyonu bırakmam. Her durakta birilerine onları sorarım."],
        ]),
        (N_MERCHANT, "tip") => vec![
            "Her gelişimde başka mallar getiririm ve herkes aynı malları görür. Neyi ne sıklıkla getirdiğimi Olasılıklar sekmesinde gösteririm.",
            "Malzemeyi çöpe atma: kumaş, ip ve paslı metal neredeyse her tarifte lazım.",
            "Fazla kemiğin ya da derin varsa bana sat; kaç tane satacağını sen seçersin.",
            "Ticaret yeteneğini geliştirirsen eşyalarını daha iyi fiyata alırım. Söz.",
        ],
        (N_MERCHANT, "rumor") => pick(&[
            &["Bir müşterim şehirde, eski bankanın kasasında asker sandıkları gördüğünü söyledi.", "Doğru mu bilmem; o adam bir kere de beni kandırmıştı."],
            &["Bir yolcu, Titan'ın göğsünde ateş yandığını anlattı.", "Kalbi bir fırın gibiymiş. Ben görmek istemem."],
            &["Gizemli ağacın altında mantar toplayan bir kadın varmış.", "Kimse yüzünü görmemiş. Selamını alanlar zombi görmeden eve dönmüş."],
        ]),
        // ------------------------------------------------------------------ görevci
        (N_QUEST, "self") => pick(&[
            &["Kırk yıl köy öğretmenliği yaptım evlat. Çocuklara harita okumayı öğretirdim.",
              "Şimdi haritaları kendim çiziyorum; her kâğıt bir hayat kurtarır.",
              "Bacaklarım artık yürümüyor ama aklım hâlâ yolları hatırlıyor."],
            &["Bu kampı üç kişi kurduk: ben, Marco ve bir asker. Adını söylemem; o kuledeki nöbetinden hiç dönmedi.",
              "Onun mızrağı hâlâ kulede asılı. Askerler onu uğur sayar."],
        ]),
        (N_QUEST, "tip") => vec![
            "Eskiden bu ormanda av vardı. Şimdi avcı biziz, av da onlar.",
            "Görevlerimi bitirdikçe sana daha zor işler veririm. Hedef haritanda işaretlidir; Görevler tuşunda da yazar.",
            "Günlük ve haftalık görevleri unutma. Küçük işlerdir ama altın, tecrübe ve bilet puanı verir.",
        ],
        (N_QUEST, "rumor") => pick(&[
            &["Bataklıkta bir kurbağanın konuştuğunu duydum. Herhalde zehirli suyu içen biri uydurmuştur.", "Ama yine de… Kaynağı temizlersen öğreniriz."],
            &["Şehrin kapısını kimin yıktığını herkes merak ediyor.", "Bence Titan değil; kapı içeriden açıldı. Biri bir şey saklıyor."],
            &["Geceleri ormanda bir ışık dolaşıyormuş.", "Fener mi, ruh mu, bilmiyorum. Bir gece sen de bak."],
        ]),
        // ------------------------------------------------------------------ doktor
        (N_DOCTOR, "self") => pick(&[
            &["Şehir hastanesinde acil serviste çalışıyordum. İlk ısırık vakası bana geldi.",
              "Ne olduğunu anlamamız iki gün sürdü. O iki gün her şeyi değiştirdi.",
              "Şimdi tek yapabildiğim, buraya ulaşanları hayatta tutmak."],
            &["Uyumuyorum desem yalan olur, ama az uyuyorum.", "Her gece birileri kapıya gelir. Ben de kapıyı her gece açarım."],
        ]),
        (N_DOCTOR, "tip") => vec![
            "Bandaj küçük yaralar içindir: iki kumaş ve bir reçineyle elde yapılır, hemen etki eder.",
            "İlk yardım kiti ağır yaralarda hayat kurtarır. Tezgâhta iki bandaj, bataklık yosunu ve kurutulmuş deriyle yapılır.",
            "Zehirlendiysen canın yavaş yavaş akar. Panzehir iç ya da bana gel; yirmi altına seni temizlerim.",
            "Sağlık eşyalarını ceplerine koy; çantayı karıştırırken zombi beklemez.",
        ],
        (N_DOCTOR, "rumor") => pick(&[
            &["Kara Bataklık'tan gelen yaralıların kanı yeşile çalıyor.", "Zehir orada bir yerden kaynıyor."],
            &["Karanlık zombilerin gözleri mor parlıyor. Işıktan kaçmıyorlar, ışığa koşuyorlar.", "Fenerini gece dikkatli kullan."],
        ]),
        // ------------------------------------------------------------------ tamirci
        (N_MECHANIC, "self") => pick(&[
            &["Otuz yıl kamyon tamir ettim. Motorun sesinden derdini anlarım.",
              "Salgın başladığında herkes kaçıyordu; ben kaçanların arabalarını tamir ediyordum.",
              "Sonunda kaçacak kimse kalmadı, ben de buraya yerleştim."],
            &["Bir arabayı tamir etmek için üç gün uğraştım.", "Sonra bir tank zombi devirdi. Hayat işte."],
        ]),
        (N_MECHANIC, "tip") => vec![
            "Bana parçaları getir, aracını ben yaparım: tekerlek, motor ve gövde. Hazır olunca anahtarı postayla yollarım.",
            "Motosiklet tek kişilik ama çeviktir. Araba iki kişiliktir; yanına bir arkadaşını al.",
            "Yakıt biterse araç durur. Benzin çölde bulunur; bidonu çantanda taşı.",
            "Zombilere çarpmak onları ezer ama aracın da hasar alır. Duman çıkıyorsa yanıma getir.",
        ],
        (N_MECHANIC, "rumor") => pick(&[
            &["Şehirdeki sanayi bölgesinde çalışan bir jeneratör sesi duyuluyormuş.", "Biri orada yaşıyor olmalı. Ya da bir şey."],
            &["Askerî kamyonların konvoyu son kez cepheye giderken görülmüş.", "İçlerinde ne vardı, kimse bilmiyor."],
        ]),
        // ------------------------------------------------------------------ köpek eğitmeni
        (N_TRAINER, "self") => pick(&[
            &["Ben Jack. Eskiden polis köpeklerini eğitirdim.",
              "Salgın gecesi köpeklerim beni uyandırdı; havlamasalar şimdi burada olmazdım.",
              "O yüzden onlara borçluyum. Her sahipsiz köpeğe bir yuva bulacağım."],
            &["İlk köpeğimin adı Karabaş'tı.", "Şimdi barınaktaki her yavru onun torunu sayılır."],
        ]),
        (N_TRAINER, "tip") => vec![
            "Köpek yalnızca benden alınır. Başka kimseye güvenme.",
            "Köpeğin seni takip eder, yakındaki düşmana saldırır. Zombiler de ona saldırır; onu korumayı unutma.",
            "Canı biterse bayılır ve otuz saniye sonra kendine gelir.",
            "Isırık, can ve iyileşme olmak üzere üç yönü var; altınla ben geliştiririm.",
        ],
        (N_TRAINER, "rumor") => pick(&[
            &["Ormanda beyaz bir kurt görülmüş. Zombi değilmiş.", "Gözleri insan gibi bakıyormuş. Bulursan bana söyle."],
            &["Zombi köpekler geceleri uluyunca barınaktakiler susuyor.", "Korkudan değil; dinliyorlar sanki."],
        ]),
        // ------------------------------------------------------------------ büyücü
        (N_WIZARD, "self") => pick(&[
            &["Adım Aldric. Salgından önce şehrin rasathanesinde yıldızları sayardım.",
              "Gökyüzü değişince ben de değiştim. Artık yıldızlar değil, kapılar sayıyorum.",
              "Dünya artık parça parça; her bölge ayrı bir kare. Bölgeden bölgeye yürünmez, kapımdan geçilir."],
            &["Bu kapıları bana kim öğretti diye sorma. Rüyamda yaşlı bir kadın vardı; gizemli ağacın altında oturuyordu.",
              "Uyandığımda ellerimde mavi bir ışık vardı. O günden beri yolcuları taşırım."],
        ]),
        (N_WIZARD, "tip") => vec![
            "Dünya yedi bölgeye ayrıldı: orman, şehir, yanık orman, çöl, kara bataklık, liman ve cephe. Her birinden kendi malzemesi çıkar.",
            "Haritama bak: bir bölgeye dokunursan orada ne yaşadığını, ne düştüğünü ve kaçıncı seviyede gitmen gerektiğini söylerim.",
            "Kilitli adalar karanlık ve kilitlidir; seviyen yetince kilit açılır.",
            "Elli devlet üssünün hepsi Cephe Hattı'nda; 10. seviyeden sonra seni oraya ben götürürüm.",
            "Askerleri temizleyip klanınla bayrağın dibinde on saniye durursan üs klanının olur.",
            "Keskin nişancının kırmızı lazerini görürsen yerinden kıpırda ya da takla at; mermi lazerin olduğu yere gider.",
        ],
        (N_WIZARD, "rumor") => pick(&[
            &["Donmuş Liman'dan gelen bir tüccar, buz tutmuş konteynerlerin içinde ışık gördüğünü anlattı.", "Kim bilir, belki asker sandıklarıdır, belki de daha kötüsü."],
            &["Yanık Orman hâlâ tütüyor. Ateşi kimin yaktığını bilen yok.", "Ama küllerin arasında koşan şeyler var; yalnız gitme."],
            &["Titan tek yerde durmuyor. Her çeyrek saatte başka bir bölgede uyanıyor.", "Haritama bakarsan nerede olduğunu görürsün; kırmızı işaret onun."],
        ]),
        // ------------------------------------------------------------------ komutan
        (N_COMMANDER, "self") => vec![
            "Salgından önce sınır karakolundaydım. Emir gelmedi, ben de emir vermeye başladım.",
            "Şehre ve cepheye gelen her hayatta kalanı kayda geçiririm. Sen de artık kayıtlısın asker.",
        ],
        (N_COMMANDER, "tip") => vec![
            "Üs almak için önce askerleri temizle, sonra bayrak halkasında on saniye dur. Klanın yoksa üs tutamazsın.",
            "Keskin nişancının lazeri üstündeyse yer değiştir ya da takla at.",
            "Yeni askerler helikopterle iner ve ilk beş saniye hiçbir şey onlara işlemez. Boşuna vurma, siper al.",
            "Tuttuğun üssü boş bırakma: devlet geri almak için asker indirir, bazen de bombalar.",
        ],
        (N_COMMANDER, "rumor") => pick(&[
            &["Cephe Hattı'nda klanların gece boyu çatıştığı söyleniyor.", "Sabah olunca bayrakların yarısı el değiştirmiş oluyor."],
            &["Karargâhtaki telsizci, kimsenin uzun süre tutamadığı bir üs olduğunu söyledi.", "Her alan bir hafta içinde kaybediyormuş."],
        ]),
        // ------------------------------------------------------------------ kule askeri
        (_, "self") => vec![
            "Bölüğümden geriye ben kaldım. Mızrak kullanmayı burada öğrendim; mermi çoktan bitti.",
            "Nöbet bana iyi geliyor. Düşünecek vakit bırakmıyor.",
        ],
        (_, "tip") => vec![
            "Kuleler güvenli bölgenin sınırını korur. Peşinde zombi varsa kuleye doğru koş.",
            "Zombiler güvenli bölgeye giremez; girmeye kalkanı gökten bir yıldırım çarpar.",
        ],
        (_, "rumor") => pick(&[
            &["Dün gece cephe tarafından bir helikopter sesi duydum.", "Kim uçurur bu zamanda? Bir bilen varsa söylesin."],
            &["Titan'ın kükremesini kulede bile hissettim.", "Hazır olmadan peşine düşme."],
        ]),
        _ => vec!["Bunu bilmiyorum, kusura bakma."],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_npc_has_rich_topics() {
        for k in [0u8, N_MERCHANT, N_QUEST, N_DOCTOR, N_MECHANIC, N_TRAINER, N_WIZARD, N_COMMANDER] {
            assert!(topics(k).len() >= 5);
            for (id, _) in topics(k) {
                if *id != "todo" {
                    for seed in 0..3 {
                        for area in ["forest", "city", "burnt", "desert", "bswamp", "harbor", "front", "home"] {
                            assert!(topic_lines(k, id, seed, area).len() >= 2, "{k} {id} {area}");
                        }
                    }
                }
            }
        }
    }
}
