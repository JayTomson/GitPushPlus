package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _usernameFlow = MutableStateFlow(sharedPrefs.getString("github_username", null))
    val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

    private val _tokenFlow = MutableStateFlow(sharedPrefs.getString("github_token", null))
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    suspend fun saveCredentials(username: String, token: String) {
        sharedPrefs.edit()
            .putString("github_username", username)
            .putString("github_token", token)
            .apply()
        _usernameFlow.value = username
        _tokenFlow.value = token
    }

    suspend fun clearCredentials() {
        sharedPrefs.edit()
            .remove("github_username")
            .remove("github_token")
            .apply()
        _usernameFlow.value = null
        _tokenFlow.value = null
    }
}
