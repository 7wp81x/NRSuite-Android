# NRSuite Android UI Handoff — Button / Action System

Last updated: 2026-11-04

## 1. Purpose

This handoff is for the next UI session. The current design foundation and major
screens are implemented; the next focused task is to formalize button/action
hierarchy so screens stop inventing one-off button styling.

This is an Android-only task. Do not change firmware or protocol behavior.

---

## 2. Current Repository State

- Repository: `android-app`
- Branch: `feature/ui-redesign`
- Base release: `1.0.0-beta.3.1.2`
- Latest commit at handoff: `694018f feat(logs): redesign runtime and history logs`
- Working tree: clean except the pre-existing untracked test artifact:
  - `test-artifacts/NRSuite-older-beta-1.0.0-beta.2.2-debug.apk`
- App version:
  - `versionCode = 10`
  - `versionName = 1.0.0-beta.3.1.2`

The design guide lives in the project root:

- `../NRSUITE-UI-DESIGN-GUIDE.md`

Read that guide before starting this button-system task.

---

## 3. Build Command

The environment needs JDK 17 for `javac` and a writable Gradle home inside the repo.

```bash
cd /home/twp81x/Documents/Tools/DeepSeek/NRSuite-App-Project/android-app
GRADLE_USER_HOME="$PWD/.gradle-agent" \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
./gradlew :app:assembleDebug --offline --console=plain
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If dependencies are missing from the offline cache, run once without `--offline`.

---

## 4. Implemented UI Work So Far

### Themes
- Switchable Material 3 themes in Settings → Appearance:
  - Obsidian
  - AMOLED Black
  - Daylight
- Themes are persisted via SharedPreferences.
- Full semantic color roles, surface-container roles, typography, shapes, status
  colors, and category accents are defined in `ui/theme/`.

### Navigation shell
- Bottom navigation has rounded top corners, neutral outline, transparent
  selection indicator, accent icon/label, and small active dot.
- Top bar:
  - connection chip hidden on root tabs
  - connection chip tappable when multiple devices are connected
  - opens the active-device chooser
  - switch confirmation is a Toast, not a Snackbar
- Settings is in the top bar.
- Full module catalog is opened from search.

### Home
- Device Link card with direct Connect for attached devices.
- Firmware update notice inside the connected device card.
- App update and OUI warnings remain dashboard-level.
- Old "Manage devices" button removed.
- Detection prompt is handled globally and suppressed where screens own
  connect/permission flows.

### Devices
- Active device card with expandable specs.
- Other USB devices list excludes the active device.
- Inactive connected devices can be switched without disconnecting.
- Per-device diagnostics and firmware update availability.
- First-run firmware onboarding dialog with "Don't show again".
- Flasher target is scoped to the selected device chip/firmware.
- Failed-device chip hints are stored per device.
- Device detection prompt:
  - multi-device stack
  - lighter surface + accent border
  - debounced and auto-dismissed
  - suppressed on Home, Devices, Firmware, Settings, Serial Monitor, BadUSB

### Logs
- Runtime/History use the shared `RoundedTabRow`.
- Runtime is a compact terminal-style feed with search, severity filters,
  live/pause follow, per-entry detail, copy/export/clear overflow, and clear
  confirmation.
- History uses compact session cards with filters, detail, export, and clear
  confirmation.

---

## 5. Current Button Inventory

Audit snapshot from `ui/`:

| Pattern | Count |
|---|---:|
| `Button(...)` | ~460 |
| `OutlinedButton(...)` | ~141 |
| `TextButton(...)` | ~147 |
| `ModuleActionFab(...)` | 30 |
| custom `FloatingActionButton(...)` | 3 |
| `ButtonDefaults.buttonColors` | 12 |
| `ButtonDefaults.outlinedButtonColors` | 15 |

The count itself is not the problem. The issue is inconsistent intent:

- solid accent vs solid status color
- outlined neutral vs outlined status color
- compact row button heights vary (32, 34, 40 dp)
- one-off `contentColor = NrBackground` overrides
- some module screens bypass `ModuleActionFab`
- filters and actions sometimes look too similar
- destructive actions are not consistently styled

Current custom FAB locations to standardize:

- `ui/MeshScreen.kt`
- `ui/RfidScreen.kt`

---

## 6. Target Button Taxonomy

### Primary — filled accent
Use for the one dominant action in a task area.

Examples:
- Start scan
- Save
- Connect
- Download update
- Flash firmware
- Open Flasher

Style:
- `containerColor = MaterialTheme.colorScheme.primary`
- `contentColor = MaterialTheme.colorScheme.onPrimary`
- regular height: `40.dp`
- compact height: `32.dp`
- category accent is inherited from `CategoryAccentTheme`

### Secondary — outlined neutral
Use for supporting/alternate actions.

Examples:
- Refresh
- Change
- Select
- Details
- Export
- Manage
- Grant permission

Style:
- transparent/tonal neutral container
- `1.dp` outline at neutral alpha
- content `onSurface`
- destructive outline variants use `error` only when needed

### Tertiary — text
Use for low-emphasis inline actions.

Examples:
- Cancel
- Clear search
- Show/Hide
- View all
- Dismiss

### Destructive
- Normal context: outlined/text red
- Final confirmation: filled red
- Never use solid red for ordinary secondary actions

### Status action
- Start/run/scan: filled accent
- Stop: filled red
- Pause: amber outlined/tonal
- Resume: accent outlined or filled depending on hierarchy

### Compact row action
For device rows and compact list rows:

- height `32.dp`
- min width about `96–108.dp`
- small label typography
- consistent icon size (`16–18.dp`)
- same padding across Connect / Connecting / Diagnosing / Switch / Disconnect

---

## 7. Proposed Shared Components

Create semantic composables in a dedicated component file, for example:

```text
ui/components/NrButtons.kt
```

Suggested API shape (final naming may be adjusted):

```kotlin
@Composable
fun NrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
)

@Composable
fun NrSecondaryButton(...)

@Composable
fun NrQuietButton(...)

@Composable
fun NrDangerButton(
    ...,
    filled: Boolean = false,
)
```

Keep these composables thin wrappers over Material 3 `Button`,
`OutlinedButton`, and `TextButton`. Centralize:

- height and content padding
- weight/typography
- icon size
- disabled colors
- destructive confirmation colors
- optional full-width behavior

Do not create a large configuration-heavy button API. Four semantic variants plus
a compact flag are enough.

---

## 8. Migration Order

1. `ui/DeviceScreen.kt`
   - Connect / Connecting / Diagnosing / Switch / Disconnect
   - Update and flasher actions
2. `ui/SettingsScreen.kt`
   - firmware flasher actions
   - permission, refresh, download, flash, manual recovery
3. `ui/LogsScreen.kt`
   - overflow actions, clear confirmation, filter chips remain chips
4. `ui/HomeScreen.kt`
   - device actions, update actions
5. `ui/ModuleAvailability.kt`
   - standardize `ModuleActionFab`
6. `ui/MeshScreen.kt`, `ui/RfidScreen.kt`
   - replace custom FABs with `ModuleActionFab`
7. Audit remaining module screens for status-colored buttons and one-off sizes.

Migrate in small groups and compile after each group. Do not mass-replace blindly.

---

## 9. Rules and Constraints

- Preserve all feature logic; this is a visual/component refactor.
- Use `MaterialTheme.colorScheme` and `NrTheme.colors`; no new hard-coded colors.
- Category accent may drive primary filled buttons; status colors retain semantic meaning.
- Chips are filters, not action buttons.
- Use Toast for transient active-device switching; do not block FABs with bottom prompts.
- Keep Firmware target/firmware state scoped to the selected device.
- Keep hotplug prompt suppression behavior.
- Keep `NrAnimatedVisibility` for collapsible sections.
- Do not modify the firmware repo or protocol repo for this task.
- Do not commit unrelated untracked test artifacts.
- Prefer one commit per coherent migration group.

---

## 10. Verification Checklist

After migration:

- [ ] Primary action is visually dominant on each screen.
- [ ] Secondary actions are outlined neutral.
- [ ] Tertiary actions are text-only.
- [ ] Destructive confirmation uses filled red; ordinary destructive actions use red outline/text.
- [ ] Compact device actions have identical sizes across states.
- [ ] No FAB is covered by Snackbar/prompt.
- [ ] All module start/stop controls use `ModuleActionFab`.
- [ ] Mesh/RFID custom FABs use the shared component.
- [ ] Chips still represent filters/selections only.
- [ ] Obsidian, Black, and Daylight all render correctly.
- [ ] `:app:assembleDebug` passes.

---

## 11. Next-Session Prompt

Copy this prompt into the next session:

```text
We are continuing NRSuite Android UI work.

Repository:
/home/twp81x/Documents/Tools/DeepSeek/NRSuite-App-Project/android-app

Branch:
feature/ui-redesign

Before editing, read:
- android-app/docs/UI-HANDOFF-BUTTON-SYSTEM.md
- NRSUITE-UI-DESIGN-GUIDE.md at the project root
- ui/theme/Theme.kt, Color.kt, Type.kt
- ui/components/NrFilterChip.kt
- ui/components/RoundedTabRow.kt
- ui/ModuleAvailability.kt
- ui/DeviceScreen.kt, SettingsScreen.kt, LogsScreen.kt, HomeScreen.kt

Task:
Implement a semantic button/action system for the current NRSuite Android UI and migrate the core screens to it.

Required hierarchy:
- Primary: filled category accent
- Secondary: outlined neutral
- Tertiary: text/quiet
- Destructive: red outline normally, filled red only for final confirmation
- Status actions: start accent, stop red, pause amber, resume accent
- Compact row actions: consistent 32dp height, ~96-108dp min width, small typography

Create shared components, preferably in ui/components/NrButtons.kt:
- NrPrimaryButton
- NrSecondaryButton
- NrQuietButton
- NrDangerButton
- Compact variants

Migrate in this order:
1. DeviceScreen
2. SettingsScreen
3. LogsScreen
4. HomeScreen
5. ModuleAvailability / ModuleActionFab
6. MeshScreen and RfidScreen custom FABs
7. Audit remaining one-off button colors/sizes

Implementation rules:
- Preserve all feature and protocol behavior.
- Use MaterialTheme.colorScheme and NrTheme.colors; do not add hard-coded colors.
- Chips remain filters, not actions.
- Do not modify firmware or protocol repos.
- Do not commit test-artifacts/NRSuite-older-beta-1.0.0-beta.2.2-debug.apk.
- Prefer focused commits.

Build command:
cd /home/twp81x/Documents/Tools/DeepSeek/NRSuite-App-Project/android-app
GRADLE_USER_HOME="$PWD/.gradle-agent" JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline --console=plain

Acceptance:
- responsive/compact action buttons remain fixed-size across states
- destructive styling is consistent
- all module start/stop controls use ModuleActionFab
- Mesh/RFID use the shared FAB
- all three themes render correctly
- assembleDebug passes
- report changed files, migration status, and APK SHA256
```
