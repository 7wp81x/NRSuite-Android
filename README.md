# NRSuite Android App

Android companion app for the **NRSuite** ESP32 wireless research toolkit.

> **Authorized testing and educational use only.**
>
> This application is intended only for security research, education, and
> authorized penetration testing on networks and devices that you own or have
> explicit written permission to test.
>
> Do not use this app on public networks, other people's networks, or any
> device/network without proper authorization. Unauthorized interception,
> disruption, or credential capture is illegal in many jurisdictions.
>
> The authors and contributors are not responsible for misuse, damage, or
> legal consequences caused by this software.

---

## Overview

NRSuite moves low-level radio work to an ESP32 companion board and drives it
from an Android phone over USB OTG.

The Android app is responsible for:

- USB device discovery and permission handling
- the NRSuite framed binary bridge protocol
- module UI and session state
- PCAP capture and export
- portal HTML upload
- DuckyScript management
- EAPOL handshake parsing and WPA2 verification
- history/logs/export

The ESP32 firmware is responsible for:

- WiFi scanning
- promiscuous packet capture
- raw 802.11 frame injection
- deauthentication frames
- beacon broadcast
- captive portal AP + DNS + HTTP server
- EAPOL capture
- USB mass storage
- native USB HID / BadUSB
- BLE HID (on supported chips)

---

## Supported boards

| Board | WiFi scan/sniff/deauth/beacon/portal | BLE HID | Mass storage / BadUSB |
|---|---:|---:|---:|
| ESP32-C3 | ✅ | ✅ | ❌ |
| ESP32-S3 | ✅ | ✅ | ✅ |
| ESP32-S2 | ✅ | ❌ (no BLE radio) | ✅ |
| Classic ESP32 devkit | ✅ | ✅ | ❌ |

The app uses firmware feature negotiation when available and falls back to
chip-name detection for older firmware.

---

## Features

- **WiFi Scan**
  - SSID, BSSID, channel, RSSI, security
  - sorted signal-strength list

- **Packet Sniffer**
  - fixed channel or channel hopping
  - all-packet mode
  - target-network mode
  - EAPOL-only mode
  - optional deauth-before-capture
  - EAPOL M1–M4 handshake status
  - PCAP output to `NRSuite_root/Pcap/`

- **Beacon Broadcast**
  - custom SSIDs
  - saved SSID lists
  - import from `.txt`
  - channel, interval, hidden SSID, stable BSSID options

- **Deauthentication**
  - target network selection via WiFi scan
  - client MAC, channel, count, duration, interval
  - confirmation before sending

- **Captive Portal**
  - custom SSID/channel
  - optional target BSSID
  - custom HTML upload
  - portal logs with page views, clients, form posts
  - copy/clear logs

- **Evil Twin**
  - scan and select target network
  - custom HTML
  - portal + deauth + EAPOL capture workflow
  - captured `password` / `pass` fields
  - WPA2 EAPOL verification:
    - `correct`
    - `incorrect`
    - `pending`

- **Mass Storage**
  - list files
  - delete files
  - free/used/total space
  - start USB mass storage mode on supported boards

- **BadUSB**
  - upload DuckyScript payload
  - optional mass storage companion mode
  - arm for execution on next boot/re-plug

- **BLE HID**
  - BadBLE DuckyScript payloads
  - realtime keyboard input
  - saved DuckyScript selection
  - only on BLE-capable boards

- **DuckyScript Editor**
  - create/edit scripts
  - save to internal library
  - load/delete from library
  - import/export `.txt`

- **Logs and History**
  - runtime session logs
  - persistent session history
  - filter/copy/export/clear

---

## Requirements

### Android

- Android 8.0+ (`minSdk 26`)
- USB OTG support
- USB debugging only for development/install; not required for normal app use

### ESP32

- Stock NRSuite firmware, or compatible firmware exposing the NRSuite bridge protocol
- One of the supported ESP32 boards
- USB OTG cable/adapter

---

## Build

The project is a standard Android Studio / Gradle project.

```bash
./gradlew :app:assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Firmware compatibility

The app expects the NRSuite bridge protocol:

```text
[0xAD 0xDE][TYPE 1B][ID 1B][LENGTH 4B LE][PAYLOAD NB]
```

Firmware `STATUS` may report:

```json
{
  "proto": 1,
  "fw": "1.3.0-dev",
  "features": [
    "wifi",
    "sniff",
    "deauth",
    "beacon",
    "portal",
    "storage",
    "ble_hid",
    "msc",
    "badusb"
  ]
}
```

The app uses these feature flags to enable or disable modules.

---

## Data and export layout

When the user selects an NRSuite root directory, the app creates the standard
folder layout and writes captures under `Pcap/`:

```text
NRSuite_root/
  Pcap/
  DuckyEditor/
  Logs/
  Portals/
```

Export wiring for logs/history, DuckyScripts, and portal event logs is the next
storage milestone. If no root directory is configured, capture start prompts the
user to choose one.

---

## Evil Twin testing

A simple test HTML fixture is included at:

```text
test-artifacts/evil_twin_test.html
```

It posts a `password` field to `/login`, matching the firmware portal handler.

Use only on an isolated test network and a device you own or are authorized to
test.

---

## Security and privacy notes

- The app does not root the phone.
- The app does not require Android Bluetooth permissions; BLE is handled by the ESP32.
- Captive Portal and Evil Twin can capture data submitted by a client.
- Only collect data with explicit authorization.
- The WPA2 verification engine processes captured handshakes locally on the phone.
- PCAP files may contain sensitive network data. Store and share them responsibly.

---

## Project structure

```text
app/src/main/java/com/swp81x/nrsuite/
  core/
    eapol/       EAPOL handshake parsing
    history/     session history model
    log/         typed log model
    pcap/        PCAP writer
    protocol/    NRSuite bridge frame codec
    session/     command/response/event session layer
    sniff/       sniff request model
    storage/     storage models
    usb/         USB serial transport
    wpa/         WPA2 handshake parsing + verification
  service/       foreground service
  ui/            Compose screens and components
```

---

## License

This Android app is part of the NRSuite project. See the parent project
`LICENSE` file for the project license.

---

## Disclaimer

**This software is provided for authorized testing and educational purposes only.**

You are solely responsible for how you use it. The authors and contributors
assume no liability and are not responsible for any misuse, damage, legal
action, or other consequences resulting from the use of this app or its
firmware.
