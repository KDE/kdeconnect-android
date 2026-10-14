# Docs

Index for `Ecosystem-Android` documentation. Start with the root [`README.md`](../README.md) for project status, then use this page to find details.

## Guides

- [`connection-lab.md`](connection-lab.md) — Connection Lab test guide (committed, current). Two-device USB harness for fallback, clipboard delivery, transfer speed, and battery drain across 11 scenarios (`normal` … `overnight`). Run via `scripts/connection-lab.sh PHONE_SERIAL TABLET_SERIAL SCENARIO` after `./gradlew installDebug`. Covers Tailscale, Shizuku hotspot, Nearby Wi-Fi, and Doze/reboot prerequisites plus pass targets (notice ≤5s, recovery ≤15s, zero lost/duplicates).

## Architecture pointers (code, no separate doc yet)

- Transports: `src/main/java/org/kde/kdeconnect/backends/ConnectionTransport.kt` (WIFI 500 > WIFI_DIRECT 400 > HOTSPOT 300 > BLUETOOTH 200 > TAILSCALE 100), `FallbackLinkManager.kt` (16KB Bluetooth guard), `lan/`, `wifidirect/WifiDirectLinkProvider.kt`, `bluetooth/`, `lan/TailscaleRoute.kt`.
- Orchestration: `src/main/java/org/kde/kdeconnect/BackgroundService.kt`, `Device.kt`, `KdeConnect.kt`, `ui/background/ConnectionRecovery.kt`, `DisconnectLog.kt`, `HotspotSupport.kt`, `ShizukuSupport.kt`, `KeepAliveSupport.kt`, `CompanionSupport.kt`.
- Clipboard: `src/main/java/org/kde/kdeconnect/plugins/clipboard/` (`ClipboardSyncManager.kt`, `EcosystemClipboardUserService.kt`, untracked `ClipboardHistory.kt`).
- Ecosystem remote/mirror/scenes (untracked): `src/main/java/org/kde/kdeconnect/plugins/ecosystem/` + `src/main/java/org/kde/kdeconnect/ui/features/`.
- UI entry: `src/main/java/org/kde/kdeconnect/ui/MainActivity.kt`, `EcosystemSettingsFragment.kt` (untracked default Settings), `DeviceFragment.kt`, `BackgroundFeaturesActivity.kt`.
- Tests: `src/test/java/org/kde/kdeconnect/` (`DeviceStatsTest`, `DeviceTest`, `NetworkPacketTest`, `backends/bluetooth/BluetoothLinkTest`, `plugins/*`).
- Build/CI: `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitlab-ci.yml`, `fastlane/` (note: `Appfile` package_name is stale, see root README).
- Design: `Graphics/Mockups/` (49 PNGs).

## Known doc gaps

- No dedicated doc for Ecosystem plugin protocol (`ecosystem.control.*`, `ecosystem.mirror.frame`, `ecosystem.clipboard.ack`) or `feature_*` screens — read `plugins/ecosystem/EcosystemPlugin.kt` and `ui/features/FeatureScreens.kt` directly. Those screens currently miss all `R.string.feature_*` definitions (see root README Known issues).
