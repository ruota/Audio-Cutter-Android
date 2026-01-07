package com.workwavestudio.audiocutter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workwavestudio.audiocutter.ui.AudioCutterScreen
import com.workwavestudio.audiocutter.ui.SettingsScreen
import com.workwavestudio.audiocutter.ui.theme.AudioCutterTheme
import com.google.android.gms.ads.MobileAds
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)

        setContent {
            val preferences = rememberAppPreferencesState()
            val useDarkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            AudioCutterTheme(darkTheme = useDarkTheme) {
                val viewModel: AudioCutterViewModel = viewModel()
                val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
                val message = viewModel.uiState.message
                val navController = rememberNavController()

                LaunchedEffect(message) {
                    if (message != null) {
                        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                        viewModel.clearMessage()
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        AudioCutterScreen(
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            appPreferences = preferences,
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            snackbarHostState = snackbarHostState,
                            appPreferences = preferences,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
