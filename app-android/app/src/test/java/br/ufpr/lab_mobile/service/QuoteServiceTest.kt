package br.ufpr.lab_mobile.service

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteServiceTest {
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
    fun `filtra por proposta descricao e status sem diferenciar acentos`() {
        val quote = parseQuotes(
            JsonParser.parseString(
                """[{"code":"EAQ-1","description":"Óleo isolante","status":"Em Andamento"}]""",
            ),
        ).single()

        assertTrue(matchesSearch(quote, "óleo"))
        assertTrue(matchesSearch(quote, "EAQ-1"))
        assertFalse(matchesSearch(quote, "concreto"))
        assertTrue(matchesStatus(quote, "InProgress"))
        assertFalse(matchesStatus(quote, "Finished"))
    }
}
