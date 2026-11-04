"""Verify the packaging boundary; this does not run Android code."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile


def inspect(path):
    prefix = "org.nexa.appinventor.probe/"
    expected_abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
    with zipfile.ZipFile(path) as aix:
        assert aix.testzip() is None, "Corrupt AIX"
        components = json.loads(aix.read(prefix + "components.json"))
        assert len(components) == 1
        component = components[0]
        assert component["androidMinSdk"] == "29"
        assert component["external"] == "true"
        assert {x["name"] for x in component["methods"]} == {"Initialize"}
        assert {x["name"] for x in component["events"]} == {
            "InitializationSucceeded", "InitializationFailed"
        }
        assert {x["name"] for x in component["blockProperties"]} == {"Initialized"}
        with zipfile.ZipFile(io.BytesIO(aix.read(prefix + "classes.jar"))) as dex:
            assert dex.read("classes.dex").startswith(b"dex\n")
        native_aars = [n for n in aix.namelist() if n.endswith("libnexakotlin-release.aar")]
        assert len(native_aars) == 1
        with zipfile.ZipFile(io.BytesIO(aix.read(native_aars[0]))) as aar:
            for abi in expected_abis:
                for library in ("libnexalight.so", "libc++_shared.so"):
                    assert aar.read(f"jni/{abi}/{library}").startswith(b"\x7fELF")
            manifest = aar.read("AndroidManifest.xml").decode()
            assert "org.nexa.libnexakotlin.Initialiser" in manifest
            assert "${applicationId}.androidx-startup" in manifest
            assert "android.permission.INTERNET" in manifest
            with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as classes:
                for name in ("LibnexaandroidKt", "Initialiser", "Native", "Native$Companion"):
                    assert f"org/nexa/libnexakotlin/{name}.class" in classes.namelist()
        assert any(n.endswith("startup-runtime-1.2.0.aar") for n in aix.namelist())
    print("PASS: descriptors, DEX, Startup metadata, Nexa JNI class names and four native ABIs")
    print("SHA-256:", hashlib.sha256(path.read_bytes()).hexdigest())
    print("Android runtime execution: NOT TESTED")


if __name__ == "__main__":
    default = Path(__file__).resolve().parents[1] / "out/org.nexa.appinventor.probe.aix"
    inspect(Path(sys.argv[1]) if len(sys.argv) > 1 else default)
