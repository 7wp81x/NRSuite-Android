import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('native_updater', ROOT / 'scripts/update_native_nrsuite.py')
u = importlib.util.module_from_spec(spec)
spec.loader.exec_module(u)
PROFILE = u.load_profile(ROOT / 'scripts/native-update-profile.json')
CERT = PROFILE['certificate_sha256']

class UpdaterTests(unittest.TestCase):
    def setUp(self):
        directory = ROOT / 'build/native-updater-test-fixtures'
        directory.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=directory)
        self.root = Path(self.temp.name)

    def tearDown(self):
        # Git worktrees are unregistered explicitly by the tests that create them.
        fixture_root = (ROOT / 'build/native-updater-test-fixtures').resolve()
        assert self.root.resolve().is_relative_to(fixture_root) and self.root.resolve() != fixture_root
        self.temp.cleanup()

    def repository(self):
        repo = self.root / 'repo'
        repo.mkdir()
        u.command(['git', 'init', '-q', repo])
        u.git(repo, 'config', 'user.name', 'Updater Fixture')
        u.git(repo, 'config', 'user.email', 'fixture@example.invalid')
        u.git(repo, 'config', 'commit.gpgsign', 'false')
        (repo / 'app.txt').write_text('base\n', encoding='utf-8')
        u.git(repo, 'add', 'app.txt')
        u.git(repo, 'commit', '-q', '-m', 'fixture base')
        base = u.text(u.git(repo, 'rev-parse', 'HEAD'))
        return repo, base

    def contribution(self, repo):
        (repo / 'app.txt').write_text('native feature\n', encoding='utf-8')
        (repo / 'phone.txt').write_text('non-root and NetHunter\n', encoding='utf-8')
        u.git(repo, 'add', '.')
        u.git(repo, 'commit', '-q', '-m', 'fixture native contribution')
        return u.text(u.git(repo, 'rev-parse', 'HEAD'))

    def test_latest_release_is_by_publish_date_and_can_include_beta(self):
        releases = [
            {'tag_name': 'v9', 'published_at': '2026-10-01T00:00:00Z', 'prerelease': False},
            {'tag_name': 'v10-beta', 'published_at': '2026-11-01T00:00:00Z', 'prerelease': True},
            {'tag_name': 'v11', 'published_at': '2026-11-02T00:00:00Z', 'draft': True},
        ]
        self.assertEqual(u.newest_release(releases), 'v10-beta')
        self.assertEqual(u.newest_release(releases, True), 'v9')

    def test_release_tags_cannot_be_options_or_paths(self):
        for tag in ['--upload-pack=x', '../escape', 'v1/../../x', 'v1\ncommand', 'v1;echo', '']:
            with self.subTest(tag=tag), self.assertRaises(u.UpdateError):
                u.validate_tag(tag)

    def test_missing_keystore_is_never_generated(self):
        missing = self.root / 'missing.keystore'
        with self.assertRaisesRegex(u.UpdateError, 'no replacement'):
            u.require_existing_key(missing)
        self.assertFalse(missing.exists())

    def test_signer_parser_requires_one_certificate(self):
        self.assertEqual(u.signer_fingerprint('Signer #1 certificate SHA-256 digest: ' + CERT + '\n'), CERT)
        for value in ['', 'Signer #1 certificate SHA-256 digest: '+CERT+'\nSigner #2 certificate SHA-256 digest: '+CERT+'\n']:
            with self.assertRaises(u.UpdateError):
                u.signer_fingerprint(value)

    def test_apply_preserves_original_and_does_not_create_a_commit(self):
        repo, base = self.repository()
        patch = self.contribution(repo)
        target = self.root / 'worktree'
        u.git(repo, 'worktree', 'add', '--detach', target, base)
        try:
            result = u.apply_commits(repo, target, [patch])
            self.assertEqual(result[0]['result'], 'applied-to-index-without-commit')
            self.assertEqual(u.text(u.git(target, 'rev-parse', 'HEAD')), base)
            self.assertEqual(u.text(u.git(repo, 'rev-parse', 'HEAD')), patch)
            self.assertEqual((target / 'phone.txt').read_text(), 'non-root and NetHunter\n')
            self.assertTrue(u.text(u.git(target, 'diff', '--cached', '--name-only')))
            self.assertFalse(u.text(u.git(target, 'diff', '--name-only')))
        finally:
            assert target.resolve().is_relative_to(self.root.resolve()) and target.resolve() != self.root.resolve()
            u.git(repo, 'worktree', 'remove', '--force', target)

    def test_already_integrated_commit_is_not_applied_twice(self):
        repo, _ = self.repository()
        patch = self.contribution(repo)
        self.assertEqual(u.apply_commits(repo, repo, [patch])[0]['result'], 'already-in-history')
        self.assertFalse(u.text(u.git(repo, 'status', '--porcelain')))

    def test_conflict_is_retained_without_overwriting_original(self):
        repo, base = self.repository()
        patch = self.contribution(repo)
        target = self.root / 'worktree'
        u.git(repo, 'worktree', 'add', '--detach', target, base)
        try:
            (target / 'app.txt').write_text('upstream changed same line\n')
            u.git(target, 'add', 'app.txt')
            u.git(target, 'commit', '-q', '-m', 'fixture upstream change')
            with self.assertRaises(u.PatchConflict):
                u.apply_commits(repo, target, [patch])
            self.assertTrue(u.text(u.git(target, 'ls-files', '--unmerged')))
            self.assertEqual((repo / 'app.txt').read_text(), 'native feature\n')
        finally:
            assert target.resolve().is_relative_to(self.root.resolve()) and target.resolve() != self.root.resolve()
            u.git(repo, 'worktree', 'remove', '--force', target)

    def test_resume_rejects_changed_base(self):
        repo, base = self.repository()
        self.contribution(repo)
        with self.assertRaisesRegex(u.UpdateError, 'HEAD changed'):
            u.assert_resumable(repo, repo, base)

    def fake_apk(self, package=None, code=10, classes=True):
        directory = self.root / 'app/build/outputs/apk/debug'
        directory.mkdir(parents=True)
        metadata = {'applicationId': package or PROFILE['expected_application_id'],
                    'elements': [{'filters': [], 'outputFile': 'app-debug.apk', 'versionCode': code, 'versionName': 'fixture'}]}
        (directory / 'output-metadata.json').write_text(json.dumps(metadata))
        with zipfile.ZipFile(directory / 'app-debug.apk', 'w') as archive:
            content = b''.join(('Lcom/swp81x/nrsuite/'+n+';').encode() for n in PROFILE['required_dex_classes']) if classes else b'missing'
            archive.writestr('classes.dex', content)
        return directory

    def verify_fixture(self, cert=CERT, floor=10):
        result = subprocess.CompletedProcess([], 0, ('Signer #1 certificate SHA-256 digest: '+cert+'\n').encode(), b'')
        with mock.patch.object(u, 'command', return_value=result):
            return u.verify_apk(self.root, PROFILE, 'java', 'apksigner.jar', {}, floor)

    def test_apk_certificate_package_and_features_are_verified(self):
        self.fake_apk()
        _, result = self.verify_fixture()
        self.assertTrue(all(result['required_classes'].values()))
        self.assertEqual(result['signer_certificate_sha256'], CERT)

    def test_wrong_certificate_is_rejected(self):
        self.fake_apk()
        with self.assertRaisesRegex(u.UpdateError, 'Signing certificate mismatch'):
            self.verify_fixture('0'*64)

    def test_wrong_package_is_rejected(self):
        self.fake_apk(package='com.swp81x.nrsuite')
        with self.assertRaisesRegex(u.UpdateError, 'package ID'):
            self.verify_fixture()

    def test_version_downgrade_is_rejected(self):
        self.fake_apk(code=10)
        with self.assertRaisesRegex(u.UpdateError, 'backwards'):
            self.verify_fixture(floor=11)

    def test_missing_non_root_or_nethunter_classes_is_rejected(self):
        self.fake_apk(classes=False)
        with self.assertRaisesRegex(u.UpdateError, 'lacks expected'):
            self.verify_fixture()

    def test_reports_require_policy_tests_and_no_failures(self):
        results = self.root / 'app/build/test-results/testDebugUnitTest'
        results.mkdir(parents=True)
        lint = self.root / 'app/build/reports'
        lint.mkdir(parents=True)
        (lint / 'lint-results-debug.xml').write_text('<issues><issue severity="Warning"/></issues>')
        report = results / 'TEST-policy.xml'
        report.write_text('<testsuite name="x.RootControlCommandsTest" tests="4" failures="0" errors="0"/>')
        self.assertEqual(u.reports(self.root)['native_policy_tests'], 4)
        report.write_text('<testsuite name="x.RootControlCommandsTest" tests="4" skipped="1" failures="0" errors="0"/>')
        with self.assertRaises(u.UpdateError):
            u.reports(self.root)
        report.write_text('<testsuite name="x.RootControlCommandsTest" tests="4" failures="1" errors="0"/>')
        with self.assertRaises(u.UpdateError):
            u.reports(self.root)

    def test_verification_failure_never_promotes_an_apk(self):
        repo, base = self.repository()
        source_root = self.root / 'sources'
        worktree = source_root / 'fixture'
        worktree.mkdir(parents=True)
        output = self.root / 'outputs'
        manifest = output / 'runs/fixture/run.json'
        key = self.root / 'existing.keystore'
        key.write_bytes(b'fixture, not a real key')
        state = {'repository': str(repo.resolve()), 'patch_commits': PROFILE['patch_commits'],
                 'worktree': str(worktree.resolve()), 'output_root': str(output.resolve()),
                 'upstream_commit': base, 'tag': 'vfixture', 'run_id': 'fixture', 'manifest': str(manifest), 'status': 'prepared'}
        u.save(manifest, state)
        with mock.patch.object(u, 'assert_resumable'), \
             mock.patch.object(u, 'java_home', return_value=(self.root, 'java')), \
             mock.patch.object(u, 'android_sdk', return_value=(self.root, 'apksigner.jar')), \
             mock.patch.object(u, 'source_digest', return_value='1'*64), \
             mock.patch.object(u, 'build'), mock.patch.object(u, 'reports', return_value={}), \
             mock.patch.object(u, 'verify_apk', side_effect=u.UpdateError('Signing certificate mismatch')):
            code = u.main(['--repo', str(repo), '--resume', str(manifest), '--keystore', str(key),
                           '--worktree-root', str(source_root), '--output-root', str(output)])
        self.assertEqual(code, 2)
        self.assertEqual(json.loads(manifest.read_text())['status'], 'failed')
        self.assertFalse((output / 'artifacts').exists())
        self.assertFalse((output / 'latest-success.json').exists())
        self.assertEqual(key.read_bytes(), b'fixture, not a real key')

    def test_rejected_foreign_resume_never_rewrites_its_report_or_logs(self):
        repo, base = self.repository()
        source_root = self.root / 'sources'
        source_root.mkdir()
        foreign = self.root / 'foreign-worktree'
        foreign.mkdir()
        output = self.root / 'outputs'
        manifest = output / 'runs/foreign/run.json'
        key = self.root / 'existing.keystore'
        key.write_bytes(b'fixture, not a real key')
        state = {'repository': str(self.root / 'another-repo'), 'patch_commits': PROFILE['patch_commits'],
                 'worktree': str(foreign), 'output_root': str(output), 'upstream_commit': base,
                 'tag': 'vfixture', 'manifest': str(manifest), 'status': 'prepared'}
        u.save(manifest, state)
        original = manifest.read_bytes()
        result = u.main(['--repo', str(repo), '--resume', str(manifest), '--keystore', str(key),
                         '--worktree-root', str(source_root), '--output-root', str(output)])
        self.assertEqual(result, 2)
        self.assertEqual(manifest.read_bytes(), original)
        self.assertFalse((foreign / 'logs').exists())

    def test_resume_applies_later_commits_after_staged_conflict_resolution(self):
        repo, base = self.repository()
        first = self.contribution(repo)
        (repo / 'later.txt').write_text('later fix must not be skipped\n')
        u.git(repo, 'add', 'later.txt')
        u.git(repo, 'commit', '-q', '-m', 'fixture later fix')
        second = u.text(u.git(repo, 'rev-parse', 'HEAD'))
        target = self.root / 'worktree'
        u.git(repo, 'worktree', 'add', '--detach', target, base)
        try:
            (target / 'app.txt').write_text('upstream changed line\n')
            u.git(target, 'add', 'app.txt')
            u.git(target, 'commit', '-q', '-m', 'fixture upstream')
            state = {'status': 'applying', 'patch_cursor': 0, 'patch_results': []}
            manifest = self.root / 'run.json'
            profile = {'patch_commits': [first, second]}
            with self.assertRaises(u.PatchConflict):
                u.continue_patches(repo, target, profile, state, manifest)
            state['status'] = 'conflict'
            self.assertEqual(state['patch_cursor'], 0)
            (target / 'app.txt').write_text('reviewed merge\n')
            u.git(target, 'add', 'app.txt')
            u.continue_patches(repo, target, profile, state, manifest)
            self.assertEqual(state['patch_cursor'], 2)
            self.assertEqual(state['patch_results'][0]['result'], 'manual-resolution-staged')
            self.assertEqual((target / 'later.txt').read_text(), 'later fix must not be skipped\n')
        finally:
            assert target.resolve().is_relative_to(self.root.resolve())
            u.git(repo, 'worktree', 'remove', '--force', target)

    def test_output_lock_blocks_concurrent_runs_and_does_not_remove_another_owner(self):
        first = u.OutputLock(self.root / 'output')
        second = u.OutputLock(self.root / 'output')
        first.acquire()
        with self.assertRaises(u.UpdateError):
            second.acquire()
        first.release()
        second.acquire()
        self.assertTrue(second.path.exists())
        second.path.write_text(json.dumps({'token': 'another-owner'}))
        second.release()
        self.assertTrue(second.path.exists())

    def test_sdk37_scheme_labels_preserve_same_key_and_reject_mixed_certificates(self):
        v2 = 'V2 Signer: certificate SHA-256 digest: ' + CERT + '\n'
        v3 = 'V3 Signer: certificate SHA-256 digest: ' + CERT + '\n'
        self.assertEqual(u.signer_fingerprint(v2), CERT)
        self.assertEqual(u.signer_fingerprint(v2 + v3), CERT)
        with self.assertRaises(u.UpdateError):
            u.signer_fingerprint(v2 + 'V3 Signer: certificate SHA-256 digest: ' + '0'*64 + '\n')

if __name__ == '__main__':
    unittest.main()
