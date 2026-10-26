package com.tgfinance.app.ui.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tgfinance.app.R
import com.tgfinance.app.data.sync.SyncStatus
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.core.sync.SyncException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Small, unobtrusive sync state for the top bar: last sync time, running, error, offline. */
@Composable
fun SyncStatusIndicator(status: SyncStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!status.enabled) return
    val color = when {
        status.error != null -> MaterialTheme.finance.warning
        !status.online -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    TextButton(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                status.running -> {
                    val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
                        initialValue = 0f,
                        targetValue = -360f,
                        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                        label = "rotation",
                    )
                    Icon(Icons.Outlined.Sync, contentDescription = null, tint = color, modifier = Modifier.size(18.dp).rotate(rotation))
                }
                !status.online -> Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                status.error != null -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                else -> Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(
                text = syncStatusText(status),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun syncStatusText(status: SyncStatus): String = when {
    status.running -> stringResource(R.string.sync_status_running)
    !status.online -> stringResource(R.string.sync_status_offline)
    status.error != null -> stringResource(R.string.sync_status_error)
    status.lastSyncAt == 0L -> stringResource(R.string.sync_status_never)
    else -> stringResource(R.string.sync_status_last, formatTime(status.lastSyncAt))
}

fun formatTime(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val formatter = if (time.toLocalDate() == java.time.LocalDate.now()) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    }
    return time.format(formatter)
}

@Preview
@Composable
private fun SyncStatusPreview() {
    TgFinanceTheme {
        androidx.compose.foundation.layout.Column {
            SyncStatusIndicator(SyncStatus(enabled = true, lastSyncAt = System.currentTimeMillis()), {})
            SyncStatusIndicator(SyncStatus(enabled = true, running = true), {})
            SyncStatusIndicator(SyncStatus(enabled = true, online = false), {})
            SyncStatusIndicator(SyncStatus(enabled = true, error = SyncException.Kind.NETWORK), {})
        }
    }
}
