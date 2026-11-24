package br.ufpr.lab_mobile.model

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.math.BigDecimal

/** Indicadores de um equipamento, como devolvidos por GET /api/equipments/{id}/health. */
data class HealthIndicators(
    val tag: String?,
    val physicalChemical: PhysicalChemicalBlock?,
    val dissolvedGases: DissolvedGasBlock?,
) {
    val gases: HealthPanel get() = dissolvedGases?.toPanel() ?: HealthPanel.EMPTY
    val oil: HealthPanel get() = physicalChemical?.toPanel() ?: HealthPanel.EMPTY
}

data class DissolvedGasBlock(
    val analysisDates: List<String?>?,
    val gases: List<DissolvedGas>?,
    val report: TestReport?,
) {
    fun toPanel(): HealthPanel {
        val dates = analysisDates.orEmpty()
        val indicators = gases.orEmpty().map { gas ->
            HealthIndicator(
                key = gas.key.orEmpty(), name = gas.name.orEmpty(), formula = gas.formula.known(), unit = GAS_UNIT,
                method = null, limit = null, readings = readings(dates, gas.results),
            )
        }
        return HealthPanel(indicators, HIGHLIGHTED_GASES, knownDate(dates.lastOrNull()), report)
    }
}

/** Um gás ou um totalizador; o totalizador vem sem fórmula. */
data class DissolvedGas(
    val key: String?,
    val name: String?,
    val formula: String?,
    val results: List<JsonElement?>?,
)

data class PhysicalChemicalBlock(
    val columns: List<AnalysisColumn>?,
    val tests: List<PhysicalChemicalTest>?,
    val report: TestReport?,
) {
    fun toPanel(): HealthPanel {
        val dates = columns.orEmpty().map(AnalysisColumn::date)
        val indicators = tests.orEmpty().map { test ->
            // A unidade já faz parte do nome destes ensaios, como em "Teor de Água (ppm m/m)".
            HealthIndicator(
                key = test.key.orEmpty(), name = test.name.orEmpty(), formula = null, unit = null,
                method = test.method.known(), limit = test.limit.known(), readings = readings(dates, test.valuesByAnalysis),
            )
        }
        return HealthPanel(indicators, HIGHLIGHTED_TESTS, knownDate(dates.lastOrNull()), report)
    }
}

data class AnalysisColumn(val date: String?)

data class PhysicalChemicalTest(
    val key: String?,
    val name: String?,
    val method: String?,
    val limit: String?,
    val valuesByAnalysis: List<JsonElement?>?,
)

/** Parecer do laboratório sobre a análise mais recente daquele tipo. */
data class TestReport(val title: String?, val paragraphs: List<String?>?) {
    /**
     * Classificação dada pelo laboratório. Só a fonte Spring a envia, no lugar do título; na fonte
     * AutoLAB o título é o do laudo e não classifica nada.
     */
    val classification: LabClassification? get() = LabClassification.entries.find { it.code == title?.trim() }

    /** Título do laudo, quando não é uma classificação. */
    val heading: String? get() = title.known().takeIf { classification == null }

    val text: String get() = paragraphs.orEmpty().mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.joinToString("\n\n")
}

enum class LabClassification(val code: String) {
    NORMAL("normal"),
    ALERT("alerta"),
    NON_COMPLIANT("nao-conforme"),
}

/** Os resultados de um tipo de análise, prontos para a tela. */
class HealthPanel(
    val indicators: List<HealthIndicator>,
    highlightedKeys: List<String>,
    /** Data da análise mais recente, ou `null` quando a fonte não informa. */
    val date: String?,
    val report: TestReport?,
) {
    /** Os indicadores em destaque que este equipamento tem, na ordem de importância. */
    val highlights: List<HealthIndicator> = highlightedKeys.mapNotNull { key -> indicators.find { it.key == key } }

    companion object {
        val EMPTY = HealthPanel(emptyList(), emptyList(), null, null)
    }
}

/** Um gás ou ensaio com as suas leituras, da mais antiga para a mais recente. */
data class HealthIndicator(
    val key: String,
    val name: String,
    val formula: String?,
    val unit: String?,
    val method: String?,
    val limit: String?,
    val readings: List<HealthReading>,
) {
    val latest: MeasuredValue get() = readings.lastOrNull()?.value ?: MeasuredValue.NotPerformed

    /**
     * Mudança em relação à leitura anterior, ou `null` quando não há com o que comparar. Análises
     * em que este ensaio não foi feito são puladas, e a data diz com qual delas é a comparação.
     */
    val variation: Variation?
        get() {
            val current = latest
            if (current == MeasuredValue.NotPerformed) return null
            val previous = readings.dropLast(1).lastOrNull { it.value != MeasuredValue.NotPerformed } ?: return null
            val before = previous.value
            if (current is MeasuredValue.Quantity && before is MeasuredValue.Quantity) {
                val difference = current.value - before.value
                return if (difference.signum() == 0) Variation.Unchanged(previous.date) else Variation.Difference(difference, previous.date)
            }
            return if (current == before) Variation.Unchanged(previous.date) else Variation.Replaced(before, previous.date)
        }
}

data class HealthReading(val date: String?, val value: MeasuredValue)

/**
 * O resultado de um ensaio. "Não detectado" é uma medição; "não realizado" é a falta dela, e a tela
 * nunca pode trocar um pelo outro nem tratar qualquer dos dois como zero.
 */
sealed interface MeasuredValue {
    data class Quantity(val value: BigDecimal) : MeasuredValue

    /** Resultado que o laboratório escreveu como texto, como "0,01%". */
    data class Text(val text: String) : MeasuredValue
    data object NotDetected : MeasuredValue
    data object NotPerformed : MeasuredValue
}

/** Mudança desde a leitura de [date]. Não diz se é boa ou ruim: isso depende do ensaio. */
sealed interface Variation {
    val date: String?

    data class Difference(val amount: BigDecimal, override val date: String?) : Variation
    data class Unchanged(override val date: String?) : Variation

    /** As duas leituras não são números, então só dá para mostrar a anterior. */
    data class Replaced(val previous: MeasuredValue, override val date: String?) : Variation
}

/** Casa cada resultado com a sua análise pela posição; resultado que falta é ensaio não realizado. */
private fun readings(dates: List<String?>, results: List<JsonElement?>?): List<HealthReading> =
    dates.mapIndexed { index, date -> HealthReading(knownDate(date), measuredValue(results?.getOrNull(index))) }

/** A API envia um número, "ND", o texto do laboratório ou nulo. */
internal fun measuredValue(result: JsonElement?): MeasuredValue {
    val primitive = result as? JsonPrimitive ?: return MeasuredValue.NotPerformed
    if (primitive.isNumber) return MeasuredValue.Quantity(primitive.asBigDecimal)
    return when (val text = primitive.asString.trim()) {
        "" -> MeasuredValue.NotPerformed
        "ND" -> MeasuredValue.NotDetected
        else -> MeasuredValue.Text(text)
    }
}

/** A API usa vazio ou "-" para o que o laboratório não cadastrou. */
private fun String?.known(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "-" }

private const val GAS_UNIT = "ppm"
private val HIGHLIGHTED_GASES = listOf("combustiveis", "h2", "c2h2")
private val HIGHLIGHTED_TESTS = listOf("rigidez", "agua", "neutralizacao")
