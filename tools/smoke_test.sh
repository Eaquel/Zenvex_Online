#!/usr/bin/env bash
# Emülatörde duman testi: uygulamayı aç, ekranları gez, oyunda joysticklere dokun, ekran görüntüsü al,
# çökme varsa hata ver. Kullanım: tools/smoke_test.sh <apk> <çıktı_klasörü>
set -u
APK="$1"
OUT="$2"
PKG=online.zenvex
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
alive() { adb shell pidof "$PKG" > /dev/null; }
open_screen() {
  adb shell am force-stop "$PKG"
  if [ -n "${1:-}" ]; then
    adb shell am start -W -n "$PKG/.MainActivity" --es screen "$1" > /dev/null
  else
    adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  fi
}
fail() {
  echo "HATA: $1"
  adb logcat -d > "$OUT/logcat.txt"
  grep -E "FATAL EXCEPTION|AndroidRuntime" -A25 "$OUT/logcat.txt" | head -80
  exit 1
}

adb install -r "$APK" || fail "kurulum başarısız"
adb logcat -c

open_screen
sleep 3; shot 01_splash
sleep 4; shot 02_title
alive || fail "açılışta çöktü"

open_screen Lobby
sleep 5; shot 03_lobby
alive || fail "lobide çöktü"

n=4
for s in Character Skills Inventory Shop Profile Servers Settings; do
  open_screen "$s"
  sleep 4; shot "0${n}_${s}"
  alive || fail "$s ekranında çöktü"
  n=$((n + 1))
done

open_screen Game
sleep 8; shot 11_game
alive || fail "oyun ekranında çöktü"

read -r W H < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr x ' ')
if [ "$W" -lt "$H" ]; then t=$W; W=$H; H=$t; fi
echo "ekran: ${W}x${H}"

# hareket joysticki: sola dokun, sağa sürükle
adb shell input swipe $((W / 5)) $((H * 3 / 4)) $((W / 5 + 160)) $((H * 3 / 4)) 4000 &
sleep 2; shot 12_move
wait
# yukarı yürü
adb shell input swipe $((W / 5)) $((H * 3 / 4)) $((W / 5)) $((H * 3 / 4 - 160)) 3000 &
sleep 1.5; shot 13_move_up
wait
# saldırı joysticki: sağa dokun, sola çevir, basılı tut
adb shell input swipe $((W * 4 / 5)) $((H * 3 / 4)) $((W * 4 / 5 - 130)) $((H * 3 / 4)) 5000 &
sleep 2; shot 14_attack
sleep 2; shot 15_attack2
wait
sleep 2; shot 16_after
alive || fail "oynanış sırasında çöktü"

adb logcat -d > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION" -A3 "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "logcat'te çökme var"
fi
echo "DUMAN TESTİ BAŞARILI"
