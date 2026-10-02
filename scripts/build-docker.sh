#!/usr/bin/env bash
# Compile l'APK sans installer Android Studio ni le SDK : tout tourne dans un conteneur
# (JDK 17 + Android SDK). Les caches Gradle et SDK sont conservés dans .cache/ entre deux builds.
#
#   scripts/build-docker.sh                 # assembleDebug
#   scripts/build-docker.sh assembleRelease # autre tâche Gradle
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${REPLAYTV_CACHE:-$ROOT/.cache}"
IMAGE="${REPLAYTV_BUILD_IMAGE:-thyrlian/android-sdk:latest}"
TASK="${1:-assembleDebug}"

mkdir -p "$CACHE/android-sdk" "$CACHE/home/.gradle" "$CACHE/home/.android"

# Première exécution : copie du SDK de l'image vers un volume inscriptible,
# pour que Gradle puisse y télécharger platforms/build-tools et les conserver.
if [ ! -d "$CACHE/android-sdk/cmdline-tools" ]; then
  docker run --rm -v "$CACHE/android-sdk:/sdk" "$IMAGE" \
    bash -c "cp -a /opt/android-sdk/. /sdk/ && chown -R $(id -u):$(id -g) /sdk"
fi

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/home/builder \
  -e GRADLE_USER_HOME=/home/builder/.gradle \
  -e ANDROID_HOME=/sdk \
  -e ANDROID_USER_HOME=/home/builder/.android \
  -v "$CACHE/android-sdk:/sdk" \
  -v "$CACHE/home:/home/builder" \
  -v "$ROOT:/project" \
  -w /project \
  "$IMAGE" \
  ./gradlew --no-daemon "$TASK"

echo
echo "APK produits :"
find "$ROOT/app/build/outputs/apk" -name '*.apk' 2>/dev/null || true
