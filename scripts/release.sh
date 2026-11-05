#!/usr/bin/env bash
# Usage: scripts/release.sh [NOTES.md]
#
# Tags the version in app/build.gradle.kts (v<versionName>) and publishes it as a GitHub release
# with its APK attached (segue-<version>.apk): a release build, signed with the release key, so
# every release installs over the one before. NOTES.md, if given, opens the release notes; after
# the first release they list the commits since the last one. Then it adds the release to the F-Droid
# repo (github.com/tblisonb/fdroid, checked out in ~/Applications/fdroid or $FDROID_REPO).
#
# First bump versionName and versionCode in app/build.gradle.kts, commit and push. It releases what
# main has on GitHub, after the unit tests pass, and refuses a version that's already tagged.
#
# Needs: gh, logged in, and the release key (~/.android/mediasync-release.properties, or the file
# -Psegue.signing names; see the README).
set -euo pipefail
# -h or --help prints the comment at the top of this file.
for arg in "$@"; do
    [[ $arg != -h && $arg != --help ]] || { awk 'NR > 1 && !/^#/ { exit } NR > 1 { sub(/^# ?/, ""); print }' "$0"; exit 0; }
done
cd "$(dirname "$0")/.."

notes_file=${1:-}
[[ -z $notes_file || -f $notes_file ]] || { echo "no file $notes_file" >&2; exit 1; }

version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
[[ -n $version ]] || { echo "no versionName in app/build.gradle.kts" >&2; exit 1; }
tag="v$version"

[[ $(git rev-parse --abbrev-ref HEAD) == main ]] || { echo "Release from main." >&2; exit 1; }
[[ -z $(git status --porcelain --untracked-files=no) ]] || { echo "Commit your changes first." >&2; exit 1; }
git fetch -q origin main
[[ $(git rev-parse HEAD) == $(git rev-parse origin/main) ]] || { echo "main differs from origin/main: push or pull first." >&2; exit 1; }
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "$tag already exists: bump the version in app/build.gradle.kts." >&2
    exit 1
fi
export JAVA_HOME=${JAVA_HOME:-$HOME/Android/jdk-21}

[[ -f $HOME/.android/mediasync-release.properties ]] ||
    { echo "No release key: see the README's Building section." >&2; exit 1; }
./gradlew -q :app:testDebugUnitTest :app:assembleRelease >&2
apk=app/build/outputs/apk/release/app-release.apk
[[ -f $apk ]] || { echo "The release build isn't signed." >&2; exit 1; }
asset="app/build/outputs/apk/release/segue-$version.apk"
cp "$apk" "$asset"

notes=${notes_file:+$(cat "$notes_file")}
previous=$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)
if [[ -n $previous ]]; then
    notes+=${notes:+$'\n\n'}"## Changes since $previous"$'\n\n'"$(git log --no-merges --format='- %s' "$previous..HEAD")"
fi

git tag -a "$tag" -m "Segue $version"
git push -q origin "$tag"
gh release create "$tag" "$asset" --verify-tag --title "Segue $version" --notes "${notes:-Segue $version}"

fdroid_repo=${FDROID_REPO:-$HOME/Applications/fdroid}
if [[ -x $fdroid_repo/scripts/publish.sh ]]; then
    "$fdroid_repo/scripts/publish.sh" ||
        echo "Released, but not in the F-Droid repo yet: run $fdroid_repo/scripts/publish.sh to retry." >&2
else
    echo "No F-Droid repo in $fdroid_repo: clone github.com/tblisonb/fdroid there and run its scripts/publish.sh." >&2
fi
