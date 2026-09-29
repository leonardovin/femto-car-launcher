#!/usr/bin/env bash
# Android 9 fork: install the launcher on the Haval H6 GT head unit.
#
# The beantechs firmware refuses `adb install` ("beantechs disallow apk"); an
# install that carries the Play Store's installer identity goes through, the
# same path shizuku-bottom-bar's deploy.sh uses.
#
#   ./gradlew :app:assembleStableDebug && scripts/deploy-to-car.sh [serial]
#
# The serial is the car's ADB endpoint (e.g. 100.x.y.z:5555 over Tailscale).
# Without one, CAR_SERIAL is used, then the only connected device whose model
# matches the head unit's SoC (msmnile; override with CAR_MODEL_MATCH).
set -euo pipefail
# Git Bash on Windows would rewrite the device-side paths below.
export MSYS_NO_PATHCONV=1

APK="${APK_PATH:-app/build/outputs/apk/stable/debug/app-stable-debug.apk}"
REMOTE="/data/local/tmp/femto-launcher.apk"
PKG="io.github.seijikohara.femto"
CAR_MODEL_MATCH="${CAR_MODEL_MATCH:-msmnile}"

if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK not found at $APK. Build it first: ./gradlew :app:assembleStableDebug" >&2
    exit 1
fi

DEVICE="${1:-${CAR_SERIAL:-}}"
if [[ -z "$DEVICE" ]]; then
    mapfile -t CANDIDATES < <(
        adb devices | awk 'NR > 1 && $2 == "device" { print $1 }' | while read -r serial; do
            model="$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
            [[ "$model" == *"$CAR_MODEL_MATCH"* ]] && echo "$serial"
        done
    )
    if [[ ${#CANDIDATES[@]} -ne 1 ]]; then
        echo "ERROR: found ${#CANDIDATES[@]} connected head units; pass the serial explicitly." >&2
        exit 1
    fi
    DEVICE="${CANDIDATES[0]}"
fi
echo ">> Deploying to $DEVICE"
ADB=(adb -s "$DEVICE")

echo ">> Pushing APK..."
"${ADB[@]}" push "$APK" "$REMOTE"

echo ">> Installing with the Play Store installer identity..."
INSTALL_OUT="$("${ADB[@]}" shell pm install -i com.android.vending -r "$REMOTE" 2>&1 | tr -d '\r' || true)"
echo "$INSTALL_OUT"
"${ADB[@]}" shell rm -f "$REMOTE" || true
if ! grep -q '^Success' <<<"$INSTALL_OUT"; then
    if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<<"$INSTALL_OUT"; then
        echo "ERROR: a launcher signed with another key is installed; uninstall it first." >&2
    fi
    exit 1
fi

echo ">> Launching..."
"${ADB[@]}" shell am start -n "$PKG/.MainActivity"
echo "Done. With Shizuku running, allow the launcher in Settings > System > Head unit."
