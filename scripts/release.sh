#!/usr/bin/env bash
# Veröffentlicht eine neue Version von MHD Safety.
#
#   scripts/release.sh 1.1.0
#
# Vorher in CHANGELOG.md einen Abschnitt "## [1.1.0]" anlegen und committen –
# der Text daraus erscheint als "Was ist neu" in der App.
#
# Ablauf: Version setzen -> Tests -> signierte Release-APK bauen ->
# releases/MHDSafety-<version>.apk -> Commit + Tag -> Push -> GitHub-Release.
set -euo pipefail
cd "$(dirname "$0")/.."

die() { echo "❌ $*" >&2; exit 1; }

VERSION="${1:-}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Nutzung: $0 <version>, z. B. $0 1.1.0"
TAG="v$VERSION"

[[ -z "$(git status --porcelain)" ]] || die "Es gibt nicht committete Änderungen. Erst committen."
git rev-parse "$TAG" >/dev/null 2>&1 && die "Tag $TAG existiert schon."

NOTES="$(awk -v v="$VERSION" '
    $0 ~ "^## \\[" v "\\]" { found = 1; next }
    found && /^## \[/ { exit }
    found { print }
' CHANGELOG.md | sed -e '/./,$!d')"
[[ -n "$NOTES" ]] || die "Kein Abschnitt \"## [$VERSION]\" in CHANGELOG.md gefunden."

echo "▶ Version $VERSION setzen"
sed -i "s/^APP_VERSION=.*/APP_VERSION=$VERSION/" gradle.properties

echo "▶ Tests und Release-Build"
APK=app/build/outputs/apk/release/app-release.apk
# Alte APK weg, damit nie eine liegengebliebene hochgeladen wird.
rm -f "$APK"
# Ohne Konfigurations-Cache: ein kopierter Cache hat schon mal in einer alten
# Projektkopie gebaut und 1.1.0 als 1.2.0 veröffentlicht.
./gradlew --quiet --no-configuration-cache :app:testDebugUnitTest :app:assembleRelease

[[ -f "$APK" ]] || die "Keine signierte APK gebaut. Sind die MHD_KEYSTORE_*-Einträge in gradle.properties von Gradle gesetzt?"
BUILD_TOOLS="$(ls -d "${ANDROID_HOME:-$HOME/Dev/Android/Sdk}"/build-tools/* | sort -V | tail -1)"
"$BUILD_TOOLS/apksigner" verify "$APK" || die "APK-Signatur ungültig."
APK_VERSION="$("$BUILD_TOOLS/aapt2" dump badging "$APK" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
[[ "$APK_VERSION" == "$VERSION" ]] || die "Die APK hat Version $APK_VERSION statt $VERSION. Nichts veröffentlicht."

mkdir -p releases
OUT="releases/MHDSafety-$VERSION.apk"
cp "$APK" "$OUT"
echo "▶ $OUT ($(du -h "$OUT" | cut -f1))"

echo "▶ Commit, Tag und Push"
git diff --quiet || git commit -qam "Version $VERSION"
git tag -a "$TAG" -m "MHD Safety $VERSION"
git push -q origin HEAD --follow-tags

echo "▶ GitHub-Release"
gh release create "$TAG" "$OUT" --title "MHD Safety $VERSION" --notes "$NOTES"

echo "✅ MHD Safety $VERSION ist veröffentlicht. Die Apps finden das Update automatisch."
