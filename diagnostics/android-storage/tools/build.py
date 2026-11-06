#!/usr/bin/env python3
"""Offline Android APK build using the extension's checksum-verified dependency graph.

Dedicated diagnostic signing key stays outside the repository. No user app key used.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
from zipfile import ZipFile, ZIP_DEFLATED
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def build(args):
    build = ROOT / 'build'
    if build.exists():
        shutil.rmtree(build)
    build.mkdir()
    extracted = build / 'libraries'
    extracted.mkdir()
    android = args.mit_checkout / 'appinventor/lib/android'
    android_jar = android / 'android-36/android.jar'
    aapt2 = android / 'tools/linux/aapt2'
    apksigner = android / 'tools/apksigner.jar'
    java = args.java_home / 'bin/java'
    javac = args.java_home / 'bin/javac'
    jar_tool = args.java_home / 'bin/jar'
    deps = REPO / 'packaging-probe/deps'
    metadata = REPO / 'packaging-probe/dependency-resolution/gradle/verification-metadata.xml'
    known = {n.attrib['value'] for n in ET.parse(metadata).getroot().iter() if n.tag.endswith('sha256')}
    pins = json.loads((ROOT / 'DEPENDENCIES.json').read_text())
    # Consume exactly the frozen extension graph, not arbitrary additional local files.
    yaml = (REPO / 'packaging-probe/rush.yml').read_text()
    section = yaml.split('dependencies:\n', 1)[1].split('\n\n', 1)[0]
    names = re.findall(r'^  - ([^\s]+\.(?:jar|aar))$', section, flags=re.M)
    if len(names) != 47 or set(names) != set(pins):
        raise ValueError('Dependency graph changed; review before building diagnostic')
    libraries, natives, resources, services, fingerprints = [], {}, {}, {}, {}
    for name in names:
        path = deps / name
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest not in known or pins[name] != digest:
            raise ValueError('Unverified dependency: ' + name)
        fingerprints[name] = digest
        with ZipFile(path) as outer:
            if path.suffix == '.aar':
                data = outer.read('classes.jar')
                for entry in outer.namelist():
                    if entry.startswith('jni/') and entry.endswith('.so'):
                        natives['lib/' + entry[4:]] = outer.read(entry)
            else:
                data = path.read_bytes()
        clean = extracted / (name.replace('.aar', '') + '.jar')
        with ZipFile(io.BytesIO(data)) as original, ZipFile(clean, 'w', ZIP_DEFLATED) as output:
            for entry in original.namelist():
                if entry.endswith('/'):
                    continue
                if entry.endswith('.class'):
                    # Android uses base bytecode; Java multi-release alternatives are not DEX inputs.
                    if not entry.startswith('META-INF/versions/') and not entry.endswith('module-info.class'):
                        output.writestr(entry, original.read(entry))
                elif entry.startswith('META-INF/services/'):
                    lines = original.read(entry).decode('utf-8').splitlines()
                    services.setdefault(entry, set()).update(line for line in lines if line and not line.startswith('#'))
                elif not entry.startswith('META-INF/') and entry not in resources:
                    resources[entry] = original.read(entry)
        libraries.append(clean)
    (build / 'dependency-checksums.json').write_text(json.dumps(fingerprints, indent=2))
    if len(natives) != 8:
        raise ValueError('Unexpected native libraries')
    res = build / 'res'
    (res / 'xml').mkdir(parents=True)
    (res / 'values').mkdir()
    domains = ['root', 'file', 'database', 'sharedpref', 'external', 'device_root',
               'device_file', 'device_database', 'device_sharedpref']
    excludes = ''.join(f'<exclude domain="{domain}" path="."/>' for domain in domains)
    (res / 'xml/nexa_backup_rules.xml').write_text('<full-backup-content>' + excludes + '</full-backup-content>')
    (res / 'xml/nexa_data_extraction_rules.xml').write_text('<data-extraction-rules><cloud-backup>'
        + excludes + '</cloud-backup><device-transfer>' + excludes + '</device-transfer></data-extraction-rules>')
    # Upstream Startup's only resource; the generated library R is needed at runtime.
    with ZipFile(deps / 'startup-runtime-1.2.0.aar') as startup:
        (res / 'values/startup.xml').write_bytes(startup.read('res/values/values.xml'))
    run(aapt2, 'compile', '--dir', res, '-o', build / 'resources.zip')
    generated = build / 'generated'
    generated.mkdir()
    unsigned = build / 'unsigned.apk'
    run(aapt2, 'link', '-I', android_jar, '--manifest', ROOT / 'AndroidManifest.xml', '--java', generated,
        '--extra-packages', 'androidx.startup', '-o', unsigned, build / 'resources.zip')
    classes = build / 'classes'
    classes.mkdir()
    sources = sorted((ROOT / 'src').rglob('*.java')) + sorted(generated.rglob('*.java'))
    run(javac, '--release', '17', '-proc:none', '-classpath', os.pathsep.join(map(str, [android_jar] + libraries)),
        '-d', classes, *sources)
    program = build / 'program.jar'
    run(jar_tool, '--create', '--file', program, '-C', classes, '.')
    dex = build / 'dex'
    dex.mkdir()
    run(java, '-Xmx3g', '-cp', args.r8_jar, 'com.android.tools.r8.D8', '--release', '--min-api', '29',
        '--lib', android_jar, '--output', dex, program, *libraries)
    with ZipFile(unsigned, 'a', ZIP_DEFLATED) as apk:
        for file in sorted(dex.glob('*.dex')):
            apk.writestr(file.name, file.read_bytes())
        for name, data in sorted(natives.items()):
            apk.writestr(name, data)
        for name, data in sorted(resources.items()):
            if name not in apk.namelist():
                apk.writestr(name, data)
        for name, lines in sorted(services.items()):
            apk.writestr(name, '\n'.join(sorted(lines)) + '\n')
    signing = args.signing_dir.resolve()
    # Do not put a private signing key or its password under the repository.
    if signing == REPO or REPO in signing.parents:
        raise ValueError('Signing directory must be outside the repository')
    signing.mkdir(parents=True, exist_ok=True)
    os.chmod(signing, 0o700)
    key = signing / 'diagnostic.p12'
    password = signing / 'store-password'
    if not key.exists():
        if password.exists():
            raise ValueError('Incomplete signing setup; inspect it manually')
        password.write_text(secrets.token_urlsafe(32))
        os.chmod(password, 0o600)
        run(args.java_home / 'bin/keytool', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
            '-storepass:file', password, '-alias', 'diagnostic', '-keyalg', 'RSA', '-keysize', '3072',
            '-validity', '3650', '-dname', 'CN=Nexa Storage Diagnostic Test Only')
        os.chmod(key, 0o600)
    aligned = build / 'aligned-unsigned.apk'
    zipalign = android / 'tools/linux/zipalign'
    run(zipalign, '-f', '4', unsigned, aligned)
    target = build / 'NexaStorageDiagnostic-0.1.0.apk'
    run(java, '-jar', apksigner, 'sign', '--ks', key, '--ks-pass', 'file:' + str(password),
        '--ks-key-alias', 'diagnostic', '--min-sdk-version', '29', '--out', target, aligned)
    run(java, '-jar', apksigner, 'verify', '--verbose', '--print-certs', target)
    run(zipalign, '-c', '4', target)
    run('python3', ROOT / 'tools/check_apk_backup.py', target, '--aapt2', aapt2)
    print('APK:', target)
    print('SHA256:', hashlib.sha256(target.read_bytes()).hexdigest())
    print('Runtime on Android: NOT TESTED')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--mit-checkout', required=True, type=Path)
    parser.add_argument('--r8-jar', required=True, type=Path)
    parser.add_argument('--signing-dir', required=True, type=Path)
    build(parser.parse_args())
