#!/usr/bin/env bash
# Download GNU Unifont 17.0.05 HEX builds, pack Minecraft unihex ZIP, refresh LICENSE.
# Not run during ordinary Gradle builds (no network on CI build path).
set -euo pipefail

VERSION="17.0.05"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/src/main/resources/assets/flexibook/font"
BASE_URL="https://unifoundry.com/pub/unifont/unifont-${VERSION}/font-builds"
LICENSE_URL="https://unifoundry.com/LICENSE.txt"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Expected SHA-256 of decompressed plane0 + upper HEX (pin for supply-chain check).
# Update these when bumping VERSION after manual review.
EXPECT_PLANE0_HEX="fd79af3613ec1b984a98d33428fdd43fcf06018d18059960d78edeb63d958622"
EXPECT_UPPER_HEX="979b60bb87ddbc861fec157d91f71973094502e7ff2a40ac2111f862d7056c56"

mkdir -p "$OUT_DIR"
cd "$WORK"

echo "Downloading Unifont ${VERSION}…"
curl -fsSL -o "unifont-${VERSION}.hex.gz" "${BASE_URL}/unifont-${VERSION}.hex.gz"
curl -fsSL -o "unifont_upper-${VERSION}.hex.gz" "${BASE_URL}/unifont_upper-${VERSION}.hex.gz"
curl -fsSL -o LICENSE.txt "$LICENSE_URL"

gunzip -kf "unifont-${VERSION}.hex.gz"
gunzip -kf "unifont_upper-${VERSION}.hex.gz"

plane0_sum="$(sha256sum "unifont-${VERSION}.hex" | awk '{print $1}')"
upper_sum="$(sha256sum "unifont_upper-${VERSION}.hex" | awk '{print $1}')"

if [[ "$plane0_sum" != "$EXPECT_PLANE0_HEX" ]]; then
  echo "ERROR: plane0 hex SHA-256 mismatch" >&2
  echo "  expected $EXPECT_PLANE0_HEX" >&2
  echo "  got      $plane0_sum" >&2
  exit 1
fi
if [[ "$upper_sum" != "$EXPECT_UPPER_HEX" ]]; then
  echo "ERROR: upper hex SHA-256 mismatch" >&2
  echo "  expected $EXPECT_UPPER_HEX" >&2
  echo "  got      $upper_sum" >&2
  exit 1
fi

ALL_HEX="unifont_all-${VERSION}.hex"
cat "unifont-${VERSION}.hex" "unifont_upper-${VERSION}.hex" > "$ALL_HEX"
ZIP_NAME="unifont_all-${VERSION}.zip"
rm -f "$ZIP_NAME"
# Store only the .hex entry at ZIP root (Minecraft UnihexProvider scans *.hex entries).
zip -9 -j "$ZIP_NAME" "$ALL_HEX"
zip_sum="$(sha256sum "$ZIP_NAME" | awk '{print $1}')"

cp -f "$ZIP_NAME" "$OUT_DIR/$ZIP_NAME"

# default.json (space + unihex only; no minecraft: references)
# Use printf so \u200c is a real JSON escape for ZWNJ (U+200C).
printf '%s\n' "{
  \"providers\": [
    {
      \"type\": \"space\",
      \"advances\": {
        \" \": 4,
        \"\\u200c\": 0
      }
    },
    {
      \"type\": \"unihex\",
      \"hex_file\": \"flexibook:font/${ZIP_NAME}\"
    }
  ]
}" > "$OUT_DIR/default.json"

{
  echo "GNU Unifont ${VERSION}"
  echo "Source: https://unifoundry.com/unifont/"
  echo "Build artifacts: ${BASE_URL}/"
  echo "Bundled as Minecraft unihex ZIP: ${ZIP_NAME}"
  echo "  (concat of unifont-${VERSION}.hex + unifont_upper-${VERSION}.hex)"
  echo ""
  echo "SHA256 (plane0 hex): ${plane0_sum}"
  echo "SHA256 (upper hex):  ${upper_sum}"
  echo "SHA256 (bundled zip): ${zip_sum}"
  echo ""
  echo "Upstream license text follows (GPL-2.0-or-later with font embedding exception;"
  echo "compiled fonts also under SIL OFL 1.1)."
  echo "================================================================================"
  echo ""
  cat LICENSE.txt
} > "$OUT_DIR/LICENSE-unifont.txt"

# Keep pin block in this script in sync message
echo "Wrote:"
echo "  $OUT_DIR/$ZIP_NAME  ($zip_sum)"
echo "  $OUT_DIR/default.json"
echo "  $OUT_DIR/LICENSE-unifont.txt"
echo "Done. If version pins change, update EXPECT_* in scripts/update-unifont.sh."
