# Ecosystem-Android

Ecosystem personal device link — Android app. Fork of [KDE Connect Android](https://invent.kde.org/network/kdeconnect-android/) (`namespace org.kde.kdeconnect_tp`), rebranded to `com.ecosystem.personal` (`com.ecosystem.personal.debug` for debug).

Upstream features (shared clipboard, notification sync, file/URL share, media remote, virtual touchpad/keyboard, SMS, Find My Phone, etc.) are retained. This fork adds multi-transport fallback, Shizuku-backed background work, per-device Ecosystem screens, and a USB Connection Lab harness.

## Current status

- Branch: `codex/connection-fallback` (HEAD `a6b75268`), ahead of `origin/master`.
- App: `versionName 1.36.0`, `versionCode 13600` in `build.gradle.kts:40-46`.
- SDKs: `minSdk 23`, `targetSdk 37`, `compileSdk 37`, Java 11 + core-library desugaring, Compose + ViewBinding + AIDL + KSP (`build.gradle.kts:37-101`).
- Toolchain: AGP `9.4.1`, Kotlin `2.4.21`, Shizuku `12.2.0` (pinned for API 23), coroutines `1.11.0`, SSHD `2.20.0` (`gradle/libs.versions.toml`).
- Last 10 committed commits cover: transport types/ranking, Tailscale detection, Bluetooth small-packet guard, Wi-Fi Direct fallback, Shizuku hotspot fallback, route prioritization + link-switch logging, deduplicated clipboard queue, Connection Lab + `scripts/connection-lab.sh`, Tailscale/flapping metric fixes.
- Working tree (uncommitted, `git status --short`): ~30 modified files under `src/`, `Graphics/Mockups/`, plus untracked Ecosystem work:
  - `plugins/ecosystem/` (`EcosystemPlugin.kt`, `MirrorService.kt`, `MirrorSession.kt`)
  - `ui/features/` (`FeatureFragment.kt`, `FeatureScreens.kt`, `SceneSupport.kt`)
  - `plugins/clipboard/ClipboardHistory.kt`, `plugins/receivenotifications/RemoteNotifications.kt`, `ui/EcosystemSettingsFragment.kt`
  - 4 drawables (`ic_appearance_24`, `ic_document_24`, `ic_notifications_24`, `ic_notifications_outline_24`), 17 new mockups (Clipboard history, Connection, Find and control, Notifications, Scenes, Touchpad, Desk mode, Mirror, App icon).

## Features

### Connection fallback (`backends/`, `BackgroundService.kt`, `Device.kt`, `KdeConnect.kt`)

Priority in `backends/ConnectionTransport.kt`:

| Transport | Priority | Notes |
|---|---|---|
| `WIFI` | 500 | LAN UDP broadcast 1716 + TCP 1716–1764 + mDNS `_kdeconnect._udp`, TLS. `LanLinkProvider.kt` |
| `WIFI_DIRECT` | 400 | P2P group, reuses LAN TLS. Needs `NEARBY_WIFI_DEVICES` + location once. `WifiDirectLinkProvider.kt` |
| `HOTSPOT` | 300 | Private Shizuku AP (`Ecosystem+12chars`, WPA2 from `SHA-256(ids)`), 5s grace / 8s confirm / 3s→60s backoff, deterministic target `min(deviceId)`. `ui/background/HotspotSupport.kt`, `HotspotUserService.kt`, `IHotspotControl.aidl` |
| `BLUETOOTH` | 200 | RFCOMM, small packets only: `supportsPayload=false`, `<=16KB` (`FallbackLinkManager.kt`, `BluetoothLinkProvider.kt`). Large/payload packets defer until a fast link returns (`Device.kt: shouldDeferForFasterLink`, 15s offline wait). |
| `TAILSCALE` | 100 | Same `LanLink` sockets, classified via `TailscaleRoute.kt` (`100.64.0.0/10`, `fd7a:115c:a1e0::/48`). Add peer Tailscale IP as custom device address; allow TCP/UDP 1716–1764 in tailnet. |
| `OTHER` | 0 | Fallback. |

`Device.sendPacketBlocking` iterates `FallbackLinkManager.preferredFirst()` and skips ineligible links. `KdeConnect.kt` + `DisconnectLog.kt` record connects/disconnects/link-switches (5000-entry `ecosystem_background` log with cause inference). `ConnectionRecovery.kt` uses a single `Handler` retry timer. `DeviceStats.kt` keeps 24h per-packet counters (UI debug, separate from Lab).

Background hardening already committed: foreground `BackgroundService` lifecycle, bounded reconnect delays for trusted peers, Bluetooth companion association + presence recovery (`CompanionSupport.kt`, `CompanionKeepAliveService.kt`, needs Android 13+ CDM), Shizuku power exemptions (`KeepAliveSupport.kt`, `KeepAliveUserService.kt`, `IKeepAliveService.aidl`).

### Clipboard (`plugins/clipboard/`)

- `ClipboardSyncManager.kt` + `EcosystemClipboardUserService.kt` + `IClipboardWatcher.aidl` / `IClipboardSyncCallback.aidl`: Shizuku user service (`uid 2000`, fakes `com.android.shell` for Android 16 validation) watches clipboard in background. `isAutomaticAvailable()` = enabled + watcher active + trusted-WiFi allowed. Pref `ecosystem_clipboard_sync` (`enabled=false` default, `trustedWifiOnly`, `plainTextOnly=true`, `showConfirmation`, `pausedUntil`, `last_local_hash`).
- Delivery: per-device queue with UUID + `ecosystem_clipboard_sent_at`, 15s retry throttle, ACK `ecosystem.clipboard.ack`, 500-ID incoming dedup, 5s remote-hash cooldown, sensitive/blank/remote-echo filtering, trusted-device + trusted-network gates. Feeds `ConnectionLab.recordClipboardDelay/Duplicate`, `pendingDeliveryCount()`.
- History (untracked `ClipboardHistory.kt`): pinned + recent (cap 100, `>100k` dropped), move-to-top dedup by text, `ClipboardPage` in `FeatureScreens.kt:158-222` with 1h pause, pin/delete/copy-to-peer.
- Manual fallback (`ClipboardPlugin.kt: canSyncAutomatically`, `userInitiatedSendClipboard`) when watcher unavailable.

### Ecosystem remote / mirror / scenes (untracked, not yet committed)

- `plugins/ecosystem/EcosystemPlugin.kt`: `ecosystem.control.request/response` + `ecosystem.mirror.frame`, 10s UUID timeout. Actions: `status/ping/volume/back/home/recent/lock/apps/launch/text/scene/mirror/stopMirror/touch/screenshot` (accessibility gestures, `takeScreenshot` API 30+).
- `MirrorService.kt` / `MirrorSession.kt`: foreground service `918` (`mediaProjection`), 720p `ImageReader`, 5fps/200ms throttle, JPEG 65/85 Base64, `>2MB` / `16MP` guards, consent via `MirrorConsentActivity` + `MirrorSession.offer()`.
- `ui/features/SceneSupport.kt`: scenes `work/movie/sleep/travel` (DND, volume, dark theme, brightness 20, `travel_mode`), `sleep@23:00` alarm + `desk` (near tablet) automations, `SceneAlarmReceiver`.
- `plugins/receivenotifications/RemoteNotifications.kt` (untracked) + `ReceiveNotificationsPlugin.kt`: dual-path — system notification + in-app `StateFlow` feed (cap 200) with quick replies (`On my way` / `Call later` + custom) and dismiss, used by `NotificationsPage` / `DeskPage`.

### Background UX (`ui/background/`)

`BackgroundFeaturesActivity.kt` hosts: `health`, `disconnect_log`, `clipboard_sync`, `companion`, `connection_lab`, `shizuku`.

- `HealthCheckScreen.kt`: Shizuku / battery-unrestricted / companion / connection / notification / clipboard / OEM (HyperOS/OxygenOS/generic) checklist.
- `DisconnectLogScreen.kt` + `DisconnectLog.kt`: plain-language connect/disconnect/switch/component rows.
- `ClipboardSyncScreen.kt`, `ShizukuSetupScreen.kt` (wireless-debugging pairing steps), `CompanionSetupScreen.kt`.
- `ConnectionLab.kt` + `ConnectionLabScreen.kt`: in-memory + `connection_lab` prefs store (max 300 rows), start/drop/note/finish/export, `files/connection-lab.tsv` + share intent, survives process restart.

### UI (`ui/`, `ui/compose/`)

- `MainActivity.kt`: drawer + `R.id.container` host, two-pane via `R.bool.is_two_pane`, menus (AddDevice/Settings/About/Share + devices), routes `EXTRA_DEVICE_ID` / `ACTION_SEND` → `ShareFragment`, `PhoneNavigationBar.kt` pill bar on narrow windows (`[Devices,Share,Settings]`).
- `EcosystemSettingsFragment.kt` (untracked, current default Settings): Compose groups Device (rename, clipboard toggle, per-device notifications), Connection (auto-connect, keep-running + Shizuku health jump), App (appearance, About `BuildConfig.VERSION_NAME`), Advanced → legacy `SettingsFragment.kt`.
- `DeviceFragment.kt` → Compose `PluginsScreen.kt` / `DevicePairingScreen.kt` / `DeviceErrorScreen.kt` + dashboard cards (Clipboard/Notifications/Share/Mpris, +MousePad/RunCommand on tablet).
- `PairingFragment.kt` → `pairing/PairingScreen.kt` + `ViewModel.kt`.
- Share: `plugins/share/ShareFragment.kt` + `compose/screen/share/ShareScreen.kt` (file picker, drag-drop, transfer history, offline URL queue).
- Untracked `ui/features/FeatureFragment.kt` (`CLIPBOARD,NOTIFICATIONS,CONNECTION,CONTROL,SCENES,MIRROR,DESK`) + `FeatureScreens.kt` (`ClipboardPage`, `NotificationsPage` All/Digest, `ConnectionPage` with ping latency, `ControlPage`, `ScenesPage`, `MirrorPage` tap/drag + keyboard, `DeskPage` clock/media/notifications/battery).
- Manifests: `src/main/AndroidManifest.xml` (~40 permissions, FGS `connectedDevice` + `mediaProjection`, Shizuku provider, `FileProvider`, tiles, MMS, RunCommand, mouse/digitizer/presenter/bigscreen). `src/debug/AndroidManifest.xml` makes `BackgroundFeaturesActivity` exported + `DUMP` (for `adb am start`) and adds `QuitActivity`.

## Connection Lab

Full guide: `docs/connection-lab.md`. Harness: `scripts/connection-lab.sh`.

```sh
./gradlew installDebug
adb devices -l  # USB serials only, network ADB refused
adb -s PHONE_USB_SERIAL shell am start -n com.ecosystem.personal.debug/org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity --es background_screen connection_lab
./scripts/connection-lab.sh PHONE_USB_SERIAL TABLET_USB_SERIAL SCENARIO
```

Scenarios: `normal`, `wifi-one`, `wifi-both`, `router`, `network`, `guest`, `airplane`, `no-wifi`, `flap` (60×5s Wi-Fi toggles), `screen-doze-reboot`, `distance`, `clipboard-100`, `overnight`, `manual`. Each run exports per-device TSV (`notice_ms`, `recovery_ms`, chosen link, pending/lost/duplicates, avg clipboard delay, Mbps, battery Δ, switch reason) + `ConnectionLab`-tagged logcats. Pass targets: notice ≤5s, recovery ≤15s, zero lost/duplicates, no retry storms or excess overnight drain. Tailscale needs same tailnet + custom IPs + open 1716–1764; hotspot needs one-time Shizuku auth; lab needs Nearby Wi-Fi grant opened once per device.

## Project layout

```
src/main/java/org/kde/kdeconnect/
  BackgroundService.kt  Device.kt  KdeConnect.kt  DeviceStats.kt
  backends/{ConnectionTransport,FallbackLinkManager,BaseLink,BaseLinkProvider,
    lan/{LanLink,LanLinkProvider,MdnsDiscovery,TailscaleRoute},
    bluetooth/, wifidirect/WifiDirectLinkProvider.kt, loopback/}
  plugins/{clipboard/, ecosystem/ (untracked), receivenotifications/, share/, ...}
  ui/{MainActivity,DeviceFragment,SettingsFragment,EcosystemSettingsFragment (untracked),
    features/ (untracked), background/{ConnectionLab,ConnectionLabScreen,HealthCheckScreen,
    DisconnectLog,ConnectionRecovery,HotspotSupport,HotspotUserService,ShizukuSupport,
    KeepAliveSupport,CompanionSupport,...}, compose/screen/...}
  *.aidl: background/IHotspotControl, background/IKeepAliveService,
    clipboard/IClipboardWatcher, clipboard/IClipboardSyncCallback
src/debug/AndroidManifest.xml  src/main/AndroidManifest.xml
src/test/java/...  (DeviceStats, PairingHandler, SslHelper, helpers, NetworkPacket,
  plugins/*, Device, bluetooth/BluetoothLinkTest + android/os stubs)
scripts/connection-lab.sh  docs/connection-lab.md
Graphics/Mockups/ (49 .png: phone 30, tablet 18, app icon)
fastlane/metadata/android/ (33 locales)  .gitlab-ci.yml
```

## Build / test

Requires Java 21 for CI image (project targets Java 11 bytecode); `JAVA_HOME` must be set locally — `./gradlew` fails without it.

```sh
./gradlew installDebug                 # debug build on USB device (Connection Lab needs this)
./gradlew assembleDebug lintDebug testDebugUnitTest   # same as CI (.gitlab-ci.yml: ubuntu:26.04, SDK tools 15859902)
./gradlew testDebugUnitTest --tests "org.kde.kdeconnect.*"
adb -s SERIAL shell dumpsys deviceidle force-idle  # only for screen-doze-reboot scenario
```

Debug signing uses `debug.keystore` (`android/androiddebugkey`); release uses minify + shrink. R8 `mapping.txt` + native symbols are copied next to Studio-signed outputs (`build.gradle.kts: copySigningExtraOutputs`).

## Design assets

`Graphics/Mockups/` (49 files): legacy `Phone – …` (6) + `Tablet – …` (2), new dark/light sets `Phone dark/light – Clipboard history/Connection/Discover/Find and control/Media/Notifications/Phone/Scenes/Settings/Share/Touchpad/Connecting` (24), `Tablet dark/light – Desk mode/Main/Mirror/TabletConnecting/TabletDiscover/TabletMedia/TabletSettings/TabletShare` (16), `App icon.png`. Launcher icon + adaptive icons already wired for debug installs.

## Known issues / TODO

- **Build break (uncommitted UI): `ui/features/FeatureScreens.kt` + `SceneSupport.kt` reference ~86 `R.string.feature_*` with zero definitions in `src/main/res/values/*.xml`.** Either add `ecosystem_strings.xml` or remove the screens before `./gradlew assembleDebug` will pass.
- Untracked Ecosystem code (`plugins/ecosystem/`, `ui/features/`, `ClipboardHistory.kt`, `RemoteNotifications.kt`, `EcosystemSettingsFragment.kt`, 4 drawables) is not in any commit — review, add missing resources, then commit.
- `fastlane/Appfile` still has `package_name("org.kde.kdeconnect_tp")`; runtime `applicationId` is `com.ecosystem.personal`. Update before any Play release; F-Droid metadata per tag is otherwise intact (33 locales).
- Many `Graphics/Mockups/` PNGs show as modified in working tree — confirm intentional re-export before committing.
- `docs/connection-lab.md` + `scripts/connection-lab.sh` are committed and current; no network-ADB, router power-cycle, or guest-isolation automation (manual steps documented).

## Contributing / license

Upstream contribution path (Community Wiki, Bugzilla, invent.kde.org GitLab, mailing list, l10n.kde.org) in the original README no longer matches this fork. File Ecosystem issues against this repo/branch for now.

License: GNU GPL v2 and GNU GPL v3 (`COPYING`, `LICENSES/`). Dependency licenses are merged into the app via `generateLicenseReport` (`build.gradle.kts:330-352`).
