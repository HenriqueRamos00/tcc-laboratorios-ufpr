package br.ufpr.lab_mobile.controller

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.DialogQuoteDecisionBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteSimulations
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.SimulatedDecision
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class QuoteDecisionDialogFragment : DialogFragment() {
    private var binding: DialogQuoteDecisionBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val views = DialogQuoteDecisionBinding.inflate(layoutInflater)
        binding = views
        val args = requireArguments()
        val rejecting = args.getBoolean("rejecting")
        views.decisionMessage.text = getString(
            if (rejecting) R.string.quote_confirm_reject else R.string.quote_confirm_accept,
            args.getString("code").orEmpty(),
        )
        views.reasonLayout.isVisible = rejecting
        views.reason.setText(savedInstanceState?.getString("reason").orEmpty())
        views.confirmDecision.setText(if (rejecting) R.string.quote_confirm_reject_action else R.string.quote_confirm_accept_action)
        views.confirmDecision.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
            if (rejecting) R.color.quote_action_reject else R.color.quote_action_accept,
        ))
        views.confirmDecision.setOnClickListener {
            views.confirmDecision.isEnabled = false
            val decision = if (rejecting) SimulatedDecision.REJECTED else SimulatedDecision.ACCEPTED
            val confirmed = QuoteSimulations.confirm(
                args.getString("id").orEmpty(), args.getString("status"),
                decision,
                if (rejecting) views.reason.text?.toString() else null,
            )
            if (confirmed) Toast.makeText(requireContext(), decision.labelRes, Toast.LENGTH_LONG).show()
            parentFragmentManager.setFragmentResult(RESULT, Bundle())
            dismiss()
        }
        views.cancelDecision.setOnClickListener { dismiss() }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quote_simulation_title)
            .setView(views.root)
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("reason", binding?.reason?.text?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val RESULT = "quote_simulation_changed"
        private const val TAG = "quote_decision"

        fun show(manager: FragmentManager, quote: Quote, rejecting: Boolean) {
            val id = quote.id?.trim().orEmpty()
            if (id.isEmpty() || !QuoteStatusRules.canDecide(quote.stage, quote.status) ||
                QuoteSimulations.get(id) != null || manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            QuoteDecisionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("id", id)
                    putString("code", quote.code ?: quote.name.orEmpty())
                    putString("status", quote.status)
                    putBoolean("rejecting", rejecting)
                }
            }.showNow(manager, TAG)
        }
    }
}
