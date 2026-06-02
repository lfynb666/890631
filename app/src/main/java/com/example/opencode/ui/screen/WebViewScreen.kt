package com.example.opencode.ui.screen

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.HttpAuthHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.opencode.data.ConnectionSettings

@Composable
fun WebViewScreen(
    connectionSettings: ConnectionSettings,
    onClose: () -> Unit = {},
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    BackHandler(enabled = !canGoBack) {
        onClose()
    }

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
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        canGoBack = view.canGoBack()
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()
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
}
