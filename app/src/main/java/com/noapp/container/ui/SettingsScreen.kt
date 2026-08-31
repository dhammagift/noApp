package com.noapp.container.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutManagerCompat
import com.noapp.container.data.ConfigStore
import com.noapp.container.model.AppConfig
import com.noapp.container.shortcuts.ShortcutSync

private const val GITHUB_URL = "https://github.com/dhammagift/noApp"

// TODO: replace with a real hosted privacy policy page before publishing to the Play Store
private const val PRIVACY_POLICY_URL = "https://github.com/dhammagift/noApp/blob/main/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    onImportConfig: (AppConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(ConfigStore.toJson(config).toByteArray()) }
        }.onSuccess {
            Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Empty file")
            ConfigStore.fromJson(json)
        }.onSuccess { imported ->
            onImportConfig(imported)
            Toast.makeText(context, "Imported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val firstSlot = config.slots.getOrNull(0)?.takeIf { it.isConfigured }
            ListItem(
                headlineContent = { Text("Pin “${firstSlot?.label?.ifBlank { "your first item" } ?: "your first item"}” to home screen") },
                supportingContent = {
                    Text(
                        if (firstSlot != null) "Adds a separate icon that launches it directly, next to No App"
                        else "Configure your first item to enable this"
                    )
                },
                modifier = Modifier.clickable(enabled = firstSlot != null) {
                    val slot = firstSlot ?: return@clickable
                    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                        ShortcutManagerCompat.requestPinShortcut(context, ShortcutSync.shortcutFor(context, slot), null)
                    } else {
                        Toast.makeText(context, "Your launcher doesn't support pinning shortcuts", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Check out No App: $GITHUB_URL"),
                            null
                        )
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Rate app") },
                leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                modifier = Modifier.clickable {
                    val pkg = context.packageName
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                    }.onFailure {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                        )
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Source code") },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                }
            )
            ListItem(
                headlineContent = { Text("Privacy policy") },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Backup") },
                modifier = Modifier.clickable { exportLauncher.launch("noapp-config.json") }
            )
            ListItem(
                headlineContent = { Text("Restore") },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) }
            )
        }
    }
}
