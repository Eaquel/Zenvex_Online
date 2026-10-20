// Zenvex Online herkese açık sayfalar: ana sayfa (?p=home), gizlilik politikası (?p=privacy), kullanım şartları (?p=terms).
// Google OAuth izin ekranı ve Play Store için gereken bağlantılar. Supabase varsayılan alan adında HTML'i düz metne
// çevirdiği için sayfalar okunaklı düz metin olarak sunulur.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const BASE = "https://blvstsvmvmdydyrnecpo.supabase.co/functions/v1/zx-legal";

const pages: Record<string, string> = {
  home: `ZENVEX ONLINE
=============

Zenvex Online; kıyamet sonrası bir şehirde geçen, çevrimiçi bir 2.5D piksel sanat zombi hayatta kalma oyunudur.
Kapüşonlu savaşçıyla zombilerle savaş, malzeme topla, üret, köpeğini eğit ve diğer oyuncularla birlikte hayatta kal.

HESAP
Oyuna misafir olarak, Zenvex kimliği ve anahtarıyla ya da Google hesabınla girebilirsin.

BAĞLANTILAR
Gizlilik politikası:  ${BASE}?p=privacy
Kullanım şartları:    ${BASE}?p=terms
`,
  privacy: `ZENVEX ONLINE — GİZLİLİK POLİTİKASI
==================================
Son güncelleme: 20 Ekim 2026

Bu politika Zenvex Online mobil oyununun ("oyun") hangi verileri neden işlediğini açıklar.

1. TOPLANAN VERİLER
- Hesap bilgileri: Zenvex kimliği, oyuncu adı ve şifrelenmiş (bcrypt) hesap anahtarı.
- Google ile giriş yaparsan: Google hesap kimliğin ("sub"), e-posta adresin ve adın. Yalnızca hesabını tanımak
  için kullanılır; Google şifren oyuna hiçbir zaman ulaşmaz.
- Oyun verileri: seviye, envanter, ilerleme, klan üyeliği, oyun içi posta ve sohbet mesajları.
- Çevrimiçi oyun: aynı sunucudaki oyuncularla paylaşılan konum, ad ve ekipman bilgisi (yalnızca oyun süresince).

2. KULLANIM AMACI
Veriler yalnızca oyunu çalıştırmak için kullanılır: giriş, ilerlemenin bulutta saklanması, sıralama, klan, sohbet ve
çok oyunculu oynanış. Reklam ya da profil çıkarma için kullanılmaz, üçüncü kişilere satılmaz.

3. SAKLAMA VE HİZMET SAĞLAYICILAR
Veriler Supabase (AB, Frankfurt) sunucularında saklanır. Google ile girişte kimlik doğrulaması Google tarafından yapılır.

4. HAKLARIN VE SİLME
Hesabının ve verilerinin silinmesini oyun içinden (Ayarlar > Hesap) ya da oyunun mağaza sayfasındaki geliştirici
iletişim adresinden isteyebilirsin. Talep 30 gün içinde yerine getirilir.

5. ÇOCUKLAR
Oyun 13 yaşın altındaki çocuklara yönelik değildir ve bilerek bu yaş grubundan veri toplanmaz.
`,
  terms: `ZENVEX ONLINE — KULLANIM ŞARTLARI
================================

Zenvex Online'ı oynayarak şu kuralları kabul edersin:
- Hile, açık istismarı ve diğer oyunculara taciz yasaktır; ihlalde hesap askıya alınabilir.
- Oyun içi eşya ve altının gerçek parayla değeri yoktur ve oyun dışında alınıp satılamaz.
- Oyun "olduğu gibi" sunulur; sunucu bakımı ve güncellemeler sırasında kesinti olabilir.
`,
};

Deno.serve((req: Request) => {
  const p = new URL(req.url).searchParams.get("p") ?? "home";
  return new Response(pages[p] ?? pages.home, {
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=600" },
  });
});
