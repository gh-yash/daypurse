package bizappz.daypurse.ui.payments

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.AppSettings
import bizappz.daypurse.di.AppContainer
import bizappz.daypurse.domain.payments.PaymentSource
import bizappz.daypurse.domain.payments.PaymentSources
import bizappz.daypurse.payments.PaymentListener
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaymentSettingsViewModel(private val c: AppContainer, context: Context) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Supported apps that are installed on this phone. */
    val installed: List<PaymentSource> = installedSources(context)

    fun setEnabled(context: Context, on: Boolean) = viewModelScope.launch {
        c.settingsRepository.setPaymentsEnabled(on)
        PaymentListener.sync(context, on)
    }

    fun setNotify(on: Boolean) = viewModelScope.launch { c.settingsRepository.setPaymentsNotify(on) }

    fun setSource(source: PaymentSource, on: Boolean) =
        viewModelScope.launch { c.settingsRepository.setPaymentSource(source.packageName, source.label, on) }

    companion object {
        fun installedSources(context: Context): List<PaymentSource> = PaymentSources.known.filter {
            try {
                context.packageManager.getPackageInfo(it.packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
