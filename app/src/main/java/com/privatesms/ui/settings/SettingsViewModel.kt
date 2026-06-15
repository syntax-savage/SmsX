package com.privatesms.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.privatesms.domain.usecase.BackupRestoreUseCase

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseManager: DatabaseManager,
    val backupRestoreUseCase: BackupRestoreUseCase
) : ViewModel() {

    companion object {
        val PREF_THEME = stringPreferencesKey("theme") // "system", "light", "dark"
        val PREF_ACCENT_COLOR = intPreferencesKey("accent_color") // 0 to 5
        val PREF_FONT_SIZE = stringPreferencesKey("font_size") // "small", "medium", "large"
        val PREF_SCREENSHOT_PREVENTION = booleanPreferencesKey("screenshot_prevention")
        val PREF_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val PREF_AUTO_LOCK_TIMEOUT = longPreferencesKey("auto_lock_timeout") // milliseconds, -1 = never
        val PREF_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PREF_PIN_HASH = stringPreferencesKey("pin_hash")
        val PREF_PIN_SALT = stringPreferencesKey("pin_salt")
        val PREF_SPAM_KEYWORDS = stringSetPreferencesKey("spam_keywords")
    }

    val theme = context.settingsDataStore.data.map { it[PREF_THEME] ?: "system" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accentColor = context.settingsDataStore.data.map { it[PREF_ACCENT_COLOR] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val fontSize = context.settingsDataStore.data.map { it[PREF_FONT_SIZE] ?: "medium" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "medium")

    val screenshotPrevention = context.settingsDataStore.data.map { it[PREF_SCREENSHOT_PREVENTION] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockEnabled = context.settingsDataStore.data.map { it[PREF_APP_LOCK_ENABLED] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoLockTimeout = context.settingsDataStore.data.map { it[PREF_AUTO_LOCK_TIMEOUT] ?: -1L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1L)

    val biometricEnabled = context.settingsDataStore.data.map { it[PREF_BIOMETRIC_ENABLED] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val spamKeywords = context.settingsDataStore.data.map { it[PREF_SPAM_KEYWORDS] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun updateTheme(value: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PREF_THEME] = value }
        }
    }

    fun updateAccentColor(value: Int) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PREF_ACCENT_COLOR] = value }
        }
    }

    fun updateFontSize(value: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PREF_FONT_SIZE] = value }
        }
    }

    fun updateScreenshotPrevention(value: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PREF_SCREENSHOT_PREVENTION] = value }
        }
    }

    fun updateAppLockEnabled(value: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PREF_APP_LOCK_ENABLED] = value }
        }
    }

    fun updateAutoLockTimeout(value: Long) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PREF_AUTO_LOCK_TIMEOUT] = value }
        }
    }

    fun updateBiometricEnabled(value: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PREF_BIOMETRIC_ENABLED] = value }
        }
    }

    fun addSpamKeyword(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            val current = spamKeywords.value.toMutableSet()
            current.add(keyword)
            context.settingsDataStore.edit { it[PREF_SPAM_KEYWORDS] = current }
        }
    }

    fun removeSpamKeyword(keyword: String) {
        viewModelScope.launch {
            val current = spamKeywords.value.toMutableSet()
            current.remove(keyword)
            context.settingsDataStore.edit { it[PREF_SPAM_KEYWORDS] = current }
        }
    }

    fun lockApp() {
        databaseManager.lock()
    }
}
