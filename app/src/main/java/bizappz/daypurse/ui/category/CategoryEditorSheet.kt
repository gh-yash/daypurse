package bizappz.daypurse.ui.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bizappz.daypurse.R
import bizappz.daypurse.data.db.CategoryPalette
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.ui.components.CategoryBadge

/** Add or edit a category: name, colour and icon. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorSheet(
    existing: Category?,
    onSave: (name: String, iconKey: String, color: Int, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var iconKey by rememberSaveable { mutableStateOf(existing?.iconKey ?: "category") }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: CategoryPalette.colors.first()) }
    var nameError by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryBadge(Category(name = name, iconKey = iconKey, color = color), size = 56.dp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); nameError = false },
                    label = { Text(stringResource(R.string.category_name)) },
                    isError = nameError,
                    supportingText = if (nameError) ({ Text(stringResource(R.string.category_name_error)) }) else null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(stringResource(R.string.category_color), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryPalette.colors.forEach { c ->
                    val selected = c == color
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(Color(c), CircleShape)
                            .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                            .clickable { color = c },
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
                            .background(
                                if (selected) Color(color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                CircleShape,
                            )
                            .clickable { iconKey = key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(res),
                            contentDescription = key,
                            tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSave(name, iconKey, color) { ok -> if (ok) onDismiss() else nameError = true }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
