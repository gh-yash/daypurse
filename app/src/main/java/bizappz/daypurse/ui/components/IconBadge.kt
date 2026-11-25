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
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.ui.category.CategoryIcons

/** Rounded-square tinted icon, shared by categories and budgets. */
@Composable
fun IconBadge(iconKey: String?, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.16f), RoundedCornerShape(percent = 32)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(CategoryIcons.forKey(iconKey)), null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A budget's colour; 0 means "follow the app accent". */
@Composable
fun Budget.tint(): Color = if (color == 0) MaterialTheme.colorScheme.primary else Color(color)

@Composable
fun BudgetBadge(budget: Budget, modifier: Modifier = Modifier, size: Dp = 40.dp) =
    IconBadge(budget.iconKey, budget.tint(), modifier, size)
