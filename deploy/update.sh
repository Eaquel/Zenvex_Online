#!/usr/bin/env bash
# Yeni sürüm: bu klasördeki zenvex_server programını yerine koyar ve servisi yeniden başlatır (sudo ./update.sh)
set -euo pipefail
cd "$(dirname "$0")"
install -m 755 zenvex_server /opt/zenvex/zenvex_server
systemctl restart zenvex-server
sleep 1 && curl -s 127.0.0.1:7778 && echo
