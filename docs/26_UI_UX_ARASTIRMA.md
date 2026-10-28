# UI/UX ve Oynanış Araştırması (V17) — Bulgular, Karar, Uygulama

Bu belge, "en güncel ve doğru bilgiyle, en az 100 kaynağa bakarak" yapılan araştırmanın özetidir. Her konu için: kaynakların
ortak söylediği → Zenvex Online'da verilen karar → kodda nerede uygulandığı. Kaynak listesi en altta (konuya göre, 100+).
Kaynak kalitesi işaretlidir: **[R]** resmî/akademik/stüdyo, **[P]** uygulayıcı yazısı/rehber, **[F]** forum/topluluk. Spam veya
güvenilmez görünen sayfalar listeye alınmadı.

---

## 1. HUD düzeni ve dokunmatik kontroller

**Kaynakların ortak noktası.** Birincil eylemler başparmağın rahat eriştiği alt köşelere; ekranın ortası oyuna boş bırakılır;
ikincil kontroller (menü) üste [Apple HIG Game controls, R]. Dokunma hedefi en az 48×48 dp, hedefler arası 8 dp [Google
erişilebilirlik, R; Material]. Sık kullanılan kontrol ≥ 44 pt, ikincil ≥ 28 pt [Apple, R]. Basınca görünür basılı durum (parmak
altında bile görünen parıltı), ses ve titreşim [Apple, R]. Kontroller kenara/köşeye sabitlenir, kritik öğeler güvenli alan içinde
kalır (çentik, Android 15 kenardan kenara) [Android cutout, R].

**Karar ve uygulama.**
- Üst ortada **tek yığın**: bölge adı → bağlamsal panel (boss çubuğu / üs kartı / etkinlik afişi; aynı anda yalnız biri) → duyuru
  afişi. `GameView.HudLayout`, `drawHud`.
- **Şarjör tuşu** saldırı çemberinin iç yayında, taklanın yanında (ekran kenarına taşmaz), mermi sayısı altında, boşken kırmızı
  nabız; değiştirilirken dönen ok. `GameView.drawReloadBtn`, `layoutControls`.
- **Altın + elmas tek kapsül** (eskiden iki 130 dp kapsül haritanın solunu kaplıyordu). `GameScreen.CurrencyPill`.
- **800×360 dp** (en dar yaygın yatay ekran) için Paparazzi çakışma testi. `HudShotsTest`.

## 2. Bildirimler ve duyurular

**Ortak nokta.** Öncelikli kuyruk; düşük öncelikli otomatik bildirim başka bildirim ekrandayken gösterilmez; görünen sayı sınırlı;
eskiyen bildirim iptal edilir [Adobe Spectrum toast, R; kuyruk sistemleri, P/F].

**Karar.** Duyurular (yeni görev, görev tamam, bölge, öfke…) **kuyrukta tek tek**; aynısı tekrar gelmez; kuyruk ≤ 3 (eskisi
düşer); sırada bekleyen varken gösterilen kısalır. Öğretici balonu duyuru varken bekler (süresi işlemez). Kısa bildirimler (toast)
üst ortadan **cep sırasının üstüne** indi. Başlıklar düzeltildi (yeni görev için "GÖREV TAMAMLANDI" yazıyordu).
`GameView.setBanner`, `drawTutorial`, `Kit.ToastHost`.

## 3. İlk deneyim (FTUE) ve kademeli açılım

**Ortak nokta.** Her adımda tek mekanik; özellikleri hepsini birden göstermek yerine kademeli aç; yeni beceriyi kullanınca hemen
ödül; oyuncuyu hızla eğlenceye ulaştır; yardım tekrar bulunabilir olsun [Meta, Unity, Roblox, Liquid & Grit, Adrian Crook; R/P].

**Karar.** Etkinlikler **10. seviyede** açılır (öncesinde afiş ve puan yok) → yeni oyuncu ekranı kalabalık görmez.
Dil seçim ekranı kaldırıldı: oyun **cihaz diliyle** açılır; Ayarlar'dan seçilirse o kalır (Android 13 uygulama başına dil
yaklaşımıyla uyumlu) [Android per-app language, R]. Öğretici bıçak yaptırır (iki farklı "balta" kafa karıştırıyordu).
`Events.MIN_LEVEL`, `Settings.load`, `story.rs g4f/g4g`.

## 4. Okunurluk: metin, kontrast, erişilebilirlik

**Ortak nokta.** Okunur varsayılan yazı boyu ve ölçeklenebilirlik; metnin arkasında koyu zemin ya da dış hat; kontrastı
ölçülebilir tut; titreşim ve ekran sarsıntısı kısılabilir olsun [Xbox XAG 101/102, IGDA GA-SIG, Game Accessibility Guidelines; R].

**Karar.** HUD yazıları dış hatlı; duyuru afişinde koyu degrade bant; dayanıklılık çubuğu renkle değil boyla da okunur (çubuk
uzunluğu + yüzde metni). Mevcut "titreşim", "ekran sarsıntısı" ayarları korunur.

## 5. Oyun hissi (juice) ve efekt hiyerarşisi

**Ortak nokta.** Darbe hissini en çok vuruş duraklaması (hit-stop ~50–100 ms), ses tutarlılığı ve kamera kontrolü belirler
[Lin vd. IEEE 2022, R]; sarsıntı küçük ve darbeyle orantılı; efektin dikkat çekişi önemiyle orantılı (hiyerarşi), gürültü
azaltılır [Riot "Clarity in League", R].

**Karar.** Patlama, boss ve seviye sesleri öncelikli; ağaç devrilince yeşil radyal patlama yerine **tacın yüksekliğinden süzülen
yapraklar** (gürültü azaldı); zombi yakındayken vuruş ağaca/kayaya gitmez (savaşta kıymık/yaprak saçılmıyordu). `Fx3D.treeFall`,
`combat.rs near_foe`.

## 6. Düşman çeşitliliği ve okunurluk

**Ortak nokta.** Her düşmanı benzersiz yapmak gürültü yaratır; okunurluk rol ve menzile göre önceliklendirilir; kompakt rol
listesi (er, sürü, tank, keskin nişancı…) ve ayırt edici siluet [Fatshark Darktide, R; Level Design Book, P].

**Karar.** Bölge başına **iki imza yaratık** (6 tür emekli); bölge türü aynı roldeki klasik görünüşün yerine geçer.
`data::RETIRED_SPECIES`, `zombies.rs pick_species`.

## 7. Dayanıklılık (her şeyin canı)

**Ortak nokta.** Kırılan eşya yok olmasın, "kırık" durumda onarılabilsin; risk görünür olsun; onarım bedeli gelirle orantılı
ve anlamlı olsun; boş angarya olmasın [Minecraft geri bildirim, 7 Days to Die, FFXIV/Engadget, Gloria Victis; F/P].

**Karar.** Silah (isabet/atış), alet (toplama vuruşu), zırh/yüzük/kolye (alınan darbe) aşınır; çantalar kırılmaz. Kırık eşya
etkisizdir ama kaybolmaz; tezgâhta ya da Tamirci Viktor'da onarılır; bedel = değerin yarısı × eksik oran. Yuvanın altında
çubuk, eşyada yüzde. Çıkar-giy, sandık, yere bırakma, kayıt ve takas canı yenilemez. `inventory.rs Slot.dur`,
`progress.rs Equipment.wear`, `ui.rs repair`, `Bag.kt PxSlot`.

## 8. Araç canı, çağırma beklemesi ve hile önleme

**Ortak nokta.** Bekleme araç **patlayınca** başlar; kendini öldürme/istismar ayrı ele alınır; terk edilen araç aşınır;
zamanlayıcı görünür olsun [Rust/uMod, Restore Monarchy, DayZ, Battlefield 1942 belgeleri; F/R].

**Karar.** Araba 500, motosiklet 200 can; sürerken hasarın tamamını (araba) / %70'ini (motor) araç alır; sürüş aşınması 60/30
sn'de %1; patlayınca 60/30 sn **oyun süresi** beklemesi (çevrimdışı süre saymaz); can, aşınma birikimi ve bekleme kayıtta → in-bin,
tür değiştirme, çık-gir yenilemez. Kalan süre çağırma mesajında yazar. `vehicles.rs`, `ui.rs summon_vehicle`, testler.

## 9. Güvenli takas

**Ortak nokta.** Paylaşılan pencere, iki taraflı onay, **herhangi bir değişiklikte onayların sıfırlanması**, eşyaların emanete
alınması ve atomik takas; onay öncesi kısa geri sayım [GlymeraTrade, TrustedTrades, BigTrades, MCC Island, ESO forumu; P/F].

**Karar.** Sunucu emaneti zaten vardı; eklendi: karşı teklif değişince Onayla **3 sn kilitli**; onayladıktan sonra teklif
değişirse sunucu takası tamamlamaz (`zx_trade_ready p_expect`); ekipman dayanıklılığıyla geçer. `Trade.kt`, `Screens.TradeScreen`,
`supabase/migrations/20261120_trade_expect.sql`.

## 10. Ses karışımı

**Ortak nokta.** Ses öncelik sırası; kategori başına eşzamanlı ses sınırı (yüz adım sesi kanalları doldurmasın); önemli anda müzik
"ducking" (hızlı atak, yumuşak bırakış); tekrarlayan seslerde çeşitlilik [CRI ADX2, Bugnet, oyun sesi rehberleri; P].

**Karar.** Ses başına seviye ve öncelik tablosu; 120 ms'de 8+ seste arka plan sesleri, 12+ seste orta öncelikliler düşer;
tekrarlayan seslere ±%2–10 perde oynaması; patlama/boss/seviye/görevde müzik %45–60'a iner, ~0,8 sn'de döner. `Audio.kt`.

## 11. Akıcılık: akış yükleme ve kare zamanlaması

**Ortak nokta.** Yeni bölgeye girerken takılma çoğunlukla akış (dosya/doku yükleme) ve GC ile çakışır; yüklemeyi oyun
döngüsünden ayır, önceden yükle; kare döngüsünde ayırma yapma, tamponları yeniden kullan [Android frame pacing & bellek, R;
mobil takılma yazıları, P].

**Karar.** Zemin parçaları (256 px) artık **arka plan iş parçacığında**, 32 satırlık şeritlerle (oyun kilidi kısa tutulur);
görüşün çevresi ve gidiş yönünde iki parça ileri önceden yüklenir; hazır olmayan parça bölgenin ortalama tonuyla. Eskiden kare
başına 4 parça eşzamanlı çiziliyordu. `GameView.drawGround`, `startGroundWorker`.

## 12. Lobi ve vitrin

**Ortak nokta.** Lobi oyunun temasını taşımalı (zombi hayatta kalma için karanlık, yaşayan bir sahne), karakter sahnede durmalı,
menüye erişim sade kalmalı [lobi tasarım örnekleri; F/P].

**Karar.** Lobi arka planı **oyunun kendi kampı** (motordan çekilmiş gece görüntüsü, yavaş kayma, kor ışığı); önünde oyuncunun 3B
kahramanı (silah ve kanatla); logo "Zenvex Online"; lobiden karakter satın alma kaldırıldı. `Lobby.kt`, `GameShotsTest.l01`.

## 13. Para kazanma ve güven

**Ortak nokta.** Güven kısa vadeli gelirden değerlidir; ödeme gücü değil görünüm/kolaylık satılır; FOMO ve yapay kıtlıktan
kaçınılır; cömert ücretsiz yol [ödeme etiği yazıları, arXiv 2025; P/R].

**Karar.** VIP ödülleri görünüm (kanat, sohbet teması) ve kolaylık (çanta, satış payı); kanatlar statü göstergesi, güç vermez.
Mağaza görselleri için yeni prompt belgesi (`docs/25`).

## 14. Yerelleştirme

**Ortak nokta.** Çeviri metni 2 kata kadar uzayabilir (Almanca), esnek kutular; RTL (Arapça) ayna düzeni erken planlanmalı;
sahte yerelleştirme ile taşma testi [Gridly, Alconost, SimpleLocalize; P].

**Karar.** Tüm yeni metinler 9 dile çevrildi (2581/2581); yeni düğmeler sabit genişlik yerine içeriğe göre (`CurrencyPill`,
onarım düğmesi); dar pencerelerde metin `maxLines` ile kısalır.

---

## Kaynaklar (konuya göre)

### HUD, dokunmatik kontroller, güvenli alan
1. [R] Apple HIG — Game controls: https://developer.apple.com/design/human-interface-guidelines/game-controls.md
2. [R] Apple WWDC26 — Make your game great with touch: https://developer.apple.com/videos/play/wwdc2026/358/?time=0
3. [P] Create with Swift — What's new in HIG (WWDC24): https://www.createwithswift.com/wwdc24-whats-new-in-the-human-interface-guidelines/?issue=036
4. [R] Google — Touch target size: https://support.google.com/accessibility/android/answer/7101858
5. [R] Compose Material3 — minimumInteractiveComponentSize: https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/minimum-interactive-component-size.html
6. [R] Material touch-target README: https://cdn.jsdelivr.net/npm/@material/touch-target@14.0.0/README.md
7. [P] CVS Health — Minimum touch target size: https://github.com/cvs-health/android-view-accessibility-techniques/blob/main/doc/basics/MinimumTouchTargetSize.md
8. [P] SAP Fiori for Android — dp: https://experience.sap.com/fiori-design-android/?p=22054
9. [P] Parachute Design — Thumb zone UX: https://parachutedesign.ca/blog/thumb-zone-ux/
10. [P] Generalist Programmer — Game UI design best practices: https://generalistprogrammer.com/tutorials/game-ui-design-best-practices
11. [P] Made Good Designs — Game UI design guide: https://madegooddesigns.com/?p=5485
12. [R] Goldsmiths — UX guidelines for HUD design (2025): https://research.gold.ac.uk/id/eprint/39439
13. [R] Android — Display cutouts: https://developer.android.com/guide/topics/display-cutout
14. [R] Android Compose — Cutouts: https://developer.android.com/develop/ui/compose/system/cutouts
15. [P] Airnative — Android 15 UI changes: https://docs.airnativeextensions.com/news/2025/08/08/android-35-ui-changes
16. [F] Unity — Game rendering over notch on Android 15: https://discussions.unity.com/t/issue-with-game-rendering-over-notch-on-android-15-in-unity-2022-3-lts/1568031
17. [P] Bugnet — Unity edge-to-edge cutout hides UI: https://bugnet.io/blog/fix-unity-android-edge-to-edge-display-cutout-hides-ui
18. [R] AIT — On-screen gamepad designs (ACM 2015): https://publications.ait.ac.at/en/publications/investigating-on-screen-gamepad-designs-for-smartphone-controlled/
19. [R] UPI — Virtual joystick, design thinking (2023): https://repository.upi.edu/101077
20. [F] Unity — Why are joystick controls terrible on mobile: https://discussions.unity.com/t/opinions-wanted-why-are-joystick-controls-so-terrible-on-mobile/640042
21. [P] Phaser — Virtual joystick write-up: https://phaser.io/news/2025/12/i-built-the-best-virtual-joystick-for-phaserjs
22. [P] Victris Games — Floating joystick interface: https://victrisgames.itch.io/multi-touch-interface-with-floating-virtual-joystick-and-two-types-of-guns

### Bildirimler, görev takibi
23. [R] Adobe Spectrum — Toast: https://spectrum.adobe.com/page/toast
24. [P] layerman (öncelikli kuyruk): https://pub.dev/packages/layerman
25. [F] Core Games — UI notifications queue: https://forums.coregames.com/t/ui-notifications/1659
26. [P] Fab — Hyper Information Prompt System: https://www.fab.com/listings/5aeb0adf-d21c-4cc1-aeee-4b7991285cb6
27. [P] Codefinity — notification UI lesson: https://codefinity.com/courses/v2/16e03902-c8cb-4324-b7a7-eb0cbd0e6742/1191a4cf-2502-43df-957c-66bc60ffbf3c/d1551cdb-1448-4d7a-8950-08771e484a6c
28. [R] Xbox XAG 109 — Objective clarity: https://star-hk2.eastasia.cloudapp.azure.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/109
29. [P] Icy Veins — Waypoint UI: https://www.icy-veins.com/wow/news/waypoint-ui-replaces-wows-navigation-frame-and-it-looks-clean/
30. [F] Icy Veins — 3D objective indicator: https://www.icy-veins.com/forums/topic/49083-quest-interface-improvements-in-shadowlands-3d-indicator-of-tracked-quest-objectives
31. [P] NMO's Quest Tracker: https://www.curseforge.com/wow/addons/nmos-quest-tracker
32. [P] XenQuest: https://www.curseforge.com/wow/addons/xenquest
33. [P] Enhanced Quest Tracker: https://www.curseforge.com/wow/addons/enhancedquesttracker
34. [P] Everything Quests: https://www.curseforge.com/wow/addons/everything-quests

### FTUE ve kademeli açılım
35. [R] Meta — Best practices for new user onboarding: https://developers.meta.com/horizon/blog/growth-insights-series-best-practices-user-onboarding-recall-progression/
36. [R] Unity — 10 FTUE tips: https://unity.com/how-to/10-first-time-user-experience-tips-games
37. [R] Roblox — Onboarding: https://create.roblox.com/docs/production/game-design/onboarding
38. [P] Liquid & Grit — FTUE toolkit: https://liquidandgrit.com/first-time-user-experience-ftue-toolkit
39. [P] Adrian Crook — Mobile game onboarding: https://adriancrook.com/?p=8055

### Erişilebilirlik, metin, kontrast
40. [R] Xbox XAG 101 — Text display: https://devdocs.xbox.com/gaming/accessibility/xbox-accessibility-guidelines/101
41. [R] Xbox XAG 102 — Contrast: https://devdocs.xbox.com/build/game-principles/accessibility/xag-deep-dives/xag-102-contrast.md
42. [R] Xbox XAG version history: https://learn.microsoft.com/ar-sa/xbox/accessibility/xag-version-history
43. [R] IGDA GA-SIG — Game accessibility top ten: https://igda-gasig.org/how/game-accessibility-top-ten-se/
44. [R] Game Accessibility Guidelines — Basic: https://gameaccessibilityguidelines.com/basic/
45. [R] Game Accessibility Guidelines — readable font size: https://gameaccessibilityguidelines.com/?p=21
46. [R] Game Accessibility Guidelines — touch controls: https://gameaccessibilityguidelines.com/?p=3102
47. [P] Glance — Accessibility features gaming apps: https://thisisglance.com/learning-centre/what-accessibility-features-do-gaming-apps-need-most

### Dil ve yerelleştirme
48. [R] Android — Per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages
49. [R] Android 13 — App languages: https://developer.android.com/about/versions/13/features/app-languages
50. [R] Android Developers Blog — Per-app language part 1: https://android-developers.googleblog.com/2022/11/per-app-language-preferences-part-1.html
51. [P] Phrase — Android per-app language: https://phrase.com/blog/posts/android-per-app-language-preferences/
52. [P] Thoughtworks Radar — per-app language: https://www.thoughtworks.com/radar/languages-and-frameworks/per-app-language-preferences
53. [P] Gridly — Localization for game UI: https://gridly.com/blog/game-ui-design-localization-best-practices
54. [P] Alconost — Trickiest languages in game localization: https://alconost.com/en/blog/hardest-languages-game-localization
55. [P] SimpleLocalize — i18n tips: https://simplelocalize.io/blog/posts/i18n-tips-and-tricks
56. [P] Bugnet — Localization bugs: https://bugnet.io/blog/how-to-track-and-fix-localization-bugs-in-your-game
57. [P] FoxData — Localizing for Arab markets: https://foxdata.com/en/blogs/how-to-localize-your-game-for-arab-markets-a-comprehensive-guide/
58. [P] Globalization Partners — localization: https://www.globalizationpartners.com/?p=4391
59. [P] Unistra — Localization tips for designers: https://mastertcloc.unistra.fr/?p=9725

### Oyun hissi, efekt hiyerarşisi
60. [R] Riot — Clarity in League: https://riotgames.com/en/news/dev-clarity-in-league
61. [R] Riot — Resistance Illaoi final update: https://nexus.leagueoflegends.com/en-us/2018/02/resistance-illaoi-the-final-update/
62. [R] Riot Jobs — Principal VFX (clarity): https://www.riotgames.com/fr/j/6996831
63. [R] arXiv — Impact feel features (Lin vd.): https://arxiv.org/abs/2208.06155v1
64. [R] SciTePress 2024 — game feedback: https://www.scitepress.org/Papers/2024/124614/124614.pdf
65. [P] Wayline — Game feel & juice: https://www.wayline.io/learn/game-feel
66. [P] Easton Dev — Flash, shake, text, sound, particles: https://eastondev.com/blog/en/posts/dev/20260521-game-feedback-feel/
67. [P] Godot asset — Saltmire Juice: https://store.godotengine.org/asset/saltmire/saltmire-juice/
68. [P] Godot asset — Juicee: https://store.godotengine.org/asset/kelpekk/juicee/
69. [P] Comic Shop Locator — Marvel Rivals VFX clarity: https://www.comicshoplocator.com/2026/07/17/marvel-rivals-vfx-clarity-update-battlefield-chaos-2026/
70. [P] GeekChamp — Art of Hades readability: https://geekchamp.com/the-art-of-hades-showcases-the-talent-that-went-into-this-beautifully-vibrant-game/

### Düşman tasarımı
71. [R] Fatshark — Darktide enemy character art dev blog: https://darktide.gameslantern.com/news/darktide-101-enemy-character-art-dev-blog
72. [R] Darktide devtracker: https://devtrackers.gg/darktide/p/67a2496a-darktide-101-enemy-character-art-dev-blog
73. [P] RocketBrush — Shape language: https://rocketbrush.com/blog/shape-language-in-game-character-design-how-to-make-characters-readable-and-consistent
74. [P] Bugnet — Readable game art: https://bugnet.io/blog/readable-game-art-why-clarity-beats-detail
75. [P] Wayline — Enemy archetypes: https://www.wayline.io/blog/designing-engaging-enemy-archetypes
76. [P] GameTyrant — Monsters recognizable at a glance: https://gametyrant.com/news/why-great-video-game-monsters-are-recognizable-at-a-glance
77. [F] itch.io — silhouette test: https://itch.io/post/7243401
78. [F] itch.io — enemy readability: https://itch.io/post/7242749

### Dayanıklılık
79. [F] The Fun Pimps — Repair mechanics: https://community.thefunpimps.com/goto/post?id=435857
80. [F] Minecraft feedback — broken item state: https://feedback.minecraft.net/hc/en-us/community/posts/24700954972813/comments/44915028464013
81. [P] Engadget — Durability is counterintuitive: https://www.engadget.com/2011-02-05-the-mog-log-durability-is-counterintuitive.html
82. [F] Funcom — Repair and max durability: https://forums.funcom.com/t/feedback-repair-button-should-also-reduce-max-durability/233390
83. [F] No Rest for the Wicked — durability rework: https://forum.norestforthewicked.com/t/durability-and-why-its-in-dire-need-of-a-rework-in-gaming/9078
84. [R] Gloria Victis wiki — Durability: https://wiki.gloriavictisgame.com/index.php/Durability
85. [F] Vintage Story — tool maintenance: https://www.vintagestory.at/forums/topic/14590-better-tool-maintenance/
86. [F] Rose Online — wear: https://forum.roseonlinegame.com/topic/62-wardrobe-wear-off/

### Araçlar
87. [F] uMod — Respawn timer for crashed vehicles: https://umod.org/community/vehicle-license/29907-respawn-timer-including-crasheddead-vehicles
88. [F] uMod — Cooldown when destroyed: https://umod.org/community/vehicle-license/14347-cooldown-when-player-destroys-vehicle
89. [F] uMod — Separate kill/destroy timer: https://umod.org/community/vehicle-license/57396-separate-timer-for-kill-and-the-vehicle-being-destroyed
90. [P] Restore Monarchy — vehicles config: https://restoremonarchy.com/docs/config_json/vehicles
91. [F] Steam (DayZ) — vehicle despawn: https://steamcommunity.com/app/221100/discussions/0/4413047330427089832
92. [R] BF1942 MDT — TimeToLive: https://realtimerendering.com/erich/bf1942/mdt/MDTDOC/Confiles/ObjectTemplate/Properties/TimeToLive.htm
93. [R] BF1942 MDT — DamageWhenLost: https://realtimerendering.com/erich/bf1942/mdt/MDTDOC/Confiles/ObjectTemplate/Properties/DamageWhenLost.htm
94. [F] BetaHub — visible respawn timers request: https://app.betahub.io/projects/pr-7682550116/feature_requests/755
95. [P] Lockdown devlog — survival changes: https://jangomango2001.itch.io/lockdown/devlog/873576/patch-v14-linux-release-survival-mode-major-changes

### Takas güvenliği
96. [P] GlymeraTrade: https://builtbybit.com/resources/glymeratrade.110978/
97. [P] TrustedTrades: https://builtbybit.com/resources/v3xl0-trustedtrades.105258/
98. [P] BigTrades: https://www.curseforge.com/minecraft/mc-mods/bigtrades
99. [R] MCC Island — Trading scams: https://support.mccisland.net/Trading-Scams-and-what-to-look-out-for-22b739c7bb4f8075b1d3dffa7a909c15
100. [P] Inven Global — Adopt Me trade scams: https://invenglobal.com/articles/25853/adopt-me-trade-scams-the-patterns-worth-recognising
101. [F] ESO forums — trade scam report: https://forums.elderscrollsonline.com/en/discussion/349718
102. [R] Enjin — Trust & safety: https://docs.enjin.io/docs/trust-safety

### Ses
103. [P] CRI ADX2 — ducking: https://blog.criware.com/?p=160
104. [P] Bugnet — Mix game audio so nothing drowns out: https://bugnet.io/blog/how-to-mix-game-audio-so-nothing-drowns-out
105. [P] Game audio principles (skills.sh): https://skills.sh/vudovn/antigravity-kit/game-audio

### Performans, akış yükleme, bellek
106. [R] Android — Frame pacing: https://source.android.com/docs/core/graphics/frame-pacing
107. [R] Android Game SDK — Frame pacing library: https://developer.android.com/games/sdk/frame-pacing
108. [R] Android — Bitmap memory usage: https://developer.android.com/topic/performance/vitals/bitmap-memory-usage
109. [R] Android — Manage memory in games: https://developer.android.com/games/optimize/memory-allocation
110. [P] Game Developer — Boosting FPS (GC): https://www.gamedeveloper.com/programming/boosting-the-fps-gc-part-1-
111. [P] freeCodeCamp — Reduced memory footprint 50%: https://www.freecodecamp.org/news/how-we-reduced-memory-footprint-by-50-in-our-android-app-49efa5c93ad8
112. [P] Vogella — Android optimization: https://vogella.com/tutorials/AndroidApplicationOptimization/article.html
113. [P] Wayline — Frame pacing for mobile: https://www.wayline.io/blog/frame-pacing-smooth-mobile-games
114. [P] BreakingAC — Why your mobile game stutters: https://breakingac.com/news/2026/jan/23/why-your-mobile-game-stutters-gpu-threading-and-asset-streaming-fixes/
115. [F] Imagination — EGL/GLES stuttering: https://forums.imgtec.com/t/android-egl-gles-frame-rate-stuttering/1173
116. [F] AGS — Slowdowns on Android (tarihî): https://www.adventuregamestudio.co.uk/forums/engine-development/understanding-slowdowns-on-android

### Lobi
117. [F] Roblox DevForum — lobby ideas: https://devforum.roblox.com/t/things-to-add-to-td-lobby/3913634
118. [P] Overgem devlog — effective main menu: https://loveglitchcoffee.itch.io/overgem/devlog/148972/an-effective-main-menu
119. [P] ArtStation — Company Man lobby art: https://jastham.artstation.com/projects/PekN3y
120. [P] Skins Factory — platform UI/UX: https://www.theskinsfactory.com/

### Para kazanma ve güven
121. [P] AppLixir — Ethical monetization: https://www.applixir.com/blog/?p=3016
122. [P] Bugnet — Non-predatory battle pass: https://bugnet.io/blog/how-to-design-a-battle-pass-thats-not-predatory
123. [P] CogConnected — Player retention 2025: https://cogconnected.com/2025/05/the-business-of-player-retention-in-2025/
124. [R] arXiv 2025 — Korean mobile game monetization tactics: https://arxiv.org/pdf/2504.10714
125. [P] The Summoning Hour — monetize without losing trust: https://thesummoninghour.riverside.com/e/episode-3-can-you-monetize-games-without-losing-player-trust-tamerlane-barak

> Not: önceki oturumlarda (V16) yapılan taramalarda incelenen ek kaynaklar (Android 15 kenardan kenara, 44–48 dp hedefler, yazı
> boyları, çentik, kademeli açılım, bildirim kuyruğu, ses otobüsleri, hit-stop, efekt netliği, dayanıklılık katmanları, takas
> sıfırlama) bu listedeki bulgularla örtüşüyor; burada yalnız bağlantısı doğrulanan kaynaklar listelendi.
