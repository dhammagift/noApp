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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.noapp.container.data.ConfigStore
import com.noapp.container.model.AppConfig
import com.noapp.container.model.AppMode

private const val GITHUB_URL = "https://github.com/dhammagift/noApp"

// TODO: replace with a real hosted privacy policy page before publishing to the Play Store
private const val PRIVACY_POLICY_URL = "https://github.com/dhammagift/noApp/blob/main/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    onImportConfig: (AppConfig) -> Unit,
    onModeChanged: (AppMode) -> Unit,
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
            ListItem(
                headlineContent = { Text("Tap opens") },
                supportingContent = {
                    Text(
                        if (config.mode == AppMode.LIST) "A list of your items, in your order (default)"
                        else "The first item directly"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = config.mode == AppMode.LIST,
                        onCheckedChange = { onModeChanged(if (it) AppMode.LIST else AppMode.DIRECT) }
                    )
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
                headlineContent = { Text("Export config") },
                modifier = Modifier.clickable { exportLauncher.launch("noapp-config.json") }
            )
            ListItem(
                headlineContent = { Text("Import config") },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) }
            )
        }
    }
}
