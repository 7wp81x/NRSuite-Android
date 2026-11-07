# Updating the custom NRSuite build

The updater fetches a published upstream release, reapplies the pinned non-root and NetHunter contribution, builds/tests/lints it, and verifies the existing preview signing identity. It does not install APKs, commit, push, publish, or replace the current development checkout.

## Run

From any PowerShell directory:

```powershell
& 'G:\23\nrSuitte\NRSuite-Android\scripts\Update-NativeNRSuite.ps1'
```

The default selects the newest published GitHub release by publish time, including betas/prereleases. Drafts are excluded. Add `--stable-only` to exclude prereleases. GitHub CLI is required for automatic selection and uses its existing login; no token is placed in the script.

For an exact release:

```powershell
& 'G:\23\nrSuitte\NRSuite-Android\scripts\Update-NativeNRSuite.ps1' --tag 'v1.0.0-beta.3.1.2'
```

That tag is the current known baseline, not an example claim that a newer release already exists. Replace it with the desired published release tag. Python 3.10+ and Git are required. The script detects cached JDK 21 and the installed Android SDK. Override with `--java-home` and `--android-sdk` when a future release requires a different toolchain.

## Source and signing

- Worktrees are retained under `G:\23\NRSuite-native-updates\<tag-time-id>`. The source checkout and its installed/tested APK stay unchanged.
- The updater fetches the exact tag into a private Git ref and applies pinned commit `e00f866d504c1f23893c0ca8cb622de79823e5e1` using a three-way indexed patch, without creating a Git commit. Already integrated exact changes are skipped.
- The profile `scripts/native-update-profile.json` pins that contribution and the public signer certificate SHA-256. It contains no private key or password.
- The existing key defaults to the current user's `.android/debug.keystore`. `--keystore` accepts an existing alternate location for the same key. A missing key is an error; the updater does not generate or copy a replacement.
- A Gradle initialization script explicitly points debug signing at that existing key and retains the `.nativepreview` package suffix. Standard Android debug signing credentials remain managed by the Android Gradle Plugin. Custom/password-protected production keystores are outside this profile's scope.
- After building, Android `apksigner` verifies the APK and its signer must match `b8651b4b8556d0f515d93be8009d345e33e7a8a24911824fd1c46c49d8b92a44`. The file must retain the package ID `com.swp81x.nrsuite.nativepreview`, a compatible version code, and the expected non-root and NetHunter DEX classes.
- The keystore and app source are checked for changes during the build. A failed check leaves build output quarantined in the worktree; it is not promoted as a verified update.

Keep the original key available privately when moving machines. Changing the certificate pin to match a different key does not make that key compatible with an already installed app. [Android app signing](https://developer.android.com/studio/publish/app-signing), [APK signature verification](https://developer.android.com/tools/apksigner).

## Outputs and checks

Run reports and build logs are under the original repository's `build/native-updater/runs/<run-id>/`. A successful build copies its APK, SHA256SUMS.txt and build-report.json into `build/native-updater/artifacts/<run-id>/` and records latest-success.json. The manifest records the release tag, fetched commit, patch results, actual version, APK hash, signer certificate, class checks, JUnit results and lint severities. No password or key contents are recorded. These local reports contain machine paths and are not automatically published.

Build tasks are assembleDebug, testDebugUnitTest and lintDebug. JUnit failures, missing/ignored native policy tests, lint errors/fatals, signature/package mismatch, missing feature classes, and version-code rollback relative to prior script output stop the run. Existing lint warnings are reported, not silently treated as errors or hidden. Version-code comparison covers this script's recorded builds, not an independently installed APK on a phone.

APK readiness means the automated build and identity checks passed. New-release behavior still needs device testing, especially discovery permissions, root/NetHunter operations and existing ESP32 modules. This is not a guarantee that every future release will merge automatically.

## Prepare, resolve and resume

Use `--prepare-only` to fetch and apply without running Gradle. The console prints the run.json path. If a patch conflicts, the updater retains the worktree and reports the files rather than overwriting upstream changes.

1. Inspect the reported worktree with `git -C <worktree> status`.
2. Resolve the conflicting files and stage the reviewed resolutions with `git -C <worktree> add -- <files>`.
3. Resume the same run:

```powershell
& 'G:\23\nrSuitte\NRSuite-Android\scripts\Update-NativeNRSuite.ps1' --resume 'ABSOLUTE-PATH-TO-run.json'
```

There is no cherry-pick sequencer to continue, because the updater applies an indexed patch without making a commit. Resume rejects unresolved index entries, unstaged tracked changes, a changed base commit or a different Git repository. Runs sharing an output root are serialized by updater.lock. An interrupted process can leave a stale lock; inspect its recorded PID before deliberately removing that lock file. The updater never kills another run or automatically clears unknown locks. Existing worktrees are never automatically deleted. To abandon a run, leave it for inspection; deliberate cleanup can be done later after verifying the paths.

## Maintaining our patch set

The profile follows pinned commits, not uncommitted edits in the current checkout or a moving PR branch. To include later fixes, review and save those changes as Git commits and update the profile's ordered patch_commits list. If upstream independently implements or restructures the features, review the port instead of assuming duplicate code should be applied.

## Script regression checks

```powershell
Set-Location 'G:\23\nrSuitte\NRSuite-Android'
python -m unittest discover -s scripts/tests -v
```

The tests use disposable Git fixtures under this project's ignored build directory. They cover real patch replay/conflicts, duplicate integration, key absence, certificate/package/class checks, downgrade protection, result validation and rejection before output promotion. Signing parser fixtures do not replace the real apksigner check used by the build pipeline.

## Verified baseline run

On 2026-11-02 the updater fetched the latest published release, v1.0.0-beta.3.1.2, applied the pinned contribution and completed a real isolated build. All 19 updater regression tests and 130 app unit tests passed. Lint reported 102 warnings and 18 hints, with no errors/fatals. The APK passed Android signature verification with the pinned existing certificate, package and both feature-class checks. Completed-run resume returned the same hash-verified artifact without rebuilding. No new-release device acceptance or phone installation was performed.

Build Tools 37 changed the certificate output labels; the parser now accepts both legacy and scheme-specific labels while rejecting different signer identities. This behavior is covered by a regression test.

[Sanitized baseline build report](../test-artifacts/native-updater-baseline.json) records the verified identities, checks and APK checksum without private machine paths. It describes the local build, not upstream CI.
