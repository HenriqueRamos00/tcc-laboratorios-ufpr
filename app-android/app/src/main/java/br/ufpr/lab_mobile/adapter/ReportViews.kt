package br.ufpr.lab_mobile.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ItemReportBinding
import br.ufpr.lab_mobile.databinding.ItemReportedQuoteBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.Report
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Mostra os laudos de um orçamento dentro de um [LinearLayout].
 *
 * A tela do orçamento tem um cabeçalho grande (etapas, resumo, busca) que rola junto com a
 * lista, que é curta; por isso a lista fica num LinearLayout dentro do NestedScrollView da tela.
 * A lista de todos os laudos do cliente usa o [ReportAdapter].
 */
internal class ReportListView(
    private val container: LinearLayout,
    private val onDownload: (Report) -> Unit,
) {
    private var reports: List<Report> = emptyList()
    private var downloadingId: String? = null

    fun show(reports: List<Report>) {
        this.reports = reports
        render()
    }

    /** Marca o laudo que está sendo baixado; os botões ficam bloqueados até o fim do download. */
    fun setDownloading(reportId: String?) {
        downloadingId = reportId
        render()
    }

    private fun render() {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        reports.forEach { report ->
            val item = ItemReportBinding.inflate(inflater, container, false)
            bindReport(item, report, downloadingId)
            item.downloadReport.setOnClickListener { onDownload(report) }
            container.addView(item.root)
        }
    }
}

/** Os laudos do cliente, que podem passar de mil: só os visíveis viram views. */
internal class ReportAdapter(private val onDownload: (Report) -> Unit) :
    GrowingListAdapter<Report, ReportAdapter.ReportViewHolder>() {
    private var downloadingId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ReportViewHolder(
        ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) = holder.bind(items[position])

    /** Marca o laudo que está sendo baixado; os botões ficam bloqueados até o fim do download. */
    fun setDownloading(reportId: String?) {
        downloadingId = reportId
        // Com payload os itens visíveis são reaproveitados, sem piscar.
        notifyItemRangeChanged(0, itemCount, Unit)
    }

    inner class ReportViewHolder(private val item: ItemReportBinding) : RecyclerView.ViewHolder(item.root) {
        fun bind(report: Report) {
            bindReport(item, report, downloadingId)
            item.downloadReport.setOnClickListener { onDownload(report) }
        }
    }
}

private fun bindReport(item: ItemReportBinding, report: Report, downloadingId: String?) {
    val resources = item.root.resources
    val title = report.identification.valueOrNull() ?: resources.getString(R.string.report_not_informed)
    item.reportIdentification.text = title
    item.requestNumber.showField(R.string.report_request, report.requestNumber)
    item.sample.showField(R.string.report_sample, report.sample)
    // Como na tabela web, o laboratório só aparece quando a fonte informa.
    item.laboratory.isVisible = !report.laboratory.isNullOrBlank()
    item.laboratory.showField(R.string.report_laboratory, report.laboratory)
    item.situation.showField(R.string.report_situation, report.status)
    item.publishedOn.showField(R.string.report_published, formatDisplayDate(report.publishedOn))

    val downloading = report.id != null && report.id == downloadingId
    item.downloadReport.isEnabled = report.canDownload && downloadingId == null
    item.downloadReport.setText(when {
        downloading -> R.string.report_downloading
        report.canDownload -> R.string.report_download
        else -> R.string.report_pdf_unavailable
    })
    item.downloadReport.contentDescription = resources.getString(
        if (report.canDownload) R.string.report_download_accessibility else R.string.report_pdf_unavailable_accessibility,
        title,
    )
}

/** Cartão de um orçamento com relatório publicado, com o botão que abre os laudos dele. */
internal fun bindReportedQuote(item: ItemReportedQuoteBinding, quote: Quote, onOpen: (Quote) -> Unit) {
    val code = quote.code.valueOrNull() ?: "—"
    item.proposalCode.text = code
    item.company.text = quote.companyName.valueOrNull() ?: "—"
    item.proposalName.text = quote.name.valueOrNull() ?: "—"
    item.contact.text = quote.externalContactName.valueOrNull() ?: quote.externalContactEmail.valueOrNull() ?: "—"
    item.createdDate.text = formatQuoteDate(quote.createdDate)
    item.viewReports.contentDescription = item.root.resources.getString(R.string.reported_quote_view_accessibility, code)
    item.viewReports.setOnClickListener { onOpen(quote) }
}

private val displayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Datas da API chegam como "2026-03-07", "2026-03-07T10:00:00" ou com fuso; a tela mostra dd/MM/aaaa. */
internal fun formatDisplayDate(value: String?): String? {
    val text = value.valueOrNull() ?: return null
    val parsers = listOf<(String) -> LocalDate>(
        { OffsetDateTime.parse(it).toLocalDate() },
        { LocalDateTime.parse(it).toLocalDate() },
        { LocalDate.parse(it) },
    )
    for (parse in parsers) {
        try { return parse(text).format(displayDate) } catch (_: DateTimeParseException) { }
    }
    return text
}

private fun TextView.showField(labelRes: Int, value: String?) {
    text = resources.getString(labelRes, value.valueOrNull() ?: resources.getString(R.string.report_not_informed))
}

private fun String?.valueOrNull() = this?.trim()?.takeIf(String::isNotEmpty)
