# NRSuite - Features & Roadmap

This document lists all current and planned features across the NRSuite ecosystem (Android app + ESP32 firmware + mesh network). Status legend:

- [x] Implemented
- [ ] Planned / in progress
- [~] Experimental concept; not release-ready

---

## 1. Offense Modules (Wi-Fi / BLE)

- [x] **Wi-Fi Scan** - AP/channel discovery with SSID, BSSID, RSSI, security, and WPS flags; screen-local Auto/2.4/5 GHz filter; regulatory settings display allowed 2.4 and 5 GHz channel lists; pull-based `SCAN_RESULTS` retrieval with per-chunk CRC/retry and legacy event fallback; retry/result-preservation transport diagnostics
- [x] **Wi-Fi regulatory country** - C5-only Settings control for the persisted country/channel mask
- [x] **Beacon Spam** - Broadcast custom or hidden SSIDs
- [x] **Deauthentication** - Finite burst or stoppable duration-based loop mode with mutually exclusive count/duration controls; multi-target selection; firmware-native `DEAUTH_MULTI` when advertised; repeated `DEAUTH_BURST` channel-hopping fallback for older firmware; manual channel locked to a target-derived schedule when multiple targets are selected
- [x] **Evil Twin** - Rogue AP with file-backed captive-portal HTML, `#SSID#` template substitution, persistent captured-password results after auto-stop, FAB start/stop progress that clears on disconnect, and a process-local toggle for the firmware test-notice page
- [x] **Captive Portal** - File-backed HTML serving with app-side `#SSID#` token substitution, chunked upload without a full-page RAM buffer, POST/GET form capture, force-served landing page for unmatched HTTP routes, DHCP DNS + wildcard DNS + optional best-effort plaintext custom-resolver DNS interception, FAB start/stop progress that clears on disconnect, separate bounded Portal/Evil Twin visitor and metric logs, and a process-local `Show firmware test notice` toggle guarded by a 5-second controlled-environment acknowledgement. ESP32-S2 builds intentionally do not advertise Portal/Evil Twin.
- [x] **WPS Pixie Dust** - Live WPS registrar flow on all supported ESP32 targets with AP-lock preflight, M3-only capture, same-session recovered-PIN continuation, Android-side Pixiewps search, and PIN/PSK persistence
- [ ] **Wifite-style Auto Audit** - Single-target Auto Capture + Crack orchestrator with optional WPS and explicitly confirmed Evil Twin stages
- [x] **Packet Sniffer** - Monitor-mode capture, pcap export, per-module Auto/2.4/5 GHz hopping, target-channel-aware fixed mode, and post-start deauth assist with periodic retries
- [x] **Wi-Fi device-handoff channel safety** - Normalizes all module channel state against the connected device and clears stale scan/target selections when the primary radio or its channel capabilities change; preserves valid selections across same-device reconnects
- [x] **Device telemetry** - Live heap, PSRAM, CPU, flash, transport, and internal chip/die temperature on Home, Devices details, and Status-driven telemetry; temperature is optional and labeled as chip temperature, not ambient
- [x] **WPA Handshake Capture/Crack** - Offline WPA2 M1/M2 dictionary cracking from saved credential/NRuGochi captures, custom PCAP, or imported `.22000`; single wordlist; multi-target selection with select-all; best M1/M2 pair selection; pause/resume with frozen progress; foreground progress notification; and checkpointed sessions that can be resumed after interruption
- [ ] **WPA Cracker advanced improvements** - ZIP/GZIP streaming, candidate rules/transforms, PMKID verification, and explicit WPA3-SAE/PMF unsupported reporting. `.22000` WPA*02 import, multi-target cracking, background progress, and checkpoint/resume are implemented
- [x] **PCAP to .hs22000** - Multi-select classic PCAP merge into a Hashcat `.22000` file; automatic best-handshake-per-BSSID+station dedupe; automatic SSID extraction; dropdown-based BSSID→ESSID overrides; preview, successful-export display, and malformed/missing-SSID reporting
- [x] **Nrugochi** - Pwnagotchi-style channel-density hunting: RECON -> INTERACT, multi-AP authorized deauth (8 frames/AP default with requested/sent/failed telemetry), all-EAPOL/all-beacon capture, per-BSSID `HS_<BSSID>.pcap` files, mood/status UI, and today/total stats (hardware validation pending). Disabled on ESP32-S2.
- [x] **BLE HID** - Bluetooth LE keyboard/mouse HID and DuckyScript payloads
- [x] **BLE Scanner** - Device discovery with names, RSSI, manufacturer data, and service badges; module-owned scan lifecycle
- [x] **BLE GATT Profile** - Read-only GATT service/characteristic enumeration with integrated BLE target scanner and module-owned scan controls
- [ ] **FastPair Model ID** - Map FastPair model IDs to known device names/types
- [x] **BadUSB** - HID injection via USB
- [x] **PIN Bruteforce** - Supervised native-HID range/wordlist tester; one-shot boot-arm like BadUSB; max-attempt cap, progress/ETA, configurable typing/start delays, saved sessions
- [x] **BLE HID PIN Bruteforce** - Hidden/experimental BLE-HID range/wordlist tester retained for future use; lock-screen use is unsupported
- [x] **Credential Manager** - Local storage of harvested creds with explicit per-credential Wi-Fi QR display/share, compact Evil Twin/WPA Cracker/WPS Pixie rows, and a separate WPS PIN row

## 2. Defense Modules

- [x] **Rogue AP Detector** - Autonomous nearby AP comparison with OUI rules; flags duplicate SSIDs, security downgrades, and suspicious APs
- [x] **Deauth Detector** - Passive deauth/disassoc monitoring with BSSID/client/RSSI/reason filters, async alerts, and live logs
- [x] **WPS Lock Detector** - Passive WPS AP discovery and AP Setup Locked parsing with Locked / Likely unlocked / Unknown states and WPS metadata
- [x] **WPS Attempt Detector** - Multi-select WPS targets; passive Identity/M1-M8/M2D/NACK monitoring; per-session evidence retention; live lock-state events; Low/Medium/High risk based on M1-M3 cracking material or M7-M8 PSK-transfer evidence
- [ ] **Deauth Locator** - RSSI-based direction/distance estimate; multi-node triangulation
- [x] **Tracker Detector** - Find My-style advert detection, repeated sightings, alerts, and export
- [x] **Client Detector** - Passive probe/assoc/reassoc/auth monitoring plus active deauth-trigger mode
- [x] **Hidden AP Revealer** - Passive hidden AP detection, probe-request SSID candidates, and association-based SSID resolution
- [ ] **Unauthorized RFID Reader Detector** - Detect unattended/skimmer-style RF field polling nearby
- [ ] **Jam Detector (Sub-GHz / 2.4GHz)** - Wideband noise-floor anomaly detection (detection only - see Legal notes)

## 3. Sensor Modules

- [x] **mmWave Radar (LD2450)** - 120° fan with green blurred dots, heartbeat scan/sleep mode, position-based dot persistence, configurable/unlimited dot cap, scan wave animation, max-range display control, no-data/parse-error/zero-target states; zone config and mesh broadcast intentionally disabled on feature/non-mesh

## 4. Tools & Firmware Utilities

- [x] **ESP32 Flasher** - Merged firmware flashing for ESP32, S2, S3, S3 PSRAM, C3, and C5; esptool-aligned reset timing, C5 SYNC/SPI-attach handling, TinyUSB-to-ROM bootloader restart, automatic manual-recovery fallback, C5 local chip review, background foreground-service progress notification with firmware-specific icon, distinct `ESP32-S3 (PSRAM)` target selection, and target-chip validation
- [x] **BSSID Geolocation** - Offline OUI/vendor lookup combined with approximate Wi-Fi AP location, persistent `Overview / Lookup History` tabs, retry/cache fallback, embedded Leaflet map, and external Google Maps link
- [x] **MAC Lookup** - Separate offline-only OUI/vendor lookup with a bundled OUI snapshot, locally-administered/multicast indicators, and copy/share actions; no map, geolocation, or lookup network request
- [x] **Credential Manager Wi-Fi QR** - `Share QR` beside `Share PCAP`; standard Wi-Fi QR generated locally with ZXing, image share, and payload copy; WPS PIN remains separate
- [x] **Serial Monitor** - Raw USB serial monitor with reliable clipboard copy, bounded log retention, and visible save/export success or failure feedback

## 5. Terminal / Termux SSH

- [x] **Native terminal** - Native Termux terminal view over local OpenSSH; multiple shell tabs, PTY resize, scrollback, selection, clipboard, and session paging
- [x] **SSH authentication** - Ed25519 and RSA public-key auth, Android Keystore-encrypted key storage, key fingerprint display, BouncyCastle Ed25519 signatures on Android, and pinned host-key validation
- [x] **Connection lifecycle** - silent app-open autoconnect, per-visit Terminal autoconnect, connecting status in the terminal surface, manual disconnect, force reconnect, and last-tab protection
- [x] **Terminal UI defaults** - 34 dp font, block cursor, 500 ms blink, pinch-to-zoom, keyboard controls, and extra keys shown only with the IME
- [x] **Terminal startup branding** - SSH-managed `~/.NRSuite/nr.sh` startup helper, styled version banner on new shells, automatic missing/modified script repair and SHA-256 verification, plus an optional non-persistent NRSuite Bash prompt with shortened paths and `$`/`#` user-root indicator
- [x] **Terminal Shortcuts** - Quick shortcut bottom sheet, search/category filters, run modes, Home pinning, confirmations, parameterized `{{name}}` commands with previews, and seeded editable examples
- [x] **Terminal documentation** - [Terminal / Termux SSH guide](docs/TERMINAL-GUIDE.md)
- [ ] **Termux tools** - planned Nmap scanning/topology, PCAP workflows, and optional Wireshark through Termux:X11

## 6. Mesh Network (ESP-NOW)

- [x] **Shared Group Key Provisioning** - HKDF-derived auth/transport keys provisioned over USB and stored in NVS
- [x] **Dynamic Master Election** - USB-authenticated candidate election with self-demotion
- [x] **Client Idle/Standby Mode** - Provisioned nodes passively listen and auto-join a valid master
- [x] **Encrypted ESP-NOW Transport** - AES-CCM transport encryption with replay protection
- [x] **Activation Handshake** - USB HMAC challenge, replay counter, and 30 s auth window
- [x] **Session Locking** - Fresh master session ID and client recovery after master reboot
- [x] **Heartbeat/Auto-Timeout** - 1 s master heartbeat, 5 s master timeout, 8 s peer timeout
- [x] **Mesh Node Health Reporting** - Phase 3A encrypted report transport and `node_health` aggregation; hardware-validated on S3 + S2
- [ ] **Distributed Detector Reporting** - Deauth/Rogue AP reports and same-frame correlation (Phase 3B+)
- [ ] **Triangulation Engine (app-side)** - Log-distance path-loss + trilateration from 3+ node RSSI reports

## 7. Mesh Chat

- [ ] **ESP-NOW Chat Transport** - Reuses mesh group-key infra
- [ ] **Multi-hop Relay** - Flood-fill with message-ID dedup
- [ ] **Store-and-Forward** - Hold messages for out-of-range recipients
- [ ] **Message Fragmentation** - Handles ESP-NOW's ~250 byte payload cap
- [ ] **Sender Aliases** - Human-readable operator names instead of raw MAC
- [ ] **Quick/Canned Messages** - Preset messages for fast field use
- [ ] **Chat/Sensor Traffic Prioritization** - Alerts pre-empt chat traffic on shared channel

## 8. Remote Camera Node (ESP32-CAM)

- [ ] **Live Feed Streaming** - Separate Wi-Fi link (not over ESP-NOW - bandwidth)
- [ ] **Snapshot-on-Trigger** - Captures still on detection events from other modules
- [ ] **Motion Detection** - Frame-diff trigger, reduces idle streaming
- [ ] **Deep-sleep/Low-power Mode** - Timer or PIR-interrupt wake
- [ ] **SD Card Buffering** - Local storage if master link is down
- [ ] **Mesh Manager Integration** - Camera nodes shown alongside sensor/chat nodes

## 9. Long-Range Relay (LoRa)

- [ ] **LoRa Node-to-Node Link** - SX1276/78, kilometers of range, requires soldering
- [ ] **LoRa Chat Bridge** - Bridges distant node clusters into mesh chat
- [ ] **Low-bandwidth Telemetry** - Alerts, GPS, heartbeats - not video
- [ ] **Long-baseline Locator Beacon** - Improves triangulation geometry via wider node spacing
- [ ] **Multi-hop Routing** - Beyond simple flood-relay, given larger distances/node counts

## 10. Peripheral Radios (planned expansion modules)

- [x] **IR** - Capture/Replay - Flipper `.ir` import/library, Flipper-IRDB download/rescan card on the Home > IR category screen with NRSuite-root guard/picker, direct extraction without large SAF directory renames, parallel SAF indexing, ESP32 GPIO defaults and save-state UI, raw TX/RX, and phone IR support
- [ ] **IR** - Universal Remote DB - Common protocol library (NEC, SIRC, RC5)
- [ ] **IR** - TV-off Brute-force - TV-B-Gone style
- [ ] **Sub-GHz (CC1101)** - Scanner - 315/433/868/915MHz sweep
- [ ] **Sub-GHz** - Capture/Replay - Fixed-code devices; rolling-code explicitly not supported/replayable
- [ ] **Sub-GHz** - Protocol Decoder - Identifies fixed vs. rolling code
- [ ] **nRF24** - RC/Device Scanner - Detect nearby nRF24-based peripherals
- [ ] **nRF24** - MouseJack-style Analysis - Detection/analysis of known-vulnerable wireless HID dongles
- [ ] **nRF24** - Packet Sniffer - Promiscuous capture for research
- [ ] **RFID/NFC** - Read (125kHz + 13.56MHz) - RC522 + LF reader modules
- [ ] **RFID/NFC** - Emulate - Replay previously-read tag
- [ ] **RFID/NFC** - Write/Clone - To blank/magic tags
- [ ] **RFID/NFC** - Access Control Analyzer - Identify tag type + known weaknesses (e.g., MIFARE Classic)

## 11. System / USB

- [x] **Multi-device USB sessions** - Multiple simultaneous USB serial sessions with per-device permission, connect/disconnect, and reconnect state. User-initiated disconnect uses idle-session reuse for S2 CDC stability.
- [x] **BadUSB device picker** - Select which connected device receives BadUSB independent of the primary Wireless/Flasher session.
- [x] **Persistent firmware device ID** - Firmware generates/stores `NRxxxxxxx` in NVS and exposes it via `STATUS.device_id`; Android shows it on Home and Device manager.
- [x] **Dual USB/UART transport reporting** - Firmware reports whether the active protocol path is `usb_cdc` or `uart0`; the app displays the reported transport in device specs and connection logs. Native-USB-capable firmware can answer on either exposed devkit port.

## Experimental Concepts

- [~] **Half Handshake capture** - Active probe-response/SoftAP experiment intended to elicit WPA2 M1+M2 from an authorized auto-connecting client for offline `.22000` analysis. Requires explicit authorization, compatible RSN/WPA2 security, client auto-join, temporary-AP cleanup, and hardware validation. Not beta.3.1 release scope unless separately approved.

## Explicitly Out of Scope

- **RF/Wi-Fi/BLE Jamming (transmission-based denial)** - illegal in most jurisdictions regardless of stated intent (US 47 U.S.C. §333 and equivalents). NRSuite implements **jam detection**, not jamming. This is a firm project boundary, not just a "not yet built" item.

---

## Roadmap Phasing

### v1 - Near-term
Builds on existing offense infrastructure (sniff/pcap/frame codec). Rogue AP Detector, Deauth Detector, and Tracker Detector shipped in beta.2 - remaining:
- Mesh Chat (single-hop, basic)

### v2 - Mesh & Camera
- Mesh master/client election + activation handshake
- Distributed sensor reporting + triangulation
- Deauth Locator
- ESP32-CAM node integration

### v3 - Peripheral & Long-range Expansion
- Sub-GHz, nRF24, RFID/NFC, IR modules
- LoRa long-range relay node
- Multi-hop mesh routing

Contributions toward any phase are welcome - see [CONTRIBUTING.md](./CONTRIBUTING.md). If you want to pick up an unchecked item, open an issue first so work isn't duplicated.
