package br.ufpr.lab_mobile.model

import java.util.Locale

/** Um laudo publicado, como devolvido por GET /api/reports e GET /api/quotes/{id}/reports. */
data class Report(
    val id: String?,
    val identification: String?,
    val requestNumber: String?,
    val quoteCode: String?,
    val sampleId: String?,
    val sampleName: String?,
    val status: String?,
    val issueDate: String?,
    val publicationDate: String?,
    val hasSignedDocument: Boolean = false,
    val pdfAvailable: Boolean? = null,
    val laboratory: String? = null,
) {
    /** A fonte nova informa [pdfAvailable]; a antiga só sabe se há documento assinado. */
    val canDownload: Boolean get() = !id.isNullOrBlank() && (pdfAvailable ?: hasSignedDocument)

    /** Amostra no formato "ID · Nome", omitindo a parte que não veio. */
    val sample: String?
        get() = listOfNotNull(sampleId, sampleName)
            .map(String::trim).filter(String::isNotEmpty)
            .joinToString(" · ").ifEmpty { null }

    /** Publicação, ou emissão quando o laudo ainda não tem data de publicação. */
    val publishedOn: String? get() = publicationDate ?: issueDate

    /** Nome sugerido para o arquivo salvo, sem caracteres que o sistema de arquivos recusa. */
    val fileName: String
        get() = "relatorio-${(identification ?: id ?: "laudo").replace(Regex("[^\\w.-]+"), "-")}.pdf"
}

/** Conclusão da análise de um orçamento: tipo de ensaio, resumo e os laudos publicados. */
data class QuoteReports(
    val quoteId: String?,
    val quoteCode: String?,
    val protocol: String?,
    val testType: String?,
    val summary: String?,
    val reports: List<Report>,
)

/** Busca local por relatório, solicitação ou orçamento, como a busca da tela web. */
internal fun filterReports(reports: List<Report>, search: String): List<Report> {
    val term = search.trim().lowercase(Locale.ROOT)
    if (term.isEmpty()) return reports
    return reports.filter { report ->
        listOf(report.identification, report.requestNumber, report.quoteCode)
            .any { it.orEmpty().lowercase(Locale.ROOT).contains(term) }
    }
}
