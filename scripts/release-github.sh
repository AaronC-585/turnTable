#!/usr/bin/env bash
# Create a GitHub release and attach Android APK; optionally iOS IPA.
# Usage:
#   ./scripts/release-github.sh <tag> <path-to.apk> [path-to.ipa]
# With IPA (preferred — matches project release policy):
#   ./scripts/release-github.sh v2026.3.19.1219 \
#     android/app/build/outputs/apk/release/turnTable.release-2026.3.19.1219.apk \
#     ./turnTable-ios-2026.3.19.1219.ipa
# APK only (e.g. Linux CI; add IPA later from macOS):
#   ./scripts/release-github.sh v2026.3.19.1219 path/to.apk
#   gh release upload "$TAG" path/to.ipa
#
# Requires: gh CLI, authenticated (gh auth login)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TAG="${1:?usage: $0 <tag> <apk> [ipa]}"
APK="${2:?apk path}"
IPA="${3:-}"

if [[ ! -f "$APK" ]]; then
  echo "Not a file: $APK" >&2
  exit 1
fi
if [[ -n "$IPA" && ! -f "$IPA" ]]; then
  echo "Not a file: $IPA" >&2
  exit 1
fi

NOTES_TMP="$(mktemp)"
trap 'rm -f "$NOTES_TMP"' EXIT

{
  echo "## turnTable ${TAG#v}"
  echo
  echo "Assets:"
  echo "- **Android:** \`$(basename "$APK")\`"
  if [[ -n "$IPA" ]]; then
    echo "- **iOS:** \`$(basename "$IPA")\`"
  else
    echo "- **iOS:** *(build IPA on macOS per RELEASE.md, then \`gh release upload $TAG your.ipa\`)*"
  fi
  echo
  echo "See **CHANGELOG.md** and **RELEASE.md** for details."
} > "$NOTES_TMP"

if [[ -n "$IPA" ]]; then
  gh release create "$TAG" "$APK" "$IPA" \
    --title "turnTable ${TAG#v}" \
    --notes-file "$NOTES_TMP"
  echo "Created release $TAG with APK + IPA."
else
  gh release create "$TAG" "$APK" \
    --title "turnTable ${TAG#v}" \
    --notes-file "$NOTES_TMP"
  echo "Created release $TAG with APK only; upload IPA when ready: gh release upload $TAG <file>.ipa"
fi

# In-app update fallback reads this from raw.githubusercontent.com (see GithubAppUpdateChecker).
VERSION_FILE="$ROOT/android/app/update-check-latest-version.txt"
{
  echo "# Latest Android release version (no leading v). Updated by scripts/release-github.sh."
  echo "${TAG#v}"
} > "$VERSION_FILE"
echo "Wrote $VERSION_FILE — commit and push so the update check sees the new version on your default branch."
