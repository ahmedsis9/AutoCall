#!/bin/bash
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.example.autocall
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | grep -v 'text=""' | head -${1:-30}; }
adb install -r $APK
adb shell pm grant $PKG android.permission.CALL_PHONE
adb shell pm grant $PKG android.permission.READ_PHONE_STATE
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c
echo "=== 1) LOGIN/REGISTER SCREEN ==="
adb shell am start -n $PKG/.SplashActivity
sleep 9; dump 20
adb shell am force-stop $PKG
echo "=== 2) MAIN SCREEN ==="
adb shell am start -n $PKG/.SplashActivity --ez ci true
sleep 9; dump 40
echo "=== LOCKING DEVICE ==="
adb shell locksettings set-pin 1234
adb shell input keyevent 223
sleep 125
echo "=== A (no overlay, locked) STORE ==="
adb shell run-as $PKG cat shared_prefs/autocall_data.xml | grep -o 'lastResult[^,]*'
adb shell dumpsys telephony.registry | grep -m2 -i "mCallState"
adb shell input keyevent 6
sleep 2
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
sleep 120
echo "=== B (overlay allowed, locked) STORE ==="
adb shell run-as $PKG cat shared_prefs/autocall_data.xml | grep -o 'lastResult[^,]*'
adb shell dumpsys telephony.registry | grep -m2 -i "mCallState"
echo "=== CRASH BUFFER ==="; adb logcat -d -b crash | head -40
echo "=== TELECOM ==="; adb logcat -d | grep -iE "AlarmReceiver|CallActivity|placeCall|NEW_OUTGOING|Background activity" | head -20
