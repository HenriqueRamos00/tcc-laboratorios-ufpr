package br.ufpr.lab_mobile.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuoteSimulationsTest {
    @Before
    @After
    fun resetSession() = QuoteSimulations.clear()

    @Test
    fun confirmedDecisionIsTrimmedAndCannotBeDuplicated() {
        QuoteSimulations.startSession("session")

        assertTrue(QuoteSimulations.confirm(" quote-1 ", "Pendente de Aceite", SimulatedDecision.REJECTED, "  Custo alto  "))
        assertEquals(QuoteSimulation(SimulatedDecision.REJECTED, "Custo alto"), QuoteSimulations.get("quote-1 "))
        assertFalse(QuoteSimulations.confirm("quote-1", "Pendente de Aceite", SimulatedDecision.ACCEPTED, null))
        assertNull(QuoteSimulations.get(""))
    }

    @Test
    fun emptyOptionalReasonAndInvalidQuoteIdsAreHandled() {
        QuoteSimulations.startSession("session")

        assertTrue(QuoteSimulations.confirm("q", "PendingAcceptance", SimulatedDecision.REJECTED, " \n "))
        assertNull(QuoteSimulations.get("q")?.reason)
        assertFalse(QuoteSimulations.confirm(" \t ", "PendingAcceptance", SimulatedDecision.ACCEPTED, null))
        assertFalse(QuoteSimulations.confirm("other", "InProgress", SimulatedDecision.ACCEPTED, null))
    }

    @Test
    fun sameSessionPreservesResultsNewSessionAndLogoutClearThem() {
        QuoteSimulations.startSession("token")
        assertTrue(QuoteSimulations.confirm("q", "Pendente de Aceite", SimulatedDecision.ACCEPTED, null))

        QuoteSimulations.startSession("token")
        assertEquals(SimulatedDecision.ACCEPTED, QuoteSimulations.get("q")?.decision)

        QuoteSimulations.startSession("new-token")
        assertNull(QuoteSimulations.get("q"))
        assertTrue(QuoteSimulations.confirm("q", "PendingAcceptance", SimulatedDecision.REJECTED, null))
        QuoteSimulations.clear() // logout and process-session reset
        assertNull(QuoteSimulations.get("q"))
    }

    @Test
    fun expiredSessionCannotRecordAnotherDecision() {
        QuoteSimulations.startSession("token")
        QuoteSimulations.clear()
        assertFalse(QuoteSimulations.isSessionActive("token"))
        assertFalse(QuoteSimulations.confirm("q", "PendingAcceptance", SimulatedDecision.ACCEPTED, null))
    }
}
