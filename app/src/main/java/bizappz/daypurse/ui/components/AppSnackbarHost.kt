package bizappz.daypurse.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Snackbar host whose snackbars can be swiped away sideways. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        // New state per snackbar so a swiped one doesn't leave the next one offset.
        key(data) {
            val swipe = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                state = swipe,
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) { Snackbar(data) }
        }
    }
}
