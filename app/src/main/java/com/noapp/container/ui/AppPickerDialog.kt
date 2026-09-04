package com.noapp.container.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noapp.container.R
import com.noapp.container.icon.AppIcon

private data class InstalledApp(val packageName: String, val label: String)

/**
 * Shared by single-slot editing (multiSelect = false, closes on first tap) and the
 * Config screen's bulk-fill (multiSelect = true, checkboxes + confirm button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    multiSelect: Boolean,
    maxSelection: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<String, String>>) -> Unit
) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val selected = remember { mutableStateListOf<InstalledApp>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (multiSelect) R.string.app_picker_title_fill else R.string.app_picker_title_choose)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.app_picker_search)) },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app in selected
                        ListItem(
                            leadingContent = { AppIcon(app.packageName, size = 40.dp) },
                            headlineContent = { Text(app.label) },
                            supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                            trailingContent = if (multiSelect) {
                                { Checkbox(checked = checked, onCheckedChange = null) }
                            } else null,
                            modifier = Modifier.clickable {
                                if (multiSelect) {
                                    if (checked) selected.remove(app)
                                    else if (selected.size < maxSelection) selected.add(app)
                                } else {
                                    onConfirm(listOf(app.packageName to app.label))
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (multiSelect) {
                TextButton(
                    onClick = { onConfirm(selected.map { it.packageName to it.label }) },
                    enabled = selected.isNotEmpty()
                ) { Text(pluralStringResource(R.plurals.app_picker_fill_slots, selected.size, selected.size)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        },
        dismissButton = if (multiSelect) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
        } else null
    )
}
