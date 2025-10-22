package com.example.soillab.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.soillab.cbr.CBRViewModel
import com.example.soillab.data.IReportRepository
import com.example.soillab.data.TestReportRepository
import com.example.soillab.liquidlimittest.AtterbergCoreViewModel
import com.example.soillab.proctor.ProctorViewModel
import com.example.soillab.reports.ReportsViewModel
import com.example.soillab.sieveanalysis.SieveAnalysisViewModel

class AppContainer(context: Context) {
    val reportRepository: IReportRepository = TestReportRepository(context)
}

/**
 * مصنع (Factory) مخصص لإنشاء كائنات ViewModel.
 * هذا النمط ضروري عندما تحتاج الـ ViewModel إلى متغيرات في مُنشئها (constructor).
 */
class AppViewModelFactory(private val repository: IReportRepository) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AtterbergCoreViewModel::class.java) -> {
                AtterbergCoreViewModel(repository) as T
            }
            modelClass.isAssignableFrom(ReportsViewModel::class.java) -> {
                ReportsViewModel(repository) as T
            }
            modelClass.isAssignableFrom(CBRViewModel::class.java) -> {
                CBRViewModel(repository) as T
            }
            modelClass.isAssignableFrom(SieveAnalysisViewModel::class.java) -> {
                SieveAnalysisViewModel(repository) as T
            }
            modelClass.isAssignableFrom(ProctorViewModel::class.java) -> {
                ProctorViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
