package bizappz.daypurse.ui.payments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.domain.payments.PaymentParser.SourceKind
import bizappz.daypurse.domain.payments.PaymentSource
import bizappz.daypurse.payments.PaymentListener
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.SectionTitle

/**
 * Turn payment spotting on or off, grant notification access, and pick which apps to read.
 * Explains plainly what is read and what is kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { PaymentSettingsViewModel(it, context.applicationContext) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var hasAccess by remember { mutableStateOf(PaymentListener.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        hasAccess = PaymentListener.hasAccess(context)
        settings?.let { if (it.paymentsEnabled) PaymentListener.sync(context, true) }
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun openAccessSettings() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, PaymentListener.component(context).flattenToString())
        runCatching { context.startActivity(detail) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    fun openAppInfo() = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
    )

    fun askToPost() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payments_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        val on = s.paymentsEnabled
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_notifications_active), null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.payments_intro), style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(18.dp).padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.payments_privacy), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                AppCard {
                    SwitchItem(stringResource(R.string.payments_switch), null, checked = on) { enable ->
                        vm.setEnabled(context, enable)
                        if (enable) {
                            askToPost()
                            if (!hasAccess) openAccessSettings()
                        }
                    }
                }
            }

            if (on && !hasAccess) {
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.payments_access_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.payments_access_text), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = ::openAccessSettings) { Text(stringResource(R.string.payments_access_open)) }
                            Text(stringResource(R.string.payments_restricted), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = ::openAppInfo) { Text(stringResource(R.string.payments_app_info)) }
                        }
                    }
                }
            }

            if (on && hasAccess) {
                item {
                    AppCard {
                        SwitchItem(stringResource(R.string.payments_notify), stringResource(R.string.payments_notify_hint), s.paymentsNotify) {
                            vm.setNotify(it)
                            if (it) askToPost()
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.payments_sources), Modifier.padding(start = 4.dp, top = 8.dp)) }
                if (vm.installed.isEmpty()) {
                    item {
                        Text(stringResource(R.string.payments_none_installed), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                listOf(
                    SourceKind.UPI_APP to R.string.payments_group_upi,
                    SourceKind.BANK_APP to R.string.payments_group_bank,
                    SourceKind.SMS to R.string.payments_group_sms,
                ).forEach { (kind, title) ->
                    val group = vm.installed.filter { it.kind == kind }
                    if (group.isNotEmpty()) {
                        item(key = kind.name) {
                            AppCard {
                                Text(stringResource(title), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 14.dp))
                                group.forEach { source -> SourceRow(source, source.packageName !in s.paymentSourcesOff) { vm.setSource(source, it) } }
                            }
                        }
                    }
                }
            }

            if (!on && hasAccess) {
                item {
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.payments_access_revoke), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = ::openAccessSettings) { Text(stringResource(R.string.payments_access_open)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchItem(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun SourceRow(source: PaymentSource, checked: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon = remember(source.packageName) {
        runCatching { context.packageManager.getApplicationIcon(source.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    ListItem(
        leadingContent = {
            if (icon != null) Image(icon, null, Modifier.size(36.dp))
            else Icon(painterResource(if (source.kind == SourceKind.SMS) R.drawable.ic_sms else R.drawable.ic_payments), null)
        },
        headlineContent = { Text(source.label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
    )
}
