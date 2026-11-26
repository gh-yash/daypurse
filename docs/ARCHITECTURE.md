# Architecture

DayPurse is a single-module Android app: Kotlin, Jetpack Compose (Material 3), Room, DataStore and coroutines.
It has no network access, no background services and no dependency-injection framework.

## Editions

Two Gradle flavors share all code:

- `full`: everything. `app/src/full/` adds the payment notification listener, its notification actions and the list
  of payment apps it may look for.
- `lite` (`in.bizappz.daypurse.lite`): no notification listener, so it never asks for notification access.
  `R.bool.payment_spotting` is false, and the UI says where to get the full version instead.

## Packages

```
app/src/main/java/bizappz/daypurse/
  DayPurseApplication.kt  App start: housekeeping, recurring catch-up, keeps widgets/shortcuts/tile fresh
  MainActivity.kt         Single activity; theme, app lock, launch actions
  di/          AppContainer: creates everything lazily (manual DI)
  data/
    model/     Room entities and settings model
    db/        DAOs, database, default categories
    repository/Expense and category repositories (every write is recorded in the activity log)
    settings/  DataStore-backed settings
    backup/    JSON backup/restore and CSV export (org.json, no extra library)
  domain/
    budget/    BudgetEngine: periods, daily limit, carry-over, pause; DailyBudgetCalculator
    activity/  ActivityLog: history of changes and the app's "data changed" signal
    recurring/ RecurringSpends: adds due recurring spends when the app, widget or reminder runs
    payments/  PaymentParser (notification text → amount + payee), PaymentInbox, supported apps
    quick/     QuickLogger (one-tap logging), QuickEntryParser ("120 food")
    money/     Keypad amount input
  payments/    NotificationListenerService and the "Log this payment?" notification
  reminder/    Daily reminder alarm, reply-to-log receiver, reschedule after reboot
  surfaces/    Widgets, launcher shortcuts, Quick Settings tile, private launch actions
  security/    App lock (platform BiometricPrompt)
  ui/          Compose screens by feature, shared components, theme, navigation
```

## Principles

- **Offline and private.** No internet permission. Nothing leaves the device unless the user exports a file.
- **Nothing runs in the background.** Budget periods are created lazily when the app asks for today's state, so a
  phone that hasn't opened the app for a month catches up in one go. The only scheduled work is one inexact daily
  alarm for the reminder (if enabled). Widgets refresh when data changes and every 30 minutes otherwise.
- **Money in minor units.** Amounts are `Long` (1/100 of the currency unit) to avoid floating-point errors.
- **Dates as epoch days** in the database, so range queries stay index-friendly.
- **One write path.** Repositories record every change in `ActivityLog`, which also emits a signal used to
  refresh widgets, shortcuts and the tile.
- **Private entry points.** Shortcuts, widgets, the tile and notifications open the app through `QuickActions`, a
  non-exported activity alias, so other apps can't trigger actions such as logging a spend.

## Budget maths

- Daily limit = money left before today ÷ days left (including today), rounded down to a whole unit and fixed for
  the day. Overspending lowers the following days' limits; underspending raises them.
- Weekly and monthly budgets can start on any day; a first partial period is prorated.
- Paused days are skipped when spreading money. Carry-over (optional) moves leftover or deficit into the next
  period and is recalculated when past spends change.

## Payment spotting

`PaymentListener` receives notifications only after the user grants notification access, and returns immediately
for apps not in `PaymentSources`. `PaymentParser` is strict: it needs a debit word and an amount, and rejects
credits, OTPs, requests, refunds, failures and reminders. Text messages also need a bank marker (A/c, UPI, card…).
A UPI app and the bank often report the same payment, so `PaymentInbox` treats the same amount within 10 minutes as
one. Only the parsed fields are stored.

## Database

Room, version 1, schema exported to `app/schemas/`. Future changes need a migration plus a test.

## Tests

`./gradlew testDebugUnitTest` runs JVM and Robolectric tests: budget maths, period calculation, parsers,
repositories, recurring catch-up, payment inbox, activity log and backup round-trips.
