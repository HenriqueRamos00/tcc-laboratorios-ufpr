package br.ufpr.lab_mobile.controller

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ItemQuoteStatusStepBinding
import br.ufpr.lab_mobile.model.QuoteStage
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.QuoteStepState

/**
 * Desenha a janela de três etapas da linha completa em volta de [stage].
 * Usado pelo diálogo de status e pela tela de relatórios do orçamento.
 */
internal fun bindQuoteProgress(context: Context, views: List<ItemQuoteStatusStepBinding>, stage: QuoteStage) {
    val steps = QuoteStatusRules.window(stage)
    views.zip(steps).forEachIndexed { index, (view, step) ->
        val label = context.getString(step.stage.labelRes)
        val state = context.getString(step.state.labelRes)
        view.label.text = label
        view.label.contentDescription = context.getString(R.string.quote_status_step_accessibility, label, state)
        view.label.setTypeface(null, if (step.state == QuoteStepState.ACTIVE) Typeface.BOLD else Typeface.NORMAL)
        view.label.setTextColor(ContextCompat.getColor(context, when (step.state) {
            QuoteStepState.COMPLETED -> R.color.lactec_ink
            QuoteStepState.ACTIVE -> R.color.lactec_primary_dark
            QuoteStepState.PENDING -> R.color.lactec_muted
        }))
        view.icon.setImageResource(if (step.state == QuoteStepState.COMPLETED) R.drawable.ic_quote_check else step.stage.iconRes)
        view.icon.setBackgroundResource(when (step.state) {
            QuoteStepState.COMPLETED -> R.drawable.bg_quote_step_completed
            QuoteStepState.ACTIVE -> R.drawable.bg_quote_step_active
            QuoteStepState.PENDING -> R.drawable.bg_quote_step_pending
        })
        view.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context,
            if (step.state == QuoteStepState.PENDING) R.color.lactec_muted else R.color.white,
        ))
        view.connectorBefore.visibility = if (steps.getOrNull(index - 1) == null) View.INVISIBLE else View.VISIBLE
        view.connectorAfter.visibility = if (steps.getOrNull(index + 1) == null) View.INVISIBLE else View.VISIBLE
        view.connectorBefore.setBackgroundColor(ContextCompat.getColor(context,
            if (steps.getOrNull(index - 1)?.state == QuoteStepState.COMPLETED) R.color.lactec_nav else R.color.lactec_line,
        ))
        view.connectorAfter.setBackgroundColor(ContextCompat.getColor(context,
            if (step.state == QuoteStepState.COMPLETED) R.color.lactec_nav else R.color.lactec_line,
        ))
    }
}
