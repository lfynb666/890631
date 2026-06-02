package com.example.opencode

import android.os.Bundle
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
}
