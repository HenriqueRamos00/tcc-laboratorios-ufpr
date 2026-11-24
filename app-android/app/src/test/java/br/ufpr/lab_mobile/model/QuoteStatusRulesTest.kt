package br.ufpr.lab_mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteStatusRulesTest {
    @Test
    fun resolvesEveryPortugueseStageLabelAndKey() {
        val expected = listOf(
            "Qualificação" to QuoteStage.QUALIFICATION,
            "Em análise pela área" to QuoteStage.AREA_ANALYSIS,
            "Elaborando proposta" to QuoteStage.DRAFTING,
            "Em negociação" to QuoteStage.NEGOTIATION,
            "Aprovado pelo cliente" to QuoteStage.APPROVED,
            "Aguardando entrega da amostra" to QuoteStage.AWAITING_SAMPLE,
            "Recebido" to QuoteStage.RECEIVED,
            "Protocolado" to QuoteStage.REGISTERED,
            "Em execução" to QuoteStage.RUNNING,
            "Completo" to QuoteStage.COMPLETE,
            "Validado" to QuoteStage.VALIDATED,
            "Finalizado" to QuoteStage.FINISHED,
            "Elaborando relatório" to QuoteStage.DRAFTING_REPORT,
            "Relatório publicado" to QuoteStage.PUBLISHED,
        )
        val keys = listOf(
            "qualificacao", "analise-da-area", "elaborando-proposta", "negociacao",
            "aprovado-pelo-cliente", "aguardando-amostra", "recebido", "protocolado",
            "em-execucao", "completo", "validado", "finalizado",
            "elaborando-relatorio", "relatorio-publicado",
        )

        assertEquals(QuoteStage.entries, expected.map { it.second })
        expected.forEachIndexed { index, (label, stage) ->
            assertEquals(ResolvedQuoteStatus.Progress(stage), QuoteStatusRules.resolve(label, null))
            assertEquals(keys[index], stage.key)
            assertEquals(ResolvedQuoteStatus.Progress(stage), QuoteStatusRules.resolve(keys[index], null))
        }
    }

    @Test
    fun resolvesAliasesAndNormalizesCaseAccentsAndUnicodeWhitespace() {
        assertEquals(QuoteStage.AREA_ANALYSIS, progress("  EM\u00a0REVISAO  "))
        assertEquals(QuoteStage.NEGOTIATION, progress("  NEGOCIAÇÃO\u2003"))
        assertEquals(QuoteStage.RECEIVED, progress("FECHADO GANHO"))
    }

    @Test
    fun unknownAndMissingValuesNeverInventProgress() {
        assertEquals(ResolvedQuoteStatus.Missing, QuoteStatusRules.resolve(null, "InProgress"))
        assertEquals(ResolvedQuoteStatus.Missing, QuoteStatusRules.resolve(" \u2003 ", "Finished"))
        assertEquals(ResolvedQuoteStatus.Unknown("Etapa futura"), QuoteStatusRules.resolve("Etapa futura", "InProgress"))
    }

    @Test
    fun terminalStageOrStatusOverridesProgress() {
        assertEquals(
            ResolvedQuoteStatus.Terminal(TerminalQuoteStatus.CANCELLED),
            QuoteStatusRules.resolve("Finalizado", "Cancelado"),
        )
        assertEquals(
            ResolvedQuoteStatus.Terminal(TerminalQuoteStatus.REFUSED),
            QuoteStatusRules.resolve("Etapa futura", "Fechado recusado Lactec"),
        )
        assertEquals(
            ResolvedQuoteStatus.Terminal(TerminalQuoteStatus.REFUSED),
            QuoteStatusRules.resolve("Recusado", "Incompleto"),
        )
        assertEquals(QuoteStage.COMPLETE, progress("Completo"))
    }

    @Test
    fun decisionRequiresPendingAcceptanceAndEffectiveNegotiationStage() {
        QuoteStage.entries.forEach { stage ->
            assertEquals(stage == QuoteStage.NEGOTIATION,
                QuoteStatusRules.canDecide(quote(stage.key, status = "Pendente de Aceite")))
            assertEquals(stage >= QuoteStage.NEGOTIATION,
                QuoteStatusRules.hasProposal(QuoteStatusRules.resolve(quote(stage.key))))
        }
        assertTrue(QuoteStatusRules.canDecide(quote("Em negociação", status = "  PENDINGACCEPTANCE\u00a0")))
        listOf(null, "", " ", "Pending", "InProgress", "Accepted", "Refused", "Unknown").forEach { status ->
            assertFalse(QuoteStatusRules.canDecide(quote("Em negociação", status = status)))
        }
        listOf("acceptance", "purchase-order", "sample-shipment", "rejection").forEach { decision ->
            assertFalse(QuoteStatusRules.canDecide(quote("Em negociação", decision = decision, status = "PendingAcceptance")))
        }
        assertFalse(QuoteStatusRules.canDecide(quote("Em negociação", laboratoryStage = "relatorio-publicado", status = "PendingAcceptance")))
    }

    @Test
    fun everyTerminalAliasWorksAsStageAndStatus() {
        val aliases = listOf(
            "cancelado" to TerminalQuoteStatus.CANCELLED,
            "cancelled" to TerminalQuoteStatus.CANCELLED,
            "canceled" to TerminalQuoteStatus.CANCELLED,
            "fechado perdido" to TerminalQuoteStatus.CANCELLED,
            "recusado" to TerminalQuoteStatus.REFUSED,
            "refused" to TerminalQuoteStatus.REFUSED,
            "rejected" to TerminalQuoteStatus.REFUSED,
            "fechado recusado lactec" to TerminalQuoteStatus.REFUSED,
        )
        aliases.forEach { (alias, status) ->
            val result = ResolvedQuoteStatus.Terminal(status)
            assertEquals(result, QuoteStatusRules.resolve(alias, null))
            assertEquals(result, QuoteStatusRules.resolve("Qualificação", alias))
        }
    }

    @Test
    fun phaseBoundariesBelongToTheLaterPhase() {
        assertEquals(QuotePhase.PROPOSAL, QuoteStatusRules.phase(QuoteStage.QUALIFICATION))
        assertEquals(QuotePhase.PROPOSAL, QuoteStatusRules.phase(QuoteStage.NEGOTIATION))
        assertEquals(QuotePhase.TECHNICAL, QuoteStatusRules.phase(QuoteStage.RECEIVED))
        assertEquals(QuotePhase.TECHNICAL, QuoteStatusRules.phase(QuoteStage.RUNNING))
        assertEquals(QuotePhase.LABORATORY, QuoteStatusRules.phase(QuoteStage.COMPLETE))
        assertEquals(QuotePhase.LABORATORY, QuoteStatusRules.phase(QuoteStage.PUBLISHED))
    }

    @Test
    fun everyWindowHasThreeConsecutiveStepsAndOneActiveStep() {
        QuoteStage.entries.forEach { stage ->
            val window = QuoteStatusRules.window(stage)
            assertEquals(3, window.size)
            assertTrue(window.zipWithNext().all { (previous, next) -> next.stage.ordinal == previous.stage.ordinal + 1 })
            assertEquals(stage, window.single { it.state == QuoteStepState.ACTIVE }.stage)
            window.forEach { step ->
                val expected = when {
                    step.stage < stage -> QuoteStepState.COMPLETED
                    step.stage == stage -> QuoteStepState.ACTIVE
                    else -> QuoteStepState.PENDING
                }
                assertEquals(expected, step.state)
            }
        }
    }

    @Test
    fun phaseStartMiddleAndEndWindowsAreExplicit() {
        assertWindow(QuoteStage.QUALIFICATION, QuoteStage.QUALIFICATION, QuoteStage.AREA_ANALYSIS, QuoteStage.DRAFTING)
        assertWindow(QuoteStage.NEGOTIATION, QuoteStage.DRAFTING, QuoteStage.NEGOTIATION, QuoteStage.APPROVED)
        assertWindow(QuoteStage.AWAITING_SAMPLE, QuoteStage.APPROVED, QuoteStage.AWAITING_SAMPLE, QuoteStage.RECEIVED)

        assertWindow(QuoteStage.RECEIVED, QuoteStage.AWAITING_SAMPLE, QuoteStage.RECEIVED, QuoteStage.REGISTERED)
        assertWindow(QuoteStage.RUNNING, QuoteStage.REGISTERED, QuoteStage.RUNNING, QuoteStage.COMPLETE)

        assertWindow(QuoteStage.COMPLETE, QuoteStage.RUNNING, QuoteStage.COMPLETE, QuoteStage.VALIDATED)
        assertWindow(QuoteStage.FINISHED, QuoteStage.VALIDATED, QuoteStage.FINISHED, QuoteStage.DRAFTING_REPORT)
        assertWindow(QuoteStage.PUBLISHED, QuoteStage.FINISHED, QuoteStage.DRAFTING_REPORT, QuoteStage.PUBLISHED)
    }

    @Test
    fun realStageCombinesCommercialLaboratoryAndClientDecision() {
        fun progress(stage: QuoteStage) = ResolvedQuoteStatus.Progress(stage)

        // Sem laboratório nem decisão, vale a etapa comercial, como antes.
        assertEquals(progress(QuoteStage.NEGOTIATION), QuoteStatusRules.resolve(quote("Em negociação")))
        // O laboratório segue depois do aceite e vence quando está à frente.
        assertEquals(progress(QuoteStage.PUBLISHED),
            QuoteStatusRules.resolve(quote("Aprovado pelo cliente", laboratoryStage = "relatorio-publicado")))
        assertEquals(progress(QuoteStage.RUNNING),
            QuoteStatusRules.resolve(quote("Fechado ganho", laboratoryStage = "em-execucao")))
        // Etapa do laboratório atrasada ou desconhecida não faz o orçamento voltar.
        assertEquals(progress(QuoteStage.RUNNING),
            QuoteStatusRules.resolve(quote("Em execução", laboratoryStage = "protocolado")))
        assertEquals(progress(QuoteStage.RECEIVED),
            QuoteStatusRules.resolve(quote("Recebido", laboratoryStage = "etapa-futura")))
        // A decisão do cliente adianta o comercial.
        assertEquals(progress(QuoteStage.APPROVED), QuoteStatusRules.resolve(quote("Em negociação", decision = "acceptance")))
        assertEquals(progress(QuoteStage.APPROVED), QuoteStatusRules.resolve(quote("Em negociação", decision = "purchase-order")))
        assertEquals(progress(QuoteStage.AWAITING_SAMPLE), QuoteStatusRules.resolve(quote("Em negociação", decision = "sample-shipment")))
        // Recusa do cliente e encerramento comercial vencem qualquer andamento.
        assertEquals(ResolvedQuoteStatus.Terminal(TerminalQuoteStatus.REFUSED),
            QuoteStatusRules.resolve(quote("Em negociação", decision = "rejection")))
        assertEquals(ResolvedQuoteStatus.Terminal(TerminalQuoteStatus.CANCELLED),
            QuoteStatusRules.resolve(quote("Cancelado", laboratoryStage = "relatorio-publicado")))
        // Etapa comercial ausente: o laboratório ainda informa o andamento.
        assertEquals(progress(QuoteStage.FINISHED), QuoteStatusRules.resolve(quote(null, laboratoryStage = "finalizado")))
        assertEquals(ResolvedQuoteStatus.Missing, QuoteStatusRules.resolve(quote(null)))
    }

    private fun quote(stage: String?, laboratoryStage: String? = null, decision: String? = null, status: String? = null) =
        Quote("q", "Q", null, status, stage, null, null, null, null, null, null,
            laboratoryStage = laboratoryStage,
            clientDecision = decision?.let { ClientDecision("d", it, null) })

    private fun assertWindow(stage: QuoteStage, vararg expected: QuoteStage) {
        assertEquals(expected.toList(), QuoteStatusRules.window(stage).map { it.stage })
    }

    private fun progress(value: String): QuoteStage =
        (QuoteStatusRules.resolve(value, null) as ResolvedQuoteStatus.Progress).stage
}
