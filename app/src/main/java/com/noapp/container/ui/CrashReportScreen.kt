package com.noapp.container.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noapp.container.DebugLog

private const val CRASH_REPORT_EMAIL = "agiftofdhamma@gmail.com"

/**
 * Shown instead of the normal screen on the launch right after a crash, so the log (including
 * the crash itself) can be grabbed straight from the phone — no ADB/PC needed. See CrashLogger
 * for where the "show this next launch" flag comes from and DebugLog for the log content itself.
 */
@Composable
fun CrashReportScreen(logText: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Not App crashed last time", style = MaterialTheme.typography.titleLarge)
            Text(
                "Send this log so the crash can be diagnosed.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = {
                runCatching {
                    context.startActivity(DebugLog.emailIntent(context, CRASH_REPORT_EMAIL, "Not App crash report"))
                }
            }) { Text("Email crash report") }
            OutlinedButton(onClick = { runCatching { context.startActivity(DebugLog.shareIntent(context)) } }) {
                Text("Share log")
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Not App log", logText))
            }) { Text("Copy log text") }
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            Text(logText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
