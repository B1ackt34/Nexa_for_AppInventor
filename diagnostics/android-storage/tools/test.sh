#!/usr/bin/env bash
set -euo pipefail
probe_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_output="$(mktemp -d)"
trap 'rm -rf -- "$test_output"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -d "$test_output" \
  "$probe_root/src/org/nexa/storage/diagnostic/NeedleScanner.java" \
  "$probe_root/src/org/nexa/storage/diagnostic/ProbeProtocol.java" \
  "$probe_root/tests/org/nexa/storage/diagnostic/DiagnosticTests.java"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$test_output" org.nexa.storage.diagnostic.DiagnosticTests
