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
# sistem uyarıları görüntüyü kapatmasın: tam ekran ipucu ve yavaş başlatıcının "yanıt vermiyor" penceresi
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put global hide_error_dialogs 1
adb shell settings put global anr_show_background 0
sleep 20
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb logcat -c

# 1) normal açılış: açılış ekranı + lobi
adb shell am start -W -n com.zenvex.online/.MainActivity
sleep 12
adb exec-out screencap -p > artifacts/00_lobby.png
sleep 8
adb exec-out screencap -p > artifacts/00_lobby_b.png
adb shell am force-stop com.zenvex.online

# 2) otomatik gösterim: uygulama "ZXDEMO: ready <ad>" yazınca ekranı yakala
adb logcat -c
adb shell am start -W -n com.zenvex.online/.MainActivity --es screen Loading --ez demo true
mkdir -p artifacts/demo
timeout 240 adb logcat -v brief -s ZXDEMO:I | while read -r line; do
  case "$line" in
    *"ready "*) n=$(echo "$line" | sed 's/.*ready //' | tr -d '\r'); adb exec-out screencap -p > "artifacts/demo/$n.png" < /dev/null ;;
    *" done"*) break ;;
  esac
done
adb exec-out screencap -p > artifacts/99_last.png
adb logcat -d > artifacts/logcat.txt
grep -E "ZXDEMO|FATAL|AndroidRuntime|libzenvex|panicked" artifacts/logcat.txt > artifacts/summary.txt || true
ls -la artifacts artifacts/demo || true
exit 0
