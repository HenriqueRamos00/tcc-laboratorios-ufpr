package br.ufpr.lab_mobile.controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.ReportListView
import br.ufpr.lab_mobile.databinding.ActivityQuoteReportsBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteReports
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.model.ResolvedQuoteStatus
import br.ufpr.lab_mobile.model.displayText
import br.ufpr.lab_mobile.model.filterReports
import br.ufpr.lab_mobile.service.QuoteApiService
import br.ufpr.lab_mobile.service.QuoteService
import br.ufpr.lab_mobile.service.ReportApiService
import br.ufpr.lab_mobile.service.ReportService
import br.ufpr.lab_mobile.service.RetrofitProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Relatórios de um orçamento ("Conclusão da Análise").
 *
 * Carrega duas coisas ao mesmo tempo: o orçamento (GET /api/quotes/{id}), para mostrar as
 * etapas reais, e a conclusão da análise (GET /api/quotes/{id}/reports), com tipo de ensaio,
 * resumo e laudos. Cada laudo pode ser baixado pelo [ReportDownloader].
 */
class QuoteReportsActivity : PortalActivity() {
    private lateinit var binding: ActivityQuoteReportsBinding
    private lateinit var quoteService: QuoteService
    private lateinit var reportService: ReportService
    private lateinit var reportList: ReportListView
    private lateinit var downloader: ReportDownloader
    private lateinit var quoteId: String
    /** Conclusão carregada; `null` enquanto carrega ou quando o orçamento ainda não tem laudo. */
    private var conclusion: QuoteReports? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasSession) return
        enableEdgeToEdge()
        binding = ActivityQuoteReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.quoteReportsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        binding.backButton.setOnClickListener { finish() }
        quoteId = intent.getStringExtra(EXTRA_QUOTE_ID)?.trim().orEmpty()

        val retrofit = RetrofitProvider.retrofit(getString(R.string.api_base_url))
        quoteService = QuoteService(retrofit.create(QuoteApiService::class.java))
        reportService = ReportService(retrofit.create(ReportApiService::class.java))
        reportList = ReportListView(binding.reportsList) { report -> downloader.start(report) }
        downloader = ReportDownloader(this, reportService::downloadReport) { reportId -> reportList.setDownloading(reportId) }
        downloader.restoreState(savedInstanceState)

        binding.search.setText(savedInstanceState?.getString(STATE_SEARCH).orEmpty())
        binding.search.doAfterTextChanged { renderReports() }
        binding.retryButton.setOnClickListener { load() }
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) {
            outState.putString(STATE_SEARCH, binding.search.text?.toString())
            downloader.saveState(outState)
        }
        super.onSaveInstanceState(outState)
    }

    private fun load() {
        loadJob?.cancel()
        conclusion = null
        binding.content.isVisible = false
        binding.stateMessage.isVisible = false
        binding.retryButton.isVisible = false
        if (quoteId.isBlank()) { showError(R.string.quote_status_invalid_id, retryable = false); return }
        binding.loading.isVisible = true
        loadJob = lifecycleScope.launch {
            try {
                // As duas consultas são independentes, então rodam em paralelo.
                val (quote, reports) = coroutineScope {
                    val quote = async { quoteService.getQuote(quoteId) }
                    val reports = async { reportService.getQuoteReports(quoteId) }
                    quote.await() to reports.await()
                }
                binding.loading.isVisible = false
                binding.content.isVisible = true
                renderQuote(quote, reports)
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                when (error.code()) {
                    401 -> showError(if (sessions.current == null) R.string.session_expired else R.string.quote_reports_loading_error, retryable = false)
                    404 -> showError(R.string.quote_status_not_found, retryable = false)
                    else -> showError(R.string.quote_reports_loading_error, retryable = true)
                }
            } catch (_: Exception) {
                showError(R.string.quote_reports_loading_error, retryable = true)
            }
        }
    }

    private fun renderQuote(quote: Quote, reports: QuoteReports?) {
        conclusion = reports
        val code = reports?.quoteCode ?: quote.code
        binding.quoteCode.text = getString(R.string.quote_reports_subtitle, code?.takeIf(String::isNotBlank) ?: "—")
        val protocol = (reports?.protocol ?: quote.protocol)?.trim().orEmpty()
        binding.protocol.isVisible = protocol.isNotEmpty()
        binding.protocol.text = getString(R.string.quote_reports_protocol, protocol)

        // Etapas: só fazem sentido para um orçamento em andamento (não encerrado nem desconhecido).
        val status = QuoteStatusRules.resolve(quote)
        binding.progressCard.isVisible = status is ResolvedQuoteStatus.Progress
        if (status is ResolvedQuoteStatus.Progress) {
            binding.phaseTitle.setText(QuoteStatusRules.phase(status.stage).titleRes)
            bindQuoteProgress(this, listOf(binding.stepOne, binding.stepTwo, binding.stepThree), status.stage)
            binding.currentStatus.text = getString(R.string.quote_status_current, status.displayText(resources))
        }

        val testType = reports?.testType?.trim().orEmpty()
        binding.testType.text = if (testType.isEmpty()) getString(R.string.quote_reports_test_type_missing)
            else getString(R.string.quote_reports_test_type, testType)
        val summary = reports?.summary?.trim().orEmpty()
        binding.summary.text = if (summary.isEmpty()) getString(R.string.quote_reports_summary_missing)
            else getString(R.string.quote_reports_summary, summary)

        renderReports()
    }

    private fun renderReports() {
        val all = conclusion?.reports.orEmpty()
        val visible = filterReports(all, binding.search.text?.toString().orEmpty())
        reportList.show(visible)
        // Sem nenhum laudo não há o que buscar: a busca some e fica só o aviso.
        binding.searchLayout.isVisible = all.isNotEmpty()
        binding.reportsState.isVisible = visible.isEmpty()
        binding.reportsState.text = if (all.isEmpty()) {
            getString(R.string.quote_reports_none_title) + "\n" + getString(R.string.quote_reports_none_message)
        } else {
            getString(R.string.reports_no_matches)
        }
    }

    private fun showError(message: Int, retryable: Boolean) {
        binding.loading.isVisible = false
        binding.content.isVisible = false
        binding.stateMessage.setText(message)
        binding.stateMessage.isVisible = true
        binding.retryButton.isVisible = retryable
    }

    override fun onDestroy() {
        loadJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUOTE_ID = "quote_id"
        private const val STATE_SEARCH = "quote_reports_search"

        fun open(context: Context, id: String?) {
            if (id.isNullOrBlank()) {
                Toast.makeText(context, R.string.quote_status_invalid_id, Toast.LENGTH_SHORT).show()
                return
            }
            context.startActivity(Intent(context, QuoteReportsActivity::class.java)
                .putExtra(EXTRA_QUOTE_ID, id.trim()))
        }
    }
}
