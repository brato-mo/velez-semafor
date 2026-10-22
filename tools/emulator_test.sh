#!/bin/bash
# Smoke test of the APK on an Android emulator (used by .github/workflows/android-test.yml).
# Installs the APK, opens it in light and dark mode, runs the hourly check and the morning
# summary, and saves screenshots, notifications, scheduled jobs/alarms and the log to OUT.
#   bash tools/emulator_test.sh velez-semafor.apk emu-out
set -u
APK=${1:-velez-semafor.apk}
OUT=${2:-emu-out}
PKG=io.github.bratomo.velez
mkdir -p "$OUT"

adb wait-for-device
adb root >/dev/null 2>&1
sleep 3
adb wait-for-device
adb shell getprop ro.build.version.release > "$OUT/android-version.txt"
# a freshly booted emulator often shows "Pixel Launcher isn't responding": let it settle and close such dialogs
sleep 25
nodialogs() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; }
nodialogs
adb logcat -c

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS >> "$OUT/install.txt" 2>&1
adb shell cmd uimode night no
adb shell am start -W -n $PKG/.MainActivity > "$OUT/start.txt" 2>&1
sleep 35
nodialogs; sleep 1
adb exec-out screencap -p > "$OUT/1-start-light.png"

# hourly job: registered? run it now (first run only remembers what it saw)
adb shell dumpsys jobscheduler | grep -A6 "$PKG" | head -60 > "$OUT/jobs.txt"
adb shell cmd jobscheduler run -f $PKG 4201 >> "$OUT/jobs.txt" 2>&1
sleep 20
adb shell dumpsys alarm | grep -B1 -A8 "$PKG" | head -80 > "$OUT/alarms.txt"

# morning summary right now
adb shell am broadcast -n $PKG/.MorningReceiver -a io.github.bratomo.velez.MORNING > "$OUT/morning.txt" 2>&1
sleep 20
adb shell dumpsys notification --noredact | grep -A14 "pkg=$PKG" | head -120 > "$OUT/notifications.txt"
adb shell cmd statusbar expand-notifications
sleep 3
adb exec-out screencap -p > "$OUT/2-notifications.png"
adb shell cmd statusbar collapse

# settings view through the page itself (tap the gear: top right)
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
adb shell am start -W -n $PKG/.MainActivity >> "$OUT/start.txt" 2>&1
sleep 3
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
nodialogs
adb shell input tap $((W * 990 / 1080)) $((H * 317 / 2400))
sleep 4
nodialogs; sleep 1
adb exec-out screencap -p > "$OUT/3-settings.png"
adb shell input keyevent KEYCODE_BACK
sleep 2

# dark mode, fresh start
adb shell cmd uimode night yes
sleep 2
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity >> "$OUT/start.txt" 2>&1
sleep 25
nodialogs; sleep 1
adb exec-out screencap -p > "$OUT/4-start-dark.png"
adb shell cmd uimode night no

adb shell ls -la /data/data/$PKG/files /data/data/$PKG/shared_prefs > "$OUT/app-files.txt" 2>&1
adb shell cat /data/data/$PKG/shared_prefs/velez.xml >> "$OUT/app-files.txt" 2>&1
adb logcat -d > "$OUT/logcat-full.txt"
grep -E "FATAL EXCEPTION|AndroidRuntime|$PKG|chromium" "$OUT/logcat-full.txt" | tail -300 > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat-full.txt"; then echo "CRASH" > "$OUT/result.txt"; else echo "OK" > "$OUT/result.txt"; fi
rm -f "$OUT/logcat-full.txt"
exit 0
