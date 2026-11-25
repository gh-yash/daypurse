package bizappz.daypurse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import bizappz.daypurse.R
import bizappz.daypurse.data.model.ExpenseWithCategory
import bizappz.daypurse.ui.format.LocalMoneyFormatter

@Composable
fun SpendRow(
    item: ExpenseWithCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = item.expense.note ?: item.expense.payee,
) {
    val money = LocalMoneyFormatter.current
    ListItem(
        leadingContent = { CategoryBadge(item.category) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.category?.name ?: stringResource(R.string.category_none),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (item.expense.recurringId != null) {
                    Icon(
                        painterResource(R.drawable.ic_repeat), stringResource(R.string.spend_repeats_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
        },
        supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Text(
                money.format(item.expense.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}
