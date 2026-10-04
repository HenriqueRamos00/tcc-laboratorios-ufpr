package br.ufpr.lab_mobile.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ItemQuoteBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStage
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.ResolvedQuoteStatus
import br.ufpr.lab_mobile.model.displayText
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class QuoteAdapter(
    private val onViewQuote: (Quote) -> Unit,
    private val onViewStatus: (Quote) -> Unit,
) : RecyclerView.Adapter<QuoteAdapter.QuoteViewHolder>() {
    private val quotes = mutableListOf<Quote>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = QuoteViewHolder(
        ItemQuoteBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: QuoteViewHolder, position: Int) = holder.bind(quotes[position])

    override fun getItemCount() = quotes.size

    fun updateQuotes(newQuotes: List<Quote>) {
        quotes.clear()
        quotes.addAll(newQuotes)
        notifyDataSetChanged()
    }

    inner class QuoteViewHolder(private val binding: ItemQuoteBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(quote: Quote) = with(binding) {
            proposalCode.text = quote.code.valueOrPlaceholder()
            proposalName.text = quote.name.valueOrPlaceholder()
            description.text = quote.description.valueOrPlaceholder()
            createdDate.text = formatQuoteDate(quote.createdDate)
            company.text = quote.companyName.valueOrPlaceholder()
            contact.text = quote.externalContactName?.takeIf(String::isNotBlank)
                ?: quote.externalContactEmail.valueOrPlaceholder()
            price.text = formatQuotePrice(quote.totalPrice)
            val resolvedStatus = QuoteStatusRules.resolve(quote.stage, quote.status)
            status.text = resolvedStatus.displayText(root.resources)
            status.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    root.context,
                    when (resolvedStatus) {
                        is ResolvedQuoteStatus.Progress -> when {
                            resolvedStatus.stage == QuoteStage.PUBLISHED -> R.color.quote_finished
                            resolvedStatus.stage.ordinal >= QuoteStage.APPROVED.ordinal -> R.color.quote_progress
                            else -> R.color.quote_pending
                        }
                        is ResolvedQuoteStatus.Terminal -> R.color.lactec_danger_bg
                        else -> R.color.lactec_line
                    },
                ),
            )
            viewQuote.setOnClickListener { onViewQuote(quote) }
            viewStatus.setOnClickListener { onViewStatus(quote) }
        }
    }
}

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val brCurrency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))

internal fun formatQuoteDate(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return try {
        OffsetDateTime.parse(value).format(displayDateFormatter)
    } catch (_: DateTimeParseException) {
        value
    }
}

internal fun formatQuotePrice(value: Double?) = value?.let(brCurrency::format) ?: "—"

private fun String?.valueOrPlaceholder() = this?.takeIf(String::isNotBlank) ?: "—"
