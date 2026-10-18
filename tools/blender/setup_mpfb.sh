#!/usr/bin/env bash
# MakeHuman/MPFB2 eklentisi + CC0 varlık paketini kurar (karakter renderı için).
# Kullanım: bash tools/blender/setup_mpfb.sh [hedef_dizin=/home/user/mh]
set -euo pipefail
MH="${1:-/home/user/mh}"
mkdir -p "$MH" && cd "$MH"
[ -f mpfb.zip ] || curl -sSL -o mpfb.zip "https://extensions.blender.org/download/sha256:c075b3e9b94b8e5556a5f03f4653602d02ea306de08126e8f55ed6dee28a2dad/add-on-mpfb-v2.0.18.zip?repository=%2Fapi%2Fv1%2Fextensions%2F&blender_version_min=4.2.0"
[ -f sys.zip ] || curl -sSL -o sys.zip https://files.makehumancommunity.org/asset_packs/makehuman_system_assets/makehuman_system_assets_cc0.zip
rm -rf pkgs/mpfb && mkdir -p pkgs && unzip -q -o mpfb.zip -d pkgs/mpfb
[ -d sysassets ] || unzip -q -o sys.zip -d sysassets
# eklenti dışı (bpy modülü) kullanım için kullanıcı dizini yamalanır
sed -i "s|        self._user_home = bpy.utils.extension_path_user(MPFB_CONTEXTUAL_INFORMATION\[\"__package__\"\])|        self._user_home = os.environ.get(\"MPFB_HOME\", \"$MH/user\")|" pkgs/mpfb/services/locationservice.py
mkdir -p user/data
for d in sysassets/*; do ln -sfn "$MH/$d" "user/data/$(basename "$d")"; done
echo "MPFB hazır: $MH"
