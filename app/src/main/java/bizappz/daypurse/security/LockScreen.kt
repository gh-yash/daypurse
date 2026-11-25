package bizappz.daypurse.security

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bizappz.daypurse.R

/** Covers the app until the user authenticates; prompts automatically on show. */
@Composable
fun LockScreen(appLock: AppLock) {
    val activity = LocalActivity.current
    val unlock = { activity?.let { appLock.authenticate(it, onSuccess = {}) } }
    LaunchedEffect(Unit) { unlock() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
            Button(onClick = { unlock() }) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
