#!/usr/bin/env bash
# Downloads Material Symbols (Rounded) vector drawables from Google's official
# repo (Apache-2.0) into the app's res/drawable. Only icons listed here ship in
# the APK, which keeps size small (no material-icons-extended dependency).
#
# Usage: scripts/fetch-icons.sh            # fetch all listed icons
# Entry format: <symbol_name>[:fill]  -> ic_<symbol_name>.xml (+ ic_<name>_filled.xml)
set -euo pipefail
cd "$(dirname "$0")/../app/src/main/res/drawable"
BASE=https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android

ICONS=(
  # navigation / actions
  home:fill receipt_long:fill pie_chart:fill settings:fill
  add arrow_back delete edit calendar_today check expand_more expand_less
  backspace search info trending_down edit_note contrast drag_indicator close pause play_arrow
  chevron_left chevron_right more_vert settings_backup_restore visibility_off label currency_exchange account_balance_wallet
  palette dark_mode notifications schedule lock fingerprint upload download table_view delete_forever
  # quick logging, recurring, payment inbox, activity
  repeat history inbox notifications_active sms event_repeat skip_next done_all
  # about
  code bug_report description
  # category icons (keys used by CategoryIcons.kt)
  restaurant shopping_cart directions_bus local_gas_station shopping_bag receipt
  movie medical_services local_cafe spa school redeem category
  home flight pets fitness_center sports_esports smartphone wifi child_care
  checkroom local_bar savings work build local_taxi train two_wheeler celebration
  volunteer_activism local_pharmacy local_laundry_service subscriptions payments
  credit_card account_balance local_parking lunch_dining icecream bakery_dining
  music_note menu_book electric_bolt water_drop sports_soccer travel_explore
)

fetch() {
  curl -sfL "$BASE/$1/materialsymbolsrounded/$1$2_24px.xml" -o "$3.xml" || { echo "failed: $1$2" >&2; exit 1; }
  # No AppCompat in this app: point the tint at the framework attribute instead.
  sed -i 's|?attr/colorControlNormal|?android:attr/colorControlNormal|' "$3.xml"
}

for entry in "${ICONS[@]}"; do
  name=${entry%%:*}
  fetch "$name" "" "ic_$name"
  [[ $entry == *:fill ]] && fetch "$name" "_fill1" "ic_${name}_filled"
done
echo "Fetched ${#ICONS[@]} icons."
