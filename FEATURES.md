# NRSuite — Features & Roadmap

This document lists all current and planned features across the NRSuite ecosystem (Android app + ESP32 firmware + mesh network). Status legend:

- ✅ Implemented
- 🚧 In progress
- 📋 Planned

---

## 1. Offense Modules (Wi-Fi / BLE)

| Feature | Status | Notes |
|---|---|---|
| Wi-Fi Scan | ✅ | Passive AP/channel discovery |
| Beacon Injection | ✅ | Beacon frame injection/spoofing |
| Deauthentication | ✅ | Deauth frame injection |
| Evil Twin | ✅ | Rogue AP with captive portal |
| Captive Portal | ✅ | Credential harvesting UI |
| Packet Sniffer | ✅ | Monitor-mode capture, pcap export |
| WPA Handshake Capture/Crack | ✅ | Offline dictionary cracking |
| BLE Scan/Interaction | ✅ | Bluetooth LE device interaction |
| BadUSB | ✅ | HID injection via USB |
| Credential Manager | ✅ | Local storage of harvested creds |

## 2. Defense Modules

| Feature | Status | Notes |
|---|---|---|
| Rogue AP Detector | 📋 | Baseline SSID→BSSID/channel/security; flags mismatches, downgrades, duplicate SSIDs, KARMA-style probe-response behavior |
| Deauth Detector | 📋 | Frame-rate anomaly detection per source/dest pair |
| Deauth Locator | 📋 | RSSI-based direction/distance estimate; multi-node triangulation |
| AirTag/Tracker Detector | 📋 | BLE "Find My"-style advert detection; cross-location persistence heuristic |
| Client/Presence Detector | 📋 | Passive client-count via associated/probe-request MACs |
| Unauthorized RFID Reader Detector | 📋 | Detect unattended/skimmer-style RF field polling nearby |
| Jam Detector (Sub-GHz / 2.4GHz) | 📋 | Wideband noise-floor anomaly detection (detection only — see Legal notes) |

## 3. Mesh Network (ESP-NOW)

| Feature | Status | Notes |
|---|---|---|
| Shared Group Key Provisioning | 📋 | Key pushed to node via USB/serial, stored in NVS |
| Dynamic Master Election | 📋 | Whichever node is USB-plugged into the app becomes master |
| Client Idle/Standby Mode | 📋 | Unplugged nodes idle until valid activation request |
| Encrypted ESP-NOW Transport | 📋 | AES-CCM link encryption + HMAC payload auth |
| Activation Handshake | 📋 | Decrypt → HMAC check → nonce/replay check → timestamp window |
| Session Locking | 📋 | First-valid-master-wins per session, ignores competing requests |
| Heartbeat/Auto-Timeout | 📋 | Clients revert to idle if master goes silent |
| Distributed Sensor Reporting | 📋 | Active clients run local detectors, report to master |
| Triangulation Engine (app-side) | 📋 | Log-distance path-loss + trilateration from 3+ node RSSI reports |

## 4. Mesh Chat

| Feature | Status | Notes |
|---|---|---|
| ESP-NOW Chat Transport | 📋 | Reuses mesh group-key infra |
| Multi-hop Relay | 📋 | Flood-fill with message-ID dedup |
| Store-and-Forward | 📋 | Hold messages for out-of-range recipients |
| Message Fragmentation | 📋 | Handles ESP-NOW's ~250 byte payload cap |
| Sender Aliases | 📋 | Human-readable operator names instead of raw MAC |
| Quick/Canned Messages | 📋 | Preset messages for fast field use |
| Chat/Sensor Traffic Prioritization | 📋 | Alerts pre-empt chat traffic on shared channel |

## 5. Remote Camera Node (ESP32-CAM)

| Feature | Status | Notes |
|---|---|---|
| Live Feed Streaming | 📋 | Separate Wi-Fi link (not over ESP-NOW — bandwidth) |
| Snapshot-on-Trigger | 📋 | Captures still on detection events from other modules |
| Motion Detection | 📋 | Frame-diff trigger, reduces idle streaming |
| Deep-sleep/Low-power Mode | 📋 | Timer or PIR-interrupt wake |
| SD Card Buffering | 📋 | Local storage if master link is down |
| Mesh Manager Integration | 📋 | Camera nodes shown alongside sensor/chat nodes |

## 6. Long-Range Relay (LoRa)

| Feature | Status | Notes |
|---|---|---|
| LoRa Node-to-Node Link | 📋 | SX1276/78, kilometers of range, requires soldering |
| LoRa Chat Bridge | 📋 | Bridges distant node clusters into mesh chat |
| Low-bandwidth Telemetry | 📋 | Alerts, GPS, heartbeats — not video |
| Long-baseline Locator Beacon | 📋 | Improves triangulation geometry via wider node spacing |
| Multi-hop Routing | 📋 | Beyond simple flood-relay, given larger distances/node counts |

## 7. Peripheral Radios (planned expansion modules)

| Feature | Status | Notes |
|---|---|---|
| **IR** — Capture/Replay | 📋 | IR LED + receiver, cheap GPIO add-on |
| **IR** — Universal Remote DB | 📋 | Common protocol library (NEC, SIRC, RC5) |
| **IR** — TV-off Brute-force | 📋 | TV-B-Gone style |
| **Sub-GHz (CC1101)** — Scanner | 📋 | 315/433/868/915MHz sweep |
| **Sub-GHz** — Capture/Replay | 📋 | Fixed-code devices; rolling-code explicitly not supported/replayable |
| **Sub-GHz** — Protocol Decoder | 📋 | Identifies fixed vs. rolling code |
| **nRF24** — RC/Device Scanner | 📋 | Detect nearby nRF24-based peripherals |
| **nRF24** — MouseJack-style Analysis | 📋 | Detection/analysis of known-vulnerable wireless HID dongles |
| **nRF24** — Packet Sniffer | 📋 | Promiscuous capture for research |
| **RFID/NFC** — Read (125kHz + 13.56MHz) | 📋 | RC522 + LF reader modules |
| **RFID/NFC** — Emulate | 📋 | Replay previously-read tag |
| **RFID/NFC** — Write/Clone | 📋 | To blank/magic tags |
| **RFID/NFC** — Access Control Analyzer | 📋 | Identify tag type + known weaknesses (e.g., MIFARE Classic) |

## Explicitly Out of Scope

- **RF/Wi-Fi/BLE Jamming (transmission-based denial)** — illegal in most jurisdictions regardless of stated intent (US 47 U.S.C. §333 and equivalents). NRSuite implements **jam detection**, not jamming. This is a firm project boundary, not just a "not yet built" item.

---

## Roadmap Phasing

### v1 — Near-term
Builds on existing offense infrastructure (sniff/pcap/frame codec).
- Rogue AP Detector
- Deauth Detector
- AirTag/Tracker Detector
- Mesh Chat (single-hop, basic)

### v2 — Mesh & Camera
- Mesh master/client election + activation handshake
- Distributed sensor reporting + triangulation
- Deauth Locator
- ESP32-CAM node integration

### v3 — Peripheral & Long-range Expansion
- Sub-GHz, nRF24, RFID/NFC, IR modules
- LoRa long-range relay node
- Multi-hop mesh routing

Contributions toward any phase are welcome — see [CONTRIBUTING.md](./CONTRIBUTING.md). If you want to pick up a 📋 item, open an issue first so work isn't duplicated.
