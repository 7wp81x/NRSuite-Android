# Native phone hardware

Open **Phone** in the main toolbar. An ESP32 connection is optional for this screen. Existing ESP32 modules continue to use their USB backend.

| Implemented capability | Stock Android | Rooted Android | NetHunter Android |
| --- | --- | --- | --- |
| Wi-Fi access-point discovery | Android API | Android API | Android API |
| BLE advertisement discovery | Android API | Android API | Android API |
| NFC identifier and technology discovery | If NFC exists | If NFC exists | If NFC exists |
| Root radio on/off and driver reports | Requires root | Control panel | Control panel |
| Kali lifecycle, diagnostics and services | Unavailable | Requires installed Kali | Control panel |
| Monitor capture / injection | Not implemented | Not implemented | Not implemented |

Discovery uses the same Android APIs on every edition. The separate Root / NetHunter panel runs fixed local privileged actions after Check root is selected and superuser access is granted. No kernel modification or automatic root request on launch is included. Rootless NetHunter does not grant Android root privileges. Root alone does not prove chipset, firmware, driver or kernel support for advanced radio operations.

## Permissions and behavior

- Wi-Fi requires precise location permission, enabled Android Location services and enabled Wi-Fi. Android throttles requests. Rejections and timeouts are shown. Results older than the request are marked cached, even after a successful scan broadcast. Other system scans may satisfy the request.
- BLE requests Nearby devices permissions on Android 12 and later, plus precise location because radio discovery may reveal location. Android 11 and earlier require enabled Location services. Enable Bluetooth in Android settings. Advertisements are not a Bluetooth Classic inventory or proof of ownership; addresses can rotate.
- BLE scans stop after 15 seconds. Wi-Fi waits up to 20 seconds. Leaving the foreground stops scans and unregisters listeners. Results stay in the screen ViewModel only, without export or persistence.
- NFC reads identifiers and technology names. It does not clone tags, write data or extract protected contents. Identifiers may be randomized. Leaving the screen stops NFC; manually restart on return.
- Missing hardware, disabled radios and denied permissions produce messages without fallback to ESP32 commands.
- Existing phone IR support remains in the IR workflow.

## Physical acceptance

In progress on a non-rooted Samsung SM-S711U with Android 16. Rooted and NetHunter device acceptance remains pending. Test build is based on v1.0.0-beta.3.1.2 and installs separately as NRSuite Native Test.

1. On stock Android, launch without an ESP32, dismiss storage setup if prompted, open Phone, grant permissions and scan Wi-Fi/BLE.
2. Deny or revoke permissions; disable Location, Wi-Fi or Bluetooth and verify useful messages.
3. Repeat Wi-Fi requests rapidly and check throttle handling and cached labels.
4. Start BLE then background, rotate or leave the screen. Check that backgrounding stops scanning and listeners are cleaned up.
5. Identify an ordinary test NFC tag. Try a phone without NFC.
6. Repeat on rooted and NetHunter phones without granting root.
7. Reconnect the ESP32 and verify existing bridge modules independently.

## References

- [Android Wi-Fi scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Kali NetHunter editions](https://www.kali.org/docs/nethunter/)

## Non-rooted Samsung acceptance, 2026-11-02

Tested SM-S711U on Android 16/API 36 using the isolated 3.1.2 debug package. Launch without ESP32, denied-location recovery, granted Wi-Fi discovery and BLE advertisements passed. At least three Wi-Fi rows and twelve distinct BLE advertisement rows were observed; identifiers were not retained in project evidence. BLE stopped after its bounded scan. NFC reader mode started, but a physical tag read is untested. At that earlier snapshot, rooted/NetHunter acceptance was still pending; the later OnePlus pass is recorded below. The existing 126 unit tests passed with no failures or skips.

See [device acceptance evidence](../test-artifacts/native-phone-sm-s711u.json). Cached-result edge cases, disabled-radio paths and ESP32 regression acceptance still need device checks.

Final APK reinstalled and foreground cleanup verified. After Android settings fully enters the foreground, BLE stops before its timeout and NFC reports stopped with a restart button. Automated UI dumps during active BLE failed to reach idle; those attempts were excluded and checks repeated after a settled activity transition.

## Root and NetHunter control panel

Open Phone > Root / NetHunter controls, then Check root. Native radios provides Wi-Fi/Bluetooth/NFC on/off commands and Wi-Fi driver capabilities. It does not silently switch a connected interface into monitor mode. NetHunter provides:

- Kali health, interface listing, tool inventory, and nmap/tcpdump version execution.
- Start chroot using the installed NetHunter initializer if required; already mounted proc/dev skips initialization. Starting an unmounted environment can change mounts, DNS, forwarding and hostname through NetHunter's initializer.
- Stop idle chroot checks /proc process roots first and refuses while Kali processes are active. It uses normal unmounts only. No process kills, forced/lazy unmounts or file deletion. A failure can leave a partially unmounted environment; inspect status in NetHunter before retrying.
- SSH, Apache and PostgreSQL status/start/stop using their installed service configuration. Start/stop actions display their effects before execution. Network services may listen beyond localhost according to their existing configuration.
- Installed NetHunter app, NetHunter terminal and KeX client launchers. Interactive terminal and desktop session management remain in those apps.

The installed Kali root is resolved from /data/local/nhsystem/kalifs, with canonical kali-arm64 fallback, and must stay under /data/local/nhsystem/. Other installation layouts need a supported adapter. The control panel is a non-exported activity. It accepts fixed enum actions, with no free-form root command field or command intent extras. Output stays in memory, is bounded to 32,768 characters and includes exit status. Commands have a 30-second deadline; a timeout does not guarantee an operation had no effect.

Device acceptance for this panel completed for the bounded checks below on the rooted NetHunter OnePlus 6T, Android 15. Discovery results from the earlier Samsung pass remain separate. Root command approval from adb does not establish approval for the Android app UID.

### OnePlus 6T control acceptance, 2026-11-02

App-UID root approval and execution passed after enabling the test app's Magisk toggle. Native Wi-Fi/Bluetooth/NFC on commands returned success. Final APK NFC off/on transitions and state reports passed, restoring NFC on. Driver reports, Kali health/interfaces/tool inventory, nmap version execution, SSH status and already-running SSH start passed. Start chroot skipped the initializer for the mounted environment. Stop idle chroot refused with exit 23 before any unmount because Kali sessions were active. NetHunter app, terminal and KeX launcher handoffs reached their expected foreground packages.

The final build passed 130 tests, including four privileged action boundary checks. All 25 generated command scripts passed Android shell syntax validation. Existing Wi-Fi/Bluetooth enabled state, NFC on, SSH server and installed release/debug apps were preserved. See [rooted device evidence](../test-artifacts/native-nethunter-oneplus6t.json).

Not runtime-verified: fresh unmounted chroot initialization, successful idle unmount, SSH stop, Apache/PostgreSQL service cycles, Wi-Fi/Bluetooth off transitions, NFC physical tag reads, monitor capture or injection. The final APK rechecked root, NFC off/on and Kali health after the NFC status correction. The earlier Samsung discovery pass remains separate.

See the [combined coverage matrix and APK provenance](NATIVE-PHONE-VALIDATION.md) for the distinction between the earlier non-root Samsung build and final combined preview.
