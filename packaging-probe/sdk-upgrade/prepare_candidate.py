#!/usr/bin/env python3
"""Prepare an isolated Rush 0.0.8 candidate from the tested 0.0.7 sources.

Does not overwrite the baseline project or publish artifacts. Consumes the exact
runtime artifact export from the pinned upstream SDK build, including matching JNI.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile

BASE = Path(__file__).resolve().parents[1]
SDK_COMMIT = '914879c7574b7d785127206667c96d474b81395a'


def prepare(args):
    sdk = args.sdk_checkout.resolve()
    target = args.output.resolve()
    if target.exists():
        raise ValueError('Output already exists; use a new empty candidate path')
    if target == BASE or BASE in target.parents:
        raise ValueError('Candidate must be outside the baseline project')
    revision = subprocess.check_output(['git', '-C', str(sdk), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != SDK_COMMIT:
        raise ValueError('SDK checkout does not match the official 0.7.5 release')
    rows = json.loads(args.artifacts_json.read_text())
    sdk_aar = sdk / 'libnexakotlin/build/outputs/aar/libnexakotlin.aar'
    native_aar = sdk / 'libnexajni/build/outputs/aar/libnexajni-release.aar'
    rows.append({'id': 'org.nexa:libnexakotlin-android:0.7.5 (source build)',
                 'name': sdk_aar.name, 'path': str(sdk_aar)})
    if not any(Path(row['path']).resolve() == native_aar for row in rows):
        raise ValueError('Matching locally built JNI is absent from the runtime graph')
    with ZipFile(native_aar) as native:
        expected = {f'jni/{abi}/{name}' for abi in ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')
                    for name in ('libnexalight.so', 'libc++_shared.so')}
        if {n for n in native.namelist() if n.endswith('.so')} != expected:
            raise ValueError('Expected all four native ABIs')
    files = {}
    for row in rows:
        original = Path(row['path'])
        if original.suffix not in {'.jar', '.aar'} or not original.is_file():
            raise ValueError('Runtime export is not an original JAR/AAR: ' + original.name)
        # Keep the tested compatibility packager's SDK filename convention.
        name = 'libnexakotlin-release.aar' if original == sdk_aar else original.name
        digest = hashlib.sha256(original.read_bytes()).hexdigest()
        if name in files and files[name]['sha256'] != digest:
            raise ValueError('Conflicting artifact filenames: ' + name)
        files[name] = {'id': row['id'], 'sha256': digest, 'source': original}
    target.mkdir(parents=True)
    for folder in ['src', 'tests', 'tools']:
        shutil.copytree(BASE / folder, target / folder)
    shutil.copyfile(BASE / 'proguard-rules.pro', target / 'proguard-rules.pro')
    component = target / 'src/org/nexa/appinventor/probe/NexaPackagingProbe.java'
    text = component.read_text()
    marker = '@DesignerComponent(version = 7,'
    if text.count(marker) != 1:
        raise ValueError('Baseline component version changed; review candidate generation')
    text = text.replace(marker, '@DesignerComponent(version = 8,')
    marker = '  @SimpleProperty(description = "The only supported network: testnet.")'
    if text.count(marker) != 1:
        raise ValueError('Baseline property insertion point changed')
    addition = '''  // Application build metadata, not a Nexa SDK API.
  @SimpleProperty(description = "Bundled libnexakotlin version, built from the official release tag.")
  public String LibNexaVersion() { return "0.7.5"; }

'''
    component.write_text(text.replace(marker, addition + marker))
    dependencies = target / 'deps'
    dependencies.mkdir()
    for name, info in files.items():
        shutil.copyfile(info['source'], dependencies / name)
    yaml = "version: '0.0.8'\nmin_sdk: 29\ndependencies:\n"
    yaml += ''.join('  - ' + name + '\n' for name in sorted(files))
    yaml += '\noriginal_names:\n  - org.nexa.libnexakotlin\n'
    (target / 'rush.yml').write_text(yaml)
    provenance = {'sdk_release': '0.7.5', 'sdk_commit': revision,
        'native_source_commit': subprocess.check_output(['git', '-C', str(sdk.parent / 'nexalnk'),
                            'rev-parse', 'HEAD'], text=True).strip(),
        'distribution': 'locally compiled from official source; not an upstream Maven binary',
        'artifacts': {name: {'id': item['id'], 'sha256': item['sha256']} for name, item in sorted(files.items())}}
    (target / 'SDK-PROVENANCE.json').write_text(json.dumps(provenance, indent=2) + '\n')
    print('Candidate:', target)
    print('Pinned runtime artifacts:', len(files))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-checkout', required=True, type=Path)
    parser.add_argument('--artifacts-json', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    prepare(parser.parse_args())
