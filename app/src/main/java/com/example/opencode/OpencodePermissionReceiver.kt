package com.example.opencode

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class OpencodePermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val requestID = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val sessionID = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val notificationID = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val reply = when (intent.action) {
            ACTION_ALLOW -> "once"
            ACTION_DENY -> "reject"
            else -> return
        }

        if (baseUrl.isBlank() || requestID.isBlank()) {
            return
        }

        context.notificationManager().cancel(notificationID)
        Thread {
            val replied = replyWithCurrentApi(baseUrl, password, requestID, reply)
            if (!replied && sessionID.isNotBlank()) {
                replyWithLegacyApi(baseUrl, password, sessionID, requestID, reply)
            }
        }.apply {
            name = "opencode-permission-reply"
            start()
        }
    }

    private fun replyWithCurrentApi(
        baseUrl: String,
        password: String,
        requestID: String,
        reply: String,
    ): Boolean {
        val url = Uri.parse(baseUrl).buildUpon()
            .path("/permission/$requestID/reply")
            .query(null)
            .fragment(null)
            .build()
            .toString()
        val body = JSONObject().put("reply", reply).toString()
        return postJson(url, password, body)
    }

    private fun replyWithLegacyApi(
        baseUrl: String,
        password: String,
        sessionID: String,
        permissionID: String,
        reply: String,
    ): Boolean {
        val url = Uri.parse(baseUrl).buildUpon()
            .path("/session/$sessionID/permissions/$permissionID")
            .query(null)
            .fragment(null)
            .build()
            .toString()
        val body = JSONObject()
            .put("response", reply)
            .put("remember", false)
            .toString()
        return postJson(url, password, body)
    }

    private fun postJson(url: String, password: String, body: String): Boolean {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (password.isNotBlank()) {
                val credentials = "opencode:$password"
                val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                connection.setRequestProperty("Authorization", "Basic $encoded")
            }
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
            }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun Context.notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_ALLOW = "com.example.opencode.permission.ALLOW"
        const val ACTION_DENY = "com.example.opencode.permission.DENY"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
