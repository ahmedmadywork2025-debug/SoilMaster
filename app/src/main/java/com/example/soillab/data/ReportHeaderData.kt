package com.example.soillab.data

import kotlinx.serialization.Serializable

/**
 * Data class to hold all user-configurable report header information.
 * It has default values for the first run.
 */
@Serializable
data class ReportHeaderData(
    val line1_en: String = "Kingdom of Saudi Arabia",
    val line1_ar: String = "المملكة العربية السعودية",
    val line2_en: String = "Ministry of Transport and Logistic Services",
    val line2_ar: String = "وزارة النقل والخدمات اللوجستية",
    val line3_en: String = "General Department of Quality and Environment",
    val line3_ar: String = "الإدارة العامة للجودة والبيئة",
    val line4_en: String = "Quality Management",
    val line4_ar: String = "إدارة ضبط الجودة",
    val line5_en: String = "Consultant: Euro Group for Engineering Consultancy",
    val line5_ar: String = "الاستشاري: يورو جروب للإستشارات الهندسية",
    val line6_en: String = "Contractor: Rashid Contracting Establishment",
    val line6_ar: String = "المقاول: مؤسسة راشد للمقاولات",
    val projectTitle_ar: String = "مشروع إستكمال الطريق الذي يربط طريق الرياض/الدمام السريع وطريق الظهران/بقيق",
    val ministryLogoUri: String = "", // Added Ministry Logo
    val consultantLogoUri: String = "",
    val contractorLogoUri: String = ""
)

