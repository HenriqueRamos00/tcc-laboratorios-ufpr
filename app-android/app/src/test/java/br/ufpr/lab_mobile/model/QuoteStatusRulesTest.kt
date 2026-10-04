package br.ufpr.lab_mobile.model

import org.junit.Assert.assertEquals
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
            "Incompleto" to QuoteStage.INCOMPLETE,
            "Completo" to QuoteStage.COMPLETE,
            "Validado" to QuoteStage.VALIDATED,
            "Finalizado" to QuoteStage.FINISHED,
            "Elaborando relatório" to QuoteStage.DRAFTING_REPORT,
            "Relatório publicado" to QuoteStage.PUBLISHED,
        )
        val keys = listOf(
            "qualificacao", "analise-da-area", "elaborando-proposta", "negociacao",
            "aprovado-pelo-cliente", "aguardando-amostra", "recebido", "protocolado",
            "em-execucao", "incompleto", "completo", "validado", "finalizado",
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
    fun terminalStageOrStatusOverridesProgressAndIncompleteIsNotTerminal() {
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
        assertEquals(QuoteStage.INCOMPLETE, progress("Incompleto"))
        assertEquals(QuoteStage.COMPLETE, progress("Completo"))
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
        assertEquals(QuotePhase.TECHNICAL, QuoteStatusRules.phase(QuoteStage.INCOMPLETE))
        assertEquals(QuotePhase.LABORATORY, QuoteStatusRules.phase(QuoteStage.COMPLETE))
        assertEquals(QuotePhase.LABORATORY, QuoteStatusRules.phase(QuoteStage.PUBLISHED))
    }

    @Test
    fun everyWindowHasThreeOrderedStepsWithinItsSelectedPhaseAndOneActiveStep() {
        QuoteStage.entries.forEach { stage ->
            val window = QuoteStatusRules.window(stage)
            val phase = QuoteStatusRules.phase(stage)
            assertEquals(3, window.size)
            val visibleSteps = window.filterNotNull()
            assertTrue(visibleSteps.size in 2..3)
            assertEquals(visibleSteps.map { it.stage.ordinal }.sorted(), visibleSteps.map { it.stage.ordinal })
            assertTrue(visibleSteps.all { it.stage.ordinal in phase.first..phase.last })
            assertEquals(stage, window[1]?.stage)
            assertEquals(QuoteStepState.ACTIVE, window[1]?.state)
            assertEquals(
                if (stage.ordinal > phase.first) QuoteStage.entries[stage.ordinal - 1] else null,
                window[0]?.stage,
            )
            assertEquals(
                if (stage.ordinal < phase.last) QuoteStage.entries[stage.ordinal + 1] else null,
                window[2]?.stage,
            )
            visibleSteps.forEach { step ->
                val expected = when {
                    step.stage.ordinal < stage.ordinal -> QuoteStepState.COMPLETED
                    step.stage == stage -> QuoteStepState.ACTIVE
                    else -> QuoteStepState.PENDING
                }
                assertEquals(expected, step.state)
            }
        }
    }

    @Test
    fun phaseStartMiddleAndEndWindowsAreExplicit() {
        assertWindow(QuoteStage.QUALIFICATION, null, QuoteStage.QUALIFICATION, QuoteStage.AREA_ANALYSIS)
        assertWindow(QuoteStage.NEGOTIATION, QuoteStage.DRAFTING, QuoteStage.NEGOTIATION, QuoteStage.APPROVED)
        assertWindow(QuoteStage.AWAITING_SAMPLE, QuoteStage.APPROVED, QuoteStage.AWAITING_SAMPLE, QuoteStage.RECEIVED)

        assertWindow(QuoteStage.RECEIVED, null, QuoteStage.RECEIVED, QuoteStage.REGISTERED)
        assertWindow(QuoteStage.RUNNING, QuoteStage.REGISTERED, QuoteStage.RUNNING, QuoteStage.INCOMPLETE)
        assertWindow(QuoteStage.INCOMPLETE, QuoteStage.RUNNING, QuoteStage.INCOMPLETE, QuoteStage.COMPLETE)

        assertWindow(QuoteStage.COMPLETE, null, QuoteStage.COMPLETE, QuoteStage.VALIDATED)
        assertWindow(QuoteStage.FINISHED, QuoteStage.VALIDATED, QuoteStage.FINISHED, QuoteStage.DRAFTING_REPORT)
        assertWindow(QuoteStage.PUBLISHED, QuoteStage.DRAFTING_REPORT, QuoteStage.PUBLISHED, null)
    }

    private fun assertWindow(stage: QuoteStage, vararg expected: QuoteStage?) {
        assertEquals(expected.toList(), QuoteStatusRules.window(stage).map { it?.stage })
    }

    private fun progress(value: String): QuoteStage =
        (QuoteStatusRules.resolve(value, null) as ResolvedQuoteStatus.Progress).stage
}
