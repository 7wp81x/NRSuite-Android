![NRSuite Banner](screenshots/banner.png)

# NRSuite

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
[![Platform: Android](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Firmware: ESP32](https://img.shields.io/badge/firmware-ESP32-E7352C?logo=espressif&logoColor=white)](https://github.com/7wp81x/nrsuite-firmware)
[![Status: Beta](https://img.shields.io/badge/status-beta-yellow.svg)](../../releases)
[![Discord](https://img.shields.io/badge/Discord-Join%20the%20server-5865F2?logo=discord&logoColor=white)](https://discord.gg/nnU9QSX5UZ)

**NRSuite turns a phone and a cheap ESP32 board into a portable Wi-Fi/BLE security research platform** - no laptop required.

Install the Android app, flash an ESP32 over USB-OTG with NRSuite firmware, and the app drives it as a combined offense/defense toolkit: Wi-Fi recon and attack modules, WPS Pixie Dust and lock/attempt detection, passive threat detection, BLE tooling, IR, BadUSB/HID, and more - all from a single pocketable setup.

> **Authorized use only.** NRSuite is for security research and testing on networks and devices you own or are explicitly authorized to test. See [LEGAL.md](./LEGAL.md).

---

## Quick Start

1. Grab the latest signed [prebuilt APK](../../releases) and install it - verify its SHA256 first, see [Installation](#installation).
2. Flash an ESP32 with NRSuite firmware using the in-app flasher (no PC needed).
3. Connect ESP32 ↔ phone via USB-OTG, open the app, grant USB permission.

Want to build from source instead? See [Building from Source](#building-from-source).

---

## Community

Join the NRSuite Discord server for setup help, build showcases, release news, and development discussion:

[![Discord](https://img.shields.io/badge/Discord-Join%20the%20server-5865F2?logo=discord&logoColor=white)](https://discord.gg/nnU9QSX5UZ)

Use `#support` for help, `#showcase` for builds, and `#github` for repo/code discussion.

---

## Table of Contents

- [Quick Start](#quick-start)
- [Community](#community)
- [Features](#features)
- [Usage Guide](#usage-guide)
- [Installation](#installation)
- [Updating](#updating)
- [Getting Started](#getting-started)
- [Requirements](#requirements)
- [Building from Source](#building-from-source)
- [Wire Protocol](#wire-protocol)
- [Screenshots](#screenshots)
- [Hardware Images](#hardware-images)
- [Contributing](#contributing)
- [Related Repos](#related-repos)
- [Versioning](#versioning--compatibility)
- [Security](#security)
- [License](#license)
- [Disclaimer](#disclaimer)

---

## Features

Categories below match the app's actual module grid and navigation.

### Interface and themes

NRSuite uses a refreshed, high-contrast field-instrument UI across Home, Devices,
Terminal, Logs, the module catalog, settings, dialogs, cards, filters, and
shared controls.

Three switchable themes are available in **Settings → Appearance**:

- **Obsidian** — near-black layered surfaces with a lime accent.
- **Black** — true-black AMOLED surfaces for darker environments.
- **Daylight** — high-contrast light surfaces for bright environments.

### Board Capability Matrix

Not every ESP32 variant supports every capability. Check your board before relying on a feature:

| Board | Wi-Fi scan/sniff/deauth/portal | BLE (scan/GATT/HID) | Mass Storage / BadUSB | C5 regulatory channel filtering |
|---|---|---|---|---|
| ESP32 (classic) | ✅ | ✅ | ❌ | ❌ |
| ESP32-C3 | ✅ | ✅ | ❌ | ❌ |
| ESP32-S2 | ✅ | ❌ | ✅ | ❌ |
| ESP32-S3 | ✅ | ✅ | ✅ | ❌ |
| ESP32-C5 | ✅ | ✅ | ❌ | ✅ |

See [nrsuite-firmware](https://github.com/7wp81x/nrsuite-firmware) for the authoritative per-board build matrix - this table covers core transport capabilities, not every individual module.

<details open>
<summary><strong>WiFi Recon</strong></summary>

- [x] **Wi-Fi Scan** - AP/channel discovery with SSID, BSSID, RSSI, security, and WPS flags; AUTO 2.4/5 GHz and regulatory-country channel filtering on C5-capable firmware; serialized/reliable scans with retry, previous-result preservation, and response-vs-event transport diagnostics
- [x] **Packet Sniffer** - monitor-mode capture with PCAP export; per-module Auto/2.4/5 GHz hopping, target-channel-aware fixed capture, and retrying deauth assist that starts after the sniffer
- [x] **Probe Request Monitor** - probe requests by MAC/SSID/channel/RSSI with vendor and randomized-MAC indication
- [x] **Channel Analyzer** - FAB-driven scan with loading state, compact 2.4/5 GHz occupancy bars, selected-channel detail, and a scrollable nearby-AP list sorted by RSSI with band filters and WPS/security metadata
- [x] **Hidden AP Revealer** - passive hidden AP detection, probe-request SSID candidates, and association-based SSID resolution

</details>

<details open>
<summary><strong>WiFi Attacks</strong></summary>

- [x] **WPS Pixie Dust** - live registrar flow with module-isolated target scans, AP-lock preflight, M3-only capture, on-phone Pixiewps recovery, same-session recovered-PIN continuation, and PIN/PSK persistence on all supported ESP32 targets
- [x] **Evil Twin** - rogue AP with captive-portal HTML, captured-password verification, persistent results after auto-stop, FAB start/stop progress, and dedicated visitor/metric logs
- [x] **Captive Portal** - file-backed custom HTML, GET and POST form capture, persistent HTTP/DNS listeners across restarts, hidden helper-AP restore, bounded visitor logging, and separate Portal/Evil Twin metrics
- [x] **NRuGochi** - Pwnagotchi-style channel-density RECON/INTERACT loop, multi-AP authorized deauth with frame telemetry, all-EAPOL/all-beacon capture, per-BSSID `HS_<BSSID>.pcap` output, mood UI, and today/total stats
- [x] **Deauth** - finite burst or unlimited loop until stopped, with elapsed time, frames/sec, sent/failed telemetry, multi-target selection, and app-side per-target channel hopping
- [x] **Beacon** - broadcast custom or hidden SSIDs; Overview / SSID Lists tabs, saved-list CRUD, and a built-in short-form Rickroll preset
- [x] **HTML Template Manager** - shared Portal/Evil Twin template tab with template dropdown, custom-file picker, import, rename, delete, SHA-256 duplicate rejection, 16 KB warning, 24 KB hard limit, and start-time firmware heap preflight
- [x] **Portal authoring guide** - [Captive Portal / Evil Twin HTML authoring](docs/CAPTIVE-PORTAL-EVIL-TWIN-AUTHORING.md)
- [ ] **PMKID Capture**

</details>

<details open>
<summary><strong>WiFi Defense</strong></summary>

- [x] **Deauth Detector** - passive deauth/disassoc monitoring with BSSID, client, RSSI, and reason filters; async alerts; live logs
- [x] **Rogue AP Detector** - compares nearby visible APs and OUI rules to flag duplicate SSIDs, security downgrades, and suspected impersonation; whitelist/blacklist OUI rules managed from WiFi Config
- [x] **Client Detector** - passive probe/assoc/reassoc/auth monitoring with optional active deauth-trigger mode
- [x] **WPS Lock Detector** - passive scan of nearby WPS APs with `Locked`, `Likely unlocked`, and `Unknown` lock-state reporting
- [x] **WPS Attempt Detector** - scan and multi-select nearby WPS APs, then passively monitor only their BSSIDs/channels for Identity/M1–M8 activity with source MAC/vendor, progression, and low/medium/high risk scoring; keeps a foreground-service notification while monitoring and shows active state in the module catalog
- [ ] **Karma/MANA Detector**
- [ ] **Jam Detector** (detection only - see [FEATURES.md](./FEATURES.md#explicitly-out-of-scope))

</details>

<details>
<summary><strong>Planned Wi-Fi automation</strong></summary>

See [`../WIFI-AUTOMATION-ROADMAP.md`](../WIFI-AUTOMATION-ROADMAP.md) for the full design and acceptance criteria.

- [x] Credential Manager QR sharing
- [x] resumable WPA cracker jobs, multiple/compressed wordlists, direct handshake linkage, and `.22000` export
- [ ] single-target Wifite-style Auto Capture + Crack; Evil Twin remains explicitly confirmed
- [ ] custom STA/AP MAC configuration and a separate experimental repeater prototype

</details>

<details>
<summary><strong>Credentials</strong></summary>

- [x] **Credential Manager** - local storage and management of captured credentials, with compact Evil Twin/WPA Cracker/WPS Pixie rows and a separate WPS PIN row; per-credential standard Wi-Fi QR generated locally with ZXing, QR image sharing/payload copy, and WPS PIN kept separate
- [x] **WPA/WPA2 Cracker** - offline dictionary cracking with WPA*02 `.22000` import, multi-target/select-all and manual single-target modes, best M1/M2 selection, pause/resume, foreground progress, and checkpointed sessions
- [x] **PCAP to .hs22000** - multi-select classic PCAP merge into a Hashcat `.22000` file; automatic best-handshake-per-BSSID+station dedupe; automatic SSID extraction; dropdown-based BSSID→ESSID overrides; preview, successful-export display, and malformed/missing-SSID reporting

</details>

<details>
<summary><strong>BLE</strong></summary>

- [x] **BLE Scanner** - device discovery with names, RSSI, manufacturer data, service UUIDs, service badges, and module-owned scan lifecycle
- [x] **BLE GATT Profile** - read-only service/characteristic enumeration with integrated BLE target scanner
- [x] **Tracker Detector** - Find My / AirTag-style advertisement detection with repeated sightings
- [ ] **FastPair Model Identification** - map FastPair model IDs to known device names/types

</details>

<details>
<summary><strong>Sensors</strong></summary>

- [x] **mmWave Radar (LD2450)** - 120° fan with green blurred dots, heartbeat scan/sleep mode, position-based dot persistence, configurable dot cap, scan wave animation, max-range display control, and no-data/parse-error/zero-target states

</details>

<details>
<summary><strong>Mesh</strong></summary>

Provision, monitor, and control the ESP-NOW mesh from its own module. The underlying mesh capabilities are still experimental - see [Mesh (ESP-NOW) - Experimental](#mesh-esp-now---experimental) below for status.

</details>

<details>
<summary><strong>Tools</strong></summary>

- [x] **MAC Lookup** - offline OUI/vendor lookup using the bundled local OUI snapshot, with locally-administered/multicast indicators and copy/share actions; no map or network request
- [x] **BSSID Geolocation** - offline OUI/vendor lookup plus approximate Wi-Fi access-point location, persistent lookup history, and an embedded Leaflet map with external Google Maps fallback

</details>

<details>
<summary><strong>HID / BadUSB</strong></summary>

- [x] **Ducky Editor** - create, import, and export DuckyScript payloads
- [x] **BLE HID** - BadBLE payloads and realtime keyboard input, Bluetooth LE keyboard/mouse HID and DuckyScript payloads
- [x] **BadUSB** - native USB HID payload injection
- [x] **PIN Bruteforce** - supervised native-HID range/wordlist tester; one-shot boot-arm like BadUSB; max-attempt cap, progress/ETA, configurable typing/start delays, saved sessions, compact attempt feed
- [x] **BLE HID PIN Bruteforce** - hidden/experimental BLE-HID tester retained for future unlocked-host and mesh HID work; not exposed in the normal module grid, lock-screen use is unsupported
- [x] **Multi-device USB sessions** - connect and use multiple USB serial devices independently, with per-device permissions and connection state; idle-session reuse keeps S2 CDC stable on user-initiated disconnect
- [x] **BadUSB device picker** - choose the target USB device without interrupting other connections

</details>

<details open>
<summary><strong>Terminal / Termux SSH</strong></summary>

- [x] **Native SSH terminal** - local Termux OpenSSH connection at `127.0.0.1:8022`, forced `nrsuite` username for local connections, native terminal emulator, multiple shell tabs, PTY resize, scrollback, selection, and clipboard support
- [x] **Ed25519 / RSA public-key authentication** - encrypted key import, JSch BouncyCastle Ed25519 support, key fingerprint display, public-key-only auth, and pinned host-key validation
- [x] **Autoconnect and recovery** - one connection attempt per Terminal visit, setup/connecting status, manual disconnect behavior, and explicit force reconnect
- [x] **Terminal settings** - 34 dp default font size, block cursor, 500 ms blink, pinch-to-zoom, keyboard toggle, and IME-aware extra-key rows
- [x] **Terminal Shortcuts** - quick bottom sheet, search, category filters, run modes, Home pinning, confirmation, `{{parameter}}` forms with command previews, and built-in editable examples
- [x] **Tab/session safety** - closing the last tab is disabled; Disconnect SSH is the explicit transport-close action
- [x] **Terminal guide** - [Termux SSH setup, auth, shortcuts, and troubleshooting](docs/TERMINAL-GUIDE.md)
- [ ] **Termux tools** - planned Nmap topology visualization, PCAP workflows, and optional Wireshark through Termux:X11

</details>

<details>
<summary><strong>Storage</strong></summary>

- [x] **Mass Storage** - list/delete files on ESP32 flash and enter USB MSC mode

</details>

<details>
<summary><strong>Firmware</strong></summary>

- [x] **Firmware Flasher** - flash merged NRSuite firmware directly from the app over USB; supports ESP32, S2, S3, C3, and C5 images with target-chip validation, esptool-aligned reset timing, C5 SYNC/SPI-attach handling, TinyUSB-to-ROM bootloader restart, automatic manual-recovery fallback, and background foreground-service progress notification
- [x] **Serial Monitor** - raw USB serial monitor for any ESP32 or USB-UART firmware, with reliable clipboard copy, bounded log retention, and visible save/export feedback
- [x] **Persistent firmware device ID** - `NRxxxxxxx` NVS ID shown in the app so devices remain identifiable across reconnects
- [x] **Dual USB/UART transport reporting** - Native-USB-capable firmware reports `usb_cdc` or `uart0`; device specs and logs show which path is active

</details>

<details>
<summary><strong>IR</strong></summary>

- [x] **IR Remotes** - Flipper `.ir` import, Flipper-IRDB download/update, local library, favorites, search, categories, brands, and loop mode
- [x] **IR Receiver** - ESP32 RMT raw capture, pending capture naming, replay, learned-button storage, and `.ir` export
- [x] **IR Finder** - category/brand targeting, common-button selection, auto-step, loop, pause/resume, previous/next, manual send, and saved results
- [x] **IR Jammer** - supervised timed/unlimited modes, authorization confirmation, wake-lock, and automatic stop
- [x] **IR Configuration** - category-level Phone/ESP32/Auto backend, ESP32 TX/RX GPIO selection, loop interval, and IRDB management
- [x] **Phone and ESP32 IR TX** - shared encoded signal path with dynamic backend selection

</details>

### Visible but not yet functional

The following modules appear in the app as disabled ("Unavailable") placeholder cards, reserving their place in the catalog ahead of hardware support:

- [ ] **RF** (Sub-GHz) - radio frequency module
- [ ] **RFID** - PN532 code is retained but hidden/disabled pending reliable hardware validation; read (125kHz + 13.56MHz), access-control analyzer, write/clone, and an unauthorized-reader detector are all planned here once hardware support lands

### Planned

For a full status and roadmap breakdown across every phase, see [FEATURES.md](./FEATURES.md).

<details>
<summary><strong>Mesh (ESP-NOW) - Experimental</strong></summary>

> Mesh code from the merged mesh branches is present but remains experimental and is not part of the non-mesh release acceptance matrix.

- [x] **Shared Group Key Provisioning**
- [x] **Dynamic Master Election**
- [x] **Client Idle/Standby Mode**
- [x] **Encrypted ESP-NOW Transport**
- [x] **Activation Handshake**
- [x] **Session Locking**
- [x] **Heartbeat/Auto-Timeout**
- [x] **Peer snapshots, chip metadata, RSSI, and online state**
- [x] **Configurable mesh channel `1..13` and recovery hopping**
- [x] **Master-requested channel switching with ACK/commit flow**
- [x] **Distributed node-health sensor reporting** - hardware-validated on S3 + S2
- [x] **Distributed same-channel Deauth Detector reporting** - hardware-validated on S3 + S2
- [ ] **Fixed/hop detector windows final hardware validation**
- [ ] **Same-frame deauth correlation**
- [ ] **Distributed Rogue AP evidence**
- [ ] **Triangulation / RSSI locator**

</details>

<details>
<summary><strong>Mesh Chat - Planned</strong></summary>

- [ ] **ESP-NOW Chat Transport**
- [ ] **Multi-hop Relay**
- [ ] **Store-and-Forward**
- [ ] **Message Fragmentation**
- [ ] **Sender Aliases**
- [ ] **Quick/Canned Messages**
- [ ] **Chat/Sensor Traffic Prioritization**

</details>

<details>
<summary><strong>Remote Camera Node - Planned</strong></summary>

- [ ] **Live Feed Streaming**
- [ ] **Snapshot-on-Trigger**
- [ ] **Motion Detection**
- [ ] **Deep-sleep/Low-power Mode**
- [ ] **SD Card Buffering**
- [ ] **Mesh Manager Integration**

</details>

<details>
<summary><strong>Peripheral Radios - Planned</strong></summary>

- [ ] **Sub-GHz (CC1101)** - Scanner, Capture/Replay, Protocol Decoder
- [ ] **nRF24** - RC/Device Scanner, MouseJack-style analysis, Packet Sniffer
- [ ] **RFID/NFC** - Read, Emulate, Write/Clone, Access Control Analyzer
- [ ] **LoRa** - long-range relay node support

</details>

---


## Usage Guide

See [docs/USAGE-GUIDE.md](docs/USAGE-GUIDE.md) for:

- First connection and root-directory setup
- Navigation and category-level configuration
- WiFi Config, shared channel controls, and Packet Sniffer workflows
- IR Config, IR Remotes, Receiver, Finder, and Jammer usage
- Sensor Config and HLK-LD2450 UART wiring
- BLE/USB essentials, troubleshooting, and safety notes
- Termux SSH setup, key authentication, terminal defaults, shortcuts, and reconnect behavior

The guide is intentionally general and is not tied to a single release version.
See [docs/TERMINAL-GUIDE.md](docs/TERMINAL-GUIDE.md) for the dedicated terminal guide.

---

## Installation

### Option A - Prebuilt APK (recommended for beta)

1. Download the latest `nrsuite-vX.X.X-beta.apk` from [Releases](../../releases).
2. Enable **Install from unknown sources** on your device if needed - Settings → Security.
3. Install the APK. Android's package installer checks the APK signature against the previous install on update, so a tampered or mismatched build will simply fail to install.

<details>
<summary>Optional: verify the SHA256 checksum yourself</summary>

Each release lists a SHA256 checksum in its notes. To confirm the file you downloaded matches it:

```sh
# Linux / macOS
sha256sum nrsuite-vX.X.X-beta.apk

# Windows (PowerShell)
certutil -hashfile nrsuite-vX.X.X-beta.apk SHA256
```

</details>

### Option B - Build from source

```sh
git clone https://github.com/7wp81x/nrsuite-android.git
cd nrsuite-android
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

You can also open the project in Android Studio and sync Gradle - see [Building from Source](#building-from-source) for prerequisites.

### Flashing the ESP32

You also need an ESP32 dev board flashed with NRSuite firmware. Flash it directly from inside the app using the built-in flasher, or manually via PlatformIO - see [nrsuite-firmware](https://github.com/7wp81x/nrsuite-firmware) for supported boards and instructions.

---

## Updating

NRSuite checks the `nrsuite-android` and `nrsuite-firmware` GitHub repos for new releases automatically - no manual "check for updates" step needed day-to-day.

- **Check frequency** - an update check runs on app start and roughly once per hour while in use (sooner if you force a check from Settings). Toggle automatic checks off in **Settings** if you'd rather check manually.
- **App updates** - when a newer app release is found, NRSuite lets you download the APK in-app; once it's downloaded, you confirm the install through Android's standard package installer, same as sideloading. Nothing installs itself without that confirmation.
- **Firmware updates** - the latest firmware release is surfaced on the Firmware Flasher screen (even without a device connected), so you can see what's available and flash it over USB when ready. Flashing itself is always a manual, in-app action.

This does **not** reach out anywhere except GitHub's API to check release metadata - no telemetry, no third-party update server.

---

## Getting Started

1. Flash NRSuite firmware to your ESP32 - use the in-app flasher, or PlatformIO from [nrsuite-firmware](https://github.com/7wp81x/nrsuite-firmware).
2. Connect your ESP32 to your phone via a USB-OTG cable.
3. Open NRSuite - the app auto-detects the connected device.
4. Grant USB host permission when prompted (one-time per device).
5. Select a module from the home screen and start testing.

---

## Requirements

### Android device

- Android 8.0 (API 26) or higher
- USB-OTG support (required - the app uses USB host mode to communicate with the ESP32)
- USB-OTG cable or adapter

### Hardware

- Any ESP32 development board flashed with NRSuite firmware
- See [nrsuite-firmware](https://github.com/7wp81x/nrsuite-firmware) for supported boards and flashing instructions

---

## Building from Source

### Prerequisites

- Android Studio Ladybug or newer (latest stable recommended)
- JDK 11 (bundled with Android Studio)
- Android SDK with API 36 (`compileSdk`) and API 26 (`minSdk`) installed

### Steps

```sh
git clone https://github.com/7wp81x/nrsuite-android.git
cd nrsuite-android
./gradlew assembleDebug
```

Signed release APKs are published by the maintainer. For testing your own changes, the debug build is sufficient.

### Project Structure

<details>
<summary>Expand full directory layout</summary>

```
app/src/main/java/com/swp81x/nrsuite/
  core/
    ble/               # BLE scan/profile/tracker models and scan limits
    credentials/       # Credential storage models and store
    defense/           # Rogue AP / client-presence / hidden-AP / deauth-alert models, OUI heuristics
    eapol/             # EAPOL frame parser (for WPA handshake capture)
    flasher/           # ESP32 in-app flasher (SLIP codec, USB serial transport)
    history/           # Session history entries
    log/               # Log entry model (Logs screen, live module logs)
    oui/               # Offline OUI/vendor database (BSSID Geolocation, defense modules)
    portal/            # Local Captive Portal / Evil Twin HTML template store
    wps/               # WPS PIN generation, Pixie Dust material, parsers, and offline cracker
    geo/               # Independent BSSID/WLOC client and persistent lookup history
    pcap/              # Pcap file reader and writer
    protocol/          # Frame codec and NrJson wire protocol (shared with firmware)
    session/           # Connection state and NrSession manager
    sniff/             # Sniff request model
    hid/               # PIN Bruteforce models/state
    storage/           # Mass storage models (ESP32 flash file browser)
    usb/               # USB serial transport, device catalog, NrTransport
    wifi/              # Pcap SSID parser
    wpa/               # WPA handshake parser, verifier, and cracker
  service/
    NrSuiteForegroundService.kt   # Foreground service keeping USB session alive
  ui/
    components/       # Reusable Compose components (including bounded scan-target lists and HTML template controls)
    theme/            # Color, typography, theme
    NRSuiteApp.kt     # App entry point and module catalog
    NRSuiteContent.kt # Shell navigation, permission flow, screen dispatch
    HomeScreen.kt     # Dashboard, categories, module list
    LogsScreen.kt     # Logs and session history
    DeviceScreen.kt   # USB device selection and connection state
    SettingsScreen.kt # Settings and firmware flasher
    ModuleAvailability.kt # Shared "module unavailable on this board" banner/FAB gating
    RogueApScreen.kt / DeauthDetectorScreen.kt / ClientPresenceScreen.kt /
    HiddenApScreen.kt / TrackerDetectorScreen.kt   # Defense modules
    BleScannerScreen.kt / BleProfileScreen.kt / BleScreen.kt   # BLE recon + BLE HID
    BlePinBruteforceScreen.kt                                  # BLE HID PIN live controls
    MacLookupScreen.kt / SerialMonitorScreen.kt    # Tools / firmware utilities
    ProbeMonitorScreen.kt / ChannelAnalyzerScreen.kt # Wi-Fi recon additions
    WpsPixieLiveScreen.kt / WpsPixieScreen.kt      # WPS Pixie Dust live and offline workflows
    WpsLockDetectorScreen.kt                     # Passive WPS lock-state scan and AP details
    WpsAttemptDetectorScreen.kt                  # Passive WPS Identity/M1–M8 attempt monitor
    *Screen.kt        # Remaining feature module screens
  MainActivity.kt
  MainViewModel.kt               # Application-scoped controller
  MainViewModelUsb.kt            # USB discovery, permission, connection lifecycle
  MainViewModelFlasher.kt        # Firmware image selection and flashing
  MainViewModelWifi.kt           # Scan, multi-target deauth, beacon, sniff
  MainViewModelPortal.kt         # Captive Portal and Evil Twin
  MainViewModelHtmlTemplates.kt  # Shared HTML template import/rename/delete/use state
  MainViewModelProbeMonitor.kt   # Probe request aggregation
  MainViewModelWpsPixie.kt       # WPS PIN storage, material import, and offline crack state
  MainViewModelWpsPixieLive.kt   # Live WPS Pixie Dust registrar workflow
  MainViewModelWpsLockDetector.kt # Passive WPS access-point lock-state scan
  MainViewModelWpsAttemptDetector.kt # Passive WPS attempt/session monitor and risk scoring
  MainViewModelBle.kt            # BLE HID
  MainViewModelBleScanner.kt     # BLE device discovery
  MainViewModelBleProfile.kt     # BLE GATT enumeration
  MainViewModelStorage.kt        # Mass storage, BadUSB, DuckyScript
  MainViewModelPinBruteforce.kt  # PIN Bruteforce HID session state
  MainViewModelBlePinBruteforce.kt # BLE HID PIN live session state
  MainViewModelCredentials.kt    # Credential sessions and WPA cracking
  MainViewModelDefense.kt        # Shared defense-module plumbing
  MainViewModelRogueAp.kt        # Rogue AP Detector
  MainViewModelHiddenAp.kt       # Hidden AP Revealer
  MainViewModelClientPresence.kt # Client Detector
  MainViewModelTracker.kt        # Tracker Detector
  MainViewModelOui.kt            # Vendor/OUI lookup
  MainViewModelBssidGeo.kt       # BSSID geolocation lookup
  MainViewModelSerialMonitor.kt  # Serial Monitor
  NrSuiteApplication.kt
```

</details>

---

## Wire Protocol

NRSuite uses a custom framing protocol (`FrameCodec` / `NrJson`) over USB serial between the app and the ESP32 firmware. The protocol spec is versioned separately in [nrsuite-protocol](https://github.com/7wp81x/nrsuite-protocol) so the app and firmware can evolve independently without silent breaking changes.

> If your contribution touches any app ↔ firmware communication, **update the protocol spec first** and reference the spec version in your PR.

---

## Screenshots

| Home | Devices | Logs |
|---|---|---|
| ![Home](screenshots/home.jpg) | ![Devices](screenshots/device-manager.jpg) | ![Logs](screenshots/logs-screen.jpg) |

| Terminal | Terminal Shortcuts | Terminal Settings |
|---|---|---|
| ![Terminal](screenshots/terminal.jpg) | ![Terminal shortcuts](screenshots/terminal-shortcuts.jpg) | ![Terminal settings](screenshots/terminal-settings.jpg) |

| WiFi Recon | WiFi Attack | WiFi Defense |
|---|---|---|
| ![WiFi recon](screenshots/wifi-recon.jpg) | ![WiFi attack](screenshots/wifi-attack.jpg) | ![WiFi defense](screenshots/wifi-defense.jpg) |

| BLE | Sensors | Tools |
|---|---|---|
| ![BLE modules](screenshots/ble-category.jpg) | ![Sensors](screenshots/sensors-category.jpg) | ![Tools](screenshots/tools-category.jpg) |

| Credentials | HID | IR |
|---|---|---|
| ![Credentials](screenshots/credentials-category.jpg) | ![HID](screenshots/hid-category.jpg) | ![IR](screenshots/ir-category.jpg) |

| Firmware | Storage | Settings |
|---|---|---|
| ![Firmware modules](screenshots/firmware-category.jpg) | ![Storage](screenshots/storage.jpg) | ![Settings](screenshots/settings.jpg) |

| Themes | Firmware Flasher | Multi-device |
|---|---|---|
| ![Themes](screenshots/settings-appearance.jpg) | ![Firmware flasher](screenshots/firmware-flasher.jpg) | ![Multi-device](screenshots/home-multi-device.jpg) |

More screenshots in [./screenshots/](./screenshots/).

---

## Hardware Images

| USB hub + ESP32-S2 | ESP32-S3 SuperMini |
|---|---|
| ![USB hub and ESP32-S2 hardware setup](screenshots/hardware-setup-usb-hub-esp32-s2.jpg) | ![ESP32-S3 SuperMini hardware setup](screenshots/hardware-setup-esp32-s3-supermini.jpg) |

---

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full guide: branching model, PR process, testing expectations, and legal/ethical scope boundaries.

**Short version:**

- Open an issue before starting any non-trivial feature.
- Branch from `develop`, not `main`.
- One feature or fix per PR.
- Test on real hardware for anything touching USB serial, the flasher, or module screens.
- Jamming features (RF/Wi-Fi/BLE denial-of-service transmission) will not be merged - see [CONTRIBUTING.md](./CONTRIBUTING.md).

---

## Related Repos

| Repo | Contents |
|---|---|
| [nrsuite-firmware](https://github.com/7wp81x/nrsuite-firmware) | ESP32 firmware (PlatformIO / ESP-IDF) |
| [nrsuite-protocol](https://github.com/7wp81x/nrsuite-protocol) | Wire protocol specification |

---

## Versioning & Compatibility

| App version | Firmware version | Protocol spec |
|---|---|---|
| v1.0.0-beta.3.2.0 | v1.0.0-beta.3.2.0 | v1.0 / proto_minor 2 (non-mesh); mesh protocol 1.1 is experimental |

This table is updated with each release. Always check compatibility before mixing app and firmware versions.

---

## Security

If you find a vulnerability in NRSuite itself (for example, a flaw in the mesh encryption scheme, credential storage, or the USB flasher), please report it privately rather than opening a public issue. See [SECURITY.md](./SECURITY.md).

---

## License

[GNU General Public License v3.0 or later](./LICENSE)

NRSuite is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This license covers redistribution of the code. It does not authorize use of
the tool against systems you do not own or lack explicit permission to test.
See [LEGAL.md](./LEGAL.md) and [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).

---

## Disclaimer

NRSuite is a tool for security research and authorized red-team operations, intended for legal and authorized security testing purposes only. Use of this software for any malicious or unauthorized activity is strictly prohibited. By downloading, installing, or using NRSuite, you agree to comply with all applicable laws and regulations. This software is provided free of charge; the developers assume no liability for any misuse. Use at your own risk. See [LEGAL.md](./LEGAL.md) for full terms.

## Native phone hardware

Open **Phone** in the toolbar for built-in Wi-Fi discovery, BLE advertisements and NFC identification without an ESP32 or root. This Android API implementation also runs on rooted and NetHunter phones. See [capabilities and acceptance](docs/NATIVE-PHONE-HARDWARE.md). Open **Phone > Root / NetHunter controls** for native radio toggles, driver reports, Kali health and tools, chroot lifecycle, services, and installed app/terminal/KeX launchers. Root access must be granted explicitly.

## Maintaining the custom native build

Use [the native release updater](docs/NATIVE-UPDATER.md) to fetch a release, reapply the saved non-root/NetHunter changes and build a verified APK with the existing preview signing key. Run `scripts/Update-NativeNRSuite.ps1` from PowerShell.
