package com.example.opencode.network

import android.util.Base64
import com.example.opencode.data.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HealthCheck {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun check(settings: ConnectionSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBuilder = Request.Builder()
                .url("${settings.baseUrl}/global/health")

            if (settings.password.isNotBlank()) {
                requestBuilder.header("Authorization", basicAuth(settings.password))
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                check(response.isSuccessful) { "Server returned HTTP ${response.code}" }
            }
        }
    }

    private fun basicAuth(password: String): String {
        val credential = "opencode:$password"
        return "Basic ${Base64.encodeToString(credential.toByteArray(), Base64.NO_WRAP)}"
    }
}
