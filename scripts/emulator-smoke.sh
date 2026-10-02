#!/usr/bin/env bash
# Teste l'APK de bout en bout sur un émulateur Android TV headless, dans Docker (KVM requis).
#
#   scripts/build-docker.sh            # produit app/build/outputs/apk/debug/app-x86_64-debug.apk
#   scripts/emulator-smoke.sh          # installe l'image système, démarre l'émulateur, lance l'app
#
# Captures d'écran dans .cache/smoke/. Le conteneur `replaytv-emu` reste actif pour d'autres
# commandes adb (`docker exec replaytv-emu adb shell …`) ; `docker rm -f replaytv-emu` pour l'arrêter.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${REPLAYTV_CACHE:-$ROOT/.cache}"
IMAGE="${REPLAYTV_BUILD_IMAGE:-thyrlian/android-sdk:latest}"
SYSIMG="system-images;android-36;android-tv;x86_64"
NAME=replaytv-emu

[ -c /dev/kvm ] || { echo "KVM indisponible : l'émulateur serait inutilisable." >&2; exit 1; }
[ -f "$ROOT/app/build/outputs/apk/debug/app-x86_64-debug.apk" ] || { echo "APK x86_64 absent : lancer scripts/build-docker.sh d'abord." >&2; exit 1; }

mkdir -p "$CACHE/android-sdk" "$CACHE/home/.android" "$CACHE/smoke"

# Émulateur + image système (≈ 1,5 Go, une seule fois).
if [ ! -d "$CACHE/android-sdk/system-images/android-36/android-tv/x86_64" ]; then
  docker run --rm --user "$(id -u):$(id -g)" -e HOME=/home/builder \
    -v "$CACHE/home:/home/builder" -v "$CACHE/android-sdk:/sdk" "$IMAGE" \
    bash -c "yes | sdkmanager --sdk_root=/sdk emulator '$SYSIMG' >/dev/null"
fi

docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" --device /dev/kvm --group-add "$(stat -c %g /dev/kvm)" \
  --user "$(id -u):$(id -g)" -e HOME=/home/builder -e QT_QPA_PLATFORM=offscreen \
  -v "$CACHE/home:/home/builder" -v "$CACHE/android-sdk:/sdk" -v "$ROOT:/project" -w /project \
  "$IMAGE" sleep infinity >/dev/null

# L'avdmanager de l'image est trop ancien pour cette image système : l'AVD est décrit à la main.
docker exec "$NAME" bash -c '
set -u
export PATH=/sdk/emulator:/sdk/platform-tools:$PATH ANDROID_AVD_HOME=/home/builder/.android/avd
AVD=$ANDROID_AVD_HOME/tv.avd; mkdir -p "$AVD"
printf "avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/tv.avd\ntarget=android-36\n" "$AVD" > "$ANDROID_AVD_HOME/tv.ini"
cat > "$AVD/config.ini" <<INI
AvdId=tv
avd.ini.displayname=tv
avd.ini.encoding=UTF-8
PlayStore.enabled=false
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=3072
hw.lcd.density=320
hw.lcd.width=1920
hw.lcd.height=1080
hw.keyboard=yes
hw.dPad=yes
hw.mainKeys=no
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.sdCard=no
hw.audioInput=no
hw.audioOutput=no
hw.camera.back=none
hw.camera.front=none
image.sysdir.1=system-images/android-36/android-tv/x86_64/
tag.display=Android TV
tag.id=android-tv
disk.dataPartition.size=16G
skin.name=1920x1080
skin.path=_no_skin
INI
nohup emulator -avd tv -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel on -no-snapshot -wipe-data > /tmp/emulator.log 2>&1 &
adb wait-for-device
for i in $(seq 1 150); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = "1" ] && break; sleep 2; done
echo "Android $(adb shell getprop ro.build.version.release) démarré"

PKG=dev.valentin.replaytv
adb install -r -g /project/app/build/outputs/apk/debug/app-x86_64-debug.apk | tail -1
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity | grep -E "Status|Error"
shot() { sleep "$1"; adb exec-out screencap -p > "/project/.cache/smoke/$2.png"; echo "capture $2"; }
key() { adb shell input keyevent "$1"; sleep 0.8; }
shot 8 01_home
key KEYCODE_DPAD_CENTER;  shot 10 02_catalogue
key KEYCODE_DPAD_CENTER;  shot 20 03_detail
key KEYCODE_DPAD_CENTER;  shot 20 04_player
echo "activité au premier plan : $(adb shell dumpsys activity activities | grep -oE "topResumedActivity=ActivityRecord\{[^ ]+ u0 [^ ]+" | sed "s/.* u0 //")"
key KEYCODE_BACK; key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER; shot 30 05_downloading
echo "== plantages éventuels"; adb logcat -d | grep -E "AndroidRuntime|FATAL" | tail -5 || true
'
echo "Captures : $CACHE/smoke/"
