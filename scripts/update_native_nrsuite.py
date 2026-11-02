"""Build the custom NRSuite preview on an upstream release without replacing live state.

Python 3.10+, Git, GitHub CLI, JDK and Android SDK. No third-party Python packages.
No private key/password is copied into a worktree or recorded in build reports.
"""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
import zipfile

class UpdateError(RuntimeError):
    pass

class PatchConflict(UpdateError):
    pass

def command(args, *, cwd=None, env=None, data=None, check=True):
    result = subprocess.run([str(x) for x in args], cwd=cwd, env=env,
                            input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and result.returncode:
        detail = result.stderr.decode('utf-8', errors='replace').strip()
        raise UpdateError(f'{Path(str(args[0])).name} failed ({result.returncode}): {detail[-2500:]}')
    return result

def git(repo, *args, **kwargs):
    return command(['git', '-C', repo, *args], **kwargs)

def text(result):
    return result.stdout.decode('utf-8', errors='replace').strip()

def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)

def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()

def validate_tag(tag):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', tag) or '..' in tag:
        raise UpdateError('Release tag must contain only letters, digits, dots, underscores or hyphens.')
    return tag

def load_profile(path):
    profile = json.loads(path.read_text(encoding='utf-8'))
    if profile.get('schema') != 1:
        raise UpdateError('Unsupported updater profile schema.')
    for item in profile.get('patch_commits', []):
        if not re.fullmatch(r'[a-f0-9]{40}', item):
            raise UpdateError('Patch commits must be pinned full Git commit IDs.')
    if not profile.get('patch_commits') or not re.fullmatch(r'[a-f0-9]{64}', profile.get('certificate_sha256', '')):
        raise UpdateError('Profile requires pinned patch commits and a public signing-certificate SHA-256.')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', profile['upstream_repository']):
        raise UpdateError('Invalid upstream repository name.')
    return profile

def newest_release(releases, stable_only=False):
    eligible = [r for r in releases if not r.get('draft') and r.get('published_at')
                and (not stable_only or not r.get('prerelease'))]
    if not eligible:
        raise UpdateError('No published release matches the selection. Specify --tag if needed.')
    return validate_tag(max(eligible, key=lambda r: r['published_at'])['tag_name'])

def release_tag(profile, explicit, stable_only):
    if explicit:
        return validate_tag(explicit)
    if not shutil.which('gh'):
        raise UpdateError('GitHub CLI is needed for automatic release selection; use --tag to select explicitly.')
    result = command(['gh', 'api', '--method', 'GET',
                      'repos/' + profile['upstream_repository'] + '/releases', '-f', 'per_page=100'])
    return newest_release(json.loads(result.stdout), stable_only)

def require_existing_key(path):
    if not path.is_file():
        raise UpdateError(f'Existing signing keystore is missing: {path}. Restore the original key; no replacement will be generated.')
    return path

def absolute_directory(value, description):
    path = Path(value)
    if not path.is_absolute():
        raise UpdateError(description + ' must be an absolute path.')
    return path.resolve()

def java_home(explicit):
    candidates = [Path(explicit)] if explicit else []
    if not explicit:
        if os.environ.get('JAVA_HOME'):
            candidates.append(Path(os.environ['JAVA_HOME']))
        candidates.extend(sorted((Path.home() / '.gradle/jdks').glob('*21*')))
    for candidate in candidates:
        executable = candidate / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
        if not executable.is_file():
            continue
        result = command([executable, '-version'], check=False)
        version = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        if result.returncode == 0 and (explicit or re.search(r'version "21[.\"]', version)):
            return candidate.resolve(), executable.resolve()
    raise UpdateError('JDK 21 was not found. Set --java-home to a compatible installed JDK.')

def android_sdk(explicit):
    candidates = [Path(explicit)] if explicit else [Path(v) for v in
        [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT')] if v]
    if not explicit and os.environ.get('LOCALAPPDATA'):
        candidates.append(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk')
    for candidate in candidates:
        jars = list((candidate / 'build-tools').glob('*/lib/apksigner.jar'))
        if jars:
            def version(path):
                return tuple(int(n) for n in re.findall(r'\d+', path.parents[1].name))
            return candidate.resolve(), max(jars, key=version).resolve()
    raise UpdateError('Android SDK with apksigner.jar was not found. Set --android-sdk.')

def prepare_worktree(repo, profile, tag, worktree):
    reference = 'refs/native-updater/' + uuid.uuid4().hex
    git(repo, 'fetch', '--no-tags', '--no-write-fetch-head', profile['upstream_git_url'],
        f'refs/tags/{tag}:{reference}')
    base = text(git(repo, 'rev-parse', reference + '^{commit}'))
    for commit in profile['patch_commits']:
        if git(repo, 'cat-file', '-e', commit + '^{commit}', check=False).returncode:
            git(repo, 'fetch', '--no-tags', '--no-write-fetch-head', profile['patch_git_url'], commit)
    git(repo, 'worktree', 'add', '--detach', worktree, base)
    return base

def apply_commits(repo, worktree, commits, on_progress=None):
    outcomes = []
    for index, commit in enumerate(commits):
        if on_progress:
            on_progress(index, outcomes)
        if git(worktree, 'merge-base', '--is-ancestor', commit, 'HEAD', check=False).returncode == 0:
            outcomes.append({'commit': commit, 'result': 'already-in-history'})
            if on_progress:
                on_progress(index + 1, outcomes)
            continue
        payload = git(repo, 'diff', '--binary', commit + '^', commit).stdout
        if git(worktree, 'apply', '--reverse', '--check', '-', data=payload, check=False).returncode == 0:
            outcomes.append({'commit': commit, 'result': 'already-applied'})
            if on_progress:
                on_progress(index + 1, outcomes)
            continue
        result = git(worktree, 'apply', '--3way', '--index', '-', data=payload, check=False)
        if result.returncode:
            unmerged = text(git(worktree, 'diff', '--name-only', '--diff-filter=U'))
            if unmerged:
                raise PatchConflict('Resolve and stage conflicts in the retained worktree, then use --resume. Files: ' + unmerged.replace('\n', ', '))
            raise UpdateError('Patch did not apply. Worktree retained; inspect Git status. ' + result.stderr.decode('utf-8', errors='replace')[-1200:])
        outcomes.append({'commit': commit, 'result': 'applied-to-index-without-commit'})
        if on_progress:
            on_progress(index + 1, outcomes)
    return outcomes

def continue_patches(repo, worktree, profile, state, manifest):
    commits = profile['patch_commits']
    cursor = state.get('patch_cursor')
    if cursor is None:
        # Prepared/building records from the first script revision had completed replay.
        recorded = len(state.get('patch_results', []))
        cursor = recorded or (len(commits) if state.get('status') in ('prepared', 'building') else 0)
    if not isinstance(cursor, int) or not 0 <= cursor <= len(commits):
        raise UpdateError('Invalid patch progress in resume report.')
    prefix = list(state.get('patch_results', []))
    if state.get('status') == 'conflict':
        if cursor >= len(commits):
            raise UpdateError('Conflict record has no pending patch.')
        prefix.append({'commit': commits[cursor], 'result': 'manual-resolution-staged'})
        cursor += 1
    def progress(index, outcomes):
        state.update({'status': 'applying', 'patch_cursor': cursor + index,
                      'patch_results': prefix + list(outcomes)})
        save(manifest, state)
    remaining = apply_commits(repo, worktree, commits[cursor:], progress)
    state['patch_cursor'] = len(commits)
    state['patch_results'] = prefix + remaining
    save(manifest, state)

def assert_resumable(repo, worktree, base):
    if not worktree.is_dir() or text(git(worktree, 'rev-parse', 'HEAD')) != base:
        raise UpdateError('Resume worktree is missing or its upstream HEAD changed.')
    original = text(git(repo, 'rev-parse', '--path-format=absolute', '--git-common-dir'))
    resumed = text(git(worktree, 'rev-parse', '--path-format=absolute', '--git-common-dir'))
    if Path(original).resolve() != Path(resumed).resolve():
        raise UpdateError('Resume worktree belongs to another repository.')
    if text(git(worktree, 'ls-files', '--unmerged')):
        raise PatchConflict('Unmerged files remain. Resolve and git add them before --resume.')
    if text(git(worktree, 'diff', '--name-only')):
        raise UpdateError('Unstaged source edits remain. Review and git add resolved edits before --resume.')

def source_digest(worktree):
    paths = [p for p in (worktree / 'app/src').rglob('*') if p.is_file()]
    for rel in ['app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties']:
        candidate = worktree / rel
        if candidate.is_file():
            paths.append(candidate)
    paths.extend(p for p in (worktree / 'gradle').rglob('*') if p.is_file())
    digest = hashlib.sha256()
    for path in sorted(paths):
        digest.update(path.relative_to(worktree).as_posix().encode())
        digest.update(bytes.fromhex(sha256(path)))
    return digest.hexdigest()

def signer_fingerprint(output):
    # Build Tools 36 uses Signer #1; 37 uses V2 Signer / V3 Signer labels.
    matches = re.findall(r'^(Signer #\d+|V\d(?:\.\d+)? Signer(?: #\d+)?):?\s+certificate SHA-256 digest:\s*([a-fA-F0-9:]+)\s*$', output, re.M)
    certificates = set()
    for label, digest in matches:
        number = re.search(r'#(\d+)', label)
        if number and int(number.group(1)) != 1:
            raise UpdateError('Expected one APK signer, not multiple signer identities.')
        certificates.add(digest.replace(':', '').lower())
    if len(certificates) != 1:
        raise UpdateError('Expected one unique APK signing certificate across signature schemes.')
    return certificates.pop()

def verify_apk(worktree, profile, java, apksigner, environment, floor):
    directory = worktree / 'app/build/outputs/apk/debug'
    metadata = json.loads((directory / 'output-metadata.json').read_text(encoding='utf-8'))
    if metadata.get('applicationId') != profile['expected_application_id']:
        raise UpdateError('APK package ID changed; refusing an incompatible update.')
    entries = metadata.get('elements', [])
    if len(entries) != 1 or entries[0].get('filters'):
        raise UpdateError('Expected one universal debug APK; split builds need manual review.')
    item = entries[0]
    if int(item['versionCode']) < floor:
        raise UpdateError('Version code would go backwards relative to the pinned/latest successful build.')
    apk = (directory / item['outputFile']).resolve()
    if not apk.is_relative_to(directory.resolve()) or not apk.is_file():
        raise UpdateError('APK path is missing or escapes its output directory.')
    result = command([java, '-jar', apksigner, 'verify', '--print-certs', apk], env=environment)
    fingerprint = signer_fingerprint(result.stdout.decode('utf-8', errors='replace'))
    if fingerprint != profile['certificate_sha256']:
        raise UpdateError('Signing certificate mismatch. Build quarantined; use the original keystore, not a new key.')
    with zipfile.ZipFile(apk) as archive:
        dex = b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes[0-9]*\.dex', n))
    classes = {name: ('Lcom/swp81x/nrsuite/' + name + ';').encode() in dex
               for name in profile['required_dex_classes']}
    if not all(classes.values()):
        raise UpdateError('APK lacks expected non-root or NetHunter classes; manual port review required.')
    return apk, {'application_id': metadata['applicationId'], 'version_code': int(item['versionCode']),
                 'version_name': item['versionName'], 'signer_certificate_sha256': fingerprint,
                 'required_classes': classes}

def reports(worktree):
    files = list((worktree / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    if not files:
        raise UpdateError('No JUnit result reports found.')
    suites = [ET.parse(p).getroot() for p in files]
    summary = {k: sum(int(s.get(k, 0)) for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
    policy = sum(int(s.get('tests', 0)) - int(s.get('skipped', 0)) for s in suites if s.get('name', '').endswith('RootControlCommandsTest'))
    if summary['failures'] or summary['errors'] or policy < 4:
        raise UpdateError('Unit tests failed or native-control policy tests are missing.')
    lint = ET.parse(worktree / 'app/build/reports/lint-results-debug.xml').getroot()
    severities = {}
    for issue in lint.findall('issue'):
        level = issue.get('severity', 'Unknown')
        severities[level] = severities.get(level, 0) + 1
    if severities.get('Error', 0) or severities.get('Fatal', 0):
        raise UpdateError('Android lint errors/fatals remain.')
    return {'unit_tests': summary, 'native_policy_tests': policy, 'lint': severities,
            'device_acceptance': 'Not performed by this build script. Test on target phones before relying on new-release behavior.'}

INIT_SCRIPT = r"""
gradle.beforeProject { project ->
    project.plugins.withId('com.android.application') {
        project.extensions.getByName('androidComponents').finalizeDsl { android ->
            def signing = android.signingConfigs.getByName('debug')
            signing.storeFile = new File(System.getenv('NRSUITE_EXISTING_KEYSTORE'))
            def preview = android.buildTypes.getByName('debug')
            preview.signingConfig = signing
            preview.applicationIdSuffix = System.getenv('NRSUITE_APPLICATION_SUFFIX')
            preview.versionNameSuffix = System.getenv('NRSUITE_VERSION_SUFFIX')
        }
    }
}
"""

def build(worktree, java, environment, init_script, log):
    wrapper = worktree / 'gradle/wrapper/gradle-wrapper.jar'
    if not wrapper.is_file():
        raise UpdateError('Upstream Gradle wrapper is missing.')
    args = [str(java), '-Dorg.gradle.appname=gradlew', '-classpath', str(wrapper),
            'org.gradle.wrapper.GradleWrapperMain', ':app:assembleDebug', ':app:testDebugUnitTest',
            ':app:lintDebug', '--no-daemon', '-Pkotlin.compiler.execution.strategy=in-process',
            '--console=plain', '--init-script', str(init_script)]
    with log.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(args, cwd=worktree, env=environment, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
        try:
            for line in process.stdout:
                print(line, end='', flush=True)
                output.write(line)
            code = process.wait()
        except BaseException:
            process.terminate()
            raise
    if code:
        raise UpdateError(f'Gradle build/tests/lint failed ({code}); see {log}.')

def worktree_records(worktree, state):
    directory = worktree / 'logs'
    directory.mkdir(exist_ok=True)
    state_file = directory / 'project-state.md'
    if not state_file.exists():
        state_file.write_text('# Update worktree state\n\nCreated by the NRSuite native updater.\n'
            f'Upstream release: {state["tag"]}\nUpstream commit: {state["upstream_commit"]}\n'
            'Native feature patches are applied to the index without creating commits.\n'
            f'Canonical build report: {state["manifest"]}\n'
            'Device acceptance is pending for this build. Original project records remain authoritative.\n', encoding='utf-8')
    history = directory / 'conversation-log.md'
    if not history.exists():
        history.write_text('# Update worktree history\n\nThis is a machine build record, not an invented conversation transcript.\n', encoding='utf-8')
    with history.open('a', encoding='utf-8') as stream:
        stream.write(f'\n- {dt.datetime.now(dt.timezone.utc).isoformat()}: {state["status"]}; report {state["manifest"]}\n')

class OutputLock:
    """One run per output root; stale locks require deliberate inspection."""
    def __init__(self, root):
        self.path = root / 'updater.lock'
        self.token = uuid.uuid4().hex
        self.owned = False

    def acquire(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        try:
            descriptor = os.open(self.path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        except FileExistsError:
            raise UpdateError(f'Another run or interrupted-run lock exists: {self.path}. Inspect its PID before deliberately removing a stale lock.')
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            json.dump({'pid': os.getpid(), 'token': self.token,
                       'started_at': dt.datetime.now(dt.timezone.utc).isoformat()}, stream)
        self.owned = True

    def release(self):
        if not self.owned:
            return
        try:
            current = json.loads(self.path.read_text(encoding='utf-8'))
            if current.get('token') == self.token:
                self.path.unlink()
        except (FileNotFoundError, ValueError):
            pass
        self.owned = False

def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, 'reconfigure'):
            stream.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', help='Exact upstream release tag. Default: newest published release, including beta/prerelease.')
    parser.add_argument('--stable-only', action='store_true', help='Exclude prereleases from automatic selection.')
    parser.add_argument('--prepare-only', action='store_true', help='Fetch and apply source, then stop before Gradle.')
    parser.add_argument('--resume', type=Path, help='Resume a prepared/conflicted/failed run after reviewing and staging resolutions.')
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--profile', type=Path, default=Path(__file__).with_name('native-update-profile.json'))
    parser.add_argument('--keystore', type=Path, default=Path.home() / '.android/debug.keystore')
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--android-sdk', type=Path)
    parser.add_argument('--worktree-root', type=Path, default=Path('G:/23/NRSuite-native-updates'))
    parser.add_argument('--output-root', type=Path, help='Default: <original repository>/build/native-updater')
    options = parser.parse_args(argv)
    state = None
    manifest = None
    output_lock = None
    try:
        repo = options.repo.resolve()
        if Path(text(git(repo, 'rev-parse', '--show-toplevel'))).resolve() != repo:
            raise UpdateError('--repo must point to the repository root.')
        profile = load_profile(options.profile.resolve())
        key = require_existing_key(options.keystore.resolve())
        worktree_root = absolute_directory(options.worktree_root, 'Worktree root')
        if os.name == 'nt' and not Path('G:/23').is_dir() and options.worktree_root == Path('G:/23/NRSuite-native-updates'):
            raise UpdateError('Cannot access the required project root G:\\23.')
        output_root = absolute_directory(options.output_root or repo / 'build/native-updater', 'Output root')
        output_lock = OutputLock(output_root)
        output_lock.acquire()
        if options.resume:
            resume_path = options.resume.resolve()
            candidate = json.loads(resume_path.read_text(encoding='utf-8'))
            if candidate.get('repository') != str(repo) or candidate.get('patch_commits') != profile['patch_commits']:
                raise UpdateError('Resume record does not match this repository/profile.')
            worktree = Path(candidate['worktree']).resolve()
            if (not worktree.is_relative_to(worktree_root)
                    or Path(candidate['output_root']).resolve() != output_root
                    or not resume_path.is_relative_to(output_root / 'runs')
                    or Path(candidate['manifest']).resolve() != resume_path):
                raise UpdateError('Resume paths do not match the configured worktree/output roots.')
            if candidate.get('status') == 'ready':
                if candidate.get('signer_certificate_sha256') != profile['certificate_sha256'] or candidate.get('application_id') != profile['expected_application_id']:
                    raise UpdateError('Completed run does not match the current signing/package profile.')
                artifact = Path(candidate['apk']).resolve()
                if (not artifact.is_relative_to(output_root / 'artifacts')
                        or not artifact.is_file() or sha256(artifact) != candidate['apk_sha256']):
                    raise UpdateError('Completed APK is missing, changed or outside its output root.')
                print('Already verified: ' + str(artifact))
                return 0
            # Associate mutable state only after rejecting foreign/invalid reports.
            assert_resumable(repo, worktree, candidate['upstream_commit'])
            manifest = resume_path
            state = candidate
        else:
            tag = release_tag(profile, options.tag, options.stable_only)
            run_id = tag + '-' + dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8]
            manifest = output_root / 'runs' / run_id / 'run.json'
            worktree = worktree_root / run_id
            state = {'schema': 1, 'run_id': run_id, 'tag': tag, 'repository': str(repo),
                     'output_root': str(output_root), 'worktree': str(worktree), 'manifest': str(manifest),
                     'patch_commits': profile['patch_commits'], 'patch_cursor': 0, 'patch_results': [], 'status': 'fetching'}
            save(manifest, state)
            print('Build report: ' + str(manifest), flush=True)
            worktree_root.mkdir(parents=True, exist_ok=True)
            if worktree.exists():
                raise UpdateError('Refusing to reuse an existing worktree path.')
            state['upstream_commit'] = prepare_worktree(repo, profile, tag, worktree)
            state['status'] = 'applying'
            save(manifest, state)
            worktree_records(worktree, state)
        if 'error' in state:
            state.setdefault('previous_errors', []).append(state.pop('error'))
        continue_patches(repo, worktree, profile, state, manifest)
        state['status'] = 'prepared'
        save(manifest, state)
        worktree_records(worktree, state)
        if options.prepare_only:
            print('Prepared only. Resume with: --resume "' + str(manifest) + '"')
            return 0
        home, java = java_home(options.java_home)
        sdk, apksigner = android_sdk(options.android_sdk)
        environment = os.environ.copy()
        environment.update({'JAVA_HOME': str(home), 'ANDROID_HOME': str(sdk), 'ANDROID_SDK_ROOT': str(sdk),
                            'NRSUITE_EXISTING_KEYSTORE': str(key),
                            'NRSUITE_APPLICATION_SUFFIX': profile['application_id_suffix'],
                            'NRSUITE_VERSION_SUFFIX': profile['version_name_suffix']})
        initial_key = sha256(key)
        initial_source = source_digest(worktree)
        init_script = manifest.parent / 'native-signing.init.gradle'
        init_script.write_text(INIT_SCRIPT, encoding='utf-8')
        state['status'] = 'building'
        save(manifest, state)
        build(worktree, java, environment, init_script, manifest.parent / 'build.log')
        if not key.is_file() or sha256(key) != initial_key:
            raise UpdateError('Signing keystore changed during the build; output quarantined.')
        if source_digest(worktree) != initial_source:
            raise UpdateError('Source changed during the build; output quarantined.')
        validation = reports(worktree)
        latest = output_root / 'latest-success.json'
        floor = int(profile['minimum_version_code'])
        if latest.is_file():
            previous = json.loads(latest.read_text(encoding='utf-8'))
            if previous.get('application_id') == profile['expected_application_id']:
                floor = max(floor, int(previous['version_code']))
        apk, metadata = verify_apk(worktree, profile, java, apksigner, environment, floor)
        destination = output_root / 'artifacts' / state['run_id']
        destination.mkdir(parents=True, exist_ok=False)
        final_apk = destination / ('NRSuite-' + state['tag'] + '-native-preview.apk')
        shutil.copyfile(apk, final_apk)
        digest = sha256(final_apk)
        (destination / 'SHA256SUMS.txt').write_text(digest + '  ' + final_apk.name + '\n', encoding='utf-8')
        state.update(metadata)
        state.update(validation)
        state.update({'status': 'ready', 'apk': str(final_apk), 'apk_sha256': digest,
                      'source_sha256': initial_source, 'keystore_path': str(key),
                      'completed_at': dt.datetime.now(dt.timezone.utc).isoformat()})
        save(manifest, state)
        save(destination / 'build-report.json', state)
        save(latest, {'apk': str(final_apk), 'apk_sha256': digest, **metadata, 'manifest': str(manifest)})
        worktree_records(worktree, state)
        print('\nVerified custom APK: ' + str(final_apk))
        print('SHA-256: ' + digest)
        print('Signer certificate: ' + metadata['signer_certificate_sha256'])
        print('Device acceptance is still required. Nothing was installed, committed or published.')
        return 0
    except (UpdateError, OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile, KeyboardInterrupt) as error:
        if state is not None and manifest is not None:
            state['status'] = 'conflict' if isinstance(error, PatchConflict) else 'failed'
            state['error'] = str(error) or 'Interrupted'
            save(manifest, state)
            if Path(state['worktree']).is_dir() and state.get('upstream_commit'):
                worktree_records(Path(state['worktree']), state)
        print('Updater stopped: ' + (str(error) or 'Interrupted'), file=sys.stderr)
        if manifest:
            print('Retained run report: ' + str(manifest), file=sys.stderr)
        return 2
    finally:
        if output_lock is not None:
            output_lock.release()

if __name__ == '__main__':
    raise SystemExit(main())
