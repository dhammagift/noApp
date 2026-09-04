package com.noapp.container.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.noapp.container.R
import com.noapp.container.model.AppMode

/**
 * The text for the restart-hint Snackbar (see MainActivity's persist()/wouldRiskTeardown) —
 * names the mode the next cold start will land in. Correct even when [mode] itself wasn't what
 * changed (e.g. an icon-variant change while already in Direct/Mix): the next launch really will
 * be in that mode either way, so naming it is never wrong, just occasionally not the whole story.
 */
@Composable
fun restartHintMessage(mode: AppMode): String = when (mode) {
    AppMode.LIST -> stringResource(R.string.restart_hint_message_list)
    AppMode.DIRECT -> stringResource(R.string.restart_hint_message_direct)
    AppMode.MIX -> stringResource(R.string.restart_hint_message_mix)
}
