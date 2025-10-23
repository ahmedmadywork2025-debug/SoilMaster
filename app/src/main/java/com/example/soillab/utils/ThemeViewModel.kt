package com.example.soillab.util

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")
private val THEME_MODE_KEY = intPreferencesKey("theme_mode")

enum class ThemeMode(val value: Int) {
    SYSTEM(0), LIGHT(1), DARK(2);
    companion object { fun from(v: Int) = values().firstOrNull { it.value == v } ?: SYSTEM }
}

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.themeDataStore

    private val _currentThemeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val currentThemeMode = _currentThemeMode.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.value }.first()
            _currentThemeMode.value = ThemeMode.from(saved)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_currentThemeMode.value == mode) return
        viewModelScope.launch {
            dataStore.edit { it[THEME_MODE_KEY] = mode.value }
            _currentThemeMode.value = mode
        }
    }
}
