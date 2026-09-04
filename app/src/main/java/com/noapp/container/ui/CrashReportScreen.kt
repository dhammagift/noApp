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

/**
 * Shown instead of the normal screen on the launch right after a crash, so the crash text can
 * be grabbed straight from the phone — no ADB/PC needed. See CrashLogger for where this is
 * written and read.
 */
@Composable
fun CrashReportScreen(crashText: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Not App crashed last time", style = MaterialTheme.typography.titleLarge)
            Text(
                "Copy this and send it over so the crash can be diagnosed.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(crashText, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Not App crash", crashText))
            }) { Text("Copy crash text") }
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
