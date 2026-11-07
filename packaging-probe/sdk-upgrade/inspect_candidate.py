#!/usr/bin/env python3
"""Check the SDK 0.7.5 candidate's backward-compatible blocks and JNI boundary.

Static archive checks only. Does not claim Android execution or wallet migration.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile

PREFIX = 'org.nexa.appinventor.probe/'


def signatures(component, field):
    result = {}
    for item in component[field]:
        result[item['name']] = {key: value for key, value in item.items()
                               if key not in {'description', 'deprecated', 'userVisible'}}
    return result


def inspect(args):
    with ZipFile(args.baseline_aix) as baseline:
        old = json.loads(baseline.read(PREFIX + 'components.json'))[0]
    assert old['versionName'] == '0.0.7'
    with ZipFile(args.aix) as archive:
        assert archive.testzip() is None
        assert all(item.file_size < 5 * 1024 * 1024 for item in archive.infolist())
        component = json.loads(archive.read(PREFIX + 'components.json'))[0]
        assert component['version'] == '8' and component['versionName'] == '0.0.8'
        assert component['type'] == old['type']
        assert component['androidMinSdk'] == '29' and component['external'] == 'true'
        for field in ['methods', 'events']:
            assert signatures(component, field) == signatures(old, field), field
        props = signatures(component, 'blockProperties')
        assert props.pop('LibNexaVersion')['type'] == 'text'
        assert props == signatures(old, 'blockProperties')
        info = json.loads(archive.read(PREFIX + 'files/component_build_infos.json'))[0]
        for library in info['libraries']:
            folder = 'aars/' if library.endswith('.aar') else 'files/'
            assert PREFIX + folder + library in archive.namelist(), library
        assert 'org.nexa.libnexakotlin.Initialiser' in ''.join(info['activities'])
        assert 'androidx.startup.InitializationProvider' in ''.join(info['activities'])
        assert 'android.permission.INTERNET' in info['permissions']
        with ZipFile(io.BytesIO(archive.read(PREFIX + 'classes.jar'))) as dex:
            assert dex.read('classes.dex').startswith(b'dex\n')
            assert b'LibNexaVersion' in dex.read('classes.dex')
            assert b'0.7.5' in dex.read('classes.dex')
        classes = set()
        for path in archive.namelist():
            if path.startswith(PREFIX + 'files/') and path.endswith('.jar'):
                with ZipFile(io.BytesIO(archive.read(path))) as jar:
                    classes.update(jar.namelist())
        for name in ['Initialiser', 'Native', 'Native$Companion', 'LibnexaandroidKt', 'InitKt', 'FastforwardKt']:
            assert 'org/nexa/libnexakotlin/' + name + '.class' in classes, name
        natives = [p for p in archive.namelist() if p.endswith('libnexajni-release.aar')]
        assert len(natives) == 1
        with ZipFile(io.BytesIO(archive.read(natives[0]))) as aar, ZipFile(args.jni_aar) as source:
            expected = {f'jni/{abi}/{name}' for abi in ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']
                        for name in ['libnexalight.so', 'libc++_shared.so']}
            assert {n for n in aar.namelist() if n.endswith('.so')} == expected
            for name in expected:
                data = aar.read(name)
                assert data.startswith(b'\x7fELF') and data == source.read(name), name
    print('PASS: existing block signatures unchanged; LibNexaVersion added')
    print('PASS: DEX, Startup, original JNI class names and eight unchanged native binaries')
    print('PASS: all archive entries below 5 MiB')
    print('SHA256:', hashlib.sha256(args.aix.read_bytes()).hexdigest())
    print('Android/App Inventor APK runtime and real wallet migration: NOT TESTED')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('aix', type=Path)
    parser.add_argument('--baseline-aix', required=True, type=Path)
    parser.add_argument('--jni-aar', required=True, type=Path)
    inspect(parser.parse_args())
