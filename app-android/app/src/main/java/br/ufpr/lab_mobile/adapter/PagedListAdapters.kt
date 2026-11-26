package br.ufpr.lab_mobile.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import br.ufpr.lab_mobile.databinding.ItemListFooterBinding

/** Uma view única que rola junto com a lista, como o cabeçalho de uma tela. */
internal class FixedViewAdapter(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(view) {}
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}

/** Lista que cresce por páginas: o que chega no fim é só inserido, sem mexer na rolagem. */
abstract class GrowingListAdapter<T, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
    protected var items: List<T> = emptyList()
        private set

    override fun getItemCount() = items.size

    fun show(newItems: List<T>) {
        val previous = items
        items = newItems
        val kept = if (newItems.size >= previous.size && newItems.subList(0, previous.size) == previous) previous.size else 0
        if (kept < previous.size) notifyItemRangeRemoved(0, previous.size)
        if (kept < newItems.size) notifyItemRangeInserted(kept, newItems.size - kept)
    }
}

/** O fim de uma lista que ainda não está completa: o andamento da leitura ou a falha. */
internal data class ListFooter(val message: String, val loading: Boolean, val canRetry: Boolean)

/** Rodapé que só existe enquanto a lista não está completa. */
internal class ListFooterAdapter(private val onRetry: () -> Unit) : RecyclerView.Adapter<ListFooterAdapter.FooterViewHolder>() {
    private var footer: ListFooter? = null

    override fun getItemCount() = if (footer == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = FooterViewHolder(
        ItemListFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: FooterViewHolder, position: Int) {
        footer?.let(holder::bind)
    }

    fun show(newFooter: ListFooter?) {
        val previous = footer
        footer = newFooter
        when {
            previous == newFooter -> Unit
            previous == null -> notifyItemInserted(0)
            newFooter == null -> notifyItemRemoved(0)
            // Com payload o mesmo item é reaproveitado, sem piscar a cada página.
            else -> notifyItemChanged(0, Unit)
        }
    }

    inner class FooterViewHolder(private val binding: ItemListFooterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(footer: ListFooter) = with(binding) {
            moreLoading.isVisible = footer.loading
            moreRetry.isVisible = footer.canRetry
            moreRetry.setOnClickListener { onRetry() }
            // Só a falha é anunciada; o andamento muda a cada página.
            moreMessage.accessibilityLiveRegion =
                if (footer.loading) View.ACCESSIBILITY_LIVE_REGION_NONE else View.ACCESSIBILITY_LIVE_REGION_POLITE
            moreMessage.text = footer.message
        }
    }
}
