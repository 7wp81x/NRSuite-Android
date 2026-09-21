# Contributing to NRSuite

Thanks for considering contributing — NRSuite spans Android, ESP32 firmware, and RF/mesh protocol work, so there's room for a wide range of skill sets.

## Before You Start

1. **Check [FEATURES.md](./FEATURES.md)** for the roadmap and current status of each module.
2. **Search existing issues** before opening a new one — avoid duplicate work.
3. **For anything nontrivial, open an issue first** describing what you want to build and why, before writing code. This avoids wasted effort on PRs that don't fit the roadmap or that conflict with work already in progress.
4. Read the [Legal/Ethical boundaries](#legalethical-boundaries) section below — some feature categories (e.g., jamming) will not be merged regardless of implementation quality.

## Repos

| Repo | Stack | What lives here |
|---|---|---|
| `nrsuite-android` | Kotlin, Jetpack Compose | App UI, USB/serial flasher, protocol client |
| `nrsuite-firmware` | ESP-IDF / PlatformIO (C/C++) | ESP32 firmware: offense, defense, mesh, peripherals |
| `nrsuite-protocol` | Markdown spec | Shared frame/packet format between app and firmware |

If your change touches the wire protocol, update `nrsuite-protocol` first and reference the spec version in your PR description.

## Development Setup

### Android app
- Android Studio (latest stable), min SDK as defined in `app/build.gradle.kts`
- Clone `nrsuite-android`, open in Android Studio, sync Gradle
- USB-OTG capable Android device recommended for testing serial/flasher features (emulator can't do USB serial)

### Firmware
- PlatformIO (recommended) or ESP-IDF directly
- Target board defined in `platformio.ini` per environment
- For mesh/ESP-NOW features, you'll need at least 2 ESP32 boards to test locally

## Branching Model

- `main` — always deployable/stable. Protected (see below).
- `develop` — integration branch for the next release. PRs from feature branches target this.
- `feature/<short-name>` — one feature/fix per branch, branched from `develop`
- `hotfix/<short-name>` — urgent fixes branched from `main`, merged to both `main` and `develop`

Naming examples: `feature/rogue-ap-detector`, `feature/espnow-mesh-activation`, `hotfix/serial-flasher-crash`.

## Pull Request Process

1. Branch from `develop` (or `main` for hotfixes).
2. Keep PRs scoped to one feature/fix — large multi-feature PRs are hard to review and will likely be asked to split.
3. Fill out the PR template: what changed, why, how it was tested, any protocol/spec changes.
4. Ensure CI passes (build, lint, any existing tests).
5. At least **one maintainer approval** required before merge (see branch protection below).
6. Squash-merge preferred, to keep `develop`/`main` history readable.

## Commit Style

Conventional-commit-style prefixes appreciated but not strictly enforced:
```
feat: add rogue AP baseline comparison
fix: correct RSSI unit conversion in locator
docs: update mesh activation handshake diagram
refactor: extract HMAC verification into shared helper
```

## Code Style

- **Kotlin**: follow existing formatting in the repo (ktlint config if present); prefer Compose idioms already used in `ui/` over introducing new patterns.
- **Firmware (C/C++)**: match existing module structure under `src/offense/`, `src/defense/`, `src/mesh/`; keep radio-specific code isolated per peripheral (don't couple sub-GHz logic into the Wi-Fi sniff path, etc.).
- Comment protocol-level "why," not just "what" — mesh/crypto logic especially benefits from inline rationale since it's security-sensitive.

## Testing Expectations

- Firmware: where feasible, test on real hardware before submitting (simulated radio behavior is unreliable for RF-timing-sensitive code).
- Mesh/protocol changes: test with at least a 2-node setup (master + 1 client) before submitting; 3-node testing appreciated for triangulation-related work.
- Android: instrumented tests for UI where practical; unit tests for parsing/codec logic (see existing `EapolParser`, `WpaHandshakeParser` for patterns).

## Legal/Ethical Boundaries

NRSuite is a dual-use security research tool. Some contribution categories will not be accepted:

- **RF/Wi-Fi/BLE jamming** (transmission-based denial of service) — illegal in most jurisdictions independent of intent. PRs implementing jamming (as opposed to jam *detection*) will be closed. See [FEATURES.md](./FEATURES.md#explicitly-out-of-scope).
- Features whose only plausible use is targeting systems without consent (vs. dual-use testing/defense tools) will be evaluated case by case and may be declined.

When in doubt, ask in an issue before building.

## Reporting Security Issues

If you find a vulnerability in NRSuite itself (e.g., a flaw in the mesh encryption/auth scheme, not a target-system vulnerability the tool is built to find), please report privately rather than opening a public issue — see [SECURITY.md](./SECURITY.md) for contact details and disclosure timeline.

## Code of Conduct

Be respectful, assume good faith, keep discussion focused on the technical merits. Harassment, doxxing, or requests for help attacking specific real-world unauthorized targets will result in a ban from the project.
