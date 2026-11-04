"""Repackage generated output for MIT servers with a 5 MiB per-entry limit.

Classes and native binaries are moved without changing their bytes. Extra JARs
are registered in component_build_infos.json, as consumed by MIT GenerateClasses
and RunD8. This does not change the upstream dependency artifacts in deps/.
"""
import io
import json
from pathlib import Path
import sys
import zipfile

PREFIX = "org.nexa.appinventor.probe/"
LIMIT = 5 * 1024 * 1024
CHUNK = 2 * 1024 * 1024


def archive(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return stream.getvalue()


def read_archive(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def convert(source, target):
    original = read_archive(source.read_bytes())
    output = dict(original)
    added_libraries = []
    runtime_path = PREFIX + "files/AndroidRuntime.jar"
    runtime = read_archive(output.pop(runtime_path))
    chunks = []
    current = {}
    size = 0
    for name, data in sorted(runtime.items()):
        assert len(data) < CHUNK, "Single runtime entry too large"
        if current and size + len(data) > CHUNK:
            chunks.append(current)
            current, size = {}, 0
        current[name] = data
        size += len(data)
    if current:
        chunks.append(current)
    reassembled = {}
    for index, chunk in enumerate(chunks):
        name = "AndroidRuntime.jar" if index == 0 else f"probe-runtime-{index + 1}.jar"
        output[PREFIX + "files/" + name] = archive(chunk)
        if index:
            added_libraries.append(name)
        reassembled.update(chunk)
    assert reassembled == runtime, "Runtime payload changed"

    for path in list(output):
        if not path.endswith("libnexakotlin-release.aar"):
            continue
        aar = read_archive(output[path])
        before = dict(aar)
        jar = aar.pop("classes.jar")
        name = "probe-nexa-android-classes.jar"
        output[PREFIX + "files/" + name] = jar
        added_libraries.append(name)
        output[path] = archive(aar)
        assert aar == {n: b for n, b in before.items() if n != "classes.jar"}
        assert output[PREFIX + "files/" + name] == before["classes.jar"]

    info_path = PREFIX + "files/component_build_infos.json"
    info = json.loads(output[info_path])
    for component in info:
        component["libraries"] = list(dict.fromkeys(component["libraries"] + added_libraries))
    output[info_path] = json.dumps(info, separators=(",", ":")).encode()
    descriptor_path = PREFIX + "components.json"
    descriptors = json.loads(output[descriptor_path])
    for component in descriptors:
        component["versionName"] = "0.0.2"
        component["version"] = "2"
    output[descriptor_path] = json.dumps(descriptors, separators=(",", ":")).encode()
    assert output[PREFIX + "classes.jar"] == original[PREFIX + "classes.jar"]
    assert all(len(data) < LIMIT for data in output.values()), "Entry exceeds MIT limit"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(archive(output))
    # Check the serialized archives too, not only the in-memory dictionaries.
    assert read_archive(target.read_bytes()) == output
    print(f"Created {target.name}: {target.stat().st_size} bytes")
    print(f"Largest internal file: {max(map(len, output.values()))} bytes (< 5 MiB)")
    print("PASS: unchanged DEX, runtime payload, Nexa classes, manifests and native binaries")


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "out/org.nexa.appinventor.probe.aix"
    target = Path(sys.argv[2]) if len(sys.argv) > 2 else root / "out/NexaPackagingProbe-0.0.2.aix"
    convert(source, target)
