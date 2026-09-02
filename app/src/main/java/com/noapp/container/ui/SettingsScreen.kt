package com.noapp.container.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.compose.foundation.Image
import com.noapp.container.R
import com.noapp.container.data.ConfigStore
import com.noapp.container.icon.ICON_VARIANTS
import com.noapp.container.icon.IconVariant
import com.noapp.container.model.AppConfig
import com.noapp.container.model.AppMode
import com.noapp.container.shortcuts.ShortcutSync

private const val GITHUB_URL = "https://github.com/dhammagift/noApp"

// TODO: replace with a real hosted privacy policy page before publishing to the Play Store
private const val PRIVACY_POLICY_URL = "https://github.com/dhammagift/noApp/blob/main/PRIVACY.md"

@Composable
private fun IconVariant.displayName(): String = when (id) {
    "default" -> stringResource(R.string.icon_variant_default)
    "material" -> stringResource(R.string.icon_variant_material)
    "bolt" -> stringResource(R.string.icon_variant_bolt)
    "boost" -> stringResource(R.string.icon_variant_boost)
    "electric" -> stringResource(R.string.icon_variant_electric)
    "flash" -> stringResource(R.string.icon_variant_flash)
    "sankha_flat" -> stringResource(R.string.icon_variant_sankha_flat)
    "sankha_3d" -> stringResource(R.string.icon_variant_sankha_3d)
    "sankha_bckgr" -> stringResource(R.string.icon_variant_sankha_bckgr)
    else -> id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    onImportConfig: (AppConfig) -> Unit,
    onUseAllSlotsInDirectModeChanged: (Boolean) -> Unit,
    onIconVariantChanged: (String) -> Unit,
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
            Toast.makeText(context, context.getString(R.string.toast_exported), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.toast_export_failed, it.message), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString(R.string.toast_imported), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.toast_import_failed, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    var showOverlayExplainer by remember { mutableStateOf(false) }
    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (AndroidSettings.canDrawOverlays(context)) {
            onUseAllSlotsInDirectModeChanged(true)
        }
        // Declined: leave the option off, config was never actually flipped.
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ListItem(headlineContent = { Text(stringResource(R.string.settings_app_icon)) })
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                items(ICON_VARIANTS, key = { it.id }) { variant ->
                    val selected = variant.id == config.iconVariant
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onIconVariantChanged(variant.id) }
                    ) {
                        Image(
                            painter = painterResource(variant.previewRes),
                            contentDescription = variant.displayName(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Text(variant.displayName(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                stringResource(R.string.settings_app_icon_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            val firstSlot = config.slots.getOrNull(0)?.takeIf { it.isConfigured }
            if (config.mode == AppMode.DIRECT) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_use_all_slots)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (config.useAllSlotsInDirectMode) R.string.settings_use_all_slots_on
                                else R.string.settings_use_all_slots_off
                            )
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.useAllSlotsInDirectMode,
                            onCheckedChange = { turningOn ->
                                when {
                                    !turningOn -> onUseAllSlotsInDirectModeChanged(false)
                                    AndroidSettings.canDrawOverlays(context) -> onUseAllSlotsInDirectModeChanged(true)
                                    else -> showOverlayExplainer = true
                                }
                            }
                        )
                    }
                )
                HorizontalDivider()
            }
            val pinDefaultItem = stringResource(R.string.settings_pin_default_item)
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            R.string.settings_pin_title,
                            firstSlot?.label?.ifBlank { pinDefaultItem } ?: pinDefaultItem
                        )
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(
                            if (firstSlot != null) R.string.settings_pin_hint_enabled
                            else R.string.settings_pin_hint_disabled
                        )
                    )
                },
                modifier = Modifier.clickable(enabled = firstSlot != null) {
                    val slot = firstSlot ?: return@clickable
                    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                        ShortcutManagerCompat.requestPinShortcut(context, ShortcutSync.shortcutFor(context, slot), null)
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_pin_unsupported), Toast.LENGTH_SHORT).show()
                    }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_share)) },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_share_text, GITHUB_URL)),
                            null
                        )
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_rate_app)) },
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
                headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup)) },
                modifier = Modifier.clickable { exportLauncher.launch("noapp-config.json") }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_restore)) },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) }
            )
            HorizontalDivider()
            val pkgInfo = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(pkgInfo?.versionName ?: stringResource(R.string.settings_version_unknown)) }
            )
        }
    }

    if (showOverlayExplainer) {
        AlertDialog(
            onDismissRequest = { showOverlayExplainer = false },
            title = { Text(stringResource(R.string.settings_overlay_dialog_title)) },
            text = { Text(stringResource(R.string.settings_overlay_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayExplainer = false
                    overlaySettingsLauncher.launch(
                        Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.settings_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayExplainer = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }
}
