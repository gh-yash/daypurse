# Contributing to DayPurse

Thanks for helping! Bug reports, ideas, translations and pull requests are all welcome.

## Ground rules

DayPurse is private by design. Pull requests that add any of these won't be accepted:

- the internet permission or any network access
- analytics, ads, crash reporters or tracking of any kind
- proprietary or closed-source libraries (including Google Play Services)

Keep the app small and light: prefer a few lines of code over a new dependency.

## Reporting a bug

[Open an issue](https://github.com/gh-yash/daypurse/issues/new/choose) with your Android version, the app version
(*Settings → About*) and steps to reproduce. Screenshots help, but hide any amounts you don't want to share.

For security problems, see [SECURITY.md](SECURITY.md) instead.

## Building

See [Build](README.md#build) in the README. Before opening a pull request, run:

```bash
./gradlew testFullDebugUnitTest lintFullDebug lintLiteDebug
```

Payment spotting lives only in the full edition (`app/src/full/`); everything else is shared. Check that
DayPurse Lite still builds (`./gradlew assembleLiteDebug`) when you touch payment code.

## Code

- Kotlin and Jetpack Compose, following the existing structure (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)).
- Every change to user data goes through a repository so it is recorded in the activity history.
- New text goes in `app/src/main/res/values/strings.xml`. Write it plainly, in sentence case.
- Add tests for logic (budget maths, parsers, repositories). UI changes should be checked on a device in light and
  dark theme.
- Database changes need a Room migration and a test for it (the schema is exported to `app/schemas/`).

### Icons

Icons are Material Symbols (Rounded) vector drawables. To add one, add its name to `scripts/fetch-icons.sh`, run the
script and use `R.drawable.ic_<name>`.

## Pull requests

- Keep each pull request to one change, with a short description of what and why.
- Update `CHANGELOG.md` under *Unreleased* for anything users will notice.
- By contributing, you agree that your work is licensed under the GPL-3.0-or-later, like the rest of the project.

## Translations

The app is English-only for now. If you'd like to translate it, open an issue first so we can set it up.
