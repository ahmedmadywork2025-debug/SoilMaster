package com.example.soillab.sandconetest

data class SandConeTest(
    val location: String = "",        // موقع العينة
    var soilWeight: Double = 0.0,     // وزن التربة الرطبة
    var sandBefore: Double = 0.0,     // وزن الرمل قبل التجربة
    var sandAfter: Double = 0.0,      // وزن الرمل بعد التجربة
    var sandAdded: Double = 0.0,      // كمية الرمل المضافة
    var unitWeight: Double = 0.0,     // الوزن الحجمي
    var waterContent: Double = 0.0,   // محتوى الماء
    var maxDryLab: Double = 0.0       // أقصى كثافة جافة مخبرية
)