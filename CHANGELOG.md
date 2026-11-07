# Changelog

## Unreleased

### Added
- Personal release updater with latest/exact-tag selection, isolated three-way patch worktrees, prepare/resume support, existing-key signing and certificate/package/feature/version checks before APK promotion.
- Standard-library Python regression tests and a PowerShell entry point for the release updater.
- Root / NetHunter control panel for native Wi-Fi, Bluetooth and NFC toggles, driver capabilities, Kali health/interfaces/tool execution, idle-only chroot lifecycle, SSH/Apache/PostgreSQL services and installed NetHunter/terminal/KeX launchers.
- Fixed action commands with root checks, 30-second deadlines, bounded output and action effect confirmations. Idle chroot stop refuses running Kali processes and never deletes files or kills processes.
- Optional Phone toolbar entry for built-in Wi-Fi discovery, bounded BLE scans and NFC identification using Android APIs on stock, rooted and NetHunter Android phones.
- Runtime permissions, missing/disabled radio messages, cached Wi-Fi labels, scan timeouts and lifecycle cleanup.
- Native hardware capability documentation and physical acceptance checklist; privileged controls are provided by the separate Root / NetHunter panel.

### Changed
- Debug builds use a separate nativepreview application ID and NRSuite Native Test label to preserve the newer installed app and its data.
- USB host hardware is optional in the manifest for phone-only discovery.

### Fixed
- Consume the complete NFC service dump when reporting radio status, avoiding a misleading broken-pipe message from an early-exiting filter.
