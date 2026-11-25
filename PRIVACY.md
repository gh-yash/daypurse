# DayPurse privacy policy

_Effective 25 November 2026_

DayPurse is an offline budget app made by Yash Kumar (bizappz.in). This policy explains what the app does with
your information. In short: **everything stays on your phone, and nothing is collected.**

## What the app stores

Everything you enter: budgets, spends, categories, notes, recurring spends, settings and your activity history.
It is stored only in the app's private storage on your device.

## What the app collects or shares

Nothing. DayPurse has no internet permission, so it cannot send data anywhere. There are no accounts, ads,
analytics, crash reporting or third-party SDKs. The developer never receives any of your data.

## Payment notifications (optional)

If you turn on *Payment notifications* and grant notification access in Android settings, DayPurse reads
notifications from the payment, bank and messaging apps you select, only to spot payments you made.

- Notifications are read on your device and are never sent anywhere.
- From a payment notification, only the **amount, payee name, app name and time** are kept, in a "To review" list.
  The notification text itself is not stored.
- Notifications from other apps are ignored.
- Nothing becomes a spend until you tap *Log*. Reviewed items are removed after 7 days.
- You can turn this off in the app at any time, and revoke notification access in Android settings.

## Backups and exports

Backups (JSON) and exports (CSV) are created only when you ask, and saved where you choose. They are not
encrypted, so keep them somewhere safe. The app's data is excluded from Android cloud backup. When you set up a
new phone with a direct phone-to-phone transfer, Android may copy it across.

## Activity history

The app keeps a history of changes you make (for example "Added ₹120 · Food") so you can review or restore them.
It stays on your device, is kept for 90 days by default (you can change this or clear it), and is deleted with
the app.

## Permissions

| Permission | Purpose |
|---|---|
| Notifications | Daily reminder and payment prompts, if you turn them on. |
| Run at startup | Re-arms the daily reminder after the phone restarts. |
| Biometrics | Optional app lock using Android's own fingerprint or face prompt. |
| Notification access | Optional; see *Payment notifications* above. |
| Package visibility | Shows which of the supported payment apps are installed. |

## Deleting your data

Use *Settings → Reset all data* in the app, or uninstall it. Either removes everything.

## Children

DayPurse does not knowingly collect data from anyone, including children, because it collects no data at all.

## Changes

If this policy changes, the new version will be published in the app's source repository with a new effective
date.

## Contact

Questions: open an issue at <https://github.com/gh-yash/daypurse/issues>.
