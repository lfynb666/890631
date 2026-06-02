package com.example.opencode.ui.screen

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.HttpAuthHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.opencode.data.ConnectionSettings

@Composable
fun WebViewScreen(
    connectionSettings: ConnectionSettings,
    onClose: () -> Unit = {},
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(connectionSettings.baseUrl) }
    var userAgent by remember { mutableStateOf("") }
    var debugLines by remember { mutableStateOf(listOf("Debug panel ready")) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    BackHandler(enabled = !canGoBack) {
        onClose()
    }

    fun addDebugLine(line: String) {
        debugLines = (debugLines + line).takeLast(12)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.apply {
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        textZoom = 100
                        userAgent = userAgentString
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            addDebugLine(
                                "console ${consoleMessage.messageLevel()}: " +
                                    "${consoleMessage.message()} " +
                                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                            )
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            currentUrl = url ?: currentUrl
                            addDebugLine("page started: $currentUrl")
                            canGoBack = view.canGoBack()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            currentUrl = url ?: currentUrl
                            addDebugLine("page finished: $currentUrl")
                            canGoBack = view.canGoBack()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            addDebugLine(
                                "resource error ${error.errorCode}: ${error.description} " +
                                    request.url,
                            )
                        }

                        override fun onReceivedHttpAuthRequest(
                            view: WebView,
                            handler: HttpAuthHandler,
                            host: String,
                            realm: String,
                        ) {
                            if (connectionSettings.password.isBlank()) {
                                handler.cancel()
                            } else {
                                handler.proceed("opencode", connectionSettings.password)
                            }
                        }
                    }
                    loadUrl(connectionSettings.baseUrl)
                }
            },
            update = { view ->
                webView = view
                if (view.url == null) {
                    view.loadUrl(connectionSettings.baseUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "URL: $currentUrl",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "UA: $userAgent",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            debugLines.forEach { line ->
                Text(
                    text = line,
                    color = Color(0xFFFFD166),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
