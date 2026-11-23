package br.ufpr.lab_mobile.controller

import android.content.Context
import androidx.fragment.app.FragmentManager
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.model.Quote
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal fun showQuoteAcceptanceOptions(context: Context, manager: FragmentManager, quote: Quote) {
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.quote_acceptance_method_title)
        .setItems(arrayOf(
            context.getString(R.string.quote_accept_by_terms),
            context.getString(R.string.quote_accept_by_purchase_order),
        )) { _, selected ->
            if (selected == 0) QuoteAcceptanceTermsDialogFragment.show(manager, quote)
            else QuotePurchaseOrderDialogFragment.show(manager, quote)
        }
        .show()
}
