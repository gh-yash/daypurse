# Releasing

## 1. Prepare

1. Bump `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`.
2. Move the *Unreleased* notes in `CHANGELOG.md` under the new version.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what's new, plain text, under 500 characters.
   F-Droid and the Play Store both show it.
4. Run `./gradlew testDebugUnitTest lintDebug`.
5. Commit, then tag: `git tag -a v1.2.3 -m "DayPurse 1.2.3"` and push with `git push --follow-tags`.

## 2. Build

```bash
./gradlew bundleRelease    # app/build/outputs/bundle/release/app-release.aab  → Play Store
./gradlew assembleRelease  # app/build/outputs/apk/release/app-release.apk     → GitHub release
```

Signing uses `keystore.properties` (see `keystore.properties.example`). Keep the keystore and its passwords backed
up outside the repository: an app can't be updated without the same key.

## 3. GitHub release

Create a release from the tag, paste the changelog and attach the APK (named `DayPurse-1.2.3.apk`).

## 4. Google Play

First release only:

- Create the app in Play Console with package `in.bizappz.daypurse`, and enable Play App Signing (upload the AAB
  signed with your upload key).
- Store listing: copy the text from `fastlane/metadata/android/en-US/` and the images from its `images/` folder.
- Privacy policy URL: `https://github.com/gh-yash/daypurse/blob/main/PRIVACY.md` (or a copy on bizappz.in).
- Data safety: no data collected, no data shared.
- Content rating questionnaire; target audience 18+ (finance); no ads.
- If Play asks about notification access, explain: optional, user-enabled, reads payment notifications on the
  device to suggest spends, nothing leaves the device; the in-app screen explains it before asking.

Each release: upload the AAB to a testing track, check it, then promote to production.

## 5. F-Droid

F-Droid builds the app from source and signs it with its own key, so F-Droid and Play installs can't update each
other (users must uninstall to switch).

First release only: open a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) adding
`metadata/in.bizappz.daypurse.yml`:

```yaml
Categories:
  - Money
License: GPL-3.0-or-later
AuthorName: Yash Kumar
SourceCode: https://github.com/gh-yash/daypurse
IssueTracker: https://github.com/gh-yash/daypurse/issues
Changelog: https://github.com/gh-yash/daypurse/blob/HEAD/CHANGELOG.md

AutoName: DayPurse

RepoType: git
Repo: https://github.com/gh-yash/daypurse.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

After that, F-Droid picks up new version tags automatically. The store text and screenshots come from
`fastlane/metadata/` in this repository.
