package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStatusRules
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.text.Normalizer
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface QuoteApiService {
    @Streaming
    @GET("quotes/{id}/document")
    suspend fun getDocument(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): Response<ResponseBody>

    @GET("quotes/{id}")
    suspend fun getQuote(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): Response<Quote>

    @GET("quotes")
    suspend fun getQuotes(
        @Header("Authorization") authorization: String,
        @Query("search") search: String?,
        @Query("status") status: String?,
    ): Response<JsonElement>
}

class QuoteService(private val api: QuoteApiService) {
    suspend fun downloadDocument(token: String, id: String, destination: File) {
        require(id.isNotBlank()) { "Quote ID must not be blank" }
        var complete = false
        try {
            val response = api.getDocument("Bearer $token", id.trim())
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw HttpException(response)
            }
            val body = response.body() ?: throw InvalidQuoteDocumentException()
            body.use {
                val mediaType = body.contentType()
                if (mediaType?.type != "application" || mediaType.subtype != "pdf") throw InvalidQuoteDocumentException()
                // Closing the body unblocks a pending socket read when the screen is abandoned.
                coroutineScope {
                    val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { body.close() }
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            body.byteStream().use { input ->
                                destination.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var total = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > 32L * 1024 * 1024) throw InvalidQuoteDocumentException()
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            destination.inputStream().use { input ->
                                val header = ByteArray(5)
                                if (input.read(header) != 5 || !header.contentEquals("%PDF-".toByteArray())) {
                                    throw InvalidQuoteDocumentException()
                                }
                            }
                        }
                    } finally { closer.cancel() }
                }
            }
            currentCoroutineContext().ensureActive()
            complete = true
        } finally {
            if (!complete) destination.delete()
        }
    }

    suspend fun getQuote(token: String, id: String): Quote {
        require(id.isNotBlank()) { "Quote ID must not be blank" }
        val response = api.getQuote("Bearer $token", id.trim())
        if (!response.isSuccessful) throw HttpException(response)
        return response.body() ?: throw IllegalStateException("Empty quote response")
    }

    suspend fun getQuotes(token: String, search: String?, status: String?): List<Quote> {
        val response = api.getQuotes("Bearer $token", search, status)
        if (!response.isSuccessful) throw HttpException(response)
        return parseQuotes(response.body())
            .filter { matchesSearch(it, search) }
            .filter { matchesStatus(it, status) }
    }
}

class InvalidQuoteDocumentException : IOException("Invalid quote PDF")

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
    if (status == "PendingAcceptance") return QuoteStatusRules.canDecide(quote.stage, quote.status)
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
