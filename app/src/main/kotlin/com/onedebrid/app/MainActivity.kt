package com.onedebrid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity for OneDebrid.
 *
 * Responsibilities:
 * - Enable edge-to-edge display
 * - Apply the Material You theme
 * - Hand off to the Navigation host
 *
 * Contains no business logic. Navigation and all UI state live
 * in the composable tree below this entry point.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Must be called before setContent to correctly configure
        // system bar appearance across all supported API levels.
        enableEdgeToEdge()

        setContent {
            OneDebridTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Navigation host will be wired here in a later file.
                    // Placeholder until the nav graph is ready.
                }
            }
        }
    }
}

/**
 * Top-level Material You theme for OneDebrid.
 *
 * Uses dynamic colour derived from the user's wallpaper (API 31+).
 * Falls back to the Material 3 baseline scheme on older devices.
 *
 * Dark/light mode follows the system setting automatically.
 *
 * This composable will move to its own file (Theme.kt) once
 * the UI layer grows enough to warrant it. Keeping it here for
 * now avoids creating empty files with no real content yet.
 */
@Composable
fun OneDebridTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        darkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}