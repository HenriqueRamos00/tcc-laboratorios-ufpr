package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.QuoteReports
import br.ufpr.lab_mobile.model.Report
import java.io.File
import okhttp3.ResponseBody
import retrofit2.HttpException
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
 * lista rolável com busca local, então este serviço percorre as páginas e junta os itens.
 */
class ReportService(private val api: ReportApiService) {
    /** Todos os laudos publicados que o cliente pode ver. */
    suspend fun getReports(): List<Report> = collectPages { page ->
        val response = api.getReports(page, PAGE_SIZE)
        val reports = successBody(response) ?: throw IllegalStateException("Empty reports response")
        PageResult(reports, totalCount(response))
    }

    /** Conclusão da análise de um orçamento, ou `null` quando ainda não há laudo publicado (204). */
    suspend fun getQuoteReports(quoteId: String): QuoteReports? {
        require(quoteId.isNotBlank()) { "Quote ID must not be blank" }
        var conclusion: QuoteReports? = null
        val reports = collectPages { page ->
            val response = api.getQuoteReports(quoteId.trim(), page, PAGE_SIZE)
            val body = successBody(response)
            if (body != null && conclusion == null) conclusion = body
            PageResult(body?.reports.orEmpty(), totalCount(response))
        }
        return conclusion?.copy(reports = reports)
    }

    /** Grava o PDF do laudo em [destination], com as mesmas validações do PDF do orçamento. */
    suspend fun downloadReport(id: String, destination: File) {
        require(id.isNotBlank()) { "Report ID must not be blank" }
        downloadPdf(destination) { api.getReportFile(id.trim()) }
    }

    private class PageResult(val items: List<Report>, val total: Int?)

    private suspend fun collectPages(loadPage: suspend (Int) -> PageResult): List<Report> {
        val all = mutableListOf<Report>()
        var page = 1
        while (true) {
            val result = loadPage(page)
            all += result.items.filter { !it.id.isNullOrBlank() }
            // Sem total no header, ou com uma página vazia, não há como saber se existe a próxima.
            val total = result.total ?: return all
            if (result.items.isEmpty() || page * PAGE_SIZE >= total) return all
            page++
        }
    }

    /** O corpo de uma resposta de sucesso; 204 (sem conteúdo) vira `null`, erro HTTP vira exceção. */
    private fun <T> successBody(response: Response<T>): T? {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        return if (response.code() == 204) null else response.body()
    }

    private fun totalCount(response: Response<*>) = response.headers()["X-Total-Count"]?.toIntOrNull()

    private companion object {
        /** Maior página aceita pela API (PageRequest.MaxPageSize). */
        const val PAGE_SIZE = 200
    }
}
