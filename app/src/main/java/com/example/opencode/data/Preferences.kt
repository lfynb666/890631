package com.example.opencode.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "connection_settings")

class Preferences(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<ConnectionSettings> = dataStore.data.map { preferences ->
        ConnectionSettings(
            host = preferences[Keys.host].orEmpty(),
            port = preferences[Keys.port] ?: "4096",
            password = preferences[Keys.password].orEmpty(),
        )
    }

    suspend fun save(settings: ConnectionSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.host] = settings.host.trim()
            preferences[Keys.port] = settings.port.trim()
            preferences[Keys.password] = settings.password
        }
    }

    private object Keys {
        val host = stringPreferencesKey("host")
        val port = stringPreferencesKey("port")
        val password = stringPreferencesKey("password")
    }
}
