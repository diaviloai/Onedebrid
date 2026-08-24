package com.onedebrid.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.onedebrid.app.ui.navigation.NavGraph
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
 *
 * As of Session 27, no longer field-injects PendingPlaybackHolder — that
 * class was deleted this session. NavGraph resolves entirely through nav
 * arguments now (see NavGraph.kt/PlayerViewModel.kt doc comments for the
 * full before/after).
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
                    NavGraph()
                }
            }
        }
    }
}

/**
 * Top-level Material You theme for OneDebrid.
 *
 * Uses dynamic colour derived from the user's wallpaper on API 31+
 * (Build.VERSION_CODES.S — dynamicDarkColorScheme/dynamicLightColorScheme
 * require this and will throw below it). On API 26-30, where dynamic
 * color isn't available, falls back to Material 3's own baseline color
 * scheme via lightColorScheme()/darkColorScheme() with no arguments —
 * this is M3's built-in default palette, not a hand-picked brand palette,
 * since OneDebrid has no custom brand colors defined yet. minSdk for this
 * project is 26 (app/build.gradle.kts), so this fallback branch is not
 * theoretical — real supported devices will hit it.
 *
 * Dark/light mode follows the system setting automatically in both the
 * dynamic and fallback paths.
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
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme ->
            dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}