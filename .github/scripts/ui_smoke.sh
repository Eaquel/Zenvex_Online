#!/usr/bin/env bash
# Emülatörde uygulamayı açar: önce normal açılış (lobi), sonra otomatik gösterim (Demo.kt) ekranları gezer.
# Çıktı: artifacts/ altında ekran görüntüleri, logcat ve özet.
set -x
mkdir -p artifacts
APK=$(ls apk/*.apk | head -1)
adb install -r -g "$APK" || exit 1
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c

# 1) normal açılış: açılış ekranı + lobi
adb shell am start -W -n com.zenvex.online/.MainActivity
sleep 12
adb exec-out screencap -p > artifacts/00_lobby.png
sleep 8
adb exec-out screencap -p > artifacts/00_lobby_b.png
adb shell am force-stop com.zenvex.online

# 2) otomatik gösterim: oyun içi ekranlar (uygulama kendi görüntüsünü alır)
adb shell am start -W -n com.zenvex.online/.MainActivity --es screen Loading --ez demo true
DIR=/sdcard/Android/data/com.zenvex.online/files/demo
for i in $(seq 1 90); do
  sleep 2
  if adb shell "test -f $DIR/done && echo yes" | grep -q yes; then break; fi
  # uygulama çöktüyse beklemeyi bırak
  if ! adb shell pidof com.zenvex.online > /dev/null; then echo "UYGULAMA KAPANDI"; break; fi
done
adb exec-out screencap -p > artifacts/99_last.png
adb pull "$DIR" artifacts/demo || true
adb logcat -d > artifacts/logcat.txt
grep -E "ZXDEMO|FATAL|AndroidRuntime|libzenvex|panicked" artifacts/logcat.txt > artifacts/summary.txt || true
ls -la artifacts artifacts/demo || true
exit 0
