"""Release policy tests use disposable Git histories, not GitHub credentials."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('release_guard', ROOT / 'tools/release_guard.py')
guard = None
if SPEC and SPEC.loader and (ROOT / 'tools/release_guard.py').exists():
    guard = importlib.util.module_from_spec(SPEC)
    SPEC.loader.exec_module(guard)


class TrustedSource(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(guard, 'Missing executable release source guard')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        self.git('init', '-b', 'main')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'Release fixture')
        (self.repo / 'app').mkdir()
        (self.repo / 'app/build.gradle').write_text("versionCode 3; versionName '1.0.0'\n")
        self.git('add', '.')
        self.git('commit', '-m', 'fixture')
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD')
        self.git('tag', 'v1.0.0')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args], stderr=subprocess.STDOUT, text=True).strip()

    def check(self, event='push', ref='refs/tags/v1.0.0'):
        return guard.validate_source(self.repo, event, ref)

    def test_optional_provisioning_version(self):
        self.assertEqual(guard.read_version(ROOT), ('1.1.0', 4))

    def test_reachable_matching_tag_allowed(self):
        self.assertEqual(self.check(), 'v1.0.0')
        self.git('tag', '-d', 'v1.0.0')
        self.git('tag', '-a', 'v1.0.0', '-m', 'annotated')
        self.assertEqual(self.check(), 'v1.0.0')

    def test_main_manual_allowed(self):
        self.assertEqual(self.check('workflow_dispatch', 'refs/heads/main'), 'v1.0.0')

    def test_events_and_refs_fail_closed(self):
        for event, ref in [('pull_request', 'refs/tags/v1.0.0'), ('push', 'refs/heads/main'),
                           ('workflow_dispatch', 'refs/heads/topic'), ('push', 'refs/tags/v1.0.1'),
                           ('push', 'refs/tags/v1.0.0-rc1'), ('push', 'refs/tags/v1.0.0\n')]:
            with self.subTest(event=event, ref=ref), self.assertRaises(ValueError):
                self.check(event, ref)

    def test_tag_off_main_rejected(self):
        self.git('checkout', '-b', 'topic')
        self.git('commit', '--allow-empty', '-m', 'off-main')
        self.git('tag', '-f', 'v1.0.0')
        with self.assertRaises(ValueError):
            self.check()

    def test_tag_must_point_to_checked_out_commit(self):
        self.git('commit', '--allow-empty', '-m', 'new main')
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD')
        with self.assertRaises(ValueError):
            self.check()

    def test_missing_main_rejected(self):
        self.git('update-ref', '-d', 'refs/remotes/origin/main')
        with self.assertRaises(ValueError):
            self.check()


if __name__ == '__main__':
    unittest.main()
