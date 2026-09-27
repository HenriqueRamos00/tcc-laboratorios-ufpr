package br.ufpr.lab_mobile.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ItemQuoteBinding
import br.ufpr.lab_mobile.model.Quote
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
            status.text = quote.status?.takeIf(String::isNotBlank)
                ?: quote.stage.valueOrPlaceholder()
            status.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    root.context,
                    when {
                        status.text.toString().contains("final", ignoreCase = true) -> R.color.quote_finished
                        status.text.toString().contains("andamento", ignoreCase = true) -> R.color.quote_progress
                        else -> R.color.quote_pending
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
