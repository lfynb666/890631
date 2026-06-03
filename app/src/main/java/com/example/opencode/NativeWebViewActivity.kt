package com.example.opencode

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class NativeWebViewActivity : Activity() {
    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView
    private var fullscreenCustomView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var eventConnection: HttpURLConnection? = null
    private var eventThread: Thread? = null
    @Volatile private var shouldListenForEvents = false
    private val baseUrl: String by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }
    private val password: String by lazy { intent.getStringExtra(EXTRA_PASSWORD).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(rootContainer)
        setupNotifications()
        launchWebView()
        startOpencodeEventListener()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    override fun onBackPressed() {
        when {
            fullscreenCustomView != null -> hideCustomView()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        shouldListenForEvents = false
        eventConnection?.disconnect()
        eventConnection = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER_REQUEST) {
            return
        }

        val callback = filePathCallback ?: return
        filePathCallback = null
        callback.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(resultCode, data),
        )
    }

    private fun launchWebView() {
        rootContainer.removeAllViews()
        webView = WebView(this)
        rootContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setupWebView()
        webView.loadUrl(baseUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            textZoom = 100
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(Color.BLACK)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val uri = request.url
                return if (shouldKeepInWebView(uri)) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String,
            ) {
                if (password.isBlank()) {
                    handler.cancel()
                } else {
                    handler.proceed("opencode", password)
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                this@NativeWebViewActivity.filePathCallback?.onReceiveValue(null)
                this@NativeWebViewActivity.filePathCallback = filePathCallback
                return try {
                    startActivityForResult(
                        fileChooserParams.createIntent(),
                        FILE_CHOOSER_REQUEST,
                    )
                    true
                } catch (exception: Exception) {
                    this@NativeWebViewActivity.filePathCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }

            override fun onShowCustomView(
                view: View,
                callback: WebChromeClient.CustomViewCallback,
            ) {
                fullscreenCustomView = view
                customViewCallback = callback
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                rootContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                webView.visibility = View.GONE
                applyImmersiveMode()
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }
    }

    private fun shouldKeepInWebView(uri: Uri): Boolean {
        val baseHost = Uri.parse(baseUrl).host
        return (uri.scheme == "http" || uri.scheme == "https") && uri.host == baseHost
    }

    private fun setupNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "opencode tasks",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager().createNotificationChannel(channel)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }
    }

    private fun startOpencodeEventListener() {
        shouldListenForEvents = true
        eventThread = Thread {
            while (shouldListenForEvents) {
                try {
                    listenToEventStream()
                } catch (_: Exception) {
                    if (shouldListenForEvents) {
                        Thread.sleep(EVENT_RECONNECT_DELAY_MS)
                    }
                }
            }
        }.apply {
            name = "opencode-events"
            start()
        }
    }

    private fun listenToEventStream() {
        val eventUrl = Uri.parse(baseUrl).buildUpon()
            .path("/event")
            .query(null)
            .fragment(null)
            .build()
            .toString()
        val connection = URL(eventUrl).openConnection() as HttpURLConnection
        eventConnection = connection
        connection.requestMethod = "GET"
        connection.connectTimeout = EVENT_CONNECT_TIMEOUT_MS
        connection.readTimeout = 0
        connection.setRequestProperty("Accept", "text/event-stream")
        if (password.isNotBlank()) {
            val credentials = "opencode:$password"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            connection.setRequestProperty("Authorization", "Basic $encoded")
        }

        BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
            val eventData = StringBuilder()
            while (shouldListenForEvents) {
                val line = reader.readLine() ?: break
                when {
                    line.isBlank() -> {
                        if (eventData.isNotBlank()) {
                            handleOpencodeEvent(eventData.toString())
                            eventData.clear()
                        }
                    }
                    line.startsWith("data:") -> eventData.append(line.removePrefix("data:").trim())
                }
            }
        }
    }

    private fun handleOpencodeEvent(payload: String) {
        val event = runCatching { JSONObject(payload) }.getOrNull() ?: return
        when (event.optString("type")) {
            "session.idle" -> showOpencodeNotification(
                title = "opencode task completed",
                text = "A session is idle and ready to review.",
            )
            "session.error" -> showOpencodeNotification(
                title = "opencode session error",
                text = "A session reported an error.",
            )
        }
    }

    private fun showOpencodeNotification(title: String, text: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, NativeWebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, baseUrl)
                putExtra(EXTRA_PASSWORD, password)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        notificationManager().notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun hideCustomView() {
        fullscreenCustomView?.let(rootContainer::removeView)
        fullscreenCustomView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_PASSWORD = "password"
        private const val FILE_CHOOSER_REQUEST = 2401
        private const val NOTIFICATION_PERMISSION_REQUEST = 2501
        private const val NOTIFICATION_CHANNEL_ID = "opencode_tasks"
        private const val EVENT_CONNECT_TIMEOUT_MS = 10_000
        private const val EVENT_RECONNECT_DELAY_MS = 3_000L
    }
}
