# Connection Lab

The lab needs the debug build installed on both devices, USB debugging enabled, and both devices connected over USB. It refuses network ADB serials. Install with `./gradlew installDebug` and verify with `adb devices -l`.

Before measuring, open the hidden lab on both devices once so Android can grant Nearby Wi-Fi permission. Enable the app's automatic clipboard sync and give the app's Shizuku user service permission if hotspot automation is part of the run. Shizuku authorization is a one-time setup; without it, the lab records hotspot fallback as unavailable. Keep USB attached for the duration of every test.

For Tailscale, sign both devices into the same tailnet and add the other device's Tailscale IP as a custom device address in Ecosystem on each device. Tailscale must permit the app's KDE Connect TCP/UDP ports (1716 through 1764) between those devices. Configured CGNAT/ULA Tailscale endpoints are labeled separately from local Wi-Fi.

```sh
adb -s PHONE_USB_SERIAL shell am start -n com.ecosystem.personal.debug/org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity --es background_screen connection_lab
adb -s TABLET_USB_SERIAL shell am start -n com.ecosystem.personal.debug/org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity --es background_screen connection_lab
./scripts/connection-lab.sh PHONE_USB_SERIAL TABLET_USB_SERIAL SCENARIO
```

Replace the two serial placeholders with the USB serials shown by `adb devices -l`. Run one scenario at a time. The script starts the run on both devices, marks planned drops, automates Wi-Fi toggles where possible, finishes each run, and writes separate TSV tables for the phone and tablet in the current directory. Each table includes notice and recovery milliseconds, chosen link, outstanding clipboard deliveries at finish, repeated delivery IDs, average clipboard delay, measured transfer Mbps, battery percentage-point change, and the last switch reason. The app's `ConnectionLab` logcat tag contains the event trail.

| # | Scenario | How to run it |
|---|---|---|
| 1 | Normal Wi-Fi | `normal`; copy 50 distinct short strings, then send a 500 MB file in the app. Press Enter only after the file finishes. |
| 2 | Wi-Fi off | Run `wifi-one`, then `wifi-both`. The script marks the drop and toggles Wi-Fi over USB ADB; press Enter after fallback is stable to restore Wi-Fi. |
| 3 | Router off mid-transfer | Start a large file, run `router` in a second terminal, power off the router while the file is active, then press Enter after recovery or failure. |
| 4 | New and guest networks | Run `network`, switch both devices to another Wi-Fi, and finish. Run `guest`, join a guest network that blocks peer traffic, and finish after fallback is observed. |
| 5 | Airplane mode | Run `airplane`; enable then disable airplane mode on the phone, then repeat on the tablet. Wait for each recovery before finishing. |
| 6 | No Wi-Fi | Run `no-wifi`; disable Wi-Fi on both devices and observe the route order: Wi-Fi Direct, private hotspot, then Bluetooth. Confirm each active route in the connection graphic and switch log. Restore Wi-Fi before finishing. |
| 7 | Rapid flapping | Run `flap`. The script toggles Wi-Fi on both devices every 2.5 seconds for 60 cycles (five minutes), then exports. |
| 8 | Screen off, Doze, reboot | Run `screen-doze-reboot`; turn both screens off for 30 minutes, force Doze from USB with `adb -s SERIAL shell dumpsys deviceidle force-idle`, wake the devices, then reboot each with `adb -s SERIAL reboot`. Wait for both USB ADB states to return to `device`, then press Enter. The active lab run is persisted across process restart. |
| 9 | Distance | Run `distance`; walk away with both devices until the current link drops, then return and wait for recovery. The script asks for the farthest distance and which link broke at each point and writes that note into both TSV rows. |
| 10 | 100 clipboard copies during switch | Run `clipboard-100`; copy 100 distinct short strings while switching Wi-Fi off and on. Wait for the clipboard queue to drain before finishing. |
| 11 | Overnight | Run `overnight`, unplug USB after the script has started, and leave both devices in their normal screen-off state overnight (record elapsed hours separately). Reconnect USB in the morning and press Enter. Battery drain is the percentage-point change from run start to finish. |

The script cannot power-cycle a router, move devices, change a Wi-Fi network's guest isolation, or force the airplane-mode UI; those steps are manual. For `screen-doze-reboot`, the script remains waiting while you issue the ADB commands from another terminal and reconnect USB after both devices reboot. An empty `notice_ms` or `recovery_ms` is exported as `-1`, meaning that event did not occur or was not observed. Review `lost_items` alongside pending items on both devices; a pending delivery is counted as undelivered at test finish.

Pass targets are notice/start recovery within 5 seconds, route recovery within 15 seconds, zero lost or duplicate clipboard deliveries, and no repeated retry storms or excessive overnight battery drain. The table reports measurements; it does not label a run as passing unless the corresponding measurement is present.
