package com.workwavestudio.audiocutter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workwavestudio.audiocutter.ui.AudioCutterScreen
import com.workwavestudio.audiocutter.ui.theme.AudioCutterTheme
import com.google.android.gms.ads.MobileAds
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)

        setContent {
            AudioCutterTheme {
                val viewModel: AudioCutterViewModel = viewModel()
                val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
                val message = viewModel.uiState.message

                LaunchedEffect(message) {
                    if (message != null) {
                        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                        viewModel.clearMessage()
                    }
                }

                AudioCutterScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}
