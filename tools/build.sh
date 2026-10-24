#!/usr/bin/env bash
# EV FIT 빌드 — 안드로이드 빌드 도구가 한글 경로에서 실패하므로 C:/tmp로 복사해 빌드하고 결과물만 가져온다
# 사용: bash tools/build.sh test|debug|install|release
set -euo pipefail
MODE="${1:-debug}"
SRC="$(cd "$(dirname "$0")/.." && pwd)"     # ev-fit
TOP="$(cd "$SRC/.." && pwd)"                # 33. 현대앱
WORK="/c/tmp/ev-fit-build"
export JAVA_HOME="C:/Android/java/jdk-17.0.19+10"
GRADLE="$(ls -d ~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13 | head -1)/bin/gradle"

mkdir -p "$WORK"
rm -rf "$WORK/app/src"
tar -C "$SRC" --exclude=.git --exclude=build --exclude=.gradle --exclude=apk --exclude=docs -cf - . | tar -C "$WORK" -xf -
echo 'sdk.dir=C\:\\Android\\sdk' > "$WORK/local.properties"
if [ -f "$TOP/keys/keystore.properties" ]; then
    cp "$TOP/keys/keystore.properties" "$WORK/keystore.properties"
    cp "$TOP/keys/evfit-release.jks" "$WORK/release.jks"
fi

cd "$WORK"
case "$MODE" in
  test)    "$GRADLE" :app:testDebugUnitTest --console=plain ;;
  debug|install)
           "$GRADLE" :app:assembleDebug --console=plain
           mkdir -p "$TOP/out"
           cp app/build/outputs/apk/debug/app-debug.apk "$TOP/out/evfit-debug.apk"
           echo "APK: $TOP/out/evfit-debug.apk"
           if [ "$MODE" = install ]; then adb install -r "$TOP/out/evfit-debug.apk"; fi ;;
  release) "$GRADLE" :app:assembleRelease --console=plain
           mkdir -p "$SRC/apk"
           cp app/build/outputs/apk/release/app-release.apk "$SRC/apk/evfit-release.apk"
           echo "APK: $SRC/apk/evfit-release.apk" ;;
  *) echo "사용: bash tools/build.sh test|debug|install|release"; exit 1 ;;
esac
