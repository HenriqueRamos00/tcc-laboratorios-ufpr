package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStage
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.ResolvedQuoteStatus
import br.ufpr.lab_mobile.model.TerminalQuoteStatus
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody
import java.io.File

interface QuoteApiService {
    @Streaming
    @GET("api/quotes/{id}/document")
    suspend fun getDocument(
        @Path("id") id: String,
    ): Response<ResponseBody>

    @GET("api/quotes/{id}")
    suspend fun getQuote(
        @Path("id") id: String,
    ): Response<Quote>

    @GET("api/quotes")
    suspend fun getQuotes(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<JsonElement>
}

class QuoteService(private val api: QuoteApiService) {
    suspend fun downloadDocument(id: String, destination: File) {
        require(id.isNotBlank()) { "Quote ID must not be blank" }
        downloadPdf(destination) { api.getDocument(id.trim()) }
    }

    suspend fun getQuote(id: String): Quote {
        require(id.isNotBlank()) { "Quote ID must not be blank" }
        val response = api.getQuote(id.trim())
        if (!response.isSuccessful) throw HttpException(response)
        return response.body() ?: throw IllegalStateException("Empty quote response")
    }

    suspend fun getQuotes(search: String?, status: String?): List<Quote> {
        val quotes = mutableListOf<Quote>()
        var page = 1
        while (true) {
            val response = api.getQuotes(page, PAGE_SIZE)
            if (!response.isSuccessful) throw HttpException(response)
            val items = parseQuotes(response.body())
            quotes += items
            val total = response.headers()["X-Total-Count"]?.toIntOrNull()
            if (total == null || items.isEmpty() || page * PAGE_SIZE >= total) break
            page++
        }
        return quotes
            .filter { matchesSearch(it, search) }
            .filter { matchesStatus(it, status) }
    }

    private companion object {
        const val PAGE_SIZE = 200
    }
}

private val quoteListType = object : TypeToken<List<Quote>>() {}.type

internal fun parseQuotes(payload: JsonElement?): List<Quote> {
    if (payload == null || payload.isJsonNull) return emptyList()
    val items = when {
        payload.isJsonArray -> payload.asJsonArray
        payload.isJsonObject -> payload.asJsonObject.getAsJsonArray("items")
        else -> null
    } ?: return emptyList()
    return Gson().fromJson(items, quoteListType)
}

internal fun matchesSearch(quote: Quote, search: String?): Boolean {
    val term = search?.trim().orEmpty()
    return term.isEmpty() ||
        quote.code.orEmpty().contains(term, ignoreCase = true) ||
        quote.description.orEmpty().contains(term, ignoreCase = true)
}

internal fun matchesStatus(quote: Quote, status: String?): Boolean {
    if (status.isNullOrBlank()) return true
    return when (val resolved = QuoteStatusRules.resolve(quote)) {
        is ResolvedQuoteStatus.Progress -> status == when (resolved.stage) {
            in QuoteStage.QUALIFICATION..QuoteStage.NEGOTIATION -> "PendingAcceptance"
            QuoteStage.PUBLISHED -> "Finished"
            else -> "InProgress"
        }
        is ResolvedQuoteStatus.Terminal -> status == when (resolved.status) {
            TerminalQuoteStatus.CANCELLED -> "Cancelled"
            TerminalQuoteStatus.REFUSED -> "Refused"
        }
        else -> false
    }
}
