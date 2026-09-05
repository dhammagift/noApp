package com.noapp.container.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import com.noapp.container.DebugLog
import com.noapp.container.R
import com.noapp.container.data.ConfigStore
import com.noapp.container.icon.DownsampledImage
import com.noapp.container.icon.ICON_VARIANTS
import com.noapp.container.icon.IconVariant
import com.noapp.container.icon.enabledLauncherComponent
import com.noapp.container.model.AppConfig
import com.noapp.container.model.AppTheme
import com.noapp.container.recents.RecentApps
import com.noapp.container.shortcuts.QuickPickPeekOverlayService
import com.noapp.container.shortcuts.ShortcutSync

private const val GITHUB_URL = "https://github.com/dhammagift/notApp"
private const val GITHUB_RELEASES_URL = "$GITHUB_URL/releases/latest"

// TODO: replace with a real hosted privacy policy page before publishing to the Play Store
private const val PRIVACY_POLICY_URL = "https://github.com/dhammagift/notApp/blob/main/PRIVACY.md"

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
    else -> id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    hint: UiHint?,
    onHintShown: (UiHint) -> Unit,
    onImportConfig: (AppConfig) -> Unit,
    onUseAllSlotsInDirectModeChanged: (Boolean) -> Unit,
    onIconVariantChanged: (String) -> Unit,
    onShowPeekBubbleChanged: (Boolean) -> Unit,
    onPeekBubbleReturnsChanged: (Boolean) -> Unit,
    onPeekBubbleSizeChanged: (Float) -> Unit,
    onPeekBubbleAlphaChanged: (Float) -> Unit,
    onPeekBubbleDockPeekChanged: (Float) -> Unit,
    onShowRecentAppsChanged: (Boolean) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // See ConfigScreen's own copy of this, and UiHint: consumed only after it's been shown.
    LaunchedEffect(hint?.id) {
        val pending = hint ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(pending.text)
        } finally {
            onHintShown(pending)
        }
    }

    // Cheap insurance against the MIX/LIST peek bubble ever being stuck somewhere the user
    // can't reach (e.g. after a display change while it was showing): visiting Settings —
    // entering or leaving — always resets it back to its default corner.
    DisposableEffect(Unit) {
        QuickPickPeekOverlayService.resetSavedPosition(context)
        onDispose { QuickPickPeekOverlayService.resetSavedPosition(context) }
    }

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

    var showOverlayExplainer by remember { mutableStateOf(false) }
    var showPeekOverlayExplainer by remember { mutableStateOf(false) }
    var showUsageAccessExplainer by remember { mutableStateOf(false) }

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
            // A backed-up config asking for one of these needs the same permission check the
            // normal toggle-on path does — the config itself already stays off without it (see
            // MainActivity's onConfigImported), so this is just about actually asking, not
            // leaving the user to wonder why the switch didn't move.
            if (imported.useAllSlotsInDirectMode && !AndroidSettings.canDrawOverlays(context)) showOverlayExplainer = true
            if (imported.showPeekBubble && !AndroidSettings.canDrawOverlays(context)) showPeekOverlayExplainer = true
            if (imported.showRecentApps && !RecentApps.hasUsageAccess(context)) showUsageAccessExplainer = true
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.toast_import_failed, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (AndroidSettings.canDrawOverlays(context)) {
            onUseAllSlotsInDirectModeChanged(true)
        }
        // Declined: leave the option off, config was never actually flipped.
    }

    val peekOverlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (AndroidSettings.canDrawOverlays(context)) {
            onShowPeekBubbleChanged(true)
        }
        // Declined: leave the option off, config was never actually flipped.
    }

    val usageAccessSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (RecentApps.hasUsageAccess(context)) {
            onShowRecentAppsChanged(true)
        }
        // Declined (or the user just didn't find/enable it): leave the option off.
    }

    Scaffold(
        // See ConfigScreen's own copy of this override for why: Material3's default Snackbar
        // colors invert the theme (light-on-dark even here), which looks out of place against
        // this app's all-dark surfaces.
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_share_text, GITHUB_URL)),
                                null
                            )
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.settings_share))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
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
                        DownsampledImage(
                            resId = variant.previewRes,
                            size = 56.dp,
                            contentDescription = variant.displayName(),
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
            ListItem(headlineContent = { Text(stringResource(R.string.settings_theme)) })
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                AppTheme.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = config.theme == candidate,
                        onClick = { onThemeChanged(candidate) },
                        shape = SegmentedButtonDefaults.itemShape(index, AppTheme.entries.size)
                    ) {
                        Text(
                            stringResource(
                                when (candidate) {
                                    AppTheme.SYSTEM -> R.string.settings_theme_system
                                    AppTheme.LIGHT -> R.string.settings_theme_light
                                    AppTheme.DARK -> R.string.settings_theme_dark
                                }
                            )
                        )
                    }
                }
            }
            HorizontalDivider()
            // Android's per-app language override only exists as of API 33 (Tiramisu) — no
            // custom in-app locale switcher below that, the app just follows the system language.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
                HorizontalDivider()
            }
            val firstSlot = config.slots.getOrNull(0)?.takeIf { it.isConfigured }
            // All three rows below stay visible in every mode (not just the mode they affect) so
            // Settings doesn't change shape as you switch modes — each one's own copy says which
            // mode(s) it works in instead.
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_recent_apps)) },
                supportingContent = { Text(stringResource(R.string.settings_show_recent_apps_hint)) },
                trailingContent = {
                    Switch(
                        checked = config.showRecentApps,
                        onCheckedChange = { turningOn ->
                            when {
                                !turningOn -> onShowRecentAppsChanged(false)
                                RecentApps.hasUsageAccess(context) -> onShowRecentAppsChanged(true)
                                else -> showUsageAccessExplainer = true
                            }
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_peek_bubble)) },
                supportingContent = { Text(stringResource(R.string.settings_show_peek_bubble_hint)) },
                trailingContent = {
                    Switch(
                        checked = config.showPeekBubble,
                        onCheckedChange = { turningOn ->
                            when {
                                !turningOn -> onShowPeekBubbleChanged(false)
                                AndroidSettings.canDrawOverlays(context) -> onShowPeekBubbleChanged(true)
                                else -> showPeekOverlayExplainer = true
                            }
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_peek_bubble_returns)) },
                supportingContent = { Text(stringResource(R.string.settings_peek_bubble_returns_hint)) },
                trailingContent = {
                    Switch(
                        checked = config.peekBubbleReturns,
                        onCheckedChange = onPeekBubbleReturnsChanged
                    )
                }
            )
            HorizontalDivider()
            // Drafts are committed on release, not per tick: each commit persists the config and
            // re-syncs the OS shortcuts, which is far too much for every pixel of a drag. The
            // preview alongside follows the drafts, so what you see is what you'll get.
            var sizeDraft by remember(config.peekBubbleSize) { mutableFloatStateOf(config.peekBubbleSize) }
            var alphaDraft by remember(config.peekBubbleAlpha) { mutableFloatStateOf(config.peekBubbleAlpha) }
            var dockPeekDraft by remember(config.peekBubbleDockPeek) { mutableFloatStateOf(config.peekBubbleDockPeek) }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_peek_size), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_peek_size_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = sizeDraft,
                        onValueChange = { sizeDraft = it },
                        onValueChangeFinished = { onPeekBubbleSizeChanged(sizeDraft) },
                        valueRange = ConfigStore.PEEK_SIZE_MIN..ConfigStore.PEEK_SIZE_MAX
                    )
                    Text(stringResource(R.string.settings_peek_alpha), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_peek_alpha_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = alphaDraft,
                        onValueChange = { alphaDraft = it },
                        onValueChangeFinished = { onPeekBubbleAlphaChanged(alphaDraft) },
                        valueRange = ConfigStore.PEEK_ALPHA_MIN..ConfigStore.PEEK_ALPHA_MAX
                    )
                    Text(stringResource(R.string.settings_peek_dock_peek), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_peek_dock_peek_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = dockPeekDraft,
                        onValueChange = { dockPeekDraft = it },
                        onValueChangeFinished = { onPeekBubbleDockPeekChanged(dockPeekDraft) },
                        valueRange = ConfigStore.PEEK_DOCK_MIN..ConfigStore.PEEK_DOCK_MAX
                    )
                }
                PeekBubblePreview(size = sizeDraft, alpha = alphaDraft, dockPeek = dockPeekDraft, modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalDivider()
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
                        val component = enabledLauncherComponent(context)
                        ShortcutManagerCompat.requestPinShortcut(context, ShortcutSync.shortcutFor(context, slot, component), null)
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_pin_unsupported), Toast.LENGTH_SHORT).show()
                    }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_rate_app)) },
                supportingContent = { Text(stringResource(R.string.settings_rate_app_hint)) },
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
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                }
            )
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = { exportLauncher.launch("noapp-config.json") }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_backup), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_backup))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_restore), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_restore))
                }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_latest_release)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_github), contentDescription = null) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL)))
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_debug_log)) },
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(DebugLog.shareIntent(context)) }
                }
            )
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

    if (showPeekOverlayExplainer) {
        PeekOverlayPermissionDialog(
            onContinue = {
                showPeekOverlayExplainer = false
                peekOverlaySettingsLauncher.launch(
                    Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            },
            onDismiss = { showPeekOverlayExplainer = false }
        )
    }

    if (showUsageAccessExplainer) {
        AlertDialog(
            onDismissRequest = { showUsageAccessExplainer = false },
            title = { Text(stringResource(R.string.settings_usage_access_dialog_title)) },
            text = { Text(stringResource(R.string.settings_usage_access_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showUsageAccessExplainer = false
                    usageAccessSettingsLauncher.launch(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
                }) { Text(stringResource(R.string.settings_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showUsageAccessExplainer = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }
}

/**
 * Live preview of the floating button at the drafted size/opacity — free-floating up top, and
 * tucked into the preview's own right edge below, the way it docks on the real screen (same
 * sliver width, same 90% scale and 60% opacity ratio as QuickPickPeekOverlayService).
 */
@Composable
private fun PeekBubblePreview(size: Float, alpha: Float, dockPeek: Float, modifier: Modifier = Modifier) {
    val bubble = (PEEK_BUBBLE_BASE_DP * size).dp
    val peek = bubble * dockPeek
    Box(
        modifier
            .width(116.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp))
            // Darkest surface of the theme (near-black in dark, white in light) rather than
            // surfaceVariant: the bubble's own grey blended into that in the dark theme.
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        PreviewBubble(bubble, alpha, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        PreviewBubble(
            bubble,
            alpha * PEEK_DOCK_ALPHA_FACTOR,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp)
                .offset(x = bubble - peek)
                .graphicsLayer { scaleX = PEEK_DOCK_SCALE; scaleY = PEEK_DOCK_SCALE }
        )
    }
}

@Composable
private fun PreviewBubble(size: Dp, alpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xCC3C4043), CircleShape)
            .padding(size * 0.24f),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.ic_list_bubble), contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
    }
}

// Mirrors QuickPickPeekOverlayService's geometry so the preview is honest.
private const val PEEK_BUBBLE_BASE_DP = 48
private const val PEEK_DOCK_SCALE = 0.9f
private const val PEEK_DOCK_ALPHA_FACTOR = 0.6f
