package com.keybook.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.keybook.vault.ui.KeybookRoot
import com.keybook.vault.ui.theme.KeybookTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        // Release builds never expose the vault in the recents thumbnail or screenshots.
        // Debug builds keep screenshots available for development and visual verification.
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContent {
            KeybookTheme {
                KeybookRoot()
            }
        }
    }
}
