# Releasing

## 1. Prepare

1. Bump `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`.
2. Move the *Unreleased* notes in `CHANGELOG.md` under the new version.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what's new, plain text, under 500 characters.
   F-Droid and the Play Store both show it.
4. Run `./gradlew testFullDebugUnitTest lintFullDebug lintLiteDebug`.
5. Commit, then tag: `git tag -a v1.2.3 -m "DayPurse 1.2.3"`.

## 2. Build

Use JDK 21 (what F-Droid's build servers use) and a clean checkout of the tag:

```bash
scripts/build-release.sh
```

It writes to `release/` (git-ignored):

| File | Edition | Build | Goes to |
|---|---|---|---|
| `DayPurse-1.2.3.apk` | full | reproducible | GitHub release (F-Droid checks its own build against it) |
| `DayPurse-Lite-1.2.3.apk` | lite | reproducible | GitHub release |
| `DayPurse-1.2.3.aab` | full | optimized | Google Play |

Then check the APK can be reproduced, the way F-Droid will (needs `pip install apksigcopier`):

```bash
scripts/check-reproducible.sh v1.2.3
```

Signing uses `keystore.properties` (see `keystore.properties.example`). Keep the keystore and its passwords backed
up outside the repository: without that key the app can't be updated, on any store.

## Reproducible builds

F-Droid normally signs apps with its own key. For reproducible apps it instead builds the app from source,
checks that the result is identical to the developer's signed APK (apart from the signature), and ships the
developer-signed APK. That means:

- F-Droid and GitHub installs carry the same signature, so users can switch between them without reinstalling.
- One signing key covers every channel, which also keeps things simple for Android's developer verification.
- If a release doesn't reproduce, F-Droid won't publish that version until it's fixed.

What keeps the build reproducible:

- **R8 optimization is off** for these builds (`app/reproducible.pro`, `-dontoptimize`). R8's optimizer gives
  slightly different code from run to run; with it off, builds are byte-identical. R8 still shrinks and obfuscates.
  The Play bundle is built with `-Pdaypurse.play=true` and keeps optimization (it's smaller, and nobody needs to
  reproduce it). If a future Android Gradle Plugin makes R8 deterministic, optimization can come back for all builds
  without changing the key.
- **Build from the tagged commit, with no local changes.** The APK records the git commit it was built from
  (`META-INF/version-control-info.textproto`), so `build-release.sh` refuses to run with uncommitted changes.
- **Pinned toolchain.** The Gradle wrapper, Android Gradle Plugin and all libraries have fixed versions.

## 3. GitHub release

Push the tag (`git push --follow-tags`), create a release from it, paste the changelog and attach
`DayPurse-1.2.3.apk` and `DayPurse-Lite-1.2.3.apk` with exactly those names (F-Droid downloads the first one).

## 4. Google Play

First release only:

- Create the app in Play Console with package `in.bizappz.daypurse`.
- **Play App Signing: use your own key.** When asked for the app signing key, choose to upload your existing key
  (Play Console walks you through exporting it from your keystore) instead of letting Google generate one. Then Play,
  F-Droid and GitHub installs all share one signature. This can't be changed easily later.
- Store listing: copy the text from `fastlane/metadata/android/en-US/` and the images from its `images/` folder.
- Privacy policy URL: `https://github.com/gh-yash/daypurse/blob/main/PRIVACY.md` (or a copy on bizappz.in).
- Data safety: no data collected, no data shared.
- Content rating questionnaire; target audience 18+ (finance); no ads.
- If Play asks about notification access, explain: optional, user-enabled, reads payment notifications on the
  device to suggest spends, nothing leaves the device; the in-app screen explains it before asking.
- New personal developer accounts must run a closed test before going to production; check the current tester
  requirements in Play Console.

Each release: upload `DayPurse-1.2.3.aab` to a testing track, check it, then promote to production.

DayPurse Lite is not published on Play: it would be a near-duplicate app, which Play's spam policy doesn't allow.

## 5. F-Droid

First release only: open a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) adding
`metadata/in.bizappz.daypurse.yml`:

```yaml
Categories:
  - Finance Manager
License: GPL-3.0-or-later
AuthorName: Yash Kumar
SourceCode: https://github.com/gh-yash/daypurse
IssueTracker: https://github.com/gh-yash/daypurse/issues
Changelog: https://github.com/gh-yash/daypurse/blob/HEAD/CHANGELOG.md

AutoName: DayPurse

RepoType: git
Repo: https://github.com/gh-yash/daypurse.git
Binaries: https://github.com/gh-yash/daypurse/releases/download/v%v/DayPurse-%v.apk

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - full

AllowedAPKSigningKeys: 6ca2b6bf9c8428d6cfe5e6ca8fd614e27c76a62801378c81bc4f364eb6c6277c

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

`Binaries` makes F-Droid ship the developer-signed APK from the GitHub release when its own build matches it.
`AllowedAPKSigningKeys` is the SHA-256 of your signing certificate; `build-release.sh` prints it. Categories must
come from fdroiddata's `config/categories.yml`, and the file must match `fdroid rewritemeta` formatting (no
comments, ends with a newline), or the merge request's checks fail. If you switch to a
new key before the first release, update it here.

After that, F-Droid picks up new version tags automatically, as long as the GitHub release with the APK is published
first. The store text and screenshots come from `fastlane/metadata/` in this repository. DayPurse Lite isn't listed
on F-Droid: the full app installs normally from F-Droid.
