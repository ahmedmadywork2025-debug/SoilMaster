package com.example.soillab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * تعريف مركزي لـ DataStore باستخدام تفويض 'by preferencesDataStore'.
 * هذا يضمن وجود نسخة واحدة فقط من مخزن البيانات على مستوى التطبيق بأكمله،
 * مما يمنع التعارضات وأخطاء "multiple DataStores active".
 */
val Context.reportsDataStore: DataStore<Preferences> by preferencesDataStore(name = "atterberg_test_reports_full")

