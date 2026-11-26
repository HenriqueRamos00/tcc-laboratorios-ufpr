package br.ufpr.lab_mobile.controller

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.DialogQuoteDecisionBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.service.ClientDecisionApiService
import br.ufpr.lab_mobile.service.ClientDecisionService
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class QuoteDecisionDialogFragment : DialogFragment() {
    private val submission by lazy { ViewModelProvider(this)[QuoteDecisionSubmission::class.java] }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val views = DialogQuoteDecisionBinding.inflate(layoutInflater)
        views.decisionMessage.text = getString(R.string.quote_confirm_reject, requireArguments().getString("code"))
        views.reason.filters = arrayOf(InputFilter.LengthFilter(ClientDecisionService.MAX_JUSTIFICATION_LENGTH))
        views.confirmDecision.setOnClickListener {
            val service = ClientDecisionService(RetrofitProvider.retrofit(getString(R.string.api_base_url))
                .create(ClientDecisionApiService::class.java))
            val id = requireArguments().getString(ARG_ID).orEmpty()
            val reason = views.reason.text?.toString()
            submission.submit { service.reject(id, reason) }
        }
        views.cancelDecision.setOnClickListener { dismiss() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                submission.state.collect { state ->
                    isCancelable = !state.sending
                    views.decisionLoading.isVisible = state.sending
                    views.confirmDecision.isEnabled = !state.sending && !state.conflict
                    views.cancelDecision.isEnabled = !state.sending
                    views.reason.isEnabled = !state.sending && !state.conflict
                    views.decisionStatus.isVisible = state.error != null
                    state.error?.let { views.decisionStatus.setText(it.decisionMessage()) }
                    if (submission.consumeCompletion()) {
                        Toast.makeText(requireContext(), R.string.quote_rejection_success, Toast.LENGTH_LONG).show()
                        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply {
                            putBoolean(REJECTED, true)
                        })
                        dismiss()
                    }
                }
            }
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quote_rejection_title)
            .setView(views.root)
            .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (submission.state.value.conflict) parentFragmentManager.setFragmentResult(RESULT, Bundle())
        super.onDismiss(dialog)
    }

    companion object {
        const val RESULT = "quote_decision_recorded"
        const val REJECTED = "rejected"
        private const val TAG = "quote_decision"
        private const val ARG_ID = "id"

        fun show(manager: FragmentManager, quote: Quote) {
            val id = quote.id?.trim().orEmpty()
            if (id.isEmpty() || !QuoteStatusRules.canDecide(quote) ||
                manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            QuoteDecisionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, id)
                    putString("code", quote.code ?: quote.name.orEmpty())
                }
            }.showNow(manager, TAG)
        }
    }
}
