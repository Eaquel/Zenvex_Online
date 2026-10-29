# Hukuki Uyum (Türkiye) — Denetim ve Yapılanlar

Son denetim: 29 Ekim 2026. Bu belge bir kontrol listesidir, hukuki görüş değildir; yayından önce bir avukata
okutulması önerilir.

## Yayından önce MUTLAKA yapılacaklar (kodla çözülemeyenler)

1. **Künye bilgilerini doldur:** `supabase/functions/zx-legal/content/legal.json` → `operator` alanındaki
   `[DOLDURULACAK: …]` değerleri (ad/unvan, adres, e-posta, KEP, telefon, MERSİS ya da vergi no, oyun sunucusu
   sağlayıcısı ve ülkesi). Tek dosya: uygulama ve web sayfaları aynı metni gösterir. Yayın derlemesi doldurulmamışsa
   uyarı verir. Doldurduktan sonra `zx-legal` fonksiyonunu yeniden yayınla.
2. **Yurt dışı aktarım (KVKK m.9):** Supabase ve Google ile Kurul'un standart sözleşmesini imzala ve imzadan sonra
   **5 iş günü içinde** Kişisel Verileri Koruma Kurumu'na bildir. Aydınlatma metni bu güvenceye dayanıyor.
3. **VERBİS:** Yıllık çalışan sayısı 50'den az ve mali bilanço 100 milyon TL'den azsa (ve ana faaliyet özel nitelikli
   veri işleme değilse) kayıt yükümlülüğü yoktur; durumunu değerlendir.
4. **ETBİS:** Elektronik ticaret hizmet sağlayıcısı olarak Elektronik Ticaret Bilgi Sistemi kaydını değerlendir.
5. **Google Play Veri Güvenliği formu:** cihaz kimliği, IP, e-posta (Google girişi), satın almalar, fotoğraf (profil,
   destek), mesajlar beyan edilmeli; "veriler silinebilir" ve silme bağlantısı işaretlenmeli.
6. **Saklama temizliği:** migration pg_cron'u açmayı dener; açılamazsa GitHub → Settings → Secrets → Actions'a
   `SUPABASE_SERVICE_ROLE_KEY` ekle (`Veri Saklama Temizliği` iş akışı her gün çalışır, destek fotoğraflarını da siler).
7. **Şikâyet süreleri:** "Telif Hakkı İhlali", "Uygunsuz Profil Fotoğrafı", "Kişisel Veri Paylaşımı", "Hukuka Aykırı
   İçerik" şikâyetleri yönetici panelinde 24 saat içinde yanıtlanmalı; telifte içerik en geç 3 gün içinde kaldırılmalı.
   KVKK başvuruları (Destek → "KVKK") en geç 30 gün içinde yanıtlanmalı.

## Denetim özeti

| Başlık | Önce | Şimdi | Yapılan |
|---|---|---|---|
| 1. Ödeme ve satın alma (6502, MSY) | Yok | Var | Satın alma onay penceresi (içerik, vergiler dahil fiyat, tek seferlik, teslim, cayma istisnası + zorunlu onay, "Ödeme Yükümlülüğü Doğurur"); Ön Bilgilendirme Formu, Mesafeli Satış Sözleşmesi; satın alma bilgilendirme postası; Satın Almalarım |
| 2. Ticari elektronik ileti (6563) | Yok | Var | Etkinlik bildirimi yalnız açık onayla (varsayılan kapalı), onay metni, onay/ret kaydı (sürüm, zaman, IP), bildirimde tek dokunuşla ret; hizmet bildirimleri ayrı ayrı kapatılabilir |
| 3. Kullanıcı içeriği ve telif (5846) | Kısmen | Var | Yükleme öncesi hak/kural bildirimi, telif ve hukuka aykırı içerik şikâyet nedenleri, profilden şikâyet, bildirim usulü sayfası, Kullanım Şartları'nda içerik kuralları |
| 4. Yer sağlayıcı (5651) | Kısmen | Var | Künye sayfası, trafik bilgisi kaydı (IP + zaman, 1 yıl), 24 saat / 3 gün süreleri, yetkili makam iletişimi |
| 5. KVKK m.10 aydınlatma | Kısmen | Var | Tam aydınlatma metni (veri sorumlusu, veri kategorileri, amaç ve hukuki sebep, toplama yöntemi, alıcılar, yurt dışı aktarım, saklama süreleri, m.11 hakları, başvuru yolu); ilk açılış bildirimi, onaylanmadan cihaz kimliği gönderilmez |
| 6. Yurt dışı aktarım ve dış kaynaklar (m.9/m.12) | Kısmen | Kısmen | Metinde açıklandı; dış CDN/analiz/reklam yok; saklama süreleri ve anonimleştirme kodlandı. **Standart sözleşme ve bildirim geliştiricide (yukarıda 2. madde)** |

## Kod haritası

- Metinler: `supabase/functions/zx-legal/content/legal.json` (uygulamaya `assets/legal.json` olarak girer)
- Web: `supabase/functions/zx-legal/index.ts` (`?p=privacy|terms|presale|sales|content|imprint|promo`)
- Uygulama: `game/Legal.kt` (onaylar), `ui/LegalUi.kt` (pencereler, Ayarlar > Yasal), `game/Store.kt` (`confirm`,
  `launch`), `game/Notifier.kt` (bildirim türleri, `PromoOptOut`), `ui/Lobby.kt` (hesap ekranı onayı)
- Sunucu: `supabase/migrations/20261122_legal_compliance.sql` (`consents`, `traffic_log`, `zx_my_data`,
  `zx_my_purchases`, `zx_retention_purge`, `_zx_anonymize`), `zx-support` fonksiyonunda `purge`
- Zamanlama: `.github/workflows/retention.yml`

## Metin değişince

`legal.json` içinde `version` değerini artır ve `updated` tarihini güncelle: oyuncular bir sonraki açılışta güncel
aydınlatmayı görür, hesaplar Kullanım Şartları'nı yeniden onaylar.
