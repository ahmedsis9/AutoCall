#!/bin/bash
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.example.autocall
adb install -r $APK
adb shell pm grant $PKG android.permission.CALL_PHONE
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -n $PKG/.SplashActivity --ez ci true
sleep 12
echo "=== PID ==="; adb shell pidof $PKG || echo "PROCESS NOT RUNNING"
echo "=== CRASH BUFFER ==="; adb logcat -d -b crash | head -80
echo "=== AndroidRuntime/Hamko ==="; adb logcat -d | grep -E "AndroidRuntime|FATAL|com.example.autocall" | head -60
echo "=== SCREEN ==="; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | head -40
echo "=== wait for alarm (95s) ==="; sleep 5
echo "=== STORE ==="; adb shell run-as $PKG cat shared_prefs/autocall_data.xml
echo "=== CRASH BUFFER 2 ==="; adb logcat -d -b crash | head -40
echo "=== TELECOM ==="; adb logcat -d | grep -iE "placeCall|Telecom|CallActivity|AlarmReceiver" | head -30
