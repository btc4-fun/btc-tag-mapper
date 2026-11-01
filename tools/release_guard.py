"""Fail-closed source/version policy shared by release CI and APK validation."""
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def read_version(root=ROOT):
    text = (Path(root) / 'app/build.gradle').read_text()
    names = re.findall(r"\bversionName\s+['\"]([0-9]+\.[0-9]+\.[0-9]+)['\"]", text)
    codes = re.findall(r'\bversionCode\s+(\d+)\b', text)
    if len(names) != 1 or len(codes) != 1 or int(codes[0]) <= 0:
        raise ValueError('Ambiguous or invalid Android version')
    return names[0], int(codes[0])


def validate_source(root, event, ref):
    version, _ = read_version(root)
    tag = 'v' + version
    if not ((event == 'push' and ref == 'refs/tags/' + tag) or
            (event == 'workflow_dispatch' and ref == 'refs/heads/main')):
        raise ValueError('Release requires matching version tag or manual main')

    def git(*args):
        try:
            return subprocess.check_output(['git', '-C', str(root), *args],
                                           stderr=subprocess.PIPE, text=True).strip()
        except subprocess.CalledProcessError as exc:
            raise ValueError('Release source trust verification failed') from exc

    head = git('rev-parse', 'HEAD^{commit}')
    git('merge-base', '--is-ancestor', head, 'refs/remotes/origin/main')
    if event == 'push' and git('rev-parse', ref + '^{commit}') != head:
        raise ValueError('Release tag does not point to checked out commit')
    return tag


if __name__ == '__main__':
    print(validate_source(ROOT, os.environ['GITHUB_EVENT_NAME'], os.environ['GITHUB_REF']))
