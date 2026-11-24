package br.ufpr.lab_mobile.controller

import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.DialogQuotePurchaseOrderBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.service.ClientDecisionApiService
import br.ufpr.lab_mobile.service.ClientDecisionService
import br.ufpr.lab_mobile.service.RetrofitProvider
import br.ufpr.lab_mobile.service.purchaseOrderName
import br.ufpr.lab_mobile.service.uploadPurchaseOrder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuotePurchaseOrderDialogFragment : DialogFragment() {
    private var binding: DialogQuotePurchaseOrderBinding? = null
    private var selectedUri: Uri? = null
    private val submission by lazy { ViewModelProvider(this)[QuoteDecisionSubmission::class.java] }
    private val selectFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            showSelection()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        selectedUri = savedInstanceState?.getString(STATE_URI)?.let(Uri::parse)
        val views = DialogQuotePurchaseOrderBinding.inflate(layoutInflater)
        binding = views
        views.selectFile.setOnClickListener { selectFile.launch(FILE_TYPES) }
        views.cancelPurchaseOrder.setOnClickListener { dismiss() }
        views.submitPurchaseOrder.setOnClickListener {
            val uri = selectedUri ?: return@setOnClickListener
            val app = requireContext().applicationContext
            val id = requireArguments().getString(ARG_ID).orEmpty()
            val service = ClientDecisionService(RetrofitProvider.retrofit(getString(R.string.api_base_url))
                .create(ClientDecisionApiService::class.java))
            submission.submit { uploadPurchaseOrder(service, id, app.contentResolver, uri, app.cacheDir) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                submission.state.collect { state ->
                    isCancelable = !state.sending
                    views.decisionLoading.isVisible = state.sending
                    views.selectFile.isEnabled = !state.sending && !state.conflict
                    views.submitPurchaseOrder.isEnabled = selectedUri != null && !state.sending && !state.conflict
                    views.cancelPurchaseOrder.isEnabled = !state.sending
                    views.decisionStatus.isVisible = state.error != null
                    state.error?.let { views.decisionStatus.setText(it.decisionMessage()) }
                    if (submission.consumeCompletion()) {
                        Toast.makeText(requireContext(), R.string.quote_acceptance_success, Toast.LENGTH_LONG).show()
                        parentFragmentManager.setFragmentResult(QuoteDecisionDialogFragment.RESULT, Bundle())
                        dismiss()
                    }
                }
            }
        }
        showSelection()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quote_accept_by_purchase_order)
            .setView(views.root)
            .create()
    }

    private fun showSelection() {
        val views = binding ?: return
        val uri = selectedUri ?: return
        val resolver = requireContext().contentResolver
        lifecycleScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { purchaseOrderName(resolver, uri) }
                if (binding !== views || selectedUri != uri) return@launch
                views.fileName.text = name
                views.submitPurchaseOrder.isEnabled = !submission.state.value.sending && !submission.state.value.conflict
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                selectedUri = null
                views.decisionStatus.setText(R.string.quote_purchase_order_invalid)
                views.decisionStatus.isVisible = true
                views.submitPurchaseOrder.isEnabled = false
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_URI, selectedUri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (submission.state.value.conflict) parentFragmentManager.setFragmentResult(QuoteDecisionDialogFragment.RESULT, Bundle())
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "quote_purchase_order"
        private const val ARG_ID = "id"
        private const val STATE_URI = "purchase_order_uri"
        private val FILE_TYPES = arrayOf("application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

        fun show(manager: FragmentManager, quote: Quote) {
            val id = quote.id?.trim().orEmpty()
            if (id.isBlank() || !QuoteStatusRules.canDecide(quote) ||
                manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            QuotePurchaseOrderDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_ID, id) }
            }.showNow(manager, TAG)
        }
    }
}
