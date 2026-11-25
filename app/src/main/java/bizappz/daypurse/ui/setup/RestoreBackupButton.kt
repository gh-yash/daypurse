package bizappz.daypurse.ui.setup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.R
import bizappz.daypurse.data.backup.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * First launch only: restore a backup instead of setting up from scratch (e.g. after moving to a
 * new phone, or switching between the F-Droid and Play builds, which can't update each other).
 * Once the backup's budget is in, the app opens by itself.
 */
@Composable
fun RestoreBackupButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val failed = stringResource(R.string.backup_failed)
    val container = (context.applicationContext as DayPurseApplication).container
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        ?: throw IOException("Can't open file")
                    val meta = container.backupManager.importJson(text)
                    container.settingsRepository.setCurrency(meta.currencyCode)
                    container.settingsRepository.setOnboardingCompleted(true)
                }.exceptionOrNull()
            }
            busy = false
            if (error != null) {
                val detail = (error as? BackupException)?.message ?: error.message
                Toast.makeText(context, failed + (detail?.let { ": $it" } ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
    TextButton(
        onClick = { pick.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        enabled = !busy,
        modifier = modifier,
    ) {
        Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.setup_restore))
    }
}
