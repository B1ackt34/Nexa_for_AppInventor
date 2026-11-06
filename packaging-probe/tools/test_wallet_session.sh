#!/usr/bin/env bash
set -euo pipefail
probe_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_output="$(mktemp -d)"
trap 'rm -rf -- "$test_output"' EXIT
compiler="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
runner="${JAVA_HOME:+$JAVA_HOME/bin/}java"
source_root="$probe_root/src/org/nexa/appinventor/probe"
"$compiler" --release 17 -d "$test_output" \
  "$source_root/WalletSession.java" "$source_root/WalletBalance.java" \
  "$source_root/ForegroundGate.java" "$source_root/GuardedRequest.java" \
  "$probe_root/tests/org/nexa/appinventor/probe/WalletSessionTest.java"
"$runner" -cp "$test_output" org.nexa.appinventor.probe.WalletSessionTest
