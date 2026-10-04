package br.ufpr.lab_mobile.controller

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.DialogQuoteStatusBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStage
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.QuoteStepState
import br.ufpr.lab_mobile.model.ResolvedQuoteStatus
import br.ufpr.lab_mobile.model.displayText
import br.ufpr.lab_mobile.service.QuoteApiService
import br.ufpr.lab_mobile.service.QuoteService
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class QuoteStatusDialogFragment : DialogFragment() {
    private var binding: DialogQuoteStatusBinding? = null
    private var requestJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val viewBinding = DialogQuoteStatusBinding.inflate(layoutInflater)
        binding = viewBinding
        viewBinding.retryButton.setOnClickListener { loadQuote() }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quote_status_title)
            .setView(viewBinding.root)
            .setNeutralButton(R.string.quote_status_close) { _, _ -> dismiss() }
            .create()
    }

    override fun onStart() {
        super.onStart()
        if (requestJob == null) loadQuote()
    }

    private fun loadQuote() {
        requestJob?.cancel()
        val views = binding ?: return
        // Hide all previous content before every attempt, including failed retries.
        views.statusContent.isVisible = false
        views.stateMessage.isVisible = false
        views.retryButton.isVisible = false
        views.loading.isVisible = false
        val quoteId = arguments?.getString(ARG_QUOTE_ID)?.trim().orEmpty()
        if (quoteId.isBlank()) {
            showError(R.string.quote_status_invalid_id, retryable = false)
            return
        }
        val token = requireActivity().intent.getStringExtra(LoginActivity.EXTRA_ACCESS_TOKEN)
            ?: requireContext().getSharedPreferences(LoginActivity.SESSION_PREFERENCES, Context.MODE_PRIVATE)
                .getString(LoginActivity.ACCESS_TOKEN, null)
        if (token.isNullOrBlank()) {
            showError(R.string.session_expired, retryable = false)
            return
        }
        views.loading.isVisible = true
        val service = QuoteService(
            RetrofitProvider.retrofit(getString(R.string.api_base_url))
                .create(QuoteApiService::class.java),
        )
        // This dialog supplies its view via onCreateDialog. Cancel its request explicitly
        // on dismissal/destruction rather than retaining work beyond the custom view.
        requestJob = lifecycleScope.launch {
            try {
                val quote = withContext(Dispatchers.IO) { service.getQuote(token, quoteId) }
                if (binding !== views) return@launch
                views.loading.isVisible = false
                renderQuote(quote, views)
                views.statusContent.isVisible = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                when (error.code()) {
                    401 -> showError(R.string.session_expired, retryable = false)
                    404 -> showError(R.string.quote_status_not_found, retryable = false)
                    else -> showError(R.string.quote_status_loading_error, retryable = true)
                }
            } catch (_: Exception) {
                showError(R.string.quote_status_loading_error, retryable = true)
            }
        }
    }

    private fun showError(messageRes: Int, retryable: Boolean) {
        val views = binding ?: return
        views.loading.isVisible = false
        views.statusContent.isVisible = false
        views.stateMessage.setText(messageRes)
        views.stateMessage.isVisible = true
        views.retryButton.isVisible = retryable
    }

    private fun renderQuote(quote: Quote, views: DialogQuoteStatusBinding) {
        views.proposalCode.text = quote.code?.trim()?.takeIf(String::isNotEmpty)
            ?.let { getString(R.string.quote_status_code, it) }
            ?: getString(R.string.quote_status_code_missing)
        views.phaseTitle.isVisible = false
        views.progressSteps.isVisible = false
        views.noticeGroup.isVisible = false
        views.noticeTitle.isVisible = false
        views.noticeMessage.isVisible = false
        views.terminalMessage.isVisible = false
        val resolved = QuoteStatusRules.resolve(quote.stage, quote.status)
        when (resolved) {
            is ResolvedQuoteStatus.Progress -> {
                val stage = resolved.stage
                views.phaseTitle.setText(QuoteStatusRules.phase(stage).titleRes)
                views.phaseTitle.isVisible = true
                views.progressSteps.isVisible = true
                val steps = QuoteStatusRules.window(stage)
                listOf(views.stepOne, views.stepTwo, views.stepThree)
                    .zip(steps).forEachIndexed { index, (view, step) ->
                        view.root.visibility = if (step == null) View.INVISIBLE else View.VISIBLE
                        if (step == null) return@forEachIndexed
                        val label = getString(step.stage.labelRes)
                        val state = getString(step.state.labelRes)
                        view.label.text = label
                        view.label.contentDescription = getString(R.string.quote_status_step_accessibility, label, state)
                        view.label.setTypeface(null, if (step.state == QuoteStepState.ACTIVE) Typeface.BOLD else Typeface.NORMAL)
                        view.label.setTextColor(ContextCompat.getColor(requireContext(), when (step.state) {
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
                        view.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                            if (step.state == QuoteStepState.PENDING) R.color.lactec_muted else R.color.white,
                        ))
                        view.connectorBefore.visibility = if (steps.getOrNull(index - 1) == null) View.INVISIBLE else View.VISIBLE
                        view.connectorAfter.visibility = if (steps.getOrNull(index + 1) == null) View.INVISIBLE else View.VISIBLE
                        view.connectorBefore.setBackgroundColor(ContextCompat.getColor(requireContext(),
                            if (steps.getOrNull(index - 1)?.state == QuoteStepState.COMPLETED) R.color.lactec_nav else R.color.lactec_line,
                        ))
                        view.connectorAfter.setBackgroundColor(ContextCompat.getColor(requireContext(),
                            if (step.state == QuoteStepState.COMPLETED) R.color.lactec_nav else R.color.lactec_line,
                        ))
                    }
                when (stage) {
                    QuoteStage.AREA_ANALYSIS -> showNotice(views, R.string.quote_status_area_title, R.string.quote_status_area_message)
                    QuoteStage.NEGOTIATION -> showNotice(views, R.string.quote_status_negotiation_title, R.string.quote_status_negotiation_message)
                    else -> Unit
                }
            }
            is ResolvedQuoteStatus.Terminal -> {
                views.terminalMessage.setText(R.string.quote_status_closed)
                views.terminalMessage.isVisible = true
            }
            else -> Unit
        }
        views.currentStatus.text = getString(R.string.quote_status_current, resolved.displayText(resources))
        val name = quote.externalContactName?.trim().orEmpty()
        val email = quote.externalContactEmail?.trim().orEmpty()
        views.externalContactGroup.isVisible = name.isNotEmpty() || email.isNotEmpty()
        views.externalContactName.text = name
        views.externalContactName.isVisible = name.isNotEmpty()
        views.externalContactEmail.text = email
        views.externalContactEmail.isVisible = email.isNotEmpty()
    }

    private fun showNotice(views: DialogQuoteStatusBinding, titleRes: Int, messageRes: Int) {
        views.noticeGroup.isVisible = true
        views.noticeTitle.setText(titleRes)
        views.noticeMessage.setText(messageRes)
        views.noticeTitle.isVisible = true
        views.noticeMessage.isVisible = true
    }

    override fun onDismiss(dialog: DialogInterface) {
        requestJob?.cancel()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        requestJob?.cancel()
        requestJob = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "quote_status"
        private const val ARG_QUOTE_ID = "quote_id"

        fun newInstance(quoteId: String) = QuoteStatusDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUOTE_ID, quoteId) }
        }
    }
}
