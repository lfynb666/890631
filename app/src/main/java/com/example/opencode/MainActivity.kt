package com.example.opencode

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencode.data.ConnectionSettings
import com.example.opencode.data.Preferences
import com.example.opencode.network.HealthCheck
import com.example.opencode.ui.screen.ConnectScreen
import com.example.opencode.ui.screen.WebViewScreen
import com.example.opencode.ui.theme.OpenCodeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = Preferences(this)
        val healthCheck = HealthCheck()

        setContent {
            OpenCodeTheme {
                val savedSettings by preferences.settings.collectAsStateWithLifecycle(
                    initialValue = ConnectionSettings(),
                )
                val scope = rememberCoroutineScope()
                var currentSettings by remember { mutableStateOf(savedSettings) }
                var isChecking by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var showWebView by remember { mutableStateOf(false) }
                var autoConnectAttempted by remember { mutableStateOf(false) }

                LaunchedEffect(showWebView) {
                    if (showWebView) {
                        enterImmersiveMode()
                    } else {
                        exitImmersiveMode()
                    }
                }

                LaunchedEffect(savedSettings) {
                    currentSettings = savedSettings
                    if (!autoConnectAttempted && savedSettings.host.isNotBlank()) {
                        autoConnectAttempted = true
                        isChecking = true
                        errorMessage = null
                        val result = healthCheck.check(savedSettings)
                        isChecking = false
                        showWebView = result.isSuccess
                        errorMessage = result.exceptionOrNull()?.message
                    }
                }

                if (showWebView) {
                    WebViewScreen(
                        connectionSettings = currentSettings,
                        onClose = {
                            showWebView = false
                            errorMessage = null
                        },
                    )
                } else {
                    ConnectScreen(
                        initialSettings = currentSettings,
                        isChecking = isChecking,
                        errorMessage = errorMessage,
                        onConnect = { settings ->
                            currentSettings = settings
                            scope.launch {
                                isChecking = true
                                errorMessage = null
                                val result = healthCheck.check(settings)
                                isChecking = false
                                if (result.isSuccess) {
                                    preferences.save(settings)
                                    showWebView = true
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message
                                        ?: "Unable to reach opencode"
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun exitImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
