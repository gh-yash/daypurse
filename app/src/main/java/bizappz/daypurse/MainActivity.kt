package bizappz.daypurse

import android.content.Intent
import android.graphics.Color
import bizappz.daypurse.surfaces.LaunchActions
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import bizappz.daypurse.data.model.AppSettings
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.ThemeMode
import bizappz.daypurse.security.LockScreen
import bizappz.daypurse.ui.DayPurseApp
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import bizappz.daypurse.ui.format.MoneyFormatter
import bizappz.daypurse.ui.setup.BudgetSetupScreen
import bizappz.daypurse.ui.theme.DayPurseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Wrapper so "not loaded yet" (null) differs from "loaded, no budget" (Loaded(null)). */
private data class Loaded<T>(val value: T)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DayPurseApplication).container
        if (savedInstanceState == null) LaunchActions.from(this, intent)?.let { container.launchActions.value = it }
        val settingsFlow = container.settingsRepository.settings
        val budgetFlow = container.budgetEngine.primaryBudget.map { Loaded(it) }
        var ready = false

        // Keep the system splash screen up until settings and budget are read, so the first
        // frame already has the right theme and screen (no flash).
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!ready) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )

        // Keep "today" correct: refresh on resume and right after midnight while visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    container.todayProvider.refresh()
                    delay(container.todayProvider.millisUntilTomorrow())
                }
            }
        }

        setContent {
            val settings: AppSettings? by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val budget: Loaded<Budget?>? by budgetFlow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            val b = budget ?: return@setContent
            SideEffect { ready = true }

            val unlocked by container.appLock.unlocked.collectAsStateWithLifecycle()
            SideEffect {
                // Hide app content from the recent-apps preview while the lock is on.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!s.appLockEnabled)
            }

            // Status/navigation bar icons must follow the app's theme, not the system's.
            val dark = when (s.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            val money = remember(s.currencyCode) { MoneyFormatter(s.currencyCode) }
            DayPurseTheme(themeMode = s.themeMode, accent = s.accent, pureBlack = s.pureBlack) {
                CompositionLocalProvider(LocalMoneyFormatter provides money) {
                    if (s.appLockEnabled && !unlocked) {
                        LockScreen(container.appLock)
                    } else if (b.value == null) {
                        // First launch: set up a budget before anything else.
                        BudgetSetupScreen(onDone = {})
                    } else {
                        DayPurseApp()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LaunchActions.from(this, intent)?.let { (application as DayPurseApplication).container.launchActions.value = it }
    }

    override fun onStart() {
        super.onStart()
        (application as DayPurseApplication).container.appLock.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        (application as DayPurseApplication).container.appLock.onAppBackgrounded()
    }
}
