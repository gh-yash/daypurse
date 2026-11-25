package bizappz.daypurse.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bizappz.daypurse.R
import bizappz.daypurse.data.db.CategoryPalette
import bizappz.daypurse.ui.category.CategoryIcons

/** Pick an icon and colour (colour 0 = app accent). Changes apply as you tap. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IconColorSheet(iconKey: String, color: Int, onPick: (String, Int) -> Unit, onDismiss: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = if (color == 0) accent else Color(color)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.category_color), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (listOf(0) + CategoryPalette.colors).forEach { c ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(if (c == 0) accent else Color(c), CircleShape)
                            .then(if (c == color) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                            .clickable { onPick(iconKey, c) },
                    )
                }
            }
            Text(stringResource(R.string.category_icon), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryIcons.all.forEach { (key, res) ->
                    val selected = key == iconKey
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(if (selected) tint.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .clickable { onPick(key, color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(res), key, tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
