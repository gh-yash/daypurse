package bizappz.daypurse.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bizappz.daypurse.ui.theme.AppTheme

/** Rounded card on the page background (white in light mode, raised grey in dark). */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = AppTheme.colors.card) {
        Column(content = content)
    }
}

/** Small heading above a card or group. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * Makes consecutive LazyColumn rows look like one rounded card: the first row gets the top
 * corners, the last the bottom ones. Keeps per-row item animations working.
 */
@Composable
fun Modifier.groupedRow(index: Int, count: Int): Modifier {
    val r = 24.dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp,
        topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp,
        bottomEnd = if (index == count - 1) r else 0.dp,
    )
    return this
        .padding(horizontal = 16.dp)
        .clip(shape)
        .background(AppTheme.colors.card)
}
