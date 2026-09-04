package com.noapp.container.ui

/**
 * A one-shot Snackbar message MainActivity hands to whichever screen is showing. The screen
 * calls back through `onHintShown` once it's done showing it — or when it leaves composition
 * while still showing it — and MainActivity clears it, so a screen entered later (Settings, or
 * Config again after Back) doesn't replay it. Not before: the screen's effect is keyed on [id],
 * and clearing the hint early would cancel the very coroutine that's showing the snackbar.
 * [id] makes two identical texts in a row still count as two hints.
 */
data class UiHint(val id: Int, val text: String)
