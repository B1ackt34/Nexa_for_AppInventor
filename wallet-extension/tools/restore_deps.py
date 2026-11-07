"""Copy a complete frozen SDK runtime directory after verifying all 53 hashes.

Usage: python3 tools/restore_deps.py /path/to/frozen/deps
Build the SDK using ../packaging-probe/sdk-upgrade first if no directory exists.
Does not resolve newer dependencies or download artifacts.
"""
import hashlib
import json
from pathlib import Path
import shutil
import sys

root = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]).resolve()
lock = json.loads((root / 'SDK-PROVENANCE.json').read_text())['artifacts']
for name, info in lock.items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == info['sha256'], name
target = root / 'deps'
target.mkdir(exist_ok=True)
assert not {p.name for p in target.iterdir()} - set(lock), 'Unexpected dependency; use an empty directory'
for name in lock:
    if source != target.resolve():
        shutil.copyfile(source / name, target / name)
print(f'PASS: {len(lock)} pinned SDK runtime artifacts verified')
