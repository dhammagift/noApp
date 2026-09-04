package com.noapp.container.ui

/**
 * A one-shot Snackbar message MainActivity hands to whichever screen is showing. The screen
 * calls back through `onHintShown` the moment it starts showing it, and MainActivity clears it —
 * so a screen entered later (Settings, or Config again after Back) with the same hint still set
 * doesn't replay it. [id] makes two identical texts in a row still count as two hints.
 */
data class UiHint(val id: Int, val text: String)
