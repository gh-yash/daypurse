package bizappz.daypurse.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import bizappz.daypurse.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App lock using the platform BiometricPrompt (fingerprint/face, falling back to the screen
 * PIN/pattern). Uses the framework API directly, so no androidx.biometric/fragment dependency.
 */
class AppLock {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var backgroundedAt = 0L

    fun markUnlocked() { _unlocked.value = true }

    fun onAppBackgrounded() { backgroundedAt = SystemClock.elapsedRealtime() }

    /** Re-locks if the app was in the background longer than the grace period. */
    fun onAppForegrounded() {
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > GRACE_MS) _unlocked.value = false
    }

    fun authenticate(activity: Activity, onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    markUnlocked()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
    }

    companion object {
        private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        private const val GRACE_MS = 30_000L

        fun isAvailable(context: Context): Boolean =
            context.getSystemService(BiometricManager::class.java)
                .canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
