package br.ufpr.lab_mobile.adapter

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ItemEquipmentFieldBinding
import br.ufpr.lab_mobile.databinding.ItemEquipmentReportBinding
import br.ufpr.lab_mobile.model.EquipmentDetail
import br.ufpr.lab_mobile.model.EquipmentReport

/**
 * Campos da identificação de um equipamento, na ordem dos cartões do portal web.
 *
 * Quando o cadastro não conhece o equipamento, a tela explica isso uma vez e os dados de placa
 * vazios são omitidos, em vez de repetir "Não informado" em todos eles.
 */
internal fun identificationFields(equipment: EquipmentDetail): List<Pair<Int, String?>> {
    val identity = listOf(
        R.string.equipment_serial_number to equipment.serialNumber,
        R.string.equipment_tag to equipment.displayTag,
    )
    val nameplate = listOf(
        R.string.equipment_type to equipment.type,
        R.string.equipment_oil_type to equipment.oilType,
        R.string.equipment_company to equipment.company,
        R.string.equipment_voltage to equipment.voltage,
        R.string.equipment_power to equipment.power,
        R.string.equipment_manufacturer to equipment.manufacturer,
        R.string.equipment_manufacturing_year to equipment.manufacturingYear,
        R.string.equipment_oil_volume to equipment.oilVolume,
    ).filter { (_, value) -> equipment.nameplateKnown != false || !value.isNullOrBlank() }
    // Só uma das fontes informa a localização; na outra o campo estaria sempre vazio.
    val location = listOf(R.string.equipment_location to equipment.location).filter { (_, value) -> !value.isNullOrBlank() }
    return identity + nameplate + location
}

/** Duas colunas cabem num celular comum; tela estreita ou fonte ampliada empilha, sem quebrar valores. */
internal val Configuration.stacksFields: Boolean get() = screenWidthDp < 360 || fontScale > 1.3f

/** Distribui os campos em linhas de colunas de mesma largura. */
internal fun showFields(container: LinearLayout, fields: List<Pair<Int, String?>>) {
    container.removeAllViews()
    val columns = if (container.resources.configuration.stacksFields) 1 else 2
    val inflater = LayoutInflater.from(container.context)
    fields.chunked(columns).forEach { rowFields ->
        val row = LinearLayout(container.context).apply { isBaselineAligned = false }
        rowFields.forEach { (label, value) ->
            val field = ItemEquipmentFieldBinding.inflate(inflater, row, false)
            field.label.setText(label)
            field.value.text = value?.trim()?.takeIf(String::isNotEmpty)
                ?: container.resources.getString(R.string.equipment_not_informed)
            row.addView(field.root)
        }
        // A última linha pode ficar incompleta; o espaço mantém a largura das colunas.
        repeat(columns - rowFields.size) {
            row.addView(Space(container.context), LinearLayout.LayoutParams(0, 0, 1f))
        }
        container.addView(row)
    }
}

/**
 * Mostra o histórico de relatórios dentro de um [LinearLayout], pelo mesmo motivo da
 * [ReportListView]: a identificação do equipamento rola junto com a lista.
 */
internal class EquipmentReportListView(
    private val container: LinearLayout,
    private val onDownload: (EquipmentReport) -> Unit,
) {
    private var items: List<Pair<EquipmentReport, ItemEquipmentReportBinding>> = emptyList()
    private var downloadingId: String? = null

    fun show(reports: List<EquipmentReport>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        items = reports.map { report ->
            val item = ItemEquipmentReportBinding.inflate(inflater, container, false)
            bindEquipmentReport(item, report)
            item.downloadReport.setOnClickListener { onDownload(report) }
            container.addView(item.root)
            report to item
        }
        bindDownloads()
    }

    /** Marca o relatório que está sendo baixado; os botões ficam bloqueados até o fim do download. */
    fun setDownloading(reportId: String?) {
        downloadingId = reportId
        bindDownloads()
    }

    /** Atualiza só os botões: recriar as linhas tiraria o foco de quem usa leitor de tela. */
    private fun bindDownloads() = items.forEach { (report, item) -> bindDownload(item, report, downloadingId) }
}

private fun bindEquipmentReport(item: ItemEquipmentReportBinding, report: EquipmentReport) {
    val resources = item.root.resources
    val date = report.displayDate(resources.getString(R.string.equipment_not_informed))
    item.reportDate.text = date
    item.reportDate.contentDescription = resources.getString(R.string.equipment_report_date_accessibility, date)
    if (resources.configuration.stacksFields) {
        // O status desce para baixo da data, que partiria ao meio dividindo a linha com ele.
        item.reportHeader.orientation = LinearLayout.VERTICAL
        item.reportHeader.gravity = Gravity.START
        item.reportDate.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        (item.status.layoutParams as LinearLayout.LayoutParams).apply {
            marginStart = 0
            topMargin = (4 * resources.displayMetrics.density).toInt()
        }
    }
    item.analysisType.setText(report.analysisLabel)
    item.status.setText(report.statusLabel)
    item.status.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(item.root.context, when (report.status) {
        "completed" -> R.color.quote_finished
        "in-progress" -> R.color.quote_progress
        else -> R.color.lactec_line
    }))
}

private fun bindDownload(item: ItemEquipmentReportBinding, report: EquipmentReport, downloadingId: String?) {
    val resources = item.root.resources
    val downloading = report.id != null && report.id == downloadingId
    item.downloadReport.isEnabled = report.canDownload && downloadingId == null
    // A cor do botão é fixa no layout; sem isto, desativado e ativo seriam iguais.
    item.downloadReport.alpha = if (item.downloadReport.isEnabled) 1f else DISABLED_ALPHA
    item.downloadReport.setText(when {
        downloading -> R.string.report_downloading
        report.canDownload -> R.string.report_download
        else -> R.string.report_pdf_unavailable
    })
    // Sem descrição própria, o leitor de tela anuncia o texto "Baixando…".
    item.downloadReport.contentDescription = if (downloading) null else resources.getString(
        if (report.canDownload) R.string.equipment_report_download_accessibility
        else R.string.equipment_report_pdf_unavailable_accessibility,
        resources.getString(report.analysisLabel),
        report.displayDate(resources.getString(R.string.equipment_not_informed)),
    )
}

private const val DISABLED_ALPHA = 0.5f

private fun EquipmentReport.displayDate(missing: String) = formatDisplayDate(knownDate) ?: missing

/** Rótulos do portal web; um tipo que o app não conhece entra em "Outros ensaios". */
internal val EquipmentReport.analysisLabel: Int
    get() = when (analysisType) {
        "physical-chemical" -> R.string.equipment_analysis_physical_chemical
        "chromatography" -> R.string.equipment_analysis_chromatography
        else -> R.string.equipment_analysis_other
    }

internal val EquipmentReport.statusLabel: Int
    get() = when (status) {
        "completed" -> R.string.equipment_report_completed
        "in-progress" -> R.string.equipment_report_in_progress
        else -> R.string.equipment_not_informed
    }
