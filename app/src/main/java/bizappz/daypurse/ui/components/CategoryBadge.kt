package bizappz.daypurse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.ui.category.CategoryIcons

/** Rounded-square coloured icon for a category; a neutral one for uncategorised spends. */
@Composable
fun CategoryBadge(category: Category?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val tint = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .size(size)
            .background(tint.copy(alpha = 0.16f), RoundedCornerShape(percent = 32)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CategoryIcons.forKey(category?.iconKey)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
