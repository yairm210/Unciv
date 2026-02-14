#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-.}"
SRC_PROVIDER_FILE="$ROOT_DIR/web/src/main/java/org/slf4j/impl/WebSlf4jServiceProvider.java"
SRC_SERVICE_FILE="$ROOT_DIR/web/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider"
BUILD_PROVIDER_CLASS="$ROOT_DIR/web/build/classes/java/main/org/slf4j/impl/WebSlf4jServiceProvider.class"
BUILD_SERVICE_FILE="$ROOT_DIR/web/build/resources/main/META-INF/services/org.slf4j.spi.SLF4JServiceProvider"
EXPECTED_PROVIDER_LINE="org.slf4j.impl.WebSlf4jServiceProvider"

LEGACY_SOURCE_BINDERS=(
  "$ROOT_DIR/web/src/main/java/org/slf4j/impl/StaticLoggerBinder.java"
  "$ROOT_DIR/web/src/main/java/org/slf4j/impl/StaticMarkerBinder.java"
  "$ROOT_DIR/web/src/main/java/org/slf4j/impl/StaticMDCBinder.java"
)

LEGACY_BUILD_BINDERS=(
  "$ROOT_DIR/web/build/classes/java/main/org/slf4j/impl/StaticLoggerBinder.class"
  "$ROOT_DIR/web/build/classes/java/main/org/slf4j/impl/StaticMarkerBinder.class"
  "$ROOT_DIR/web/build/classes/java/main/org/slf4j/impl/StaticMDCBinder.class"
)

die() {
  echo "SLF4J web provider guard failed: $1" >&2
  exit 1
}

[ -f "$SRC_PROVIDER_FILE" ] || die "missing source provider class at $SRC_PROVIDER_FILE"
[ -f "$SRC_SERVICE_FILE" ] || die "missing source service descriptor at $SRC_SERVICE_FILE"
[ -f "$BUILD_PROVIDER_CLASS" ] || die "missing built provider class at $BUILD_PROVIDER_CLASS (run web build first)"
[ -f "$BUILD_SERVICE_FILE" ] || die "missing built service descriptor at $BUILD_SERVICE_FILE (run web build first)"

src_line="$(tr -d '\r' < "$SRC_SERVICE_FILE" | sed -e '/^[[:space:]]*$/d' | sed -n '1p')"
build_line="$(tr -d '\r' < "$BUILD_SERVICE_FILE" | sed -e '/^[[:space:]]*$/d' | sed -n '1p')"
[ "$src_line" = "$EXPECTED_PROVIDER_LINE" ] || die "unexpected source service provider line '$src_line'"
[ "$build_line" = "$EXPECTED_PROVIDER_LINE" ] || die "unexpected built service provider line '$build_line'"

for legacy in "${LEGACY_SOURCE_BINDERS[@]}"; do
  [ ! -f "$legacy" ] || die "legacy 1.7 source binder still present: $legacy"
done

for legacy in "${LEGACY_BUILD_BINDERS[@]}"; do
  [ ! -f "$legacy" ] || die "legacy 1.7 built binder still present: $legacy"
done

echo "SLF4J web provider guard passed."
