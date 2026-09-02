package com.noapp.container

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.noapp.container.data.ConfigStore
import com.noapp.container.shortcuts.EXTRA_OPEN_CONFIG
import com.noapp.container.ui.QuickPickSheet
import com.noapp.container.ui.theme.NoAppTheme

const val EXTRA_SHARED_TEXT = "extra_shared_text"

/**
 * A separate, translucent Activity (see Theme.NoApp.Transparent) just for the
 * plain-tap LIST-mode picker and the share-target sheet — so it renders as a
 * popup over whatever was already on screen instead of a full opaque app switch.
 * MainActivity keeps everything else (Config/Settings/edit screens).
 */
class QuickPickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent.getStringExtra(EXTRA_SHARED_TEXT)
        val slots = ConfigStore.load(this).slots.filter { it.isConfigured }

        setContent {
            NoAppTheme {
                QuickPickSheet(
                    slots = slots,
                    sharedText = sharedText,
                    onConfigure = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(EXTRA_OPEN_CONFIG, true)
                        )
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
