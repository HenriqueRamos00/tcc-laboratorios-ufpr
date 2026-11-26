package br.ufpr.lab_mobile.controller

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.FixedViewAdapter
import br.ufpr.lab_mobile.adapter.ListFooterAdapter
import br.ufpr.lab_mobile.adapter.ReportAdapter
import br.ufpr.lab_mobile.adapter.bindReportedQuote
import br.ufpr.lab_mobile.databinding.ActivityResultsBinding
import br.ufpr.lab_mobile.databinding.ItemReportedQuoteBinding
import br.ufpr.lab_mobile.databinding.ResultsListHeaderBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteStage
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.Report
import br.ufpr.lab_mobile.model.ResolvedQuoteStatus
import br.ufpr.lab_mobile.model.filterReports
import br.ufpr.lab_mobile.service.PagedList
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

/**
 * Tela "Relatórios" do menu, como a página de relatórios do portal web. Tem duas abas:
 * - "Todos os relatórios": os laudos publicados do cliente (GET /api/reports), com download;
 * - "Orçamentos com relatório": orçamentos na etapa "Relatório publicado", que abrem a
 *   [QuoteReportsActivity] com as etapas, o resumo e os laudos daquele orçamento.
 */
class ResultsActivity : PortalActivity() {
    private enum class Tab { REPORTS, QUOTES }

    private lateinit var binding: ActivityResultsBinding
    private lateinit var header: ResultsListHeaderBinding
    private lateinit var reportService: ReportService
    private lateinit var quoteService: QuoteService
    private lateinit var downloader: ReportDownloader
    private var portalDrawer: PortalDrawer? = null
    private var tab = Tab.REPORTS
    private lateinit var reports: PagedList<Report>
    private var reportsStatus: LoadStatus = LoadStatus.Loading
    private var quotes: List<Quote> = emptyList()
    /** `null` até a aba de orçamentos ser aberta pela primeira vez. */
    private var quotesStatus: LoadStatus? = null
    private var reportsJob: Job? = null
    private var quotesJob: Job? = null
    private val reportAdapter = ReportAdapter { report -> downloader.start(report) }
    private val footerAdapter = ListFooterAdapter { loadReports() }

    override val greeting: TextView? get() = if (::header.isInitialized) header.quotesGreeting else null

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
        binding.resultsScroll.layoutManager = LinearLayoutManager(this)
        // O cabeçalho rola junto com os laudos, como na tela de equipamentos.
        header = ResultsListHeaderBinding.inflate(layoutInflater, binding.resultsScroll, false)
        binding.resultsScroll.adapter = ConcatAdapter(FixedViewAdapter(header.root), reportAdapter, footerAdapter)

        val retrofit = RetrofitProvider.retrofit(getString(R.string.api_base_url))
        reportService = ReportService(retrofit.create(ReportApiService::class.java))
        quoteService = QuoteService(retrofit.create(QuoteApiService::class.java))
        reports = reportService.reports()
        downloader = ReportDownloader(this, reportService::downloadReport) { reportId -> reportAdapter.setDownloading(reportId) }
        downloader.restoreState(savedInstanceState)

        tab = savedInstanceState?.getString(STATE_TAB)?.let(Tab::valueOf) ?: Tab.REPORTS
        header.resultsTabs.getTabAt(tab.ordinal)?.select()
        header.resultsTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(selected: TabLayout.Tab) = selectTab(Tab.entries[selected.position])
            override fun onTabUnselected(unselected: TabLayout.Tab) = Unit
            override fun onTabReselected(reselected: TabLayout.Tab) = Unit
        })
        header.search.setText(savedInstanceState?.getString(STATE_SEARCH).orEmpty())
        header.search.doAfterTextChanged { render() }
        header.retry.setOnClickListener { if (tab == Tab.REPORTS) loadReports() else loadQuotes() }

        loadReports()
        if (tab == Tab.QUOTES) loadQuotes()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) {
            outState.putString(STATE_TAB, tab.name)
            outState.putString(STATE_SEARCH, header.search.text?.toString())
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
        header.search.setText("")
        if (selected == Tab.QUOTES && quotesStatus == null) loadQuotes()
        render()
    }

    /** Lê as páginas que faltam; depois de uma falha, continua de onde parou. */
    private fun loadReports() {
        reportsJob?.cancel()
        reportsStatus = LoadStatus.Loading
        renderReports()
        reportsJob = lifecycleScope.launch {
            reportsStatus = try {
                reports.loadRemaining(::renderReports)
                LoadStatus.Complete
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loadFailure(error, R.string.reports_loading_error, hasSession = sessions.current != null)
            }
            renderReports()
        }
    }

    private fun loadQuotes() {
        quotesJob?.cancel()
        quotesStatus = LoadStatus.Loading
        renderQuotes()
        quotesJob = lifecycleScope.launch {
            quotesStatus = try {
                // Igual ao web: só entram os orçamentos cuja etapa real é "Relatório publicado".
                quotes = quoteService.getQuotes(search = null, status = null).filter { quote ->
                    QuoteStatusRules.resolve(quote) == ResolvedQuoteStatus.Progress(QuoteStage.PUBLISHED)
                }
                LoadStatus.Complete
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loadFailure(error, R.string.reported_quotes_loading_error, hasSession = sessions.current != null)
            }
            renderQuotes()
        }
    }

    /** Redesenha a aba ativa a partir do estado atual e do termo de busca. */
    private fun render() {
        header.search.setHint(if (tab == Tab.REPORTS) R.string.reports_search_hint else R.string.reported_quotes_search_hint)
        header.quotesList.isVisible = tab == Tab.QUOTES
        if (tab == Tab.REPORTS) {
            renderReports()
        } else {
            reportAdapter.show(emptyList())
            footerAdapter.show(null)
            renderQuotes()
        }
    }

    private fun renderReports() {
        if (tab != Tab.REPORTS) return
        val search = header.search.text?.toString().orEmpty()
        val loaded = reports.items
        val visible = filterReports(loaded, search)
        reportAdapter.show(visible)
        footerAdapter.show(listFooter(reports, reportsStatus))
        showState(reportsStatus, loaded.isEmpty(), visible.isEmpty(), search, R.string.reports_empty, R.string.reports_no_matches)
    }

    private fun renderQuotes() {
        if (tab != Tab.QUOTES) return
        val search = header.search.text?.toString().orEmpty()
        val visible = filterQuotes(quotes, search)
        showQuotes(visible)
        showState(quotesStatus ?: LoadStatus.Loading, quotes.isEmpty(), visible.isEmpty(), search,
            R.string.reported_quotes_empty, R.string.reported_quotes_no_matches)
    }

    /** O que aparece no lugar da lista. Com parte dela na tela, o andamento e a falha ficam no rodapé. */
    private fun showState(status: LoadStatus, nothingLoaded: Boolean, nothingVisible: Boolean, search: String, empty: Int, noMatches: Int) {
        val failure = (status as? LoadStatus.Failed)?.takeIf { nothingLoaded }
        val message = when {
            failure != null -> failure.message
            status != LoadStatus.Complete || !nothingVisible -> null
            search.isBlank() -> empty
            else -> noMatches
        }
        header.loading.isVisible = status == LoadStatus.Loading && nothingLoaded
        header.retry.isVisible = failure?.retryable == true
        header.resultsState.isVisible = message != null
        if (message != null) header.resultsState.setText(message)
    }

    private fun showQuotes(visible: List<Quote>) {
        header.quotesList.removeAllViews()
        visible.forEach { quote ->
            val item = ItemReportedQuoteBinding.inflate(layoutInflater, header.quotesList, false)
            bindReportedQuote(item, quote) { QuoteReportsActivity.open(this, it.id) }
            header.quotesList.addView(item.root)
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
