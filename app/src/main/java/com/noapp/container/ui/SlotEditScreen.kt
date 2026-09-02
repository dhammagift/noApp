package com.noapp.container.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noapp.container.R
import com.noapp.container.icon.displayName
import com.noapp.container.model.AppMode
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotEditScreen(mode: AppMode, slot: ShortcutSlot, onSave: (ShortcutSlot) -> Unit, onCancel: () -> Unit) {
    var type by remember { mutableStateOf(slot.type) }
    var label by remember { mutableStateOf(slot.label) }
    var color by remember { mutableStateOf(slot.color) }
    var param by remember { mutableStateOf(slot.param) }
    var customIcon by remember { mutableStateOf(slot.customIcon) }
    // Landing here fresh on an unconfigured App slot (from the "+" menu) — skip the
    // extra "Choose app" tap and open the picker immediately.
    var showAppPicker by remember { mutableStateOf(slot.type == SlotType.APP && slot.param.isBlank()) }

    val title = if (mode != AppMode.LIST && slot.id == 0) {
        stringResource(R.string.slot_edit_title_main)
    } else {
        stringResource(R.string.slot_edit_title_item, slot.id + 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.slot_edit_cancel_desc))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(stringResource(R.string.slot_edit_type_label), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlotType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t; param = "" },
                        label = { Text(t.displayName()) }
                    )
                }
            }

            val sharedTextHint = stringResource(R.string.slot_edit_shared_text_hint)
            when (type) {
                SlotType.APP -> OutlinedButton(onClick = { showAppPicker = true }) {
                    Text(param.ifBlank { stringResource(R.string.slot_edit_choose_app) })
                }
                SlotType.URL -> OutlinedTextField(
                    value = param,
                    onValueChange = { param = it },
                    label = { Text(stringResource(R.string.slot_edit_url_label)) },
                    placeholder = { Text(stringResource(R.string.slot_edit_url_placeholder)) },
                    supportingText = { Text(sharedTextHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                SlotType.INTENT, SlotType.CUSTOM -> OutlinedTextField(
                    value = param,
                    onValueChange = { param = it },
                    label = { Text(stringResource(R.string.slot_edit_intent_label)) },
                    placeholder = { Text(stringResource(R.string.slot_edit_intent_placeholder)) },
                    supportingText = { Text(sharedTextHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                null -> {}
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.slot_edit_label_field)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = customIcon,
                onValueChange = { customIcon = it },
                label = { Text(stringResource(R.string.slot_edit_icon_field)) },
                placeholder = { Text("🚀") },
                supportingText = {
                    Text(
                        stringResource(
                            if (type == SlotType.APP) R.string.slot_edit_icon_hint_app
                            else R.string.slot_edit_icon_hint_other
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.slot_edit_color_label), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShortcutSlot.PALETTE.forEach { hex ->
                    val selected = color == hex
                    Box(
                        Modifier
                            .size(if (selected) 36.dp else 32.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .clickable { color = hex }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = type != null && param.isNotBlank(),
                    onClick = {
                        onSave(slot.copy(type = type, label = label, color = color, param = param, customIcon = customIcon))
                    }
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            multiSelect = false,
            onDismiss = { showAppPicker = false },
            onConfirm = { picks ->
                val (pkg, appLabel) = picks.first()
                param = pkg
                if (label.isBlank()) label = appLabel
                showAppPicker = false
            }
        )
    }
}
