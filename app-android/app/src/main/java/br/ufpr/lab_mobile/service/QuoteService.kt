package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Quote
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.text.Normalizer
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface QuoteApiService {
    @GET("quotes")
    suspend fun getQuotes(
        @Header("Authorization") authorization: String,
        @Query("search") search: String?,
        @Query("status") status: String?,
    ): Response<JsonElement>
}

class QuoteService(private val api: QuoteApiService) {
    suspend fun getQuotes(token: String, search: String?, status: String?): List<Quote> {
        val response = api.getQuotes("Bearer $token", search, status)
        if (!response.isSuccessful) throw HttpException(response)
        return parseQuotes(response.body())
            .filter { matchesSearch(it, search) }
            .filter { matchesStatus(it, status) }
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
    val value = normalize("${quote.status.orEmpty()} ${quote.stage.orEmpty()}")
    val acceptedTerms = when (status) {
        "PendingAcceptance" -> listOf("pending acceptance", "pendente de aceite")
        "InProgress" -> listOf("in progress", "em andamento")
        "Finished" -> listOf("finished", "finalizado", "relatorio publicado")
        "Refused" -> listOf("refused", "recusado")
        "Cancelled" -> listOf("cancelled", "canceled", "cancelado")
        else -> listOf(status)
    }
    return acceptedTerms.any { normalize(it) in value }
}

private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
