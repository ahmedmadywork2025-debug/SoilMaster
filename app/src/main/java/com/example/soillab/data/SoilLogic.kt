package com.example.soillab.data

import com.example.soillab.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

// --- Atterberg Limits Logic ---
class AISoilDataValidator {
    fun validateLiquidLimitSample(sample: LiquidLimitSample): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        val blows = sample.blows.toIntOrNull()
        val waterContent = sample.waterContent.toFloatOrNull()
        if (blows == null || blows <= 0) errors.add(ValidationError("INVALID_BLOWS", "Blows must be a positive integer.", "blows"))
        else {
            if (blows < 10) warnings.add(ValidationWarning("LOW_BLOWS", "Blows count is very low.", SeverityLevel.MEDIUM))
            if (blows > 40) warnings.add(ValidationWarning("HIGH_BLOWS", "Blows count is very high.", SeverityLevel.MEDIUM))
        }
        if (waterContent == null || waterContent <= 0) errors.add(ValidationError("INVALID_WC", "Water content must be a positive number.", "waterContent"))
        else if (waterContent > 120) warnings.add(ValidationWarning("HIGH_WC", "Water content is unusually high.", SeverityLevel.HIGH))
        return ValidationResult(errors.isEmpty(), warnings, errors)
    }

    fun validatePlasticLimitSample(sample: PlasticLimitSample): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        val waterContent = sample.waterContent.toFloatOrNull()
        if (waterContent == null || waterContent <= 0) errors.add(ValidationError("INVALID_WC", "Water content must be a positive number.", "waterContent"))
        else if (waterContent > 60) warnings.add(ValidationWarning("HIGH_PL_WC", "PL water content seems high.", SeverityLevel.LOW))
        return ValidationResult(errors.isEmpty(), warnings, errors)
    }
}
class AdvancedSoilClassifier {
    fun classifySoilAdvanced(liquidLimit: Float?, plasticityIndex: Float?, points: List<Pair<Float, Float>>, correlationCoefficient: Float?): AdvancedClassificationResult {
        val ll = liquidLimit ?: 0f; val pi = plasticityIndex ?: 0f; val aLinePI = 0.73f * (ll - 20)
        var confidence = 0.8f; if (points.size >= 3) confidence += 0.1f; if (correlationCoefficient != null && abs(correlationCoefficient) > 0.95) confidence += 0.05f; if (points.any { it.first in 20f..30f }) confidence += 0.05f

        val classification = when {
            ll < 50 -> when {
                pi > 7 && pi >= aLinePI -> USCSClassification(R.string.soil_cl, R.string.desc_cl, "CL", confidence.coerceIn(0.1f, 0.99f))
                pi < 4 || pi < aLinePI -> USCSClassification(R.string.soil_ml, R.string.desc_ml, "ML", confidence.coerceIn(0.1f, 0.99f))
                else -> USCSClassification(R.string.soil_cl_ml, R.string.desc_cl_ml, "CL-ML", confidence.coerceIn(0.1f, 0.99f))
            }
            else -> when {
                pi >= aLinePI -> USCSClassification(R.string.soil_ch, R.string.desc_ch, "CH", confidence.coerceIn(0.1f, 0.99f))
                else -> USCSClassification(R.string.soil_mh, R.string.desc_mh, "MH", confidence.coerceIn(0.1f, 0.99f))
            }
        }

        return AdvancedClassificationResult(
            basicClassification = classification,
            behaviorAnalysis = analyzeSoilBehavior(classification.symbol),
            engineeringProperties = estimateEngineeringProperties(classification.symbol),
            constructionRecommendations = generateConstructionRecommendations(classification.symbol),
            qualityAssessment = assessDataQuality(points, correlationCoefficient, ll)
        )
    }

    private fun analyzeSoilBehavior(symbol: String): SoilBehaviorAnalysis { return SoilBehaviorAnalysis(SwellPotential.LOW, Compressibility.LOW, Permeability.MEDIUM, FrostSusceptibility.LOW, ShearStrength.MEDIUM) }
    private fun estimateEngineeringProperties(symbol: String): EngineeringProperties { return EngineeringProperties(20f..30f, 5f..15f, 0.1f..0.2f, 20f..50f) }
    private fun generateConstructionRecommendations(symbol: String): List<ConstructionRecommendation> {
        return listOf(ConstructionRecommendation(R.string.rec_general_cat, R.string.rec_general_desc, Priority.MEDIUM, "General practice."))
    }
    private fun assessDataQuality(points: List<Pair<Float, Float>>, correlationCoefficient: Float?, ll: Float): QualityAssessment {
        return QualityAssessment(DataQuality.FAIR, ReliabilityLevel.MEDIUM, ComplianceLevel.PARTIAL, listOf(R.string.quality_improvement_rec))
    }
}

/**
 * يقوم بإنشاء بيانات مثال واقعية لاختبار حدود السيولة واللدونة.
 */
object AtterbergExampleDataGenerator {
    private enum class SoilProfile(
        val descriptionResId: Int,
        val llRange: IntRange,
        val piRange: IntRange // Plasticity Index Range
    ) {
        LEAN_CLAY(R.string.example_desc_cl, 30..50, 10..22),
        FAT_CLAY(R.string.example_desc_ch, 55..90, 30..55),
        SILT(R.string.example_desc_ml, 25..45, 4..10)
    }

    fun generate(): ExampleAtterbergData {
        val random = Random.Default
        val profile = SoilProfile.values().random(random)

        val targetLL = random.nextInt(profile.llRange.first, profile.llRange.last).toFloat()
        val targetPI = random.nextInt(profile.piRange.first, profile.piRange.last).toFloat()
        val targetPL = targetLL - targetPI

        // Generate Liquid Limit Samples
        val llSamples = mutableListOf<LiquidLimitSample>()
        val blowsList = listOf(15, 20, 28, 35).shuffled() // Use standard blow counts
        for (i in 0..2) { // Generate 3 points
            val blows = blowsList[i]
            // Simple inverse relationship: log scale for blows
            val wc = targetLL + (25 - blows) * 0.4f + random.nextFloat() * 2 - 1
            llSamples.add(
                LiquidLimitSample(
                    id = i + 1,
                    blows = blows.toString(),
                    waterContent = String.format(Locale.US, "%.1f", wc)
                )
            )
        }

        // Generate Plastic Limit Samples
        val plSamples = (1..2).map { i ->
            val wc = targetPL + random.nextFloat() * 2 - 1 // small variation
            PlasticLimitSample(
                id = i,
                waterContent = String.format(Locale.US, "%.1f", wc)
            )
        }

        return ExampleAtterbergData(
            descriptionResId = profile.descriptionResId,
            llSamples = llSamples,
            plSamples = plSamples
        )
    }
}


// --- CBR Test Logic ---
object CBRCalculator {
    fun calculate(points: List<CBRDataPoint>, parameters: CBRTestParameters): Pair<CBRCalculationResult?, List<CBRDataPoint>?> {
        if (points.size < 2) return Pair(null, null)

        val refLoad25 = parameters.refLoad25.toDoubleOrNull() ?: 13.34
        val refLoad50 = parameters.refLoad50.toDoubleOrNull() ?: 20.01

        val corrected = correctCurve(points)
        val pointsToUse = corrected ?: points

        val load25 = interpolateLoadAt(pointsToUse, 2.5)
        val load50 = interpolateLoadAt(pointsToUse, 5.0)

        if (load25 == null || load50 == null) return Pair(null, corrected)

        val cbr25 = computeCBR(load25, refLoad25)
        val cbr50 = computeCBR(load50, refLoad50)

        val messageResId = if (cbr50 > cbr25) {
            R.string.cbr_calc_warning_5mm
        } else {
            R.string.cbr_calc_success
        }
        val finalCbr = getFinalCBR(cbr25, cbr50)

        val insights = CBRCurveAnalyzer.analyze(finalCbr, corrected != null, points)
        val predictedK = predictSubgradeModulusK(finalCbr)


        val result = CBRCalculationResult(
            loadAt2_5 = load25, loadAt5_0 = load50, cbrAt2_5 = cbr25, cbrAt5_0 = cbr50,
            finalCbrValue = finalCbr, isCorrected = corrected != null, messageResId = messageResId,
            insights = insights,
            predictedKValue = predictedK
        )
        return Pair(result, corrected)
    }

    private fun predictSubgradeModulusK(cbr: Double): Double {
        // k (MN/m³) = 10 * CBR. This is a common simplified correlation.
        return (10 * cbr).coerceAtLeast(0.0)
    }

    internal fun interpolateLoadAt(points: List<CBRDataPoint>, penetration: Double): Double? {
        if (points.size < 2) return null
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if (penetration >= p1.penetration && penetration <= p2.penetration) {
                if (p2.penetration - p1.penetration == 0.0) return p1.load
                val fraction = (penetration - p1.penetration) / (p2.penetration - p1.penetration)
                return p1.load + fraction * (p2.load - p1.load)
            }
        }
        return null
    }
    internal fun correctCurve(points: List<CBRDataPoint>): List<CBRDataPoint>? {
        if (points.size < 3) return null
        var maxSlopeIndex = -1
        var maxSlope = Double.MIN_VALUE

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if (p2.penetration > p1.penetration) {
                val slope = (p2.load - p1.load) / (p2.penetration - p1.penetration)
                if (slope > maxSlope) {
                    maxSlope = slope
                    maxSlopeIndex = i
                }
            }
        }

        if (maxSlopeIndex == -1 || maxSlope <= 0) return null

        val p1_pen = points[maxSlopeIndex].penetration
        val p1_load = points[maxSlopeIndex].load
        val newZeroPenetration = p1_pen - (p1_load / maxSlope)

        return if (newZeroPenetration > 0.001) {
            points.map { point -> CBRDataPoint(penetration = point.penetration - newZeroPenetration, load = point.load) }
        } else {
            null
        }
    }
    private fun computeCBR(load: Double, refLoad: Double): Double {
        if (refLoad == 0.0) return 0.0
        return (load / refLoad) * 100.0
    }
    private fun getFinalCBR(cbr2_5: Double, cbr5_0: Double): Double {
        return if (cbr5_0 > cbr2_5) cbr5_0 else cbr2_5
    }
}

/**
 * AI engine for analyzing the CBR curve and providing professional insights.
 */
object CBRCurveAnalyzer {
    fun analyze(cbrValue: Double, isCorrected: Boolean, points: List<CBRDataPoint>): CBRInsights {
        val quality = classifyStrength(cbrValue)
        val modulus = estimateResilientModulus(cbrValue)
        val strength = estimateShearStrength(cbrValue)
        val recommendation = generateRecommendations(quality.first)
        val interpretation = interpretCurveShape(isCorrected, points)

        return CBRInsights(
            qualityRatingResId = quality.first,
            ratingColorHex = quality.second,
            curveInterpretationResId = interpretation,
            estimatedResilientModulus = "${modulus.first} - ${modulus.second} MPa",
            estimatedShearStrength = "${strength.first} - ${strength.second} kPa",
            primaryRecommendationResId = recommendation
        )
    }

    private fun classifyStrength(cbr: Double): Pair<Int, String> { // (StringResId, ColorHex)
        return when {
            cbr >= 80 -> R.string.cbr_rating_excellent to "#4CAF50"
            cbr >= 30 -> R.string.cbr_rating_very_good to "#8BC34A"
            cbr >= 20 -> R.string.cbr_rating_good to "#CDDC39"
            cbr >= 8 -> R.string.cbr_rating_fair to "#FFEB3B"
            cbr >= 4 -> R.string.cbr_rating_poor to "#FF9800"
            else -> R.string.cbr_rating_very_poor to "#F44336"
        }
    }

    private fun interpretCurveShape(isCorrected: Boolean, points: List<CBRDataPoint>): Int {
        if (isCorrected) {
            return R.string.cbr_interp_corrected
        }
        val lastPoint = points.lastOrNull()
        if (lastPoint != null && lastPoint.penetration < 7.5) {
            return R.string.cbr_interp_stopped_early
        }
        return R.string.cbr_interp_normal
    }

    private fun estimateResilientModulus(cbr: Double): Pair<Int, Int> {
        val mrPsi = 1500 * cbr
        val mrMpa = mrPsi / 145.0
        val lowerBound = (mrMpa * 0.8).toInt()
        val upperBound = (mrMpa * 1.2).toInt()
        return lowerBound to upperBound
    }

    private fun estimateShearStrength(cbr: Double): Pair<Int, Int> {
        // This correlation is mainly for cohesive soils (clays).
        if (cbr > 15) return 0 to 0 // Not applicable for strong materials
        val su = 20 * cbr
        return (su * 0.75).toInt() to (su * 1.25).toInt()
    }

    private fun generateRecommendations(qualityResId: Int): Int {
        return when (qualityResId) {
            R.string.cbr_rating_excellent, R.string.cbr_rating_very_good -> R.string.cbr_rec_excellent
            R.string.cbr_rating_good -> R.string.cbr_rec_good
            R.string.cbr_rating_fair -> R.string.cbr_rec_fair
            R.string.cbr_rating_poor -> R.string.cbr_rec_poor
            R.string.cbr_rating_very_poor -> R.string.cbr_rec_very_poor
            else -> R.string.rec_general_desc
        }
    }
}


/**
 * يقوم بإنشاء بيانات مثال واقعية لاختبار CBR عن طريق محاكاة أنواع مختلفة من التربة.
 */
object CBRExampleDataGenerator {

    private enum class SoilProfile(
        val descriptionResId: Int,
        val baseStrengthRange: ClosedRange<Double>, // The load in kN at 5.0mm penetration
        val shape: List<Pair<Double, Double>> // (Penetration, Load Factor relative to load at 5.0mm)
    ) {
        GOOD_SUBBASE(
            R.string.example_desc_cbr_good_subbase,
            4.0..16.0, // Corresponds to approx CBR 20-80%
            listOf(
                0.0 to 0.0, 0.5 to 0.094, 1.0 to 0.219, 1.5 to 0.352, 2.0 to 0.516,
                2.5 to 0.664, 3.0 to 0.797, 4.0 to 0.906, 5.0 to 1.0, 7.5 to 1.082,
                10.0 to 1.117, 12.5 to 1.125
            )
        ),
        SOFT_CLAY(
            R.string.example_desc_cbr_soft_clay,
            0.5..2.0, // Approx CBR 2.5-10%
            listOf(
                0.0 to 0.0, 0.5 to 0.3, 1.0 to 0.5, 1.5 to 0.65, 2.0 to 0.78,
                2.5 to 0.88, 3.0 to 0.92, 4.0 to 0.96, 5.0 to 1.0, 7.5 to 1.04,
                10.0 to 1.06, 12.5 to 1.08
            )
        ),
        CORRECTION_NEEDED(
            R.string.example_desc_cbr_correction,
            2.0..8.0, // Approx CBR 10-40%
            listOf(
                0.0 to 0.0, 0.5 to 0.1, 1.0 to 0.2, 1.5 to 0.45, 2.0 to 0.7,
                2.5 to 0.85, 3.0 to 0.9, 4.0 to 0.95, 5.0 to 1.0, 7.5 to 1.05,
                10.0 to 1.08, 12.5 to 1.1
            )
        )
    }

    fun generate(): ExampleCBRData {
        val random = Random.Default
        val profile = SoilProfile.values().random(random)

        val baseStrength = random.nextDouble(profile.baseStrengthRange.start, profile.baseStrengthRange.endInclusive)

        val points = profile.shape.map { (penetration, factor) ->
            val idealLoad = baseStrength * factor
            val noise = idealLoad * random.nextDouble(-0.04, 0.04) // إضافة تشويش طفيف
            val finalLoad = kotlin.math.max(0.0, idealLoad + noise)
            CBRDataPoint(penetration = penetration, load = finalLoad)
        }

        return ExampleCBRData(points, profile.descriptionResId)
    }
}

// --- Sieve Analysis Logic ---

object SieveAnalysisCalculator {
    fun calculate(sieves: List<Sieve>, params: ClassificationParameters): SieveAnalysisResult {

        // --- GeoMind 3025 AI Upgrade: Logical Continuity Protocol v3.0 ---
        var lastValidCumulativeWeight = 0.0
        val calculatedSievesWithCumulative = sieves.map { sieve ->
            val currentInput = sieve.retainedWeight.toDoubleOrNull()

            // If input is valid and not less than the previous cumulative weight, update it.
            if (currentInput != null && currentInput >= lastValidCumulativeWeight) {
                lastValidCumulativeWeight = currentInput
            }
            // For blank entries or illogical decreasing values, carry over the last valid weight.
            sieve.copy(cumulativeRetained = lastValidCumulativeWeight)
        }

        val sumOfRetained = calculatedSievesWithCumulative.lastOrNull()?.cumulativeRetained ?: 0.0
        val initialWeight = params.initialWeight.toDoubleOrNull()

        val totalWeight = if (initialWeight != null && initialWeight > 0) initialWeight else sumOfRetained

        if (totalWeight == 0.0) {
            return SieveAnalysisResult(sieves, 0f, 0f, 0f, null, null, null, null, null, 0f, null)
        }

        val materialLossPercentage = if (initialWeight != null && initialWeight > 0) {
            ((initialWeight - sumOfRetained) / initialWeight * 100.0).toFloat()
        } else {
            null
        }

        val calculatedSieves = calculatedSievesWithCumulative.map { sieve ->
            val percentPassing = (((totalWeight - sieve.cumulativeRetained) / totalWeight) * 100.0).coerceIn(0.0, 100.0)
            sieve.copy(percentPassing = percentPassing)
        }

        val p200 = calculatedSieves.find { it.opening == 0.075 }?.percentPassing ?: 0.0
        val p4 = calculatedSieves.find { it.opening == 4.75 }?.percentPassing ?: 0.0

        val percentGravel = (100.0 - p4).toFloat()
        val percentSand = (p4 - p200).toFloat()
        val percentFines = p200.toFloat()

        val d10 = getDx(calculatedSieves, 10.0)
        val d30 = getDx(calculatedSieves, 30.0)
        val d60 = getDx(calculatedSieves, 60.0)

        val cu = if (d10 != null && d60 != null && d10 > 0) d60 / d10 else null
        val cc = if (d10 != null && d30 != null && d60 != null && d10 > 0 && d60 > 0) (d30.pow(2)) / (d10 * d60) else null

        // --- Fineness Modulus Calculation ---
        val fmSievesOpenings = listOf(4.75, 2.36, 1.18, 0.6, 0.3, 0.15)
        val sumCumulativePercentRetained = calculatedSieves
            .filter { s -> fmSievesOpenings.any { fms -> abs(s.opening - fms) < 0.001 } }
            .sumOf { 100.0 - it.percentPassing }

        val finenessModulus = (sumCumulativePercentRetained / 100.0).toFloat()

        // --- Hazen's Permeability Estimation ---
        val isCleanSand = (percentSand > 50 && percentFines < 5 && cu != null && cu < 6)
        val estimatedPermeability = if (isCleanSand && d10 != null && d10 > 0) {
            val C = 1.0 // Typical constant for Hazen's formula
            (C * (d10 / 10.0).pow(2))// d10 in mm, result in cm/s
        } else {
            null
        }

        return SieveAnalysisResult(calculatedSieves, percentGravel, percentSand, percentFines, d10, d30, d60, cu, cc, finenessModulus, materialLossPercentage, estimatedPermeability = estimatedPermeability)
    }

    private fun getDx(sieves: List<Sieve>, percent: Double): Double? {
        val reversedSieves = sieves.reversed()
        for (i in 0 until reversedSieves.size - 1) {
            val s1 = reversedSieves[i]
            val s2 = reversedSieves[i + 1]
            if (s1.percentPassing <= percent && s2.percentPassing >= percent) {
                if (s1.opening <= 0) return null
                if (s2.percentPassing == s1.percentPassing || s2.opening <= 0) return s1.opening

                val logD1 = log10(s1.opening)
                val logD2 = log10(s2.opening)
                val logDx = logD1 + (percent - s1.percentPassing) * (logD2 - logD1) / (s2.percentPassing - s1.percentPassing)

                return 10.0.pow(logDx)
            }
        }
        return null
    }
}

/**
 * GeoMind 3025 - AI Soil Classification Bot
 * Implements AASHTO M 145 and ASTM D2487 standards.
 */
object AASHTO_USCS_Classifier {
    fun classify(result: SieveAnalysisResult, params: ClassificationParameters): SoilClassificationResult {
        val ll = params.liquidLimit.toFloatOrNull() ?: 0f
        val pl = params.plasticLimit.toFloatOrNull() ?: 0f
        val pi = if (ll > 0 && pl > 0 && ll >= pl) ll - pl else 0f

        val aashto = classifyAASHTO(result, ll, pi)
        val uscs = classifyUSCS(result, ll, pi)

        val aiCommentary = getAiCommentary(aashto, uscs, result, ll, pi)
        val recommendation = getRecommendation(aashto, uscs)

        return SoilClassificationResult(aashto, uscs, aiCommentary, recommendation)
    }

    private fun classifyAASHTO(result: SieveAnalysisResult, ll: Float, pi: Float): AASHTOResult {
        val p10 = result.sieves.find { abs(it.opening - 2.00) < 0.001 }?.percentPassing?.toFloat() ?: 100f
        val p40 = result.sieves.find { abs(it.opening - 0.425) < 0.001 }?.percentPassing?.toFloat() ?: 100f
        val p200 = result.percentFines

        if (p200 <= 35) { // Granular Materials
            if (p200 <= 25 && p40 <= 50 && pi <= 6) {
                if (p200 <= 15 && p40 <= 30) return AASHTOResult("A-1-a", "0")
                return AASHTOResult("A-1-b", "0")
            }
            if (p200 <= 10 && p40 > 50 && pi <= 0) return AASHTOResult("A-3", "0")
            if (ll <= 40 && pi <= 10) return AASHTOResult("A-2-4", calculateGI(p200, ll, pi).toString())
            if (ll > 40 && pi <= 10) return AASHTOResult("A-2-5", calculateGI(p200, ll, pi).toString())
            if (ll <= 40 && pi > 10) return AASHTOResult("A-2-6", calculateGI(p200, ll, pi).toString())
            if (ll > 40 && pi > 10) return AASHTOResult("A-2-7", calculateGI(p200, ll, pi).toString())
        } else { // Silt-Clay Materials
            if (ll <= 40 && pi <= 10) return AASHTOResult("A-4", calculateGI(p200, ll, pi).toString())
            if (ll > 40 && pi <= 10) return AASHTOResult("A-5", calculateGI(p200, ll, pi).toString())
            if (ll <= 40 && pi > 10) return AASHTOResult("A-6", calculateGI(p200, ll, pi).toString())
            if (ll > 40 && pi > 10) {
                return if (pi <= ll - 30) AASHTOResult("A-7-5", calculateGI(p200, ll, pi).toString())
                else AASHTOResult("A-7-6", calculateGI(p200, ll, pi).toString())
            }
        }
        return AASHTOResult("Unknown", "N/A")
    }

    private fun calculateGI(f: Float, ll: Float, pi: Float): Int {
        if (ll == 0f || pi == 0f) return 0
        var gi = (f - 35) * (0.2 + 0.005 * (ll - 40)) + 0.01 * (f - 15) * (pi - 10)
        if (gi < 0) gi = 0.0
        return gi.toInt()
    }

    private fun classifyUSCS(result: SieveAnalysisResult, ll: Float, pi: Float): USCSResult {
        val fines = result.percentFines
        val gravel = result.percentGravel
        val sand = result.percentSand

        if (fines >= 50) { // Fine-grained Soils
            val aLine = 0.73f * (ll - 20)
            return if (ll < 50) { // Low Plasticity
                if (pi > aLine && pi >= 4) USCSResult("CL", R.string.desc_cl)
                else USCSResult("ML", R.string.desc_ml)
            } else { // High Plasticity
                if (pi > aLine) USCSResult("CH", R.string.desc_ch)
                else USCSResult("MH", R.string.desc_mh)
            }
        } else { // Coarse-grained Soils
            val isGravel = gravel >= sand
            val cu = result.cu ?: 0.0
            val cc = result.cc ?: 0.0

            if (fines < 5) { // Clean coarse
                if (isGravel) {
                    return if (cu >= 4 && cc in 1.0..3.0) USCSResult("GW", R.string.uscs_desc_gw) else USCSResult("GP", R.string.uscs_desc_gp)
                } else { // Sand
                    return if (cu >= 6 && cc in 1.0..3.0) USCSResult("SW", R.string.uscs_desc_sw) else USCSResult("SP", R.string.uscs_desc_sp)
                }
            } else if (fines > 12) { // Coarse with fines
                val aLine = 0.73f * (ll - 20)
                val plotsAboveAline = pi > aLine
                if (isGravel) {
                    return if (plotsAboveAline) USCSResult("GC", R.string.uscs_desc_gc) else USCSResult("GM", R.string.uscs_desc_gm)
                } else { // Sand
                    return if (plotsAboveAline) USCSResult("SC", R.string.uscs_desc_sc) else USCSResult("SM", R.string.uscs_desc_sm)
                }
            } else { // Dual symbol case (5% to 12% fines)
                val aLine = 0.73f * (ll - 20)
                val plotsAboveAline = pi > aLine
                if (isGravel) {
                    val first = if (cu >= 4 && cc in 1.0..3.0) "GW" else "GP"
                    val second = if (plotsAboveAline) "GC" else "GM"
                    return USCSResult("$first-$second", R.string.uscs_desc_dual)
                } else { // Sand
                    val first = if (cu >= 6 && cc in 1.0..3.0) "SW" else "SP"
                    val second = if (plotsAboveAline) "SC" else "SM"
                    return USCSResult("$first-$second", R.string.uscs_desc_dual)
                }
            }
        }
    }

    fun getFrostSusceptibility(percentFines: Float): FrostSusceptibilityResult {
        return when {
            percentFines <= 3 -> FrostSusceptibilityResult(R.string.frost_neg, "#4CAF50")
            percentFines <= 10 -> FrostSusceptibilityResult(R.string.frost_low, "#8BC34A")
            percentFines <= 20 -> FrostSusceptibilityResult(R.string.frost_medium, "#FFEB3B")
            percentFines <= 35 -> FrostSusceptibilityResult(R.string.frost_high, "#FF9800")
            else -> FrostSusceptibilityResult(R.string.frost_v_high, "#F44336")
        }
    }

    private fun getAiCommentary(aashto: AASHTOResult, uscs: USCSResult, result: SieveAnalysisResult, ll: Float, pi: Float): Int {
        val isWellGraded = (uscs.groupName.startsWith("GW") || uscs.groupName.startsWith("SW"))
        val isPoorlyGraded = (uscs.groupName.startsWith("GP") || uscs.groupName.startsWith("SP"))
        val isHighPlasticity = (uscs.groupName.contains("H"))

        return when {
            isWellGraded -> R.string.commentary_well_graded
            isPoorlyGraded -> R.string.commentary_poorly_graded
            isHighPlasticity -> R.string.commentary_high_plasticity
            result.percentSand > 50 && result.percentFines > 12 -> R.string.commentary_sandy_fines
            else -> R.string.commentary_low_plasticity
        }
    }

    private fun getRecommendation(aashto: AASHTOResult, uscs: USCSResult): Int {
        return R.string.cbr_rec_good
    }
    fun predictEngineeringProperties(result: SieveAnalysisResult, params: ClassificationParameters, uscs: USCSResult): PredictedProperties? {
        val ll = params.liquidLimit.toFloatOrNull()
        val pi = params.plasticLimit.toFloatOrNull()?.let { pl -> ll?.minus(pl) }
        val p200 = result.percentFines

        if (ll == null || pi == null) return null

        val predictedMdd: Float
        val predictedOmc: Float

        if (uscs.groupName.contains("C") || uscs.groupName.contains("M")) { // Fine-grained or coarse-grained with fines
            predictedOmc = (2.4f + 0.6f * ll + 0.23f * pi).coerceIn(8f, 35f)
            predictedMdd = (2.14f - 0.007f * ll - 0.005f * pi).coerceIn(1.6f, 2.1f)
        } else { // Coarse-grained, clean (GW, GP, SW, SP)
            predictedOmc = (0.25f * ll + 0.15f * pi + 0.05f * p200).coerceIn(5f, 20f)
            predictedMdd = (2.25f - 0.002f * ll - 0.001f * pi - 0.008f * p200).coerceIn(1.8f, 2.3f)
        }

        // CBR prediction is safer and more robust
        val predictedCbr: Float? = if (pi > 0) {
            // This type of correlation is mainly valid for cohesive soils (PI > 0).
            val logCbr = 2.4 - 1.8 * log10(pi.toDouble()) + 0.08 * predictedMdd
            (10.0.pow(logCbr)).toFloat().coerceIn(1f, 100f)
        } else {
            // Cannot predict CBR for non-plastic soils with this specific model.
            null
        }

        return PredictedProperties(
            predictedMdd = if(predictedMdd.isNaN()) null else predictedMdd,
            predictedOmc = if(predictedOmc.isNaN()) null else predictedOmc,
            predictedCbr = if(predictedCbr == null || predictedCbr.isNaN()) null else predictedCbr
        )
    }
}

// --- Proctor Test Logic ---
object ProctorCalculator {
    fun calculate(points: List<ProctorDataPoint>, parameters: ProctorTestParameters): ProctorResult? {
        if (points.size < 3) return null

        // Using a simple parabolic fit for y = ax^2 + bx + c
        // where y = dry density, x = moisture content
        val n = points.size.toDouble()
        val sumX = points.sumOf { it.moistureContent }
        val sumY = points.sumOf { it.dryDensity }
        val sumX2 = points.sumOf { it.moistureContent.pow(2) }
        val sumX3 = points.sumOf { it.moistureContent.pow(3) }
        val sumX4 = points.sumOf { it.moistureContent.pow(4) }
        val sumXY = points.sumOf { it.moistureContent * it.dryDensity }
        val sumX2Y = points.sumOf { it.moistureContent.pow(2) * it.dryDensity }

        val matrix = arrayOf(
            doubleArrayOf(sumX4, sumX3, sumX2, sumX2Y),
            doubleArrayOf(sumX3, sumX2, sumX, sumXY),
            doubleArrayOf(sumX2, sumX, n, sumY)
        )

        // Simple Gaussian elimination with pivot check
        for (i in 0..2) {
            var maxEl = abs(matrix[i][i])
            var maxRow = i
            for (k in i + 1..2) {
                if (abs(matrix[k][i]) > maxEl) {
                    maxEl = abs(matrix[k][i])
                    maxRow = k
                }
            }
            for (k in i..3) {
                val tmp = matrix[maxRow][k]
                matrix[maxRow][k] = matrix[i][k]
                matrix[i][k] = tmp
            }
            if (abs(matrix[i][i]) < 1e-9) return null
            for (k in i + 1..2) {
                val c = -matrix[k][i] / matrix[i][i]
                for (j in i..3) {
                    if (i == j) {
                        matrix[k][j] = 0.0
                    } else {
                        matrix[k][j] += c * matrix[i][j]
                    }
                }
            }
        }

        val coeffs = DoubleArray(3)
        for (i in 2 downTo 0) {
            coeffs[i] = matrix[i][3] / matrix[i][i]
            for (k in i - 1 downTo 0) {
                matrix[k][3] -= matrix[k][i] * coeffs[i]
            }
        }
        val a = coeffs[0]
        val b = coeffs[1]
        val c = coeffs[2]

        if (a >= 0) return null // Not a downward-opening parabola

        val omc = -b / (2 * a)
        val mdd = a * omc.pow(2) + b * omc + c

        val minMoisture = points.first().moistureContent - 2
        val maxMoisture = points.last().moistureContent + 2
        val fittedCurvePoints = mutableListOf<Pair<Float, Float>>()
        for (i in 0..100) {
            val mc = minMoisture + (maxMoisture - minMoisture) * i / 100
            val dd = a * mc.pow(2) + b * mc + c
            fittedCurvePoints.add(mc.toFloat() to dd.toFloat())
        }

        val gs = parameters.specificGravity.toDoubleOrNull() ?: 2.70
        val zavPoints = fittedCurvePoints.map { (mc, _) ->
            val w = mc / 100.0
            val zavDensity = (gs * 1.0) / (1 + w * gs)
            mc to zavDensity.toFloat()
        }

        return ProctorResult(
            points = points,
            maxDryDensity = mdd.toFloat(),
            optimumMoistureContent = omc.toFloat(),
            fittedCurvePoints = fittedCurvePoints,
            zavCurvePoints = zavPoints,
            ninetyFivePercentMDD = (mdd * 0.95).toFloat()
        )
    }

    fun getDensityAtMoisture(curvePoints: List<Pair<Float, Float>>, moisture: Double): Float? {
        if(curvePoints.size < 2) return null
        for (i in 0 until curvePoints.size - 1) {
            val p1 = curvePoints[i]
            val p2 = curvePoints[i + 1]
            if (moisture >= p1.first && moisture <= p2.first) {
                if (p2.first - p1.first == 0f) return p1.second
                val fraction = (moisture.toFloat() - p1.first) / (p2.first - p1.first)
                return (p1.second + fraction * (p2.second - p1.second))
            }
        }
        return null
    }

    fun getMoistureRangeForCompaction(curvePoints: List<Pair<Float, Float>>, mdd: Float, requiredCompaction: Double): Pair<Float, Float>? {
        if (curvePoints.isEmpty()) return null
        val targetDensity = mdd * (requiredCompaction / 100.0)

        // Find intersections
        val intersections = mutableListOf<Float>()
        for (i in 0 until curvePoints.size - 1) {
            val p1 = curvePoints[i]
            val p2 = curvePoints[i+1]
            // Check if the line segment crosses the target density
            if ((p1.second >= targetDensity && p2.second < targetDensity) || (p1.second < targetDensity && p2.second >= targetDensity)) {
                // Linear interpolation to find the moisture content at intersection
                val fraction = (targetDensity.toFloat() - p1.second) / (p2.second - p1.second)
                val moisture = p1.first + fraction * (p2.first - p1.first)
                intersections.add(moisture)
            }
        }
        return if (intersections.size >= 2) {
            Pair(intersections.minOrNull() ?: 0f, intersections.maxOrNull() ?: 0f)
        } else {
            null
        }
    }
}

object ProctorExampleDataGenerator {
    private data class ProctorProfile(
        val descriptionResId: Int,
        val omc: Double,
        val mdd: Double,
        val gs: String
    )

    private val profiles = listOf(
        ProctorProfile(R.string.example_desc_proctor_sc, 10.5, 2.12, "2.68"), // Clayey Sand
        ProctorProfile(R.string.example_desc_proctor_cl, 16.0, 1.85, "2.72"), // Lean Clay
        ProctorProfile(R.string.example_desc_proctor_gw, 7.5, 2.25, "2.65")  // Well-graded Gravel
    )

    fun generate(): ExampleProctorData {
        val random = Random.Default
        val profile = profiles.random(random)

        val params = ProctorTestParameters(
            moldWeight = String.format(Locale.US, "%.1f", 4250.0 + random.nextDouble(-5.0, 5.0)),
            moldVolume = "944",
            specificGravity = profile.gs,
            testType = if (random.nextBoolean()) ProctorTestType.MODIFIED else ProctorTestType.STANDARD
        )

        val points = mutableListOf<ProctorDataPoint>()
        val moisturePoints = listOf(-4.0, -2.0, 0.0, 2.0, 4.0).shuffled(random)

        for (i in 0..4) {
            val moisture = profile.omc + moisturePoints[i] + random.nextDouble(-0.5, 0.5)
            // Simulate a parabolic curve: Density = MDD - k * (Moisture - OMC)^2
            val k = 0.005 + random.nextDouble(-0.0005, 0.0005) // Steepness factor
            val dryDensity = profile.mdd - k * (moisture - profile.omc).pow(2) + random.nextDouble(-0.01, 0.01)
            val wetDensity = dryDensity * (1 + moisture / 100.0)

            points.add(ProctorDataPoint(
                moistureContent = "%.1f".format(Locale.US, moisture).toDouble(),
                wetDensity = "%.2f".format(Locale.US, wetDensity).toDouble(),
                dryDensity = "%.3f".format(Locale.US, dryDensity).toDouble()
            ))
        }

        return ExampleProctorData(
            parameters = params,
            points = points.sortedBy { it.moistureContent },
            descriptionResId = profile.descriptionResId
        )
    }
}

