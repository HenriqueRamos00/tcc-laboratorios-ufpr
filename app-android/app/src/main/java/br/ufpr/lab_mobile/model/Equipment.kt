package br.ufpr.lab_mobile.model

import br.ufpr.lab_mobile.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Equipment(
    val id: String,
    val serialNumber: String?,
    val tag: String?,
    val type: String?,
    val location: String?,
)

data class EquipmentDetail(
    val id: String?,
    val serialNumber: String?,
    val tag: String?,
    val type: String?,
    val location: String?,
    val company: String?,
    val identification: String?,
    val reportCount: Int?,
    val mostRecentReportDate: String?,
    val oilType: String?,
    val voltage: String?,
    val power: String?,
)

data class EquipmentReport(
    val id: String?,
    val date: String?,
    val analysisType: String?,
    val status: String?,
    val reportUrl: String?,
)

internal const val REPORTS_PER_PAGE = 4

internal fun EquipmentReport.analysisLabel() = when (analysisType) {
    "physical-chemical" -> R.string.equipment_analysis_physical_chemical
    "chromatography" -> R.string.equipment_analysis_chromatography
    else -> R.string.equipment_not_informed
}

internal fun EquipmentReport.statusLabel() = when (status) {
    "completed" -> R.string.equipment_report_completed
    "in-progress" -> R.string.equipment_report_in_progress
    else -> R.string.equipment_not_informed
}

private fun EquipmentReport.localDate(): LocalDate? =
    runCatching { LocalDate.parse(date?.take(10)) }.getOrNull()

internal fun EquipmentReport.formattedDate(): String? =
    localDate()?.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))

internal fun newestReportsFirst(reports: List<EquipmentReport>) =
    reports.sortedByDescending { it.localDate() ?: LocalDate.MIN }

internal fun reportPageCount(total: Int) = ((total + REPORTS_PER_PAGE - 1) / REPORTS_PER_PAGE).coerceAtLeast(1)

internal fun reportPage(reports: List<EquipmentReport>, page: Int): List<EquipmentReport> =
    reports.drop(page.coerceIn(0, reportPageCount(reports.size) - 1) * REPORTS_PER_PAGE).take(REPORTS_PER_PAGE)

internal fun filterEquipment(equipment: List<Equipment>, search: String): List<Equipment> {
    val term = search.trim()
    if (term.isEmpty()) return equipment
    return equipment.filter {
        it.serialNumber.orEmpty().contains(term, ignoreCase = true) ||
            it.tag.orEmpty().contains(term, ignoreCase = true)
    }
}
