package br.ufpr.lab_mobile.model

import android.content.res.Resources
import br.ufpr.lab_mobile.R
import java.text.Normalizer
import java.util.Locale

enum class QuoteStage(val key: String, val labelRes: Int, val iconRes: Int) {
    QUALIFICATION("qualificacao", R.string.quote_stage_qualification, R.drawable.ic_quote_fact_check),
    AREA_ANALYSIS("analise-da-area", R.string.quote_stage_area_analysis, R.drawable.ic_quote_hourglass_empty),
    DRAFTING("elaborando-proposta", R.string.quote_stage_drafting, R.drawable.ic_quote_description),
    NEGOTIATION("negociacao", R.string.quote_stage_negotiation, R.drawable.ic_quote_attach_money),
    APPROVED("aprovado-pelo-cliente", R.string.quote_stage_approved, R.drawable.ic_quote_how_to_reg),
    AWAITING_SAMPLE("aguardando-amostra", R.string.quote_stage_awaiting_sample, R.drawable.ic_quote_inventory_2),
    RECEIVED("recebido", R.string.quote_stage_received, R.drawable.ic_quote_inbox),
    REGISTERED("protocolado", R.string.quote_stage_registered, R.drawable.ic_quote_assignment_turned_in),
    RUNNING("em-execucao", R.string.quote_stage_running, R.drawable.ic_quote_science),
    INCOMPLETE("incompleto", R.string.quote_stage_incomplete, R.drawable.ic_quote_warning_amber),
    COMPLETE("completo", R.string.quote_stage_complete, R.drawable.ic_quote_done_all),
    VALIDATED("validado", R.string.quote_stage_validated, R.drawable.ic_quote_autorenew),
    FINISHED("finalizado", R.string.quote_stage_finished, R.drawable.ic_quote_task_alt),
    DRAFTING_REPORT("elaborando-relatorio", R.string.quote_stage_drafting_report, R.drawable.ic_quote_list_alt),
    PUBLISHED("relatorio-publicado", R.string.quote_stage_published, R.drawable.ic_quote_cloud_done),
}

enum class QuotePhase(val titleRes: Int, val first: Int, val last: Int) {
    PROPOSAL(R.string.quote_status_phase_proposal, 0, 6),
    TECHNICAL(R.string.quote_status_phase_technical, 6, 10),
    LABORATORY(R.string.quote_status_phase_laboratory, 10, 14),
}

enum class TerminalQuoteStatus(val labelRes: Int) {
    CANCELLED(R.string.quote_status_cancelled),
    REFUSED(R.string.quote_status_refused),
}

sealed interface ResolvedQuoteStatus {
    data class Progress(val stage: QuoteStage) : ResolvedQuoteStatus
    data class Terminal(val status: TerminalQuoteStatus) : ResolvedQuoteStatus
    data class Unknown(val raw: String) : ResolvedQuoteStatus
    data object Missing : ResolvedQuoteStatus
}

fun ResolvedQuoteStatus.displayText(resources: Resources): String = when (this) {
    is ResolvedQuoteStatus.Progress -> resources.getString(stage.labelRes)
    is ResolvedQuoteStatus.Terminal -> resources.getString(status.labelRes)
    is ResolvedQuoteStatus.Unknown -> resources.getString(R.string.quote_status_unknown_value, raw)
    ResolvedQuoteStatus.Missing -> resources.getString(R.string.quote_status_unavailable)
}

enum class QuoteStepState(val labelRes: Int) {
    COMPLETED(R.string.quote_status_completed),
    ACTIVE(R.string.quote_status_active),
    PENDING(R.string.quote_status_pending),
}

data class QuoteProgressStep(val stage: QuoteStage, val state: QuoteStepState)

object QuoteStatusRules {
    // Exact values only: unfamiliar data must never fabricate progress.
    private val stages = mapOf(
        "qualificação" to QuoteStage.QUALIFICATION,
        "em análise pela área" to QuoteStage.AREA_ANALYSIS,
        "em revisão" to QuoteStage.AREA_ANALYSIS,
        "elaborando proposta" to QuoteStage.DRAFTING,
        "em negociação" to QuoteStage.NEGOTIATION,
        "negociação" to QuoteStage.NEGOTIATION,
        "aprovado pelo cliente" to QuoteStage.APPROVED,
        "aguardando entrega da amostra" to QuoteStage.AWAITING_SAMPLE,
        "recebido" to QuoteStage.RECEIVED,
        "fechado ganho" to QuoteStage.RECEIVED,
        "protocolado" to QuoteStage.REGISTERED,
        "em execução" to QuoteStage.RUNNING,
        "incompleto" to QuoteStage.INCOMPLETE,
        "completo" to QuoteStage.COMPLETE,
        "validado" to QuoteStage.VALIDATED,
        "finalizado" to QuoteStage.FINISHED,
        "elaborando relatório" to QuoteStage.DRAFTING_REPORT,
        "relatório publicado" to QuoteStage.PUBLISHED,
    ).mapKeys { normalize(it.key) } + QuoteStage.entries.associateBy { normalize(it.key) }

    private val terminals = mapOf(
        "cancelado" to TerminalQuoteStatus.CANCELLED,
        "cancelled" to TerminalQuoteStatus.CANCELLED,
        "canceled" to TerminalQuoteStatus.CANCELLED,
        "fechado perdido" to TerminalQuoteStatus.CANCELLED,
        "recusado" to TerminalQuoteStatus.REFUSED,
        "refused" to TerminalQuoteStatus.REFUSED,
        "rejected" to TerminalQuoteStatus.REFUSED,
        "fechado recusado lactec" to TerminalQuoteStatus.REFUSED,
    ).mapKeys { normalize(it.key) }

    fun resolve(stage: String?, status: String?): ResolvedQuoteStatus {
        val normalizedStage = normalize(stage.orEmpty())
        val terminal = terminals[normalizedStage] ?: terminals[normalize(status.orEmpty())]
        if (terminal != null) return ResolvedQuoteStatus.Terminal(terminal)
        if (normalizedStage.isEmpty()) return ResolvedQuoteStatus.Missing
        return stages[normalizedStage]?.let(ResolvedQuoteStatus::Progress)
            ?: ResolvedQuoteStatus.Unknown(stage.orEmpty())
    }

    fun phase(stage: QuoteStage): QuotePhase =
        QuotePhase.entries.last { stage.ordinal in it.first..it.last }

    @Suppress("UNUSED_PARAMETER")
    fun canDecide(stage: String?, status: String?): Boolean =
        normalize(status.orEmpty()) in setOf("pendente de aceite", "pendingacceptance")

    fun window(stage: QuoteStage): List<QuoteProgressStep?> {
        val phase = phase(stage)
        // Empty neighbor slots retain the current step in the center at phase edges.
        return (stage.ordinal - 1..stage.ordinal + 1).map { index ->
            if (index !in phase.first..phase.last) return@map null
            QuoteProgressStep(
                QuoteStage.entries[index],
                when {
                    index < stage.ordinal -> QuoteStepState.COMPLETED
                    index == stage.ordinal -> QuoteStepState.ACTIVE
                    else -> QuoteStepState.PENDING
                },
            )
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\p{Z}]+"), " ")
            .trim()
}
