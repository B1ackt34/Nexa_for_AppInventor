#!/usr/bin/env bash
set -euo pipefail
module_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_output="$(mktemp -d)"
trap 'rm -rf -- "$test_output"' EXIT
compiler="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
runner="${JAVA_HOME:+$JAVA_HOME/bin/}java"
# org.json is supplied by Android at runtime. Use a compatible JSON jar for desktop tests.
json_jar="${JSON_JAR:?Set JSON_JAR to App Inventor lib/json/json.jar}"
source_root="$module_root/src/org/nexa/appinventor/probe"
"$compiler" --release 17 -cp "$json_jar" -d "$test_output" \
  "$source_root/WalletSession.java" "$source_root/WalletBalance.java" "$source_root/SendData.java" "$source_root/NoBackupStore.java" \
  "$module_root/tests/org/nexa/appinventor/probe/SendSessionTest.java" \
  "$module_root/tests/org/nexa/appinventor/probe/NoBackupStoreTest.java"
"$runner" -cp "$test_output:$json_jar" org.nexa.appinventor.probe.NoBackupStoreTest
