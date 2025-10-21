package com.example.soillab.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface IReportRepository {
    // Atterberg Limits
    val atterbergReports: Flow<List<AtterbergReport>>
    suspend fun getAtterbergReportById(id: String): AtterbergReport?
    suspend fun saveAtterbergReport(report: AtterbergReport)
    suspend fun deleteAtterbergReport(reportId: String)

    // CBR Test
    val cbrReports: Flow<List<CBRReport>>
    suspend fun getCBRReportById(id: String): CBRReport?
    suspend fun saveCBRReport(report: CBRReport)
    suspend fun deleteCBRReport(reportId: String)

    // Sieve Analysis
    val sieveAnalysisReports: Flow<List<SieveAnalysisReport>>
    suspend fun getSieveAnalysisReportById(id: String): SieveAnalysisReport?
    suspend fun saveSieveAnalysisReport(report: SieveAnalysisReport)
    suspend fun deleteSieveAnalysisReport(reportId: String)

    // Custom Specifications
    val customSpecifications: Flow<List<Specification>>
    suspend fun saveCustomSpecification(spec: Specification)
    suspend fun deleteCustomSpecification(specId: String)

    // Proctor Test
    val proctorReports: Flow<List<ProctorReport>>
    suspend fun saveProctorReport(report: ProctorReport)
    suspend fun getProctorReportById(id: String): ProctorReport?
    suspend fun deleteProctorReport(id: String)
}

class TestReportRepository(private val context: Context) : IReportRepository {

    private val ATTERBERG_REPORTS_KEY = stringPreferencesKey("atterberg_full_reports_list")
    private val CBR_REPORTS_KEY = stringPreferencesKey("cbr_full_reports_list")
    private val SIEVE_REPORTS_KEY = stringPreferencesKey("sieve_analysis_reports_list")
    private val CUSTOM_SPECS_KEY = stringPreferencesKey("custom_specifications_list")
    private val PROCTOR_REPORTS_KEY = stringPreferencesKey("proctor_reports_list")


    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    // --- Atterberg Implementation ---
    override val atterbergReports: Flow<List<AtterbergReport>> = context.reportsDataStore.data
        .map { preferences ->
            preferences[ATTERBERG_REPORTS_KEY]?.let { jsonString ->
                try { json.decodeFromString<List<AtterbergReport>>(jsonString) }
                catch (e: Exception) {
                    Log.e("TestReportRepository", "Error decoding Atterberg reports", e)
                    emptyList()
                }
            } ?: emptyList()
        }
    override suspend fun getAtterbergReportById(id: String): AtterbergReport? {
        return atterbergReports.first().find { it.id == id }
    }
    override suspend fun saveAtterbergReport(report: AtterbergReport) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[ATTERBERG_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<AtterbergReport>>(currentReportsJson)
            val existingIndex = currentReports.indexOfFirst { it.id == report.id }
            if (existingIndex != -1) {
                currentReports[existingIndex] = report
            } else {
                currentReports.add(0, report)
            }
            preferences[ATTERBERG_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }
    override suspend fun deleteAtterbergReport(reportId: String) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[ATTERBERG_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<AtterbergReport>>(currentReportsJson)
            currentReports.removeAll { it.id == reportId }
            preferences[ATTERBERG_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }

    // --- CBR Implementation ---
    override val cbrReports: Flow<List<CBRReport>> = context.reportsDataStore.data
        .map { preferences ->
            preferences[CBR_REPORTS_KEY]?.let { jsonString ->
                try { json.decodeFromString<List<CBRReport>>(jsonString) }
                catch (e: Exception) {
                    Log.e("TestReportRepository", "Error decoding CBR reports", e)
                    emptyList()
                }
            } ?: emptyList()
        }
    override suspend fun getCBRReportById(id: String): CBRReport? {
        return cbrReports.first().find { it.id == id }
    }
    override suspend fun saveCBRReport(report: CBRReport) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[CBR_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<CBRReport>>(currentReportsJson)
            val existingIndex = currentReports.indexOfFirst { it.id == report.id }
            if (existingIndex != -1) {
                currentReports[existingIndex] = report
            } else {
                currentReports.add(0, report)
            }
            preferences[CBR_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }
    override suspend fun deleteCBRReport(reportId: String) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[CBR_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<CBRReport>>(currentReportsJson)
            currentReports.removeAll { it.id == reportId }
            preferences[CBR_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }

    // --- Sieve Analysis Implementation ---
    override val sieveAnalysisReports: Flow<List<SieveAnalysisReport>> = context.reportsDataStore.data
        .map { preferences ->
            preferences[SIEVE_REPORTS_KEY]?.let { jsonString ->
                try { json.decodeFromString<List<SieveAnalysisReport>>(jsonString) }
                catch (e: Exception) {
                    Log.e("TestReportRepository", "Error decoding Sieve reports", e)
                    emptyList()
                }
            } ?: emptyList()
        }

    override suspend fun getSieveAnalysisReportById(id: String): SieveAnalysisReport? {
        return sieveAnalysisReports.first().find { it.id == id }
    }

    override suspend fun saveSieveAnalysisReport(report: SieveAnalysisReport) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[SIEVE_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<SieveAnalysisReport>>(currentReportsJson)
            val existingIndex = currentReports.indexOfFirst { it.id == report.id }
            if (existingIndex != -1) {
                currentReports[existingIndex] = report
            } else {
                currentReports.add(0, report)
            }
            preferences[SIEVE_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }

    override suspend fun deleteSieveAnalysisReport(reportId: String) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[SIEVE_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<SieveAnalysisReport>>(currentReportsJson)
            currentReports.removeAll { it.id == reportId }
            preferences[SIEVE_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }

    // --- Custom Specifications Implementation ---
    override val customSpecifications: Flow<List<Specification>> = context.reportsDataStore.data
        .map { preferences ->
            preferences[CUSTOM_SPECS_KEY]?.let { jsonString ->
                try {
                    json.decodeFromString<List<Specification>>(jsonString)
                } catch (e: Exception) {
                    Log.e("TestReportRepository", "Error decoding custom specs", e)
                    emptyList()
                }
            } ?: emptyList()
        }

    override suspend fun saveCustomSpecification(spec: Specification) {
        context.reportsDataStore.edit { preferences ->
            val currentSpecsJson = preferences[CUSTOM_SPECS_KEY] ?: "[]"
            val currentSpecs = json.decodeFromString<MutableList<Specification>>(currentSpecsJson)
            val existingIndex = currentSpecs.indexOfFirst { it.id == spec.id }
            if (existingIndex != -1) {
                currentSpecs[existingIndex] = spec
            } else {
                currentSpecs.add(0, spec)
            }
            preferences[CUSTOM_SPECS_KEY] = json.encodeToString(currentSpecs)
        }
    }

    override suspend fun deleteCustomSpecification(specId: String) {
        context.reportsDataStore.edit { preferences ->
            val currentSpecsJson = preferences[CUSTOM_SPECS_KEY] ?: "[]"
            val currentSpecs = json.decodeFromString<MutableList<Specification>>(currentSpecsJson)
            currentSpecs.removeAll { it.id == specId }
            preferences[CUSTOM_SPECS_KEY] = json.encodeToString(currentSpecs)
        }
    }

    // --- Proctor Implementation ---
    override val proctorReports: Flow<List<ProctorReport>> = context.reportsDataStore.data
        .map { preferences ->
            preferences[PROCTOR_REPORTS_KEY]?.let { jsonString ->
                try {
                    json.decodeFromString<List<ProctorReport>>(jsonString)
                } catch (e: Exception) {
                    Log.e("TestReportRepository", "Error decoding Proctor reports", e)
                    emptyList()
                }
            } ?: emptyList()
        }

    override suspend fun saveProctorReport(report: ProctorReport) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[PROCTOR_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<ProctorReport>>(currentReportsJson)
            val existingIndex = currentReports.indexOfFirst { it.id == report.id }
            if (existingIndex != -1) {
                currentReports[existingIndex] = report
            } else {
                currentReports.add(0, report)
            }
            preferences[PROCTOR_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }

    override suspend fun getProctorReportById(id: String): ProctorReport? {
        return proctorReports.first().find { it.id == id }
    }

    override suspend fun deleteProctorReport(id: String) {
        context.reportsDataStore.edit { preferences ->
            val currentReportsJson = preferences[PROCTOR_REPORTS_KEY] ?: "[]"
            val currentReports = json.decodeFromString<MutableList<ProctorReport>>(currentReportsJson)
            currentReports.removeAll { it.id == id }
            preferences[PROCTOR_REPORTS_KEY] = json.encodeToString(currentReports)
        }
    }
}

