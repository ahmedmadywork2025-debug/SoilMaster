package com.example.soillab.util

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// DataStore to persist the selected language
private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_settings")
private val LANGUAGE_KEY = stringPreferencesKey("selected_language")

/**
 * ViewModel لإدارة لغة التطبيق.
 * مسؤوليته فقط هي حفظ واسترجاع اللغة المختارة وإعلام المراقبين.
 */
class LanguageViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = getApplication<Application>().languageDataStore

    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage = _currentLanguage.asStateFlow()

    init {
        viewModelScope.launch {
            // عند بدء التشغيل، اقرأ اللغة المحفوظة وحدّث الحالة الداخلية.
            val savedLang = dataStore.data.map { preferences ->
                preferences[LANGUAGE_KEY] ?: "en"
            }.first()
            _currentLanguage.value = savedLang
        }
    }

    /**
     * يقوم بحفظ اللغة الجديدة وتحديث الحالة لإعلام الواجهة (MainActivity).
     */
    fun setLanguage(languageCode: String) {
        // إذا كانت اللغة المختارة هي نفسها الحالية، لا تفعل شيئًا.
        if (_currentLanguage.value == languageCode) return

        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[LANGUAGE_KEY] = languageCode
            }
            // قم بتحديث الحالة لإعلام المراقبين (Observers) بالتغيير
            _currentLanguage.value = languageCode
        }
    }
}

