"""Run the official validator against public fixtures, without Android JNI.

Requires restored pinned dependencies and JDK21 (JAVA_HOME).
Extracts AAR class payloads unchanged into a temporary classpath.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
lock = json.loads((root / 'SDK-PROVENANCE.json').read_text())['artifacts']
java_bin = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path('/usr/bin')
with tempfile.TemporaryDirectory(prefix='nexa-recovery-test-') as folder:
    work = Path(folder)
    classpath = []
    for name, info in lock.items():
        artifact = root / 'deps' / name
        assert hashlib.sha256(artifact.read_bytes()).hexdigest() == info['sha256'], name
        if artifact.suffix == '.aar':
            with ZipFile(artifact) as archive:
                if 'classes.jar' in archive.namelist():
                    jar = work / (name + '.jar')
                    jar.write_bytes(archive.read('classes.jar'))
                    classpath.append(str(jar))
        else:
            classpath.append(str(artifact))
    source = root / 'src/org/nexa/appinventor/probe'
    test = root / 'tests/org/nexa/appinventor/probe/RecoveryPhraseSdkTest.java'
    cp = os.pathsep.join(classpath)
    subprocess.run([str(java_bin / 'javac'), '--release', '17', '-cp', cp, '-d', str(work),
                    *[str(source / name) for name in ['WalletSession.java', 'WalletBalance.java', 'SendData.java', 'RecoveryPhrase.java']],
                    str(root / 'tests/android/util/Log.java'), str(test)], check=True)
    subprocess.run([str(java_bin / 'java'), '-cp', str(work) + os.pathsep + cp,
                    'org.nexa.appinventor.probe.RecoveryPhraseSdkTest'], check=True)
