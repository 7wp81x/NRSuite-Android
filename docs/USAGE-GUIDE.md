# NRSuite Usage Guide

> Authorized testing only. Use NRSuite only on networks, devices, and accessories
> you own or have explicit written permission to test.

This guide covers the Android app's general layout, connection flow, category-level
configuration, and the main workflows that need more than a quick description.

---

## 1. First connection

1. Connect the ESP32 to the phone with a USB-OTG-capable cable/adapter.
2. Open NRSuite and grant USB permission for the ESP32 or USB-UART bridge.
3. Open **Devices**, select the connected ESP32, and press **Connect**.
4. Wait for the connection indicator to show the chip name.
5. Confirm basic communication with a `PING`/status update.

If the app receives heartbeats but commands time out, verify the phone-to-ESP32
TX/RX path before troubleshooting individual modules.

### Root directory

NRSuite needs a writable root directory for captures, imports, exports, and
history-related files. Select it when prompted, or use the app settings.

Captured PCAP files are normally written under:

```text
<Pcap>/
```

---

## 2. Navigation and configuration model

NRSuite uses category screens. A category contains related modules, while
hardware and shared settings normally live at the **category level**.

```text
Home
└── Category
    ├── Shared information/status header
    ├── [Tune] Category Config
    └── Module cards
```

Current category-level configuration screens include:

- **WiFi Config**
- **IR Config**
- **Sensor Config**

This is intentional: configuration is not repeated inside every feature screen.
If a module needs a different GPIO or backend, open the configuration action on
the category header first.

Some categories also expose filters. WiFi, for example, uses:

```text
[ Recon ] [ Attack ] [ Defend ]
```

---

## 3. WiFi

### Shared channel control

WiFi tools use a common channel control. The channel/band summary sits beside
the section label and uses the module accent color:

```text
Channel                              [CH 6 · 2.4 GHz]
[-]  ch 6  [+]
```

- The summary pill shows the selected channel and band; it is not a second
  control.
- 5 GHz channels are selectable only when the connected board and regulatory
  channel mask allow them.
- ESP32-C5 supports 2.4 GHz and 5 GHz. Other targets are normally 2.4 GHz-only.
- Selecting a target AP in a targeted workflow automatically selects its channel.

### WiFi Config

Open **WiFi Config** from the WiFi category header.

#### Regulatory country

Select your actual operating location. The firmware uses the country setting to
restrict allowed channels. This is not a region-unlock control.

#### Preferred scan band

On C5 builds, select the general scan preference:

- Auto
- 2.4 GHz
- 5 GHz

Individual tools may provide their own band or channel controls.

#### Capture output

WiFi capture output defaults to the global NRSuite root. You can choose a
WiFi-specific folder:

- **Choose** selects a custom folder.
- **Use global root** removes the WiFi-specific override.
- PCAP files are still placed under a `Pcap/` subdirectory.

### Packet Sniffer

Packet Sniffer has two capture profiles.

#### Capture

Use this for general packet capture:

- Fixed channel or channel hopping
- Channel or band selection
- Hop interval
- No target-specific network filtering
- No handshake-only or deauth-assist options

#### Target Capture

Use this to focus on one access point:

1. Press **Scan WiFi for targets**.
2. Select the target AP from the scan results.
3. The target's channel is selected automatically.
4. Optionally enable:
   - **Handshake-only capture**
   - **Deauth assist during capture**
5. Enable deauth assist only on authorized networks.

Target Capture has no manual target entry and no separate channel-mode selector.

### Live capture card

The live card shows:

- Capture status
- Packet count
- Current PCAP file size
- Capture path
- M1–M4 handshake progress when handshake-only mode is enabled

Handshake-only capture records EAPOL frames plus one contextual beacon. The
captured filename and PCAP remain in the configured output directory.

### Wi-Fi target lists

Attack and target-selection screens use a bounded, independently scrollable scan-result
list. Re-scanning does not make the whole screen grow indefinitely.

- Single-target modules use radio selection.
- Deauth uses multi-select checkboxes.
- Scan results include SSID, BSSID, channel/band, RSSI, security, and WPS state.

### Deauth

Deauth supports two modes:

- **Burst** — sends a finite frame count.
- **Loop** — runs until **Stop** is pressed.

With multiple targets selected, the app sends one burst to each selected target,
then moves to that target's channel. Multi-target loop mode repeats this
channel-hopping pass until stopped. This orchestration currently uses repeated
`DEAUTH_BURST` commands; a dedicated firmware multi-target command is future work.

### Beacon

Beacon has two tabs:

- **Overview** — current SSID summary, channel/timing, options, and broadcast status.
- **SSID Lists** — edit SSIDs, save/load/rename/delete lists, import text lists,
  and load presets.

The Rickroll preset is loaded by default on a fresh screen. Every preset SSID is
within the firmware's 32-byte SSID limit.

### Probe Request Monitor and Channel Analyzer

- **Probe Request Monitor** groups probes by MAC/SSID/channel, with RSSI,
  sightings, vendor, and randomized-MAC hints.
- **Channel Analyzer** performs a scan and shows compact 2.4/5 GHz bars plus a
  scrollable nearby-AP list. APs are sorted by RSSI and can be filtered by band.

Both screens clear their module-local results when you leave the module.

### Captive Portal, Evil Twin, and HTML templates

Captive Portal and Evil Twin share one local HTML template library:

- Import, rename, delete, or select saved templates.
- Imported templates are content-hashed; duplicate HTML is rejected.
- The 24 KB firmware limit is enforced. Files above 16 KB show a warning.
- HTML is required for both Captive Portal and Evil Twin before start.
- The app checks device free heap before starting the portal; a firmware
  `RESET_HTML`/upload rejection is shown clearly and the portal is stopped.
- The HTML Templates tab is available from both portal workflows.

See [Captive Portal / Evil Twin HTML authoring](CAPTIVE-PORTAL-EVIL-TWIN-AUTHORING.md)
for page structure and test guidance.

### WPS Lock Detector

Use **WiFi → Defend → WPS Lock Detector**.

1. Connect the ESP32.
2. Tap the scan FAB.
3. Review nearby WPS-enabled APs.
4. Filter by `Locked`, `Likely unlocked`, or `Unknown`.
5. Tap an AP to inspect WPS version, configuration methods, selected registrar,
   vendor, and the exact lock-attribute result.

`Likely unlocked` means the WPS IE was parsed and the AP did not advertise the
AP Setup Locked attribute. It is not a guarantee that a PIN attempt will succeed.
The detector never performs an active WPS PIN attempt to test lock state.

### WPS Attempt Detector

Use **WiFi → Defend → WPS Attempt Detector**.

The detector passively parses EAP Identity and EAP-WSC M1–M8 activity. It does
not transmit WPS frames or attempt authentication.

Configuration:
- Fixed channel or channel hopping
- Hop interval
- Start/Stop FAB

Observed attempt cards show:
- Source MAC, vendor, and randomized-MAC indicator
- Target SSID/BSSID
- Channel/band and RSSI
- Current WPS stage and observed frame count
- Session duration
- Low / Medium / High risk and the reasons behind the score

Risk is heuristic. A single WPS exchange is generally Low; repeated attempts,
multiple source MACs, or NACK/M2D activity can raise it. The wording is
intentionally **“WPS attempt detected”**, not “attack confirmed.”

### WPS Pixie Dust

Use **WiFi → Attack → WPS Pixie Dust**.

- WPS target scans are isolated to that module; other Wi-Fi scans do not populate
  the WPS target list.
- Select a WPS-enabled target after a scan, then start the live attempt.
- The live status card appears only after an attempt has activity or a result.
- Leaving the module clears transient WPS state and targets.
- Recovered PINs/PSKs are persisted through Credential Manager and the WPS PIN store.

---

## 4. IR

The IR category contains:

- IR Remotes
- IR Receiver
- IR Finder
- IR Jammer
- IR Configuration

Use the category-level **IR Config** action for hardware and library settings.

### IR Config

#### Transmit backend

- **Phone IR** uses the phone's built-in IR emitter, if available.
- **ESP32 IR** uses the connected board's IR TX GPIO and RMT output.
- **Auto** chooses an available backend based on the connected hardware.

#### ESP32 IR GPIOs

The firmware supplies the safe GPIO choices for the connected target.

```text
ESP32 IR TX GPIO -> IR LED / driver input
ESP32 IR RX GPIO <- IR receiver output
```

Configuration is persisted in ESP32 NVS. Changing GPIOs stops active IR work.

#### IR library and database

IR Config also manages:

- Flipper `.ir` import
- Flipper-IRDB download/update
- ZIP import
- Database rescan/delete
- Loop interval

### IR Remotes

Use this screen to browse remote-level libraries:

- Favorite remotes
- Search and category/brand filters
- Button grid
- Single press or loop transmission
- Open remote details

### IR Receiver

Use **Start RX** before sending an IR signal from another remote.

The receiver can:

- Capture one pending raw signal
- Replay the pending signal
- Save it as a learned button
- Export learned buttons as `.ir`
- Show overflow or no-signal states

The ESP32 RMT receiver has a bounded symbol buffer. Very long or unusual signals
may be marked as overflow and should be re-captured.

### IR Finder

IR Finder helps identify a working remote/button combination:

1. Choose category and brand.
2. Select the common buttons to test.
3. Adjust the minimum remote-occurrence threshold.
4. Set the auto-step interval.
5. Start the search.
6. Confirm the effect on the target device.
7. Use **Save** for a working result.

Finder controls:

- **Previous / Next** selects a candidate manually.
- **Send** transmits the current candidate.
- **Loop signal** repeats the current candidate.
- **Pause / Resume** controls auto-stepping.
- **Save** stores the discovered result.
- **Open remote** opens the current remote for verification.

The transmission indicator shows:

- **SENDING** — blinking while auto-stepping transmits
- **LOOPING** — blinking during active loop
- **PAUSED** — static neutral dot
- **IDLE** — static neutral dot

Saved results are kept in a bounded scrollable container so long searches do not
make the whole screen grow indefinitely.

### IR Jammer

IR Jammer is an active, supervised test tool.

- Requires explicit authorization confirmation.
- Supports timed and screen-active unlimited modes.
- Stops automatically when leaving the screen, backgrounding the app, or changing
  the IR backend.
- Must only be used at short range against equipment you are authorized to test.

---

## 5. Sensors

### Sensor Config

Open **Sensor Config** from the Sensors category header.

For the HLK-LD2450:

- Select the ESP32 UART RX and TX GPIOs.
- UART speed is `256000` baud.
- Settings are persisted in ESP32 NVS.
- Changing pins stops and reinitializes the UART.

Wiring rules:

```text
LD2450 TX  -> ESP32 RX GPIO
LD2450 RX  -> ESP32 TX GPIO
LD2450 VCC -> stable 5 V supply
LD2450 GND -> common ground with ESP32
```

Do not connect the sensor to the ESP32 USB-UART bridge/UART0 pins used for the
NRSuite command link. Do not use unsafe or conflicting GPIOs.

### mmWave Radar screen

The radar screen provides:

- 120° radar fan
- Up to three targets
- X/Y position, distance, and speed
- Heartbeat scanning and dot persistence
- Configurable range and dot limits
- No-data and parse-error states

Radar display options remain inside the radar module. Hardware GPIO settings live
in Sensor Config.

---

## 6. BLE and USB essentials

### BLE

Depending on the board:

- BLE Scanner discovers nearby advertisements.
- Tracker Detector flags Find My/AirTag-style behavior.
- BLE GATT Profile enumerates read-only services/characteristics.
- BLE HID supports keyboard, mouse, and scripts.
- FastPair Model ID is parked until suitable test hardware is available.

ESP32-S2 has no BLE radio.

### USB / HID / storage

- BadUSB and native-HID PIN Bruteforce require ESP32-S2 or S3.
- Mass Storage/MSC is available on ESP32-S2 and S3.
- Multiple supported USB serial devices can be connected and managed independently.
- Always disconnect modules cleanly before unplugging the ESP32.

---

## 7. Mesh status

Mesh code is present in merged development branches but remains **experimental**
and is outside the non-mesh release validation matrix.

Mesh configuration and status are controlled from the Mesh screen when the
connected firmware advertises mesh support. Do not rely on experimental mesh
behavior as part of a non-mesh release validation.

---

## 8. Common troubleshooting

### Device appears but commands time out

- Confirm the firmware heartbeat is arriving.
- Verify phone-to-ESP32 TX/RX wiring, especially with CP2102/CH340 boards.
- Do not share UART0 with sensor or IR wiring.
- Try another USB-OTG cable/adapter.
- For external UART bridges, verify the correct TX/RX crossover and common ground.

### WiFi capture does not start

- Select a root/output directory.
- Verify WiFi Config output paths.
- Check that no other Wi-Fi radio mode is active.
- Confirm the selected channel is allowed by the country/channel mask.

### Target Capture cannot start

- Scan Wi-Fi and select a target AP.
- Confirm the target still exists and the channel is allowed.
- Enable deauth assist only when a valid target BSSID is selected.

### IR does not transmit

- Check the selected backend.
- Phone IR requires a phone IR emitter.
- ESP32 IR requires configured, safe TX GPIO and correct LED/driver wiring.
- Confirm the remote/protocol is supported; unsupported protocols may remain
  visible but disabled.

### LD2450 shows no data

- Confirm Sensor Config RX/TX pins.
- Verify sensor TX/RX crossover.
- Use a stable 5 V supply and common ground.
- Confirm nothing else is using those UART pins.
- Check for no-data or parse-error messages on the radar screen.

### RFID

PN532/RFID is hidden and unsupported in the current release. Do not treat the
presence of compiled firmware code as hardware support.

---

## 9. Safety and data handling

- Only test networks and devices you own or are authorized to test.
- Confirm before using active deauth, portal, Evil Twin, IR Jammer, or other
  transmitting tools.
- Keep captures and credential data in a controlled location.
- Review local laws before using radio or interference tooling.
- Do not use NRSuite for jamming or unauthorized disruption.
