package com.workwavestudio.audiocutter

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "audio_cutter_prefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_ONBOARDING_SEEN = "onboarding_seen"

enum class ThemeMode(@StringRes val labelRes: Int, val value: String) {
    SYSTEM(R.string.theme_system, "system"),
    LIGHT(R.string.theme_light, "light"),
    DARK(R.string.theme_dark, "dark");

    companion object {
        fun fromValue(value: String?): ThemeMode {
            return values().firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}

data class AppPreferencesState(
    val themeMode: ThemeMode,
    val hasSeenOnboarding: Boolean,
    val setThemeMode: (ThemeMode) -> Unit,
    val setOnboardingSeen: (Boolean) -> Unit
)

@Composable
fun rememberAppPreferencesState(): AppPreferencesState {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var themeMode by remember {
        mutableStateOf(ThemeMode.fromValue(prefs.getString(KEY_THEME_MODE, null)))
    }
    var hasSeenOnboarding by remember {
        mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_SEEN, false))
    }

    val setThemeMode: (ThemeMode) -> Unit = { mode ->
        themeMode = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.value).apply()
    }
    val setOnboardingSeen: (Boolean) -> Unit = { seen ->
        hasSeenOnboarding = seen
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, seen).apply()
    }

    return remember(themeMode, hasSeenOnboarding) {
        AppPreferencesState(
            themeMode = themeMode,
            hasSeenOnboarding = hasSeenOnboarding,
            setThemeMode = setThemeMode,
            setOnboardingSeen = setOnboardingSeen
        )
    }
}
