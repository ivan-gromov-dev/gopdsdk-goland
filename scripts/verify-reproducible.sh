#!/usr/bin/env bash
set -euo pipefail

first="$(find build/distributions -maxdepth 1 -name '*.zip' ! -name '*-signed.zip' -print -quit)"
test -n "$first" || { echo "unsigned plugin ZIP not found" >&2; exit 1; }
first_hash="$(sha256sum "$first" | cut -d' ' -f1)"
./gradlew clean buildPlugin
second="$(find build/distributions -maxdepth 1 -name '*.zip' ! -name '*-signed.zip' -print -quit)"
second_hash="$(sha256sum "$second" | cut -d' ' -f1)"
test "$first_hash" = "$second_hash" || {
  echo "plugin ZIP is not reproducible: $first_hash != $second_hash" >&2
  exit 1
}
echo "reproducible plugin ZIP: $second_hash"
