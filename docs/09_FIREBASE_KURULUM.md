# Çevrimiçi Mod (Firebase) Kurulumu — Sadece Telefonla

Oyun Firebase ayarı olmadan da tam çalışır (çevrimdışı). Ayarlar eklenince **canlı oyuncu konumları**, **Genel** ve **Yakın** sohbet açılır.

## 1. Firebase projesi aç (telefondan tarayıcı)
1. https://console.firebase.google.com → **Proje ekle** → ad: `zenvex-online` → Analytics kapalı olabilir.
2. Sol menü **Build → Authentication → Get started → Sign-in method → Anonymous → Enable**.
3. **Build → Realtime Database → Create database** → konum: `europe-west1` → **Locked mode**.
4. **Rules** sekmesine bu depodaki `firebase/database.rules.json` içeriğini yapıştır → **Publish**.
5. **Proje ayarları (⚙) → Genel → Uygulamalarınız → Android simgesi**: paket adı `com.zenvex.online` → Kaydet (google-services.json indirmen GEREKMEZ).

## 2. Değerleri GitHub'a gizli anahtar olarak ekle
GitHub'da depo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Nereden |
|---|---|
| `FB_API_KEY` | Proje ayarları → Genel → *Web API Key* |
| `FB_APP_ID` | Proje ayarları → Uygulamalarınız → Android → *App ID* (`1:...:android:...`) |
| `FB_PROJECT_ID` | Proje ayarları → *Project ID* |
| `FB_DB_URL` | Realtime Database sayfasının üstündeki adres (`https://...firebasedatabase.app`) |

## 3. Yeni derleme
Herhangi bir push (veya Actions → *Android APK* → **Run workflow**) yeni APK üretir. Lobide **SUNUCU: Çevrimiçi** yazıyorsa hazır.

## Veri yapısı
- `/world/dunya1/z` ev sahibinin zombi görüntüsü · `/world/dunya1/hits` vuruşlar · `/world/dunya1/kills/<uid>` ödüller · `/world/dunya1/pdmg/<uid>` hasar
- `/trade/<a_b>` takas oturumu · `/trade_req/<uid>` takas istekleri
(Kurallar `firebase/database.rules.json` içinde — kurallar değiştiyse tekrar yapıştırıp Publish et.)

- `/presence/dunya1/<uid>` → `{n: isim, s: [x, y, yön, anim, t, karakter, ekip0, ekip1, ekip2, araç], ts}` (8 Hz, bağlantı kopunca silinir)
- `/chat/global/<id>`, `/chat/local/<id>` → `{n, m, u, g, ts}` (+ `x, y` yakın sohbette)

Ücretsiz Spark planı ~100 eşzamanlı bağlantıya kadar yeterlidir. İleride kendi Rust sunucumuza (aynı `zenvex_core`) geçilecek.
