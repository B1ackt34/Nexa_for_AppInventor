# Nexa packaging probe — experimental

This is a Java MIT App Inventor extension to test Nexa Android packaging. It is
not a wallet implementation. It creates no keys, connections or transactions.

## What is implemented

- `Initialize()` runs native initialization on a worker thread.
- Read-only `Initialized` reports success within the current process.
- `InitializationSucceeded` and `InitializationFailed(code, message)` run on the UI thread.
- Initialization is serialized across component instances in this extension's classloader.
  Repeated calls after success do not initialize again; another installed extension may use
  a separate classloader, which this probe cannot coordinate.

All requested annotations are present: DesignerComponent, SimpleFunction,
SimpleProperty, SimpleEvent. Rush additionally requires its Extension annotation.

The only Nexa API called is `initializeLibNexa(variant)` from
[nexa-wallet-lifecycle-and-chain, Setup and versions](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-wallet-lifecycle-and-chain/SKILL.md).
The Java entry point `LibnexaandroidKt.initializeLibNexa(String)` was verified
with javap against the published Android 0.5.69 classes.jar. An empty string is
the documented default native variant. No undocumented Android context setter
or private JNI method is called. The dependency manifest retains its Startup
initializer, which still needs validation inside the final APK.

## Rebuild

Requirements: complete JDK 21, Gradle 8.14.3, Rush 2.1.2, Python 3 for archive inspection.
Use Android 10/API 29 or newer for the device. Rush v206 supplies compile API 36.

1. In `dependency-resolution/`, run `gradle --no-daemon resolveAndroidGraph`.
   The lockfile pins the graph and Gradle verification metadata checks artifact
   hashes. The lock includes the Kotlin stdlib-common metadata alias required by
   Gradle; no runtime artifact differs from the candidate manifest. It copies the 47 resolved artifacts into `../deps/`. Do not update
   locks or verification metadata to silence an unexpected mismatch.
2. Return to this directory and run `rush deps sync`, then `rush build`.
3. Run `python3 tools/inspect_aix.py`.

Repository access must use the execution environment's configured proxy and CA
trust. For Gradle, supply its HTTP/HTTPS proxy system properties if needed. No
tokens or secret values belong in the project. JDK 21 is needed to read Nexa's
Java 21 classes even though Rush compiles the component with `--release 17`.
Local dependency files avoid relying on Rush's Maven resolver to select KMP variants.
`original_names` preserves Nexa classes discovered by JNI, per Rush's diagnostic.
The dependency hashes in the root candidate manifest describe the inspected
downloads; they are not a claim of independent upstream signature verification.

## Device test (not yet executed)

1. Import `out/org.nexa.appinventor.probe.aix` into a new App Inventor project.
2. Add NexaPackagingProbe, a Button and a Label. On Button.Click call Initialize.
3. On InitializationSucceeded set the Label to `native initialization succeeded`;
   verify Initialized is true. On InitializationFailed display code and message.
4. Build an APK and install it on an Android 10+ test device. Launch and press
   the button. An app-start crash may indicate Startup/provider packaging trouble
   and requires Android logs; it cannot necessarily reach the failure event.
5. Repeat the call and restart the app to test initialization after process restart.

Rush documents that extensions shipping AARs do not work in the AI Companion.
Use a compiled APK. A successful event proves native initialization only, not
wallet/database readiness, balance correctness or transaction broadcasting.
This `.aix` is a development artifact using an older Nexa release, not a
production wallet. No real funds are needed for this test.

## Validation obtained

Rush build succeeded with Java compilation, manifest merging, R8 shrinking,
library relocation, desugaring, D8 and AIX generation. Archive inspection checks
the block descriptors, DEX, Startup metadata, native class names and ELF files
for four ABIs. An APK build and Android execution remain unverified.


## Import compatibility variant 0.0.2

After `rush build`, run `python3 tools/compat_aix.py`, then
`python3 tools/inspect_aix.py out/NexaPackagingProbe-0.0.2.aix`.
Import that compatibility output instead of the original Rush archive.

MIT's PostgreSQL backend formerly limited each imported internal file to 5 MiB;
commit b097193903890d6aa729ac52464ae342d3a600e2 raises this to 15 MiB. The live
service version has not been established. The original archive includes a
7,729,022-byte runtime JAR and a 5,629,487-byte AAR. These exceed the old limit.

The postprocessor splits the runtime JAR, moves the Nexa AAR's classes.jar to a
registered library JAR, preserves native files and manifest bytes, and updates
component_build_infos.json. It verifies unchanged runtime entry payloads, Nexa
classes, native binaries and DEX. No AIX entry reaches 5 MiB (largest: 3,276,061
bytes). JSON descriptor version 0.0.2/2 identifies this packaging revision.

The extra JAR registration follows MIT GenerateClasses.java and RunD8.java;
AARs without classes.jar are already supported by MIT's AAR pipeline. The
repackaged JAR graph passes D8 and Java ZIP/JSON parsing. This does not prove
import into the live MIT service or execution on Android.
