package br.ufpr.lab_mobile.controller

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.formatQuotePrice
import br.ufpr.lab_mobile.databinding.ActivityQuoteDetailsBinding
import br.ufpr.lab_mobile.model.Quote
import br.ufpr.lab_mobile.model.QuoteSimulations
import br.ufpr.lab_mobile.model.QuoteStatusRules
import br.ufpr.lab_mobile.service.InvalidQuoteDocumentException
import br.ufpr.lab_mobile.service.QuoteApiService
import br.ufpr.lab_mobile.service.QuoteService
import br.ufpr.lab_mobile.service.RetrofitProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class QuoteDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuoteDetailsBinding
    private lateinit var service: QuoteService
    private lateinit var token: String
    private lateinit var quoteId: String
    private var quote: Quote? = null
    private var quoteJob: Job? = null
    private var pdfJob: Job? = null
    private var renderJob: Job? = null
    private var document: File? = null
    private var bitmap: Bitmap? = null
    private var currentPage = 0
    private var pageCount = 0
    private var exporting = false
    private var pendingExportUri: Uri? = null
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@registerForActivityResult
        pendingExportUri = uri
        exportPdf(uri)
    }

    private fun exportPdf(uri: Uri) {
        // The picker can return immediately after recreation, before the PDF reload completes.
        val file = document ?: return
        if (exporting) return
        exporting = true
        binding.downloadPdf.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = contentResolver.openOutputStream(uri, "wt") ?: error("Missing document destination")
                    output.use { target -> file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            target.write(buffer, 0, count)
                        }
                    } }
                }
                pendingExportUri = null
                Toast.makeText(this@QuoteDetailsActivity, R.string.quote_download_success, Toast.LENGTH_SHORT).show()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                pendingExportUri = null
                Toast.makeText(this@QuoteDetailsActivity, R.string.quote_download_error, Toast.LENGTH_LONG).show()
            } finally {
                exporting = false
                binding.downloadPdf.isEnabled = document != null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQuoteDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.backButton.setOnClickListener { finish() }
        quoteId = intent.getStringExtra(EXTRA_QUOTE_ID)?.trim().orEmpty()
        token = intent.getStringExtra(LoginActivity.EXTRA_ACCESS_TOKEN)
            ?: getSharedPreferences(LoginActivity.SESSION_PREFERENCES, MODE_PRIVATE).getString(LoginActivity.ACCESS_TOKEN, null).orEmpty()
        currentPage = savedInstanceState?.getInt("pdf_page") ?: 0
        pendingExportUri = savedInstanceState?.getString("export_uri")?.let(Uri::parse)
        binding.retryButton.setOnClickListener { loadQuote() }
        binding.pdfRetryButton.setOnClickListener { loadDocument() }
        binding.previousPage.setOnClickListener { renderPage(currentPage - 1) }
        binding.nextPage.setOnClickListener { renderPage(currentPage + 1) }
        binding.downloadPdf.setOnClickListener {
            exportDocument.launch("orcamento-${quote?.code.orEmpty().replace(Regex("[^a-zA-Z0-9_-]"), "_")}.pdf")
        }
        binding.acceptQuote.setOnClickListener { quote?.let { QuoteDecisionDialogFragment.show(supportFragmentManager, it, false) } }
        binding.rejectQuote.setOnClickListener { quote?.let { QuoteDecisionDialogFragment.show(supportFragmentManager, it, true) } }
        supportFragmentManager.setFragmentResultListener(QuoteDecisionDialogFragment.RESULT, this) { _, _ -> renderSimulation() }
        if (token.isBlank()) { showError(R.string.session_expired, false); return }
        QuoteSimulations.startSession(token)
        service = QuoteService(RetrofitProvider.retrofit(getString(R.string.api_base_url)).create(QuoteApiService::class.java))
        loadQuote()
    }

    private fun loadQuote() {
        quoteJob?.cancel()
        pdfJob?.cancel()
        renderJob?.cancel()
        resetDocument()
        quote = null
        binding.detailsContent.isVisible = false
        binding.stateMessage.isVisible = false
        binding.retryButton.isVisible = false
        if (quoteId.isBlank()) { showError(R.string.quote_status_invalid_id, false); return }
        binding.loading.isVisible = true
        quoteJob = lifecycleScope.launch {
            try {
                val result = service.getQuote(token, quoteId)
                quote = result
                binding.loading.isVisible = false
                binding.detailsContent.isVisible = true
                binding.proposalCode.text = getString(R.string.quote_status_code, result.code ?: "—")
                binding.proposalName.text = getString(R.string.quote_details_heading, result.name ?: result.code ?: "—")
                binding.clientName.text = getString(R.string.quote_client, result.companyName ?: "—")
                binding.description.text = result.description ?: "—"
                binding.totalPrice.text = getString(R.string.quote_total, formatQuotePrice(result.totalPrice))
                binding.serverStatus.text = getString(R.string.quote_status_current, result.status ?: getString(R.string.quote_status_unavailable))
                renderSimulation()
                loadDocument()
            } catch (error: CancellationException) { throw error
            } catch (error: HttpException) {
                if (error.code() == 401) LoginActivity.clearSession(this@QuoteDetailsActivity)
                showError(when (error.code()) { 401 -> R.string.session_expired; 404 -> R.string.quote_status_not_found; else -> R.string.quote_details_error }, error.code() !in listOf(401,404))
            } catch (_: Exception) { showError(R.string.quote_details_error, true) }
        }
    }

    private fun renderSimulation() {
        val result = QuoteSimulations.get(quoteId)
        val eligible = QuoteSimulations.isSessionActive(token) &&
            quote?.let { QuoteStatusRules.canDecide(it.stage, it.status) } == true && result == null
        binding.acceptQuote.isVisible = eligible
        binding.rejectQuote.isVisible = eligible
    }

    private fun showError(message: Int, retryable: Boolean) {
        binding.loading.isVisible = false
        binding.detailsContent.isVisible = false
        binding.stateMessage.setText(message)
        binding.stateMessage.isVisible = true
        binding.retryButton.isVisible = retryable
    }

    private fun resetDocument() {
        binding.pdfPage.setImageDrawable(null)
        binding.pdfPage.isVisible = false
        bitmap?.recycle()
        bitmap = null
        document?.delete()
        document = null
        pageCount = 0
        binding.downloadPdf.isEnabled = false
        binding.pdfControls.isVisible = false
    }

    private fun loadDocument() {
        pdfJob?.cancel()
        renderJob?.cancel()
        resetDocument()
        binding.pdfLoading.isVisible = true
        binding.pdfMessage.setText(R.string.quote_document_loading)
        binding.pdfMessage.isVisible = true
        binding.pdfRetryButton.isVisible = false
        pdfJob = lifecycleScope.launch {
            var file: File? = null
            var retained = false
            try {
                val downloaded = File.createTempFile("quote-", ".pdf", cacheDir)
                file = downloaded
                service.downloadDocument(token, quoteId, downloaded)
                val count = withContext(Dispatchers.IO) {
                    try {
                        ParcelFileDescriptor.open(downloaded, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { it.pageCount }
                        }
                    } catch (_: Exception) { throw InvalidQuoteDocumentException() }
                }
                if (count <= 0) throw InvalidQuoteDocumentException()
                document = downloaded
                retained = true
                pageCount = count
                renderPage(currentPage.coerceIn(0, count - 1))
            } catch (error: CancellationException) { throw error
            } catch (error: HttpException) {
                if (error.code() == 401) { LoginActivity.clearSession(this@QuoteDetailsActivity); renderSimulation() }
                pdfError(when (error.code()) { 401 -> R.string.session_expired; 404 -> R.string.quote_document_missing; else -> R.string.quote_document_error }, error.code() !in listOf(401,404))
            } catch (_: InvalidQuoteDocumentException) { pdfError(R.string.quote_document_invalid, true)
            } catch (_: Exception) { pdfError(R.string.quote_document_error, true)
            } finally { if (!retained) file?.delete() }
        }
    }

    private fun renderPage(index: Int) {
        val file = document ?: return
        if (index !in 0 until pageCount) return
        renderJob?.cancel()
        binding.pdfLoading.isVisible = true
        binding.previousPage.isEnabled = false
        binding.nextPage.isEnabled = false
        renderJob = lifecycleScope.launch {
            var rendered: Bitmap? = null
            var displayed = false
            try {
                withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(index).use { page ->
                                // Bound raster memory even on large or unusual PDF page sizes.
                                val width = resources.displayMetrics.widthPixels.coerceIn(320, 1440)
                                val height = (width.toDouble() * page.height / page.width).toInt().coerceIn(1, 4096)
                                rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                                    eraseColor(Color.WHITE)
                                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                }
                binding.pdfPage.setImageBitmap(rendered)
                binding.pdfPage.isVisible = true
                bitmap?.recycle()
                bitmap = rendered
                displayed = true
                currentPage = index
                binding.pageCount.text = getString(R.string.quote_pdf_page, index + 1, pageCount)
                binding.pdfPage.contentDescription = getString(R.string.quote_pdf_accessibility, index + 1, pageCount)
                binding.pdfMessage.isVisible = false
                binding.pdfLoading.isVisible = false
                binding.pdfControls.isVisible = true
                binding.previousPage.isEnabled = index > 0
                binding.nextPage.isEnabled = index < pageCount - 1
                binding.downloadPdf.isEnabled = !exporting
                pendingExportUri?.let(::exportPdf)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { resetDocument(); pdfError(R.string.quote_document_invalid, true)
            } finally { if (!displayed) rendered?.recycle() }
        }
    }

    private fun pdfError(message: Int, retryable: Boolean) {
        if (pendingExportUri != null) {
            pendingExportUri = null
            Toast.makeText(this, R.string.quote_download_error, Toast.LENGTH_LONG).show()
        }
        binding.pdfLoading.isVisible = false
        binding.pdfMessage.setText(message)
        binding.pdfMessage.isVisible = true
        binding.pdfRetryButton.isVisible = retryable
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pdf_page", currentPage)
        outState.putString("export_uri", pendingExportUri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        quoteJob?.cancel()
        pdfJob?.cancel()
        renderJob?.cancel()
        resetDocument()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUOTE_ID = "quote_id"
        fun open(context: Context, id: String?, token: String?) {
            if (id.isNullOrBlank()) {
                Toast.makeText(context, R.string.quote_status_invalid_id, Toast.LENGTH_SHORT).show()
                return
            }
            context.startActivity(Intent(context, QuoteDetailsActivity::class.java)
                .putExtra(EXTRA_QUOTE_ID, id.trim()).putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, token))
        }
    }
}
