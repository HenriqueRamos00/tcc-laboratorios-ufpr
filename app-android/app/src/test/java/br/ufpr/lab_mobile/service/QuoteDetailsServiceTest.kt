package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Quote
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

class QuoteDetailsServiceTest {
    @Test
    fun `envia autorizacao e identificador e retorna o modelo existente`() = runBlocking {
        val quote = Gson().fromJson("""{"id":"q-17","code":"EAQ-17","stage":"Recebido"}""", Quote::class.java)
        val api = FakeQuoteApi(Response.success(quote))

        assertSame(quote, QuoteService(api).getQuote("session-token", "  q-17  "))
        assertEquals("Bearer session-token", api.authorization)
        assertEquals("q-17", api.id)
    }

    @Test
    fun `rejeita identificador vazio antes de chamar a API`() = runBlocking {
        listOf("", "  ", "\t\n").forEach { id ->
            val api = FakeQuoteApi(Response.success(null))
            try {
                QuoteService(api).getQuote("token", id)
                fail("Expected invalid ID")
            } catch (_: IllegalArgumentException) {
                assertFalse(api.called)
            }
        }
    }

    @Test
    fun `resposta sem corpo e falha e nao cria uma proposta`() = runBlocking {
        val api = FakeQuoteApi(Response.success(null))
        try {
            QuoteService(api).getQuote("token", "q-17")
            fail("Expected empty-body failure")
        } catch (_: IllegalStateException) {
            assertTrue(api.called)
        }
    }

    @Test
    fun `preserva 401 404 e falha do servidor para estados do dialogo`() = runBlocking {
        listOf(401, 404, 500, 502).forEach { code ->
            val api = FakeQuoteApi(Response.error(code, "{}".toResponseBody()))
            try {
                QuoteService(api).getQuote("token", "q-17")
                fail("Expected HTTP $code")
            } catch (error: HttpException) {
                assertEquals(code, error.code())
            }
        }
    }

    @Test
    fun `contrato Retrofit usa GET quotes por id e header Authorization`() {
        val method = QuoteApiService::class.java.methods.single { it.name == "getQuote" }
        assertEquals("quotes/{id}", requireNotNull(method.getAnnotation(GET::class.java)).value)
        assertEquals("Authorization", method.parameterAnnotations[0].filterIsInstance<Header>().single().value)
        assertEquals("id", method.parameterAnnotations[1].filterIsInstance<Path>().single().value)
    }

    private class FakeQuoteApi(private val response: Response<Quote>) : QuoteApiService {
        var authorization: String? = null
        var id: String? = null
        var called = false

        override suspend fun getDocument(authorization: String, id: String): Response<ResponseBody> =
            error("Unexpected document request")

        override suspend fun getQuote(authorization: String, id: String): Response<Quote> {
            this.authorization = authorization
            this.id = id
            called = true
            return response
        }

        override suspend fun getQuotes(
            authorization: String,
            search: String?,
            status: String?,
        ): Response<JsonElement> = error("Unexpected list request")
    }
}
