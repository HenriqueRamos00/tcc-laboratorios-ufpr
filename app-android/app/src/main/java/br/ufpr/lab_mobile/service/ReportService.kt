package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.QuoteReports
import br.ufpr.lab_mobile.model.Report
import java.io.File
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ReportApiService {
    @GET("api/reports")
    suspend fun getReports(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<List<Report>>

    /** 200 com a conclusão da análise, ou 204 sem corpo quando o orçamento ainda não tem laudo. */
    @GET("api/quotes/{quoteId}/reports")
    suspend fun getQuoteReports(
        @Path("quoteId") quoteId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<QuoteReports>

    @Streaming
    @GET("api/reports/{id}/file")
    suspend fun getReportFile(
        @Path("id") id: String,
    ): Response<ResponseBody>
}

/**
 * Consulta os laudos publicados do cliente no portal.
 *
 * A API pagina as listas e informa o total no header X-Total-Count. O app mostra tudo numa
 * lista rolável com busca local, então este serviço percorre todas as páginas.
 */
class ReportService(private val api: ReportApiService) {
    /** Os laudos publicados que o cliente pode ver, página a página. */
    fun reports(): PagedList<Report> = PagedList(PAGE_SIZE) { page ->
        val response = api.getReports(page, PAGE_SIZE)
        val reports = successBody(response) ?: throw IllegalStateException("Empty reports response")
        PageResult(reports.withId(), totalCount(response))
    }

    /** Conclusão da análise de um orçamento, ou `null` quando ainda não há laudo publicado (204). */
    suspend fun getQuoteReports(quoteId: String): QuoteReports? {
        require(quoteId.isNotBlank()) { "Quote ID must not be blank" }
        var conclusion: QuoteReports? = null
        val reports = collectPages(PAGE_SIZE) { page ->
            val response = api.getQuoteReports(quoteId.trim(), page, PAGE_SIZE)
            val body = successBody(response)
            if (body != null && conclusion == null) conclusion = body
            PageResult(body?.reports.orEmpty(), totalCount(response))
        }
        return conclusion?.copy(reports = reports.withId())
    }

    /** Grava o PDF do laudo em [destination], com as mesmas validações do PDF do orçamento. */
    suspend fun downloadReport(id: String, destination: File) {
        require(id.isNotBlank()) { "Report ID must not be blank" }
        downloadPdf(destination) { api.getReportFile(id.trim()) }
    }

    private fun List<Report>.withId() = filter { !it.id.isNullOrBlank() }

    private companion object {
        /** Na fonte AutoLAB cada laudo da página custa uma consulta; páginas grandes estouram o tempo de leitura. */
        const val PAGE_SIZE = 20
    }
}
