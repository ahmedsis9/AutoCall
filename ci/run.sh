#!/bin/bash
PKG=com.example.autocall
dump() { adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml > /tmp/u.xml; python3 - <<'PY'
import re
x=open('/tmp/u.xml',encoding='utf-8').read()
print([t for t in re.findall(r'text="([^"]*)"',x) if t][:30])
PY
}
tapText() { python3 - "$1" <<'PY'
import re,sys,subprocess
x=open('/tmp/u.xml',encoding='utf-8').read()
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x):
    if sys.argv[1] in m.group(1):
        a=list(map(int,m.groups()[1:])); subprocess.run(['adb','shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2)]); print('tapped',m.group(1)); break
PY
}
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.CALL_PHONE
adb shell pm grant $PKG android.permission.READ_PHONE_STATE
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -n $PKG/.SplashActivity
sleep 3; echo "=== SPLASH ==="; dump
sleep 6; echo "=== REGISTER ==="; dump
echo "=== MAIN (after ci register) ==="
adb shell am force-stop $PKG
adb shell am start -n $PKG/.SplashActivity --ez ci true
sleep 10; dump
echo "=== EDITOR ==="; tapText "اتصال جديد"; sleep 2; dump
echo "=== CRASH ==="; adb logcat -d -b crash | head -40
