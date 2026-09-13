#!/usr/bin/env bash
set -euo pipefail

channel="${1:?release channel is required}"
ref_type="${2:?GitHub ref type is required}"
ref_name="${3:?GitHub ref name is required}"
publish="${4:-false}"
version="$(sed -n 's/^version = "\([^"]*\)"/\1/p' build.gradle.kts | tr -d '\r')"

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "invalid release version: $version" >&2; exit 1; }
case "$channel" in eap|stable) ;; *) echo "unsupported channel: $channel" >&2; exit 1;; esac
test "$ref_type" = "tag" || { echo "release must run from tag v$version" >&2; exit 1; }
test "$ref_name" = "v$version" || { echo "expected tag v$version, got $ref_name" >&2; exit 1; }
if test "$version" = "0.1.0"; then
  test "$channel" = "eap" || { echo "version 0.1.0 is the EAP candidate" >&2; exit 1; }
  test "$publish" = "false" || { echo "the first Marketplace upload must be performed manually" >&2; exit 1; }
fi

echo "PLUGIN_VERSION=$version"
