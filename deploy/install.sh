#!/usr/bin/env bash
# Zenvex Online oyun sunucusu kurulumu (Ubuntu 22.04 / 24.04, root ile):
#   sudo ./install.sh
# Bu klasörde zenvex_server programı olmalı (GitHub Actions → "Oyun Sunucusu" çıktısı).
# Sorulanlar: Supabase servis anahtarı (Supabase → Project Settings → API Keys → service_role / secret).
# Alan adın yoksa sunucunun IP adresinden ücretsiz bir alan adı kullanılır: <ip>.sslip.io (HTTPS sertifikası otomatik).
set -euo pipefail
cd "$(dirname "$0")"
[ "$(id -u)" = 0 ] || { echo "root ile çalıştır: sudo ./install.sh"; exit 1; }
[ -x ./zenvex_server ] || { echo "zenvex_server programı bu klasörde yok"; exit 1; }

IP=$(curl -fsS https://api.ipify.org || hostname -I | awk '{print $1}')
DOMAIN=${DOMAIN:-$(echo "$IP" | tr '.' '-').sslip.io}
if [ -z "${SB_SERVICE_KEY:-}" ]; then
  read -r -s -p "Supabase servis anahtarı (service_role): " SB_SERVICE_KEY; echo
fi

id zenvex >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin zenvex
install -d /opt/zenvex /etc/zenvex
install -m 755 zenvex_server /opt/zenvex/zenvex_server
umask 077
cat > /etc/zenvex/server.env <<ENV
BIND=127.0.0.1:7777
HEALTH_BIND=127.0.0.1:7778
SB_URL=${SB_URL:-https://blvstsvmvmdydyrnecpo.supabase.co}
SB_SERVICE_KEY=${SB_SERVICE_KEY}
INTEREST_PX=3000
ENV
chmod 600 /etc/zenvex/server.env
install -m 644 zenvex-server.service /etc/systemd/system/zenvex-server.service
systemctl daemon-reload
systemctl enable --now zenvex-server

# HTTPS (wss://) için Caddy: sertifikayı kendisi alır ve yeniler
if ! command -v caddy >/dev/null; then
  apt-get update -qq
  apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https curl gnupg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -qq && apt-get install -y -qq caddy
fi
cat > /etc/caddy/Caddyfile <<CADDY
${DOMAIN} {
    reverse_proxy 127.0.0.1:7777
}
CADDY
systemctl reload caddy || systemctl restart caddy
if command -v ufw >/dev/null; then ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null; fi

sleep 2
echo
echo "Kurulum tamam. Durum: curl -s 127.0.0.1:7778"
echo "Oyun sunucusu adresi:  wss://${DOMAIN}"
echo "Oyunu bu sunucuya geçirmek için Supabase SQL düzenleyicisinde çalıştır (ya da Claude'a adresi söyle):"
echo "  update app_config set value = '{\"url\": \"wss://${DOMAIN}\"}' where key = 'game_server';"
