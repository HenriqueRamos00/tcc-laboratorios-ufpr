package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Quote
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.ResponseBody
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteServiceTest {
    @Test
    fun `filtros seguem a etapa efetiva do orcamento`() {
        val quotes = parseQuotes(JsonParser.parseString("""[
            {"status":"PendingAcceptance","stage":"Finalizado"},
            {"status":"PendingAcceptance","stage":"Em análise pela área"},
            {"status":"InProgress","stage":"Em negociação","laboratoryStage":"relatorio-publicado"},
            {"status":"PendingAcceptance","stage":"Em negociação","clientDecision":{"kind":"rejection"}}
        ]"""))
        assertTrue(matchesStatus(quotes[0], "InProgress"))
        assertFalse(matchesStatus(quotes[0], "Finished"))
        assertTrue(matchesStatus(quotes[1], "PendingAcceptance"))
        assertTrue(matchesStatus(quotes[2], "Finished"))
        assertFalse(matchesStatus(quotes[2], "InProgress"))
        assertTrue(matchesStatus(quotes[3], "Refused"))
        assertFalse(matchesStatus(quotes[3], "PendingAcceptance"))
    }

    @Test
    fun `busca as paginas seguintes mesmo quando a primeira nao corresponde ao filtro`() = runBlocking {
        val requestedPages = mutableListOf<Int>()
        val api = object : QuoteApiService {
            override suspend fun getDocument(id: String): Response<ResponseBody> = error("Unexpected document request")
            override suspend fun getQuote(id: String): Response<Quote> = error("Unexpected quote request")
            override suspend fun getQuotes(page: Int, pageSize: Int): Response<JsonElement> {
                requestedPages += page
                val body = if (page == 1) {
                    List(pageSize) { """{"stage":"Em execução"}""" }.joinToString(",", "[", "]")
                } else {
                    """[{"code":"EAQ-201","stage":"Relatório publicado"}]"""
                }
                return Response.success(JsonParser.parseString(body), Headers.headersOf("X-Total-Count", (pageSize + 1).toString()))
            }
        }
        assertEquals("EAQ-201", QuoteService(api).getQuotes(null, "Finished").single().code)
        assertEquals(listOf(1, 2), requestedPages)
    }
    @Test
    fun `aceita lista atual e resposta paginada do contrato`() {
        val item = """{"code":"EAQ-1","description":"Óleo isolante"}"""

        assertEquals("EAQ-1", parseQuotes(JsonParser.parseString("[$item]")).single().code)
        assertEquals(
            "EAQ-1",
            parseQuotes(JsonParser.parseString("{\"items\":[$item]}")).single().code,
        )
    }

    @Test
    fun `filtra por codigo ou descricao e pela etapa`() {
        val quote = parseQuotes(
            JsonParser.parseString(
                """[{"code":"EAQ-1","description":"Óleo isolante","stage":"Em execução"}]""",
            ),
        ).single()

        assertTrue(matchesSearch(quote, "óleo"))
        assertTrue(matchesSearch(quote, "EAQ-1"))
        assertFalse(matchesSearch(quote, "concreto"))
        assertTrue(matchesStatus(quote, "InProgress"))
        assertFalse(matchesStatus(quote, "Finished"))
    }
}
