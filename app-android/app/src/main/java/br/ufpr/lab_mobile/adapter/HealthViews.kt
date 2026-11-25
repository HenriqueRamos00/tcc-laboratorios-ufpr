package br.ufpr.lab_mobile.adapter

import android.content.res.ColorStateList
import android.content.res.Resources
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ItemHealthHighlightBinding
import br.ufpr.lab_mobile.databinding.ItemHealthResultBinding
import br.ufpr.lab_mobile.model.HealthIndicator
import br.ufpr.lab_mobile.model.LabClassification
import br.ufpr.lab_mobile.model.MeasuredValue
import br.ufpr.lab_mobile.model.Variation
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Destaques: o primeiro ocupa a linha inteira e os demais dividem as seguintes em duas colunas. */
internal fun showHighlights(container: LinearLayout, highlights: List<HealthIndicator>) {
    container.removeAllViews()
    val inflater = LayoutInflater.from(container.context)
    val columns = if (container.resources.configuration.stacksFields) 1 else 2
    val gap = (8 * container.resources.displayMetrics.density).toInt()
    val rows = highlights.take(1).map(::listOf) + highlights.drop(1).chunked(columns)
    rows.forEach { indicators ->
        val row = LinearLayout(container.context).apply { isBaselineAligned = false }
        indicators.forEachIndexed { index, indicator ->
            val item = ItemHealthHighlightBinding.inflate(inflater, row, false)
            item.name.text = indicator.name.keepUnitsTogether()
            item.value.text = indicator.latest.displayText(container.resources, indicator.unit)
            item.variation.showVariation(indicator)
            // Cartões da mesma linha ficam com a mesma altura, mesmo quando um nome quebra.
            row.addView(item.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                topMargin = gap
                if (index > 0) marginStart = gap
            })
        }
        container.addView(row)
    }
}

/** Todos os resultados da análise mais recente, na ordem da API. */
internal fun showResults(container: LinearLayout, indicators: List<HealthIndicator>) {
    container.removeAllViews()
    val inflater = LayoutInflater.from(container.context)
    val resources = container.resources
    indicators.forEachIndexed { index, indicator ->
        val item = ItemHealthResultBinding.inflate(inflater, container, false)
        item.divider.isVisible = index > 0
        item.name.text = listOfNotNull(indicator.name, indicator.formula).joinToString(" ").keepUnitsTogether()
        val reference = listOfNotNull(
            indicator.limit?.let { resources.getString(R.string.health_limit, it) },
            indicator.method?.let { resources.getString(R.string.health_method, it) },
        ).joinToString(" · ")
        item.reference.text = reference.keepUnitsTogether()
        item.reference.isVisible = reference.isNotEmpty()
        item.value.text = indicator.latest.displayText(resources, indicator.unit)
        item.variation.showVariation(indicator)
        if (resources.configuration.stacksFields) stack(item)
        container.addView(item.root)
    }
}

/** Com fonte ampliada ou tela estreita, o valor desce para baixo do nome em vez de espremê-lo. */
private fun stack(item: ItemHealthResultBinding) {
    item.content.orientation = LinearLayout.VERTICAL
    listOf(item.description, item.measurement).forEach {
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    listOf(item.value, item.variation).forEach { it.gravity = Gravity.START }
}

internal val LabClassification.label: Int
    get() = when (this) {
        LabClassification.NORMAL -> R.string.health_classification_normal
        LabClassification.ALERT -> R.string.health_classification_alert
        LabClassification.NON_COMPLIANT -> R.string.health_classification_non_compliant
    }

/** A cor acompanha a classificação que o laboratório deu; o app não classifica nada. */
internal fun TextView.showClassification(classification: LabClassification?) {
    isVisible = classification != null
    if (classification == null) return
    setText(classification.label)
    val nonCompliant = classification == LabClassification.NON_COMPLIANT
    setTextColor(ContextCompat.getColor(context, if (nonCompliant) R.color.lactec_danger_ink else R.color.lactec_ink))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, when (classification) {
        LabClassification.NORMAL -> R.color.quote_finished
        LabClassification.ALERT -> R.color.health_alert
        LabClassification.NON_COMPLIANT -> R.color.lactec_danger_bg
    }))
}

private fun TextView.showVariation(indicator: HealthIndicator) {
    val description = indicator.variation?.displayText(resources, indicator.unit)
    isVisible = description != null
    text = description
}

internal fun MeasuredValue.displayText(resources: Resources, unit: String?): String = when (this) {
    is MeasuredValue.Quantity -> listOfNotNull(formatQuantity(value), unit).joinToString(" ")
    is MeasuredValue.Text -> text
    MeasuredValue.NotDetected -> resources.getString(R.string.health_not_detected)
    MeasuredValue.NotPerformed -> resources.getString(R.string.health_not_performed)
}

private fun Variation.displayText(resources: Resources, unit: String?): String {
    val since = formatDisplayDate(date) ?: resources.getString(R.string.health_previous_analysis)
    return when (this) {
        is Variation.Difference -> resources.getString(R.string.health_variation, formatDifference(amount), since)
        is Variation.Unchanged -> resources.getString(R.string.health_unchanged, since)
        is Variation.Replaced -> resources.getString(R.string.health_replaced, previous.displayText(resources, unit), since)
    }
}

/** Impede a quebra de linha depois da barra, que partiria "g/mL" e "NBR/IEC" em duas linhas. */
internal fun String.keepUnitsTogether(): String = replace("/", "/$WORD_JOINER")

/** Caractere invisível que proíbe a quebra de linha (U+2060). */
private const val WORD_JOINER = '\u2060'

/** Mostra todas as casas que o laboratório registrou: arredondar mudaria o resultado dele. */
internal fun formatQuantity(value: BigDecimal): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply { maximumFractionDigits = MAX_DECIMALS }.format(value)

internal fun formatDifference(difference: BigDecimal): String =
    (if (difference.signum() < 0) "−" else "+") + formatQuantity(difference.abs())

/** Casas decimais que a API aceita num resultado. */
private const val MAX_DECIMALS = 8
