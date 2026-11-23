package br.ufpr.lab_mobile.controller

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.DialogQuoteAcceptanceTermsBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.service.AcceptanceApprovalRequest
import br.ufpr.lab_mobile.service.AcceptanceTermsRequest
import br.ufpr.lab_mobile.service.BillingAddressRequest
import br.ufpr.lab_mobile.service.ClientDecisionApiService
import br.ufpr.lab_mobile.service.ClientDecisionService
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class QuoteAcceptanceTermsDialogFragment : DialogFragment() {
    private var approvalDate: LocalDate = LocalDate.now()
    private val submission by lazy { ViewModelProvider(this)[QuoteDecisionSubmission::class.java] }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val views = DialogQuoteAcceptanceTermsBinding.inflate(layoutInflater)
        approvalDate = savedInstanceState?.getString(STATE_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        showDate(views)

        views.approvalDate.setOnClickListener { chooseDate(views) }
        views.allowsResultsByEmail.setOnCheckedChangeListener { _, checked ->
            views.resultsEmailLayout.isVisible = checked
            if (!checked) views.resultsEmailLayout.error = null
        }
        views.resultsEmailLayout.isVisible = views.allowsResultsByEmail.isChecked
        views.submitAcceptance.setOnClickListener { submit(views) }
        views.cancelAcceptance.setOnClickListener { dismiss() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                submission.state.collect { state ->
                    isCancelable = !state.sending
                    views.decisionLoading.isVisible = state.sending
                    views.submitAcceptance.isEnabled = !state.sending && !state.conflict
                    views.cancelAcceptance.isEnabled = !state.sending
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

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quote_accept_by_terms)
            .setView(views.root)
            .create()
    }

    private fun chooseDate(views: DialogQuoteAcceptanceTermsBinding) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                approvalDate = LocalDate.of(year, month + 1, day)
                showDate(views)
            },
            approvalDate.year,
            approvalDate.monthValue - 1,
            approvalDate.dayOfMonth,
        ).show()
    }

    private fun showDate(views: DialogQuoteAcceptanceTermsBinding) {
        val formatted = approvalDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")))
        views.approvalDate.text = getString(R.string.quote_approval_date, formatted)
    }

    private fun submit(views: DialogQuoteAcceptanceTermsBinding) {
        clearErrors(views)
        val request = readRequest(views) ?: return
        val id = requireArguments().getString(ARG_QUOTE_ID).orEmpty()
        val service = ClientDecisionService(
            RetrofitProvider.retrofit(getString(R.string.api_base_url))
                .create(ClientDecisionApiService::class.java),
        )
        submission.submit { service.accept(id, request) }
    }

    private fun readRequest(views: DialogQuoteAcceptanceTermsBinding): AcceptanceTermsRequest? {
        var valid = true
        fun required(input: TextInputEditText, layout: TextInputLayout): String {
            val value = input.text?.toString()?.trim().orEmpty()
            if (value.isEmpty()) {
                layout.error = getString(R.string.quote_decision_required_field)
                valid = false
            }
            return value
        }

        val company = required(views.company, views.companyLayout)
        val cnpj = required(views.cnpj, views.cnpjLayout).filter { it.isLetterOrDigit() }
        val street = required(views.street, views.streetLayout)
        val city = required(views.city, views.cityLayout)
        val state = required(views.state, views.stateLayout)
        val postalCode = required(views.postalCode, views.postalCodeLayout).filter(Char::isDigit)
        val requester = required(views.requesterName, views.requesterNameLayout)
        val responsible = required(views.responsibleName, views.responsibleNameLayout)
        val jobTitle = required(views.jobTitle, views.jobTitleLayout)

        if (!cnpj.matches(CNPJ_FORMAT)) {
            views.cnpjLayout.error = getString(R.string.quote_decision_invalid_cnpj)
            valid = false
        }
        if (!state.matches(STATE_FORMAT)) {
            views.stateLayout.error = getString(R.string.quote_decision_invalid_state)
            valid = false
        }
        if (!postalCode.matches(POSTAL_CODE_FORMAT)) {
            views.postalCodeLayout.error = getString(R.string.quote_decision_invalid_postal_code)
            valid = false
        }

        val emails = views.invoiceEmails.text?.toString().orEmpty()
            .split(',')
            .map(String::trim)
        val invoiceEmailValid = emails.size in 2..5 &&
            emails.all(::isValidEmail) &&
            emails.distinctBy { it.lowercase(Locale.ROOT) }.size == emails.size
        if (!invoiceEmailValid) {
            views.invoiceEmailsLayout.error = getString(R.string.quote_decision_invalid_emails)
            valid = false
        }

        val allowsResultsByEmail = views.allowsResultsByEmail.isChecked
        val resultsEmail = views.resultsEmail.text?.toString()?.trim().orEmpty()
        if (allowsResultsByEmail && !isValidEmail(resultsEmail)) {
            views.resultsEmailLayout.error = getString(R.string.quote_decision_invalid_email)
            valid = false
        }

        if (!valid) return null
        return AcceptanceTermsRequest(
            billing = BillingAddressRequest(company, cnpj, street, city, state, postalCode, emails),
            approval = AcceptanceApprovalRequest(
                requesterName = requester,
                responsibleName = responsible,
                jobTitle = jobTitle,
                date = approvalDate.toString(),
                allowsResultsByEmail = allowsResultsByEmail,
                resultsEmail = resultsEmail.takeIf { allowsResultsByEmail },
            ),
        )
    }

    private fun isValidEmail(value: String): Boolean =
        value.length <= 254 && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun clearErrors(views: DialogQuoteAcceptanceTermsBinding) {
        listOf(
            views.companyLayout,
            views.cnpjLayout,
            views.streetLayout,
            views.cityLayout,
            views.stateLayout,
            views.postalCodeLayout,
            views.invoiceEmailsLayout,
            views.requesterNameLayout,
            views.responsibleNameLayout,
            views.jobTitleLayout,
            views.resultsEmailLayout,
        ).forEach { it.error = null }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DATE, approvalDate.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (submission.state.value.conflict) {
            parentFragmentManager.setFragmentResult(QuoteDecisionDialogFragment.RESULT, Bundle())
        }
        super.onDismiss(dialog)
    }

    companion object {
        private const val TAG = "quote_acceptance_terms"
        private const val ARG_QUOTE_ID = "quote_id"
        private const val STATE_DATE = "approval_date"
        private val CNPJ_FORMAT = Regex("[A-Za-z0-9]{12}[0-9]{2}")
        private val STATE_FORMAT = Regex("[A-Za-z]{2}")
        private val POSTAL_CODE_FORMAT = Regex("[0-9]{8}")

        fun show(manager: FragmentManager, quote: Quote) {
            val id = quote.id?.trim().orEmpty()
            if (id.isBlank() || !QuoteStatusRules.canDecide(quote.stage, quote.status) ||
                manager.isStateSaved || manager.findFragmentByTag(TAG) != null
            ) return
            QuoteAcceptanceTermsDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_QUOTE_ID, id) }
            }.showNow(manager, TAG)
        }
    }
}
