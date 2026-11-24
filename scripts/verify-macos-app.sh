#!/usr/bin/env bash
set -euo pipefail

APP="${1:?usage: verify-macos-app.sh /path/to/Dockhand.app}"
PLIST="$APP/Contents/Info.plist"

[[ -d "$APP" ]] || { echo "missing app bundle: $APP" >&2; exit 1; }
plutil -lint "$PLIST"

for key in \
  CFBundleIdentifier CFBundleShortVersionString CFBundleVersion \
  CFBundleIconFile LSMinimumSystemVersion LSApplicationCategoryType \
  NSDesktopFolderUsageDescription NSDocumentsFolderUsageDescription \
  NSDownloadsFolderUsageDescription NSRemovableVolumesUsageDescription \
  NSNetworkVolumesUsageDescription; do
  value=$(/usr/libexec/PlistBuddy -c "Print :$key" "$PLIST" 2>/dev/null || true)
  if [[ -z "$value" ]]; then
    echo "missing Info.plist key: $key" >&2
    exit 1
  fi
  echo "$key = $value"
done

codesign --verify --deep --strict --verbose=2 "$APP"
echo 'entitlements:'
codesign -d --entitlements :- "$APP" 2>/dev/null || true
echo 'gatekeeper:'
spctl --assess --type execute --verbose "$APP" || true

MANIFEST="$APP/Contents/Resources/PrivacyInfo.xcprivacy"
if [[ -f "$MANIFEST" ]]; then
  plutil -lint "$MANIFEST"
else
  echo 'warning: PrivacyInfo.xcprivacy is missing from Contents/Resources' >&2
fi
