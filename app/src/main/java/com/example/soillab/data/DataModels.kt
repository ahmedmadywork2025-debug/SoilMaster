package com.example.soillab.data

import com.example.soillab.R
import com.github.mikephil.charting.data.Entry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.*

// --- Serializers for Custom Types ---

object ClosedFloatRangeSerializer : KSerializer<ClosedRange<Float>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClosedFloatRange") {
        element<Float>("start"); element<Float>("endInclusive")
    }
    override fun serialize(encoder: Encoder, value: ClosedRange<Float>) {
        encoder.beginStructure(descriptor).apply {
            encodeFloatElement(descriptor, 0, value.start)
            encodeFloatElement(descriptor, 1, value.endInclusive)
            endStructure(descriptor)
        }
    }
    override fun deserialize(decoder: Decoder): ClosedRange<Float> {
        var start = 0f; var end = 0f
        decoder.beginStructure(descriptor).apply {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> start = decodeFloatElement(descriptor, index)
                    1 -> end = decodeFloatElement(descriptor, index)
                    -1 -> break
                    else -> error("Unexpected index: $index")
                }
            }
            endStructure(descriptor)
        }
        return start..end
    }
}

object EntrySerializer : KSerializer<Entry> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Entry") {
        element<Float>("x"); element<Float>("y")
    }
    override fun serialize(encoder: Encoder, value: Entry) {
        encoder.beginStructure(descriptor).apply {
            encodeFloatElement(descriptor, 0, value.x)
            encodeFloatElement(descriptor, 1, value.y)
            endStructure(descriptor)
        }
    }
    override fun deserialize(decoder: Decoder): Entry {
        var x = 0f; var y = 0f
        decoder.beginStructure(descriptor).apply {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeFloatElement(descriptor, index)
                    1 -> y = decodeFloatElement(descriptor, index)
                    -1 -> break
                    else -> error("Unexpected index: $index")
                }
            }
            endStructure(descriptor)
        }
        return Entry(x, y)
    }
}

@Serializable
data class EntryList(val entries: List<@Serializable(with = EntrySerializer::class) Entry>)
object EntryListSerializer : KSerializer<List<Entry>> {
    private val listSerializer = EntryList.serializer()
    override val descriptor: SerialDescriptor = listSerializer.descriptor
    override fun serialize(encoder: Encoder, value: List<Entry>) {
        encoder.encodeSerializableValue(listSerializer, EntryList(value))
    }
    override fun deserialize(decoder: Decoder): List<Entry> {
        return decoder.decodeSerializableValue(listSerializer).entries
    }
}

// --- Universal Data Models ---

@Serializable
data class TestInfo(
    val boreholeNo: String = "",
    val sampleNo: String = "",
    val sampleDescription: String = "",
    val testDate: String = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(Date()),
    val testedBy: String = "",
    val checkedBy: String = ""
)

// --- Atterberg Limits Data Models ---

@Serializable
data class AtterbergReport(
    val id: String = UUID.randomUUID().toString(),
    val testInfo: TestInfo,
    val llSamples: List<LiquidLimitSample>,
    val plSamples: List<PlasticLimitSample>,
    val calculationResult: CalculationResult
) {
    @Transient
    val summary: AtterbergReportSummary = AtterbergReportSummary(
        id = id,
        boreholeNo = testInfo.boreholeNo,
        sampleNo = testInfo.sampleNo,
        testDate = testInfo.testDate,
        liquidLimit = calculationResult.liquidLimit,
        plasticityIndex = calculationResult.plasticityIndex,
        soilClassification = calculationResult.soilClassification
    )
}

@Serializable data class LiquidLimitSample(val id: Int = 0, val blows: String = "", val waterContent: String = "")
@Serializable data class PlasticLimitSample(val id: Int = 0, val waterContent: String = "")
@Serializable data class DataPoint(val blows: Float, val waterContent: Float)

@Serializable
data class CalculationResult(
    val liquidLimit: Float? = null,
    val plasticLimit: Float? = null,
    val plasticityIndex: Float? = null,
    val points: List<DataPoint> = emptyList(),
    @Serializable(with = EntryListSerializer::class) val bestFitLine: List<Entry> = emptyList(),
    val isNonPlastic: Boolean = false,
    val soilClassification: String = "N/A",
    val advancedAnalysis: AdvancedClassificationResult? = null,
    val calculationMethod: String = "Standard Flow Curve",
    val correlationCoefficient: Float? = null
)

@Serializable
data class AtterbergReportSummary(
    val id: String, val boreholeNo: String, val sampleNo: String, val testDate: String,
    val liquidLimit: Float?, val plasticityIndex: Float?, val soilClassification: String
)

data class ExampleAtterbergData(
    val descriptionResId: Int,
    val llSamples: List<LiquidLimitSample>,
    val plSamples: List<PlasticLimitSample>
)

// --- CBR Test Data Models ---

enum class TestPurpose(val displayNameResId: Int) {
    SUBGRADE(R.string.purpose_subgrade),
    SUBBASE(R.string.purpose_subbase),
    BASE_COURSE(R.string.purpose_base_course)
}


@Serializable
data class CBRReport(
    val id: String,
    val testParameters: CBRTestParameters,
    val points: List<CBRDataPoint>,
    val correctedPoints: List<CBRDataPoint>? = null,
    val result: CBRCalculationResult? = null
)

@Serializable
data class CBRTestParameters(
    val testInfo: TestInfo = TestInfo(),
    val moistureContent: String = "",
    val dryDensity: String = "",
    val surchargeWeight: String = "2.27",
    val soakingTime: String = "4",
    val refLoad25: String = "13.34",
    val refLoad50: String = "20.01",
    val provingRingFactor: String = "",
    val testPurpose: TestPurpose = TestPurpose.SUBGRADE
)

@Serializable data class CBRDataPoint(val penetration: Double, val load: Double)

@Serializable
data class CBRCalculationResult(
    val loadAt2_5: Double,
    val loadAt5_0: Double,
    val cbrAt2_5: Double,
    val cbrAt5_0: Double,
    val finalCbrValue: Double,
    val isCorrected: Boolean,
    val messageResId: Int,
    val insights: CBRInsights? = null,
    val predictedKValue: Double? = null
)

@Serializable
data class CBRInsights(
    val qualityRatingResId: Int,
    val ratingColorHex: String,
    val curveInterpretationResId: Int,
    val estimatedResilientModulus: String,
    val estimatedShearStrength: String,
    val primaryRecommendationResId: Int
)

data class ExampleCBRData(val points: List<CBRDataPoint>, val descriptionResId: Int)

// --- Sieve Analysis Data Models ---

@Serializable
data class SieveAnalysisReport(
    val id: String,
    val testInfo: TestInfo,
    val parameters: ClassificationParameters,
    val sieves: List<Sieve>,
    val result: SieveAnalysisResult? = null
)

@Serializable
data class ClassificationParameters(
    val liquidLimit: String = "",
    val plasticLimit: String = "",
    val initialWeight: String = ""
)

enum class SampleType(val displayNameResId: Int) {
    SOIL(R.string.sample_type_soil),
    AGGREGATE(R.string.sample_type_aggregate)
}


@Serializable
data class Sieve(
    val name: String,
    val opening: Double, // in mm
    val retainedWeight: String = "",
    @Transient val cumulativeRetained: Double = 0.0,
    @Transient val percentPassing: Double = 0.0
) {
    companion object {
        fun standardSet(): List<Sieve> = listOf(
            Sieve("3\"", 75.0), Sieve("2\"", 50.0), Sieve("1.5\"", 37.5), Sieve("1\"", 25.0),
            Sieve("3/4\"", 19.0), Sieve("1/2\"", 12.5), Sieve("3/8\"", 9.5),
            Sieve("No. 4", 4.75), Sieve("No. 10", 2.00), Sieve("No. 20", 0.850),
            Sieve("No. 40", 0.425), Sieve("No. 60", 0.250), Sieve("No. 100", 0.150),
            Sieve("No. 200", 0.075), Sieve("Pan", 0.0)
        )
        fun aggregateSet(): List<Sieve> = listOf(
            Sieve("3\"", 75.0), Sieve("2.5\"", 63.0), Sieve("2\"", 50.0), Sieve("1.5\"", 37.5),
            Sieve("1\"", 25.0), Sieve("3/4\"", 19.0), Sieve("1/2\"", 12.5), Sieve("3/8\"", 9.5),
            Sieve("No. 4", 4.75), Sieve("No. 8", 2.36), Sieve("No. 16", 1.18),
            Sieve("No. 30", 0.600), Sieve("No. 50", 0.300), Sieve("No. 100", 0.150),
            Sieve("No. 200", 0.075), Sieve("Pan", 0.0)
        )
    }
}

@Serializable
data class PredictedProperties(
    val predictedMdd: Float? = null,
    val predictedOmc: Float? = null,
    val predictedCbr: Float? = null
)


@Serializable
data class SieveAnalysisResult(
    val sieves: List<Sieve>,
    val percentGravel: Float,
    val percentSand: Float,
    val percentFines: Float,
    val d10: Double?,
    val d30: Double?,
    val d60: Double?,
    val cu: Double?, // Coefficient of Uniformity
    val cc: Double?, // Coefficient of Curvature
    val finenessModulus: Float,
    val materialLossPercentage: Float?,
    val classification: SoilClassificationResult? = null,
    val estimatedPermeability: Double? = null,
    val frostSusceptibility: FrostSusceptibilityResult? = null,
    val predictedProperties: PredictedProperties? = null
)

@Serializable
data class SoilClassificationResult(
    val aashto: AASHTOResult,
    val uscs: USCSResult,
    val aiCommentaryResId: Int,
    val recommendationResId: Int
)
@Serializable
data class FrostSusceptibilityResult(val resId: Int, val colorHex: String)

@Serializable data class AASHTOResult(val groupName: String, val groupIndex: String)
@Serializable data class USCSResult(val groupName: String, val groupDescriptionResId: Int)


// --- Shared AI & Validation Models ---

@Serializable data class ValidationResult(val isValid: Boolean, val warnings: List<ValidationWarning>, val errors: List<ValidationError>)
@Serializable data class ValidationWarning(val code: String, val message: String, val severity: SeverityLevel)
@Serializable data class ValidationError(val code: String, val message: String, val field: String)
@Serializable enum class SeverityLevel { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class AdvancedClassificationResult(
    val basicClassification: USCSClassification,
    val behaviorAnalysis: SoilBehaviorAnalysis,
    val engineeringProperties: EngineeringProperties,
    val constructionRecommendations: List<ConstructionRecommendation>,
    val qualityAssessment: QualityAssessment
)

@Serializable
data class USCSClassification(
    val nameResId: Int,
    val descriptionResId: Int,
    val symbol: String,
    val confidence: Float
)

@Serializable data class SoilBehaviorAnalysis(val swellPotential: SwellPotential, val compressibility: Compressibility, val permeability: Permeability, val frostSusceptibility: FrostSusceptibility, val shearStrength: ShearStrength)
@Serializable data class EngineeringProperties(@Serializable(with = ClosedFloatRangeSerializer::class) val estimatedFrictionAngle: ClosedRange<Float>, @Serializable(with = ClosedFloatRangeSerializer::class) val estimatedCohesion: ClosedRange<Float>, @Serializable(with = ClosedFloatRangeSerializer::class) val compressionIndex: ClosedRange<Float>, @Serializable(with = ClosedFloatRangeSerializer::class) val elasticModulus: ClosedRange<Float>)
@Serializable data class ConstructionRecommendation(val categoryResId: Int, val recommendationResId: Int, val priority: Priority, val technicalBasis: String)
@Serializable data class QualityAssessment(val dataQuality: DataQuality, val calculationReliability: ReliabilityLevel, val complianceWithStandards: ComplianceLevel, val recommendationsForImprovement: List<Int>)
@Serializable enum class SwellPotential { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
@Serializable enum class Compressibility { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
@Serializable enum class Permeability { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
@Serializable enum class FrostSusceptibility { NONE, VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
@Serializable enum class ShearStrength { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
@Serializable enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }
@Serializable enum class DataQuality { EXCELLENT, GOOD, FAIR, POOR, UNACCEPTABLE }
@Serializable enum class ReliabilityLevel { VERY_HIGH, HIGH, MEDIUM, LOW, VERY_LOW }
@Serializable enum class ComplianceLevel { FULL, PARTIAL, MINIMAL, NON_COMPLIANT }

// --- Specification Models ---
@Serializable
data class Specification(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val nameResId: Int = 0,
    val limits: List<SpecificationLimit>,
    val isCustom: Boolean = false
)

@Serializable
data class SpecificationLimit(
    val sieveOpening: Double,
    val minPassing: Float,
    val maxPassing: Float
)


object SpecificationRepository {
    fun getPredefinedSpecs(): List<Specification> {
        return listOf(
            saudiBaseCourseA, saudiSubbase, astmC33Fine, astmC33Size57, astmC33Size67, superpave19
        )
    }

    private val saudiBaseCourseA = Specification(
        nameResId = R.string.spec_saudi_base_a,
        limits = listOf(
            SpecificationLimit(50.0, 100f, 100f),
            SpecificationLimit(37.5, 70f, 95f),
            SpecificationLimit(25.0, 55f, 85f),
            SpecificationLimit(4.75, 30f, 55f),
            SpecificationLimit(2.00, 20f, 40f),
            SpecificationLimit(0.425, 10f, 25f),
            SpecificationLimit(0.075, 5f, 12f)
        )
    )

    private val saudiSubbase = Specification(
        nameResId = R.string.spec_saudi_subbase,
        limits = listOf(
            SpecificationLimit(50.0, 100f, 100f),
            SpecificationLimit(25.0, 60f, 100f),
            SpecificationLimit(4.75, 35f, 70f),
            SpecificationLimit(0.425, 10f, 30f),
            SpecificationLimit(0.075, 5f, 15f)
        )
    )

    private val astmC33Fine = Specification(
        nameResId = R.string.spec_astm_c33_fine,
        limits = listOf(
            SpecificationLimit(9.5, 100f, 100f),
            SpecificationLimit(4.75, 95f, 100f),
            SpecificationLimit(2.36, 80f, 100f),
            SpecificationLimit(1.18, 50f, 85f),
            SpecificationLimit(0.600, 25f, 60f),
            SpecificationLimit(0.300, 5f, 30f),
            SpecificationLimit(0.150, 0f, 10f)
        )
    )

    private val astmC33Size57 = Specification(
        nameResId = R.string.spec_astm_c33_57,
        limits = listOf(
            SpecificationLimit(37.5, 100f, 100f),
            SpecificationLimit(25.0, 95f, 100f),
            SpecificationLimit(12.5, 25f, 60f),
            SpecificationLimit(4.75, 0f, 10f),
            SpecificationLimit(2.36, 0f, 5f)
        )
    )

    private val astmC33Size67 = Specification(
        nameResId = R.string.spec_astm_c33_67,
        limits = listOf(
            SpecificationLimit(25.0, 100f, 100f),
            SpecificationLimit(19.0, 90f, 100f),
            SpecificationLimit(9.5, 20f, 55f),
            SpecificationLimit(4.75, 0f, 10f),
            SpecificationLimit(2.36, 0f, 5f)
        )
    )

    private val superpave19 = Specification(
        nameResId = R.string.spec_superpave_19,
        limits = listOf(
            SpecificationLimit(25.0, 100f, 100f),
            SpecificationLimit(19.0, 90f, 100f),
            SpecificationLimit(12.5, 90f, 90f), // Control point
            SpecificationLimit(2.36, 23f, 49f),
            SpecificationLimit(0.075, 2f, 8f)
        )
    )
}

// --- Proctor Test Models ---

enum class ProctorTestType(val displayNameResId: Int) {
    STANDARD(R.string.proctor_standard),
    MODIFIED(R.string.proctor_modified)
}

@Serializable
data class ProctorTestParameters(
    val testInfo: TestInfo = TestInfo(),
    val testType: ProctorTestType = ProctorTestType.STANDARD,
    val moldWeight: String = "",
    val moldVolume: String = "",
    val specificGravity: String = "2.70"
)

@Serializable
data class ProctorDataPoint(
    val moistureContent: Double,
    val wetDensity: Double,
    val dryDensity: Double
)

@Serializable
data class ProctorResult(
    val points: List<ProctorDataPoint>,
    val maxDryDensity: Float,
    val optimumMoistureContent: Float,
    val fittedCurvePoints: List<Pair<Float, Float>>,
    val zavCurvePoints: List<Pair<Float, Float>>,
    val ninetyFivePercentMDD: Float,
    val achievableDryDensity: Float? = null,
    val compactionBand: Pair<Float, Float>? = null,
    val predictedSoakedCBR: Float? = null,
    val swellPotential: SwellPotential? = null
)

@Serializable
data class ProctorReport(
    val id: String = UUID.randomUUID().toString(),
    val parameters: ProctorTestParameters,
    val result: ProctorResult
)

data class ExampleProctorData(
    val parameters: ProctorTestParameters,
    val points: List<ProctorDataPoint>,
    val descriptionResId: Int
)

