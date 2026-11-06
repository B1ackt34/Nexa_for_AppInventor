#!/usr/bin/env python3
"""Read-only gate for this project's strict final-APK backup policy.

Uses aapt2 to read compiled manifest/resources. Never reads wallet files.
Exit 0: static policy matches; 1: absent/unsafe policy; 2: inspection failure.
Does not test actual backup/restore, signatures or SDK encryption.
"""
import argparse
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re
import subprocess

ANDROID = "http://schemas.android.com/apk/res/android:"
DOMAINS = {"root", "file", "database", "sharedpref", "external", "device_root",
           "device_file", "device_database", "device_sharedpref"}


@dataclass
class Node:
    name: str
    attrs: dict = field(default_factory=dict)
    children: list = field(default_factory=list)


def tree(dump):
    root, stack = None, []
    for line in dump.splitlines():
        indent = len(line) - len(line.lstrip())
        content = line.strip()
        if content.startswith("E: "):
            node = Node(content[3:].split()[0])
            while stack and stack[-1][0] >= indent:
                stack.pop()
            if stack:
                stack[-1][1].children.append(node)
            elif root is None:
                root = node
            else:
                raise ValueError("Multiple XML roots")
            stack.append((indent, node))
        elif content.startswith("A: "):
            match = re.fullmatch(r'A: ([^=]+?)=(.*)', content)
            if not match or not stack:
                raise ValueError("Unsupported aapt2 attribute")
            name = re.sub(r'\(0x[0-9a-fA-F]+\)$', '', match[1])
            if name.startswith(ANDROID):
                name = 'android:' + name[len(ANDROID):]
            value = match[2].split(' (Raw:', 1)[0]
            if value.startswith('"'):
                value = json.loads(value)
            if name in stack[-1][1].attrs:
                raise ValueError("Duplicate XML attribute")
            stack[-1][1].attrs[name] = value
    if root is None:
        raise ValueError("No XML root")
    return root


def resource_paths(dump):
    result, current = {}, None
    for line in dump.splitlines():
        resource = re.search(r'\bresource (0x[0-9a-fA-F]+)\s', line)
        if resource:
            current = resource[1].lower()
        file = re.search(r'\(file\)\s+(res/\S+)', line)
        if current and file:
            result.setdefault(current, set()).add(file[1])
    return result


def exclusions(node):
    if any(child.name != 'exclude' for child in node.children):
        return False
    found = set()
    for child in node.children:
        if child.attrs.get('path') != '.' or child.children:
            return False
        found.add(child.attrs.get('domain'))
    return found == DOMAINS


def verify(apk, aapt2):
    def dump(*args):
        run = subprocess.run([str(aapt2), 'dump', *args, str(apk)], capture_output=True,
                             text=True, timeout=60)
        if run.returncode:
            raise ValueError('aapt2 inspection failed; check APK and tool compatibility')
        return run.stdout

    manifest = tree(dump('xmltree', '--file', 'AndroidManifest.xml'))
    apps = [c for c in manifest.children if c.name == 'application']
    if len(apps) != 1:
        raise ValueError('Expected one application')
    attrs = apps[0].attrs
    issues = []
    if attrs.get('android:allowBackup') != 'false':
        issues.append('allowBackup must be explicitly false')
    if 'android:backupAgent' in attrs:
        issues.append('Custom backupAgent requires separate review')
    paths = resource_paths(dump('resources'))

    def policy(attr, expected):
        reference = attrs.get('android:' + attr, '').lower()
        files = paths.get(reference.removeprefix('@'), set())
        if not files:
            issues.append(attr + ': missing/unresolved XML policy')
            return
        # Verify every resource configuration, not just a default XML filename.
        for file in sorted(files):
            root = tree(dump('xmltree', '--file', file))
            if root.name != expected:
                issues.append(attr + ': unexpected XML root')
            elif expected == 'full-backup-content':
                if not exclusions(root):
                    issues.append(attr + ': all storage domains must be excluded')
            else:
                sections = {c.name: c for c in root.children}
                if (len(root.children) != 2 or set(sections) != {'cloud-backup', 'device-transfer'}
                        or not all(exclusions(section) for section in sections.values())):
                    issues.append(attr + ': cloud and device transfer must exclude all domains')

    policy('fullBackupContent', 'full-backup-content')
    policy('dataExtractionRules', 'data-extraction-rules')
    return {'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'static_backup_policy': 'PASS' if not issues else 'FAIL', 'issues': issues,
            'device_backup_restore_tested': False,
            'sdk_secret_storage_tested': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--aapt2', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = verify(args.apk, args.aapt2)
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(json.dumps({'inspection': 'ERROR', 'reason': str(error)}, indent=2))
        return 2
    print(json.dumps(report, indent=2))
    return 0 if report['static_backup_policy'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
