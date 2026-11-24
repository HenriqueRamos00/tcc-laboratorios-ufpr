package br.ufpr.lab_mobile.controller

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.ReportListView
import br.ufpr.lab_mobile.adapter.bindReportedQuote
import br.ufpr.lab_mobile.databinding.ActivityResultsBinding
import br.ufpr.lab_mobile.databinding.ItemReportedQuoteBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStage
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.Report
import br.ufpr.lab_mobile.model.ResolvedQuoteStatus
import br.ufpr.lab_mobile.model.filterReports
import br.ufpr.lab_mobile.service.QuoteApiService
import br.ufpr.lab_mobile.service.QuoteService
import br.ufpr.lab_mobile.service.ReportApiService
import br.ufpr.lab_mobile.service.ReportService
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.tabs.TabLayout
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Tela "Relatórios" do menu, como a página de relatórios do portal web. Tem duas abas:
 * - "Todos os relatórios": os laudos publicados do cliente (GET /api/reports), com download;
 * - "Orçamentos com relatório": orçamentos na etapa "Relatório publicado", que abrem a
 *   [QuoteReportsActivity] com as etapas, o resumo e os laudos daquele orçamento.
 */
class ResultsActivity : PortalActivity() {
    private enum class Tab { REPORTS, QUOTES }

    /** Situação de uma lista vinda da API. */
    private sealed interface ListState<out T> {
        data object Loading : ListState<Nothing>
        data class Loaded<T>(val items: List<T>) : ListState<T>
        data class Failed(val message: Int, val retryable: Boolean) : ListState<Nothing>
    }

    private lateinit var binding: ActivityResultsBinding
    private lateinit var reportService: ReportService
    private lateinit var quoteService: QuoteService
    private lateinit var reportList: ReportListView
    private lateinit var downloader: ReportDownloader
    private var portalDrawer: PortalDrawer? = null
    private var tab = Tab.REPORTS
    private var reports: ListState<Report> = ListState.Loading
    /** `null` até a aba de orçamentos ser aberta pela primeira vez. */
    private var quotes: ListState<Quote>? = null
    private var reportsJob: Job? = null
    private var quotesJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasSession) return
        enableEdgeToEdge()
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.resultsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        portalDrawer = PortalDrawer(this, PortalDestination.REPORTS)

        val retrofit = RetrofitProvider.retrofit(getString(R.string.api_base_url))
        reportService = ReportService(retrofit.create(ReportApiService::class.java))
        quoteService = QuoteService(retrofit.create(QuoteApiService::class.java))
        reportList = ReportListView(binding.reportsList) { report -> downloader.start(report) }
        downloader = ReportDownloader(this, reportService) { reportId -> reportList.setDownloading(reportId) }
        downloader.restoreState(savedInstanceState)

        tab = savedInstanceState?.getString(STATE_TAB)?.let(Tab::valueOf) ?: Tab.REPORTS
        binding.resultsTabs.getTabAt(tab.ordinal)?.select()
        binding.resultsTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(selected: TabLayout.Tab) = selectTab(Tab.entries[selected.position])
            override fun onTabUnselected(unselected: TabLayout.Tab) = Unit
            override fun onTabReselected(reselected: TabLayout.Tab) = Unit
        })
        binding.search.setText(savedInstanceState?.getString(STATE_SEARCH).orEmpty())
        binding.search.doAfterTextChanged { render() }
        binding.retry.setOnClickListener { if (tab == Tab.REPORTS) loadReports() else loadQuotes() }

        loadReports()
        if (tab == Tab.QUOTES) loadQuotes()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) {
            outState.putString(STATE_TAB, tab.name)
            outState.putString(STATE_SEARCH, binding.search.text?.toString())
            downloader.saveState(outState)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        portalDrawer?.onResume()
    }

    private fun selectTab(selected: Tab) {
        if (selected == tab) return
        tab = selected
        // Cada aba busca por campos diferentes, então o termo não é reaproveitado.
        binding.search.setText("")
        if (selected == Tab.QUOTES && quotes == null) loadQuotes()
        render()
    }

    private fun loadReports() {
        reportsJob?.cancel()
        reports = ListState.Loading
        render()
        reportsJob = lifecycleScope.launch {
            reports = try {
                ListState.Loaded(reportService.getReports())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure(error, R.string.reports_loading_error)
            }
            render()
        }
    }

    private fun loadQuotes() {
        quotesJob?.cancel()
        quotes = ListState.Loading
        render()
        quotesJob = lifecycleScope.launch {
            quotes = try {
                // Igual ao web: só entram os orçamentos cuja etapa real é "Relatório publicado".
                val published = quoteService.getQuotes(search = null, status = null).filter { quote ->
                    QuoteStatusRules.resolve(quote) == ResolvedQuoteStatus.Progress(QuoteStage.PUBLISHED)
                }
                ListState.Loaded(published)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure(error, R.string.reported_quotes_loading_error)
            }
            render()
        }
    }

    private fun failure(error: Exception, message: Int): ListState.Failed =
        if (error is HttpException && error.code() == 401) {
            ListState.Failed(if (sessions.current == null) R.string.session_expired else message, retryable = false)
        } else {
            ListState.Failed(message, retryable = true)
        }

    /** Redesenha a aba ativa a partir do estado atual e do termo de busca. */
    private fun render() {
        val search = binding.search.text?.toString().orEmpty()
        binding.search.setHint(if (tab == Tab.REPORTS) R.string.reports_search_hint else R.string.reported_quotes_search_hint)
        binding.reportsList.isVisible = tab == Tab.REPORTS
        binding.quotesList.isVisible = tab == Tab.QUOTES
        when (tab) {
            Tab.REPORTS -> {
                val visible = (reports as? ListState.Loaded)?.items?.let { filterReports(it, search) }.orEmpty()
                reportList.show(visible)
                showState(reports, visible.isEmpty(), search, R.string.reports_empty, R.string.reports_no_matches)
            }
            Tab.QUOTES -> {
                val state = quotes ?: ListState.Loading
                val visible = (state as? ListState.Loaded)?.items?.let { filterQuotes(it, search) }.orEmpty()
                showQuotes(visible)
                showState(state, visible.isEmpty(), search, R.string.reported_quotes_empty, R.string.reported_quotes_no_matches)
            }
        }
    }

    private fun showState(state: ListState<*>, nothingVisible: Boolean, search: String, empty: Int, noMatches: Int) {
        binding.loading.isVisible = state is ListState.Loading
        binding.retry.isVisible = state is ListState.Failed && state.retryable
        val message = when {
            state is ListState.Failed -> state.message
            state is ListState.Loaded && nothingVisible -> if (search.isBlank()) empty else noMatches
            else -> null
        }
        binding.resultsState.isVisible = message != null
        if (message != null) binding.resultsState.setText(message)
    }

    private fun showQuotes(visible: List<Quote>) {
        binding.quotesList.removeAllViews()
        visible.forEach { quote ->
            val item = ItemReportedQuoteBinding.inflate(layoutInflater, binding.quotesList, false)
            bindReportedQuote(item, quote) { QuoteReportsActivity.open(this, it.id) }
            binding.quotesList.addView(item.root)
        }
    }

    /** Busca local por ID da proposta, nome ou empresa, como na aba do portal web. */
    private fun filterQuotes(quotes: List<Quote>, search: String): List<Quote> {
        val term = search.trim().lowercase(Locale.ROOT)
        if (term.isEmpty()) return quotes
        return quotes.filter { quote ->
            listOf(quote.code, quote.name, quote.companyName).any { it.orEmpty().lowercase(Locale.ROOT).contains(term) }
        }
    }

    private companion object {
        const val STATE_TAB = "results_tab"
        const val STATE_SEARCH = "results_search"
    }
}
