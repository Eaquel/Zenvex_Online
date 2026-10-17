# 06 — Açık Sorular (Karar Bekleyenler)

Her sorunun yanında **önerim** var. Cevap vermediğin sorularda önerilen seçenekle ilerlenir. Cevaplarını numarasıyla yazman yeterli (örn. "3: B, 7: 8 yön").

## A. Vizyon & Platform
1. **Platform:** Sadece Android mi, Android + iOS mu, PC de olacak mı? → *Öneri: Önce Android; PC sadece geliştirme/test için.* (iOS için Kotlin kabuğu kullanılamaz — Swift kabuğu gerekir.)
2. **Hedef cihaz:** En düşük hangi telefon akıcı çalışmalı? (RAM / yıl) → *Öneri: 3 GB RAM, 2019+ orta segment, Android 8.0 (API 26)+.*
3. **Ekran yönü:** Sadece yatay mı? → *Öneri: Sadece yatay.*
4. **Oyunun adı** kesin "Zenvex Online" mı? Karakter adı "Kaan" olsun mu?
5. **Hedef yaş / şiddet:** Kan efekti olsun mu (stilize kırmızı pixel), yoksa tamamen kansız mı? → *Öneri: Stilize, ayardan kapatılabilir; PEGI 12.*
6. **Online ne zaman?** MVP tamamen tek oyunculu mu? → *Öneri: Evet, ama kod online'a hazır mimaride.*
7. **Para kazanma modeli** (ileride): Reklam, kozmetik mağaza, battle pass, hiçbiri? → *Öneri: Sadece kozmetik + isteğe bağlı ödüllü reklam; "kazanmak için öde" yok.*

## B. Karakter
8. **Tek karakter mi, karakter yaratma (cinsiyet, saç, ten, kıyafet) mi?** → *Öneri: MVP'de 1 hazır karakter; lobi tasarımında "Görünüm 🔒" tuşu.*
9. Kaan'ın tasarımı (hardal kapüşonlu, lacivert kot, el sargısı, sırt çantası) uygun mu? Değiştirmek istediğin renk/kıyafet?
10. **Yön sayısı:** 4 yön (3 çizim + ayna) mi, 8 yön mü? → *Öneri: 4 yön — çizim yükü yarı yarıya; 8 yön 2. aşamada.*
11. **Sprite boyutu:** 48×48 kare (karakter ~38 px) uygun mu, daha detaylı 64×64 mü? → *Öneri: 48×48 — mobilde net, çizimi hızlı.*
12. **Oran:** Chibi'ye yakın (büyük kafa) mı, daha gerçekçi uzun mu? → *Öneri: 1:2.5 yarı-chibi.*

## C. Sanat & Görseller
13. **Görselleri kim üretecek?** Sadece yapay zeka mı, yapay zeka + elle temizleme (Aseprite) mi? → *Öneri: Yapay zeka + elle temizleme; aksi halde animasyonlar titrer.*
14. Hangi yapay zeka araçlarına erişimin var? (Midjourney, ChatGPT/GPT-Image, Leonardo, PixelLab, Retro Diffusion, Stable Diffusion yerel)
15. **Aseprite** var mı / alınabilir mi? (Ücretli ~20$; derleyerek ücretsiz de kurulabilir.) Alternatif: LibreSprite (ücretsiz).
16. Önerilen 48 renklik palet uygun mu, yoksa hazır bir palet mi (ör. Lospec "Resurrect 64", "Endesga 64")?
17. **Gün saati:** Sabit gündüz mü, gece-gündüz döngüsü mü? → *Öneri: MVP sabit öğleden sonra; gece döngüsü F5 sonrası (zombiler gece güçlenir).*
18. Font: "Pixelify Sans" + "Silkscreen" (ücretsiz, Türkçe destekli) uygun mu?

## D. Oynanış
19. **Saldırı joysticki davranışı:** (A) Dokun = otomatik nişan, sürükle = yönlü vuruş (önerilen) · (B) Sadece yönlü · (C) Sadece dokun-vur tuşu?
20. **Hareket joysticki:** Dinamik (dokunduğun yerde belirir) mi, sabit mi varsayılan? → *Öneri: Dinamik, ayardan değişir.*
21. **Stamina** olsun mu? (Koşu ve yumruk harcar) → *Öneri: Evet, taktik derinlik katar.*
22. **Ölüm cezası:** Yok / %5 XP kaybı / envanterin bir kısmı düşer? → *Öneri: MVP'de yok.*
23. **Level sınırı** 50 uygun mu? XP eğrisi (`50 × n^1.6`) hızlı mı yavaş mı olsun? → *Öneri: Lv10 ≈ 30–40 dk oyun.*
24. **Skill ağacı:** 4 dal (Güç, Dayanıklılık, Çeviklik, Odunculuk) uygun mu? Skill sıfırlama ücretsiz mi?
25. **Aktif skill** MVP'ye girsin mi? (Örn. Lv15 "Kaçış/Dash") → *Öneri: Evet, tek aktif skill — skill tuşu boş durmasın.*
26. **Ağaç kesme:** Odun şimdilik sadece sayaç mı, yoksa basit bir kullanım (ör. kamp ateşi yakıp HP yenileme) olsun mu? → *Öneri: Sadece sayaç + XP.*
27. **Zombi zorluğu:** Merkez kolay, kenarlar zor bölge sistemi uygun mu?
28. Zombi sayısı aynı anda en fazla 25 — daha kalabalık "sürü" hissi mi istersin?
29. **Kombo** (3'lü yumruk) mu, tek tip yumruk mu? → *Öneri: 3'lü kombo, vuruş hissi için kritik.*
30. **Harita boyutu:** 128×128 karo (≈ uçtan uca 2 dk yürüme) uygun mu? Sabit harita mı, her oyunda rastgele (seed) mi? → *Öneri: Sabit seed — oyuncu haritayı öğrensin.*

## E. Arayüz
31. Lobi yerleşimi (alt şerit menü + sağ altta büyük OYNA) uygun mu, yoksa sol dikey menü mü?
32. **Lobi teması:** Akşam kamp ateşi mi, gündüz orman mı?
33. Para birimleri: Altın + Elmas + Enerji — **enerji** sistemi olsun mu? (Oyun girişini sınırlar, oyuncuyu sinirlendirebilir.) → *Öneri: Enerji yok; sadece Altın + Elmas.*
34. "Yakında" tuşları görünür ve normal renkte mi, gri mi olsun? → *Öneri: Normal + küçük kilit rozeti + tost mesajı.*
35. **Diller:** Türkçe + İngilizce yeterli mi?
36. **Sol el modu**, renk körlüğü modu, yazı boyutu gibi erişilebilirlik ayarları kalsın mı? → *Öneri: Evet, ucuz ve değerli.*
37. Gamepad (Bluetooth kol) desteği MVP'de olsun mu? → *Öneri: Temel destek evet (Rust tarafında kolay).*

## F. Teknik
38. **Lobi teknolojisi:** (A) Lobi Kotlin + Jetpack Compose, oyun Rust (önerilen, belgede bu) · (B) Her şey Rust (Bevy UI), Kotlin sadece ince kabuk — tek kod, PC'de de lobi çalışır ama menü geliştirmek daha zahmetli.
39. **Rust motoru:** Bevy uygun mu? (Alternatif: Macroquad daha hafif, özel wgpu motoru en kontrollü.) → *Öneri: Bevy.*
40. Geliştirme bilgisayarın: Windows / macOS / Linux? Android Studio kurulu mu? Test için fiziksel Android telefonun var mı (model)?
41. Rust ve Kotlin deneyim seviyen nedir? (Kod belgelerinin ve açıklamaların detay seviyesini buna göre ayarlarım.)
42. **Kayıt:** Sadece cihazda mı? İleride bulut kaydı (Google Play Games / kendi sunucumuz)?
43. **Analitik / çökme raporu** (Firebase) eklensin mi? → *Öneri: Kapalı test sürümünde Crashlytics evet, analitik sonra.*
44. **Sunucu** (ileride): Kendi sunucumuz (Rust) mu, hazır servis mi? Bütçe var mı?
45. Repo yapısı: Rust ve Android aynı repoda (monorepo) mı? → *Öneri: Evet, bu repo.*

## G. Ses & Müzik
46. Müzik/ses nereden gelecek? (Yapay zeka müzik — Suno/Udio, ücretsiz kütüphane — freesound/OpenGameArt, ücretli paket?) İstersen ses/müzik için de prompt hazırlarım.
47. Müzik tarzı: Akustik-sakin + gerilimli synth mi, chiptune (8-bit) mi? → *Öneri: Lo-fi akustik + hafif chiptune karışımı.*

## H. Süreç
48. Sonraki adımda ne istersin? (A) Görselleri üretmeye başla, ben eksik/yanlışları kontrol edeyim · (B) Lobi ve HUD için **tıklanabilir HTML prototip/mockup** · (C) Rust proje iskeletini ve Android kabuğunu kurmaya başla.
49. Zaman hedefin var mı? (Örn. 2 ay içinde kapalı test APK'sı)
50. Ekipte başka biri var mı (çizer, ses, test)?
