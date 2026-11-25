package bizappz.daypurse.ui.components

import androidx.compose.foundation.layout.Column
import bizappz.daypurse.ui.format.currentLocale
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import bizappz.daypurse.R
import bizappz.daypurse.ui.format.MoneyFormatter
import java.util.Locale

/** Searchable currency list with a "no currency" option at the top. Fully offline (JDK data). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    selectedCode: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    val all = remember(locale) { MoneyFormatter.availableCurrencies(locale) }
    val localCode = remember(locale) { MoneyFormatter.localeCurrencyCode(locale) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, all) {
        val q = query.trim()
        val matches = if (q.isEmpty()) all else all.filter {
            it.currencyCode.contains(q, true) || it.getDisplayName(locale).contains(q, true) ||
                it.getSymbol(locale).contains(q, true)
        }
        // Device-locale currency first.
        matches.sortedByDescending { it.currencyCode == localCode }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.currency_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                placeholder = { Text(stringResource(R.string.currency_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                if (query.isBlank()) {
                    item(key = "none") {
                        CurrencyRow(
                            title = stringResource(R.string.currency_none),
                            subtitle = stringResource(R.string.currency_none_hint),
                            trailing = "123",
                            selected = selectedCode == null,
                            onClick = { onSelect(null) },
                        )
                    }
                }
                items(filtered, key = { it.currencyCode }) { c ->
                    CurrencyRow(
                        title = c.getDisplayName(locale),
                        subtitle = c.currencyCode,
                        trailing = c.getSymbol(locale),
                        selected = c.currencyCode == selectedCode,
                        onClick = { onSelect(c.currencyCode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(title: String, subtitle: String, trailing: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary)
            else Text(trailing, style = MaterialTheme.typography.titleMedium)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
