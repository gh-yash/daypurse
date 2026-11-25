package bizappz.daypurse.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import bizappz.daypurse.R

/** Bottom-bar destinations. Routes are plain strings to avoid extra plugins. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    HOME("home", R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_filled),
    HISTORY("history", R.string.nav_history, R.drawable.ic_receipt_long, R.drawable.ic_receipt_long_filled),
    INSIGHTS("insights", R.string.nav_insights, R.drawable.ic_pie_chart, R.drawable.ic_pie_chart_filled),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
}
