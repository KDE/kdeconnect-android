#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  printf 'ERROR: adb is not installed or is not on PATH. Install Android platform-tools, connect both devices by USB, then retry.\n' >&2
  exit 127
fi

if [[ $# -lt 2 ]]; then
  printf 'Usage: %s PHONE_USB_SERIAL TABLET_USB_SERIAL [scenario]\n' "$0" >&2
  printf 'Scenarios: normal wifi-one wifi-both router network guest airplane no-wifi flap screen-doze-reboot distance clipboard-100 overnight\n' >&2
  exit 2
fi

PHONE_SERIAL=$1
TABLET_SERIAL=$2
SCENARIO=${3:-manual}
PACKAGE=com.ecosystem.personal.debug
ACTIVITY=org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity
OUT=${CONNECTION_LAB_OUT:-"$PWD/connection-lab-${SCENARIO}-$(date +%Y%m%d-%H%M%S)"}
mkdir -p "$(dirname -- "$OUT")"

capture_logs() {
  local status=$?
  adb -s "$PHONE_SERIAL" logcat -d -v threadtime -s ConnectionLab EcosystemRecovery EcosystemHotspot WifiDirectLink EcosystemClipboard BluetoothLinkProvider LanLinkProvider KDE/sendPacket KDE/onConnectionLost > "${OUT}-phone.log" 2>&1 || true
  adb -s "$TABLET_SERIAL" logcat -d -v threadtime -s ConnectionLab EcosystemRecovery EcosystemHotspot WifiDirectLink EcosystemClipboard BluetoothLinkProvider LanLinkProvider KDE/sendPacket KDE/onConnectionLost > "${OUT}-tablet.log" 2>&1 || true
  if [[ $status -ne 0 ]]; then
    printf 'Scenario command failed with exit %s. Captured device logs:\n  %s-phone.log\n  %s-tablet.log\n' "$status" "$OUT" "$OUT" >&2
  fi
  return "$status"
}
trap capture_logs EXIT

assert_usb_device() {
  local serial=$1
  if [[ "$serial" == *:* ]]; then
    printf 'ERROR: %s looks like a network ADB serial; this script accepts USB devices only.\n' "$serial" >&2
    exit 2
  fi
  if ! adb devices -l | awk -v id="$serial" '$1 == id && $2 == "device" { found=1 } END { exit !found }'; then
    printf 'ERROR: USB device %s is not visible in `adb devices -l` or is unauthorized.\n' "$serial" >&2
    adb devices -l >&2
    exit 2
  fi
  if [[ $(adb -s "$serial" get-state 2>/dev/null || true) != device ]]; then
    printf 'ERROR: ADB transport %s is not in device state.\n' "$serial" >&2
    exit 2
  fi
}

assert_usb_device "$PHONE_SERIAL"
assert_usb_device "$TABLET_SERIAL"

device_action() {
  local serial=$1 action=$2 scenario=${3:-}
  adb -s "$serial" shell am start -n "$PACKAGE/$ACTIVITY" \
    --es background_screen connection_lab --es lab_action "$action" --es scenario "$scenario" >/dev/null
}

mark_drop_both() {
  device_action "$PHONE_SERIAL" drop "$1"
  device_action "$TABLET_SERIAL" drop "$1"
}

wifi() {
  adb -s "$1" shell cmd wifi set-wifi-enabled "$2"
}

case "$SCENARIO" in
  normal|router|network|guest|airplane|no-wifi|flap|screen-doze-reboot|distance|clipboard-100|overnight|wifi-one|wifi-both|manual) ;;
  *) printf 'ERROR: unknown scenario: %s\n' "$SCENARIO" >&2; exit 2 ;;
esac

device_action "$PHONE_SERIAL" start "$SCENARIO"
device_action "$TABLET_SERIAL" start "$SCENARIO"
printf 'Connection Lab started: %s\n' "$SCENARIO"
printf 'Both phones are addressed only by their USB ADB serials. Logcat tag: ConnectionLab.\n'

case "$SCENARIO" in
  wifi-one)
    mark_drop_both "Wi-Fi disabled on phone"
    wifi "$PHONE_SERIAL" disabled
    read -r -p 'Observe fallback, then press Enter to restore phone Wi-Fi. '
    wifi "$PHONE_SERIAL" enabled
    ;;
  wifi-both)
    mark_drop_both "Wi-Fi disabled on both devices"
    wifi "$PHONE_SERIAL" disabled
    wifi "$TABLET_SERIAL" disabled
    read -r -p 'Observe offline fallback, then press Enter to restore Wi-Fi. '
    wifi "$PHONE_SERIAL" enabled
    wifi "$TABLET_SERIAL" enabled
    ;;
  flap)
    mark_drop_both "Rapid Wi-Fi flapping begins"
    for ((i=0; i<60; i++)); do
      if (( i % 2 == 0 )); then
        wifi "$PHONE_SERIAL" disabled
        wifi "$TABLET_SERIAL" disabled
      else
        wifi "$PHONE_SERIAL" enabled
        wifi "$TABLET_SERIAL" enabled
      fi
      sleep 5
    done
    ;;
  normal)
    printf 'Run 50 clipboard copies and send a 500 MB file now. Press Enter when both finish.\n'
    read -r
    ;;
  distance)
    mark_drop_both "$SCENARIO: walk away and observe route changes"
    printf 'Walk away until the connection drops, then return and wait for recovery.\n'
    read -r -p 'Record farthest distance and the link that broke at each point: ' DISTANCE_NOTE
    device_action "$PHONE_SERIAL" note "$DISTANCE_NOTE"
    device_action "$TABLET_SERIAL" note "$DISTANCE_NOTE"
    ;;
  router|network|guest|airplane|no-wifi|screen-doze-reboot|clipboard-100|overnight|manual)
    mark_drop_both "$SCENARIO: mark the physical/environmental drop now"
    printf 'Perform the %s actions from the Connection Lab test guide. Press Enter when this case is complete.\n' "$SCENARIO"
    read -r
    ;;
esac

device_action "$PHONE_SERIAL" finish
device_action "$TABLET_SERIAL" finish
device_action "$PHONE_SERIAL" export
device_action "$TABLET_SERIAL" export
sleep 1
adb -s "$PHONE_SERIAL" shell run-as "$PACKAGE" cat files/connection-lab.tsv > "${OUT}-phone.tsv"
adb -s "$TABLET_SERIAL" shell run-as "$PACKAGE" cat files/connection-lab.tsv > "${OUT}-tablet.tsv"
printf 'Wrote reports:\n  %s\n  %s\n' "${OUT}-phone.tsv" "${OUT}-tablet.tsv"
printf 'Captured logs:\n  %s-phone.log\n  %s-tablet.log\n' "$OUT" "$OUT"
