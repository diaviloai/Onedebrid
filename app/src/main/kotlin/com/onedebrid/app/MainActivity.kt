package com.onedebrid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.onedebrid.app.ui.navigation.NavGraph
import com.onedebrid.app.ui.theme.OneDebridTheme
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var getActiveProfileUseCase: GetActiveProfileUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Observe the active profile to instantly react to Theme changes
            val activeProfile by getActiveProfileUseCase().collectAsState(initial = null)
            
            val isDarkTheme = activeProfile?.theme?.darkMode ?: isSystemInDarkTheme()
            val useDynamicColor = activeProfile?.theme?.useDynamicColor ?: true

            OneDebridTheme(
                darkTheme = isDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}