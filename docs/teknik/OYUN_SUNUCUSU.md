# Oyun Sunucusu (zenvex_server)

Canlı oyun (oyuncu konumları, zombiler, vuruşlar, sohbet, takas) artık kendi sunucumuzda. Hesap, kayıt, klan,
posta, sıralama yine Supabase'de.

## Neden

Supabase Realtime ücretsiz planda **saniyede 100 mesaj** ve **ayda 2 milyon mesaj** sınırlı; her yayını alıcı sayısı
kadar sayıyor. 10 oyuncu saniyede ~550 mesaj üretiyordu: sınır aşılınca bağlantılar kısılıyor, oyun "ölüyordu".
Kendi sunucumuzda böyle bir sınır yok; 100 botluk yük testinde ortalama ping 7 ms, işlemci %3, bellek 21 MB.

## Nasıl çalışır

| | |
|-|-|
| Oda | (oyun sunucusu, bölge): oyuncu yalnız kendi bölgesindekileri görür |
| Konum | istemci hareket ederken 3,3 Hz gönderir; sunucu 10 Hz tek pakette ve yalnız 3000 px içindekilere dağıtır |
| Zombiler | bölgedeki bir oyuncu yönetir (ev sahibi); ev sahibini **sunucu** seçer, ayrılınca hemen devreder |
| Hedefli mesaj | vuruş, hasar, PvP, takas, fısıltı yalnız alıcıya gider |
| Kimlik | istemci oturum numarası + oturum sırrıyla imzalı zaman damgası yollar; sunucu `zx_net_auth` ile doğrular |
| Koruma | saniyede en çok 40 mesaj, sohbette 2,5 sn, 32 KB'tan büyük mesaj atılır, 40 sn sessiz bağlantı kapanır |
| Geri dönüş | `app_config.game_server.url` boşsa istemciler eskisi gibi Supabase Realtime kullanır |

## Kurulum (bir kez)

1. **Sunucu kirala.** Öneri: Hetzner CX22 (2 çekirdek, 4 GB, ~5 €/ay) ya da Oracle Cloud Always Free (ücretsiz,
   ARM). İşletim sistemi Ubuntu 24.04. 100 oyuncu için CX22 yeter; daha fazlası için CPX31.
2. **Programı indir.** GitHub → Actions → "Oyun Sunucusu" → son çalışma → Artifacts:
   `zenvex-server-x64` (Hetzner, çoğu sunucu) ya da `zenvex-server-arm64` (Oracle ARM).
3. **Sunucuya kopyala ve kur:**
   ```
   scp zenvex-server-x64.tar.gz root@SUNUCU_IP:
   ssh root@SUNUCU_IP
   tar xzf zenvex-server-x64.tar.gz && cd zenvex-server && ./install.sh
   ```
   Betik Supabase **servis anahtarını** sorar (Supabase → Project Settings → API Keys → `service_role`). Bu anahtar
   yalnız sunucuda, `/etc/zenvex/server.env` içinde (yalnız root okur) durur; depoya ya da uygulamaya girmez.
4. Betik sonunda adresi yazar (`wss://<ip>.sslip.io`). Oyunu sunucuya geçirmek için bu adresi `app_config`'e yaz
   (ya da Claude'a söyle): oyuncuların uygulamayı güncellemesi gerekmez, bir sonraki girişte geçerler.

## Güncelleme

Yeni paketi indirip `./update.sh`.

## İzleme

Sunucuda `curl -s 127.0.0.1:7778` → oyuncu sayısı, oda başına oyuncu, saniyelik mesaj. Günlük:
`journalctl -u zenvex-server -f`.

## Yük testi

```
ALLOW_GUESTS=1 BIND=127.0.0.1:7790 ./zenvex_server &
./zenvex_server loadtest ws://127.0.0.1:7790 100 30
```
