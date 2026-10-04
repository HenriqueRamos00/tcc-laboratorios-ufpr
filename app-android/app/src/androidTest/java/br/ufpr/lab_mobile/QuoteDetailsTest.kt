package br.ufpr.lab_mobile

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.controller.QuoteDecisionDialogFragment
import br.ufpr.lab_mobile.controller.QuoteDetailsActivity
import br.ufpr.lab_mobile.model.QuoteSimulations
import br.ufpr.lab_mobile.model.SimulatedDecision
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.textfield.TextInputEditText
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuoteDetailsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private val baseUrlField = RetrofitProvider::class.java.getDeclaredField("currentBaseUrl").apply { isAccessible = true }
    private var oldRetrofit: Any? = null
    private var oldBaseUrl: Any? = null
    private var scenario: ActivityScenario<QuoteDetailsActivity>? = null
    private lateinit var pdf: ByteArray
    @Volatile private var documentCode = 200
    @Volatile private var invalidPdf = false
    @Volatile private var offline = false
    @Volatile private var block = false
    @Volatile private var status: String? = "Pendente de Aceite"
    @Volatile private var stage: String? = "Qualificação"
    private val documentRequests = AtomicInteger()
    private val blocked = AtomicInteger()
    private val cancelled = AtomicInteger()
    private val forbiddenRequests = AtomicInteger()

    @Before
    fun installTestApi() {
        QuoteSimulations.clear()
        oldRetrofit = retrofitField.get(null)
        oldBaseUrl = baseUrlField.get(null)
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                page.canvas.drawText("Proposta fictícia — página ${index + 1}", 30f, 40f, android.graphics.Paint())
                document.finishPage(page)
            }
            pdf = ByteArrayOutputStream().use { output -> document.writeTo(output); output.toByteArray() }
        } finally { document.close() }
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            assertEquals("Bearer test-session", request.header("Authorization"))
            if (request.method != "GET") forbiddenRequests.incrementAndGet()
            val isPdf = request.url.encodedPath.endsWith("/document")
            if (isPdf) {
                documentRequests.incrementAndGet()
                if (block) {
                    blocked.incrementAndGet()
                    val deadline = SystemClock.uptimeMillis() + 10_000
                    while (block && !chain.call().isCanceled() && SystemClock.uptimeMillis() < deadline) Thread.sleep(20)
                    if (chain.call().isCanceled()) {
                        cancelled.incrementAndGet()
                        throw IOException("Cancelled instrumented request")
                    }
                }
                if (offline) throw IOException("Offline instrumented request")
            }
            val body = if (isPdf) (if (invalidPdf) "%PDF-broken".toByteArray() else pdf)
                .toResponseBody("application/pdf".toMediaType())
                else quoteJson().toResponseBody("application/json".toMediaType())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (isPdf) documentCode else 200).message("Instrumented response").body(body).build()
        }).build()
        val url = context.getString(R.string.api_base_url)
        retrofitField.set(null, RetrofitProvider.retrofit(url).newBuilder().client(client).build())
    }

    @After
    fun restore() {
        block = false
        if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true) {
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        }
        scenario?.close()
        retrofitField.set(null, oldRetrofit)
        baseUrlField.set(null, oldBaseUrl)
        QuoteSimulations.clear()
    }

    @Test
    fun nativePdfPaginationAndRotationPreservePage() {
        launch()
        awaitPage(1)
        assertTrue(check { it.findViewById<ImageView>(R.id.pdfPage).drawable != null })
        assertTrue(check { it.findViewById<View>(R.id.acceptQuote).visibility == View.VISIBLE })
        assertTrue(check { !it.findViewById<View>(R.id.previousPage).isEnabled })
        scenario!!.onActivity { it.findViewById<View>(R.id.nextPage).performClick() }
        awaitPage(2)
        assertTrue(check { !it.findViewById<View>(R.id.nextPage).isEnabled })
        scenario!!.recreate()
        awaitPage(2)
        assertTrue(check { it.findViewById<ImageView>(R.id.pdfPage).contentDescription.contains("2") })
        assertTrue(check { it.findViewById<View>(R.id.detailsScroll).canScrollVertically(1) })
        assertTrue(check {
            val back = it.findViewById<android.widget.ImageButton>(R.id.backButton)
            back.drawable != null && back.contentDescription == context.getString(R.string.quote_back) &&
                kotlin.math.abs((back.top + back.bottom) - back.parent.let { parent -> (parent as View).height }) <= 1 &&
                back.width >= (48 * it.resources.displayMetrics.density).toInt()
        })
        screenshot("hu03-details.png")
    }

    @Test
    fun missingInvalidNetworkRetryAndExpiredPdfStates() {
        documentCode = 404
        launch()
        awaitMessage(R.string.quote_document_missing)
        assertTrue(check { !it.findViewById<View>(R.id.downloadPdf).isEnabled && it.findViewById<View>(R.id.pdfRetryButton).visibility == View.GONE })
        documentCode = 200
        invalidPdf = true
        scenario!!.recreate()
        awaitMessage(R.string.quote_document_invalid)
        invalidPdf = false
        offline = true
        retry()
        awaitMessage(R.string.quote_document_error)
        offline = false
        retry()
        awaitPage(1)
        documentCode = 401
        scenario!!.recreate()
        awaitMessage(R.string.session_expired)
        assertTrue(check { it.findViewById<View>(R.id.acceptQuote).visibility == View.GONE && it.findViewById<View>(R.id.rejectQuote).visibility == View.GONE })
        assertFalse(QuoteSimulations.confirm("quote-test", "PendingAcceptance", SimulatedDecision.ACCEPTED, null))
    }

    @Test
    fun abandonedRequestAndRotationCancelPdfFetch() {
        block = true
        launch()
        await("PDF started") { blocked.get() == 1 }
        scenario!!.recreate()
        await("Old PDF cancelled and restarted") { cancelled.get() == 1 && blocked.get() == 2 }
        block = false
        awaitPage(1)
        block = true
        scenario!!.recreate()
        await("Another PDF started") { blocked.get() == 3 }
        scenario!!.close()
        scenario = null
        await("Abandoned PDF cancelled") { cancelled.get() == 2 }
    }

    @Test
    fun confirmationCancellationOptionalReasonRotationAndDuplicatePrevention() {
        launch()
        awaitPage(1)
        openDecision(false)
        scenario!!.onActivity { decision(it)!!.findViewById<View>(R.id.cancelDecision).performClick() }
        assertNull(QuoteSimulations.get("quote-test"))
        assertTrue(check { it.findViewById<View>(R.id.acceptQuote).visibility == View.VISIBLE })
        openDecision(true)
        scenario!!.onActivity { decision(it)!!.findViewById<TextInputEditText>(R.id.reason).setText("Preço para revisão") }
        scenario!!.recreate()
        await("Reason restored") { check { decision(it)?.findViewById<TextInputEditText>(R.id.reason)?.text.toString() == "Preço para revisão" } }
        awaitPage(1)
        screenshot("hu03-rejection.png")
        scenario!!.onActivity { decision(it)!!.findViewById<View>(R.id.confirmDecision).performClick() }
        await("Simulation confirmed without status change") { check { it.findViewById<View>(R.id.acceptQuote).visibility == View.GONE } }
        assertEquals("Preço para revisão", QuoteSimulations.get("quote-test")?.reason)
        assertEquals(SimulatedDecision.REJECTED, QuoteSimulations.get("quote-test")?.decision)
        assertTrue(check { it.findViewById<View>(R.id.acceptQuote).visibility == View.GONE && it.findViewById<View>(R.id.rejectQuote).visibility == View.GONE })
        assertFalse(QuoteSimulations.confirm("quote-test", status, SimulatedDecision.ACCEPTED, null))
        assertEquals(0, forbiddenRequests.get())
        scenario!!.recreate()
        awaitPage(1)
        assertEquals(context.getString(R.string.quote_status_current, status), checkText(R.id.serverStatus))
    }

    @Test
    fun acceptanceAndRejectionWithoutReasonAreExplicitlySimulated() {
        launch()
        awaitPage(1)
        openDecision(false)
        assertTrue(check { decision(it)!!.findViewById<TextView>(R.id.simulationNotice).text.toString().contains("ainda não é definitiva") })
        scenario!!.onActivity { decision(it)!!.findViewById<View>(R.id.confirmDecision).performClick() }
        assertEquals(SimulatedDecision.ACCEPTED, QuoteSimulations.get("quote-test")?.decision)
        assertEquals(context.getString(R.string.quote_status_current, status), checkText(R.id.serverStatus))
        QuoteSimulations.clear()
        scenario!!.recreate()
        awaitPage(1)
        openDecision(true)
        scenario!!.onActivity { decision(it)!!.findViewById<View>(R.id.confirmDecision).performClick() }
        assertEquals(SimulatedDecision.REJECTED, QuoteSimulations.get("quote-test")?.decision)
        assertNull(QuoteSimulations.get("quote-test")?.reason)
        assertEquals(0, forbiddenRequests.get())
        scenario!!.onActivity { LoginActivity.clearSession(it) }
        assertNull(QuoteSimulations.get("quote-test"))
    }

    @Test
    fun decisionVisibilityDependsOnlyOnPendingStatusAtAnyStage() {
        launch()
        listOf(null, "Etapa futura", "Finalizado", "Qualificação", "Negociação").forEach { value ->
            stage = value
            scenario!!.recreate()
            awaitPage(1)
            assertTrue(check { it.findViewById<View>(R.id.acceptQuote).visibility == View.VISIBLE && it.findViewById<View>(R.id.rejectQuote).visibility == View.VISIBLE })
        }
        stage = "Negociação"
        listOf(null, "Em Andamento", "Aceito", "Unknown").forEach { value ->
            status = value
            scenario!!.recreate()
            awaitPage(1)
            assertTrue(check { it.findViewById<View>(R.id.acceptQuote).visibility == View.GONE && it.findViewById<View>(R.id.rejectQuote).visibility == View.GONE })
        }
    }

    @Test
    fun documentPickerExportsPdfAndSurvivesActivityRecreation() {
        launch()
        awaitPage(1)
        val name = "hu03-instrumented-${SystemClock.uptimeMillis()}.pdf"
        var activity: QuoteDetailsActivity? = null
        scenario!!.onActivity { activity = it; it.findViewById<View>(R.id.downloadPdf).performClick() }
        await("Document picker opened") { findNode { it.isEditable } != null }
        // ActivityScenario.recreate waits for RESUMED; the native picker keeps this Activity STOPPED.
        instrumentation.runOnMainSync { activity!!.recreate() }
        await("Details recreated behind document picker") { documentRequests.get() >= 2 }
        val filename = findNode { it.isEditable }!!
        assertTrue(filename.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name)
        }))
        val save = findNode { it.isClickable && (it.text?.toString()?.equals("Salvar", true) == true || it.text?.toString()?.equals("Save", true) == true) }
        assertNotNull("Save action in native document picker", save)
        assertTrue(save!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        try {
            await("Export completed") { check { it.findViewById<View>(R.id.downloadPdf).isEnabled } }
            var exported = byteArrayOf()
            await("PDF bytes exported to selected document") {
                exported = instrumentation.uiAutomation.executeShellCommand("cat /sdcard/Download/$name")
                    .let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
                exported.size == pdf.size
            }
            assertArrayEquals(pdf, exported)
        } finally {
            instrumentation.uiAutomation.executeShellCommand("rm /sdcard/Download/$name").close()
        }
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (predicate(node)) return node
            repeat(node.childCount) { index -> search(node.getChild(index))?.let { return it } }
            return null
        }
        return search(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, QuoteDetailsActivity::class.java)
            .putExtra(QuoteDetailsActivity.EXTRA_QUOTE_ID, "quote-test")
            .putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "test-session"))
    }
    private fun retry() = scenario!!.onActivity { it.findViewById<View>(R.id.pdfRetryButton).performClick() }
    private fun openDecision(rejecting: Boolean) {
        scenario!!.onActivity { it.findViewById<View>(if (rejecting) R.id.rejectQuote else R.id.acceptQuote).performClick() }
        await("Decision dialog shown") { check { decision(it) != null } }
    }
    private fun decision(activity: QuoteDetailsActivity) = activity.supportFragmentManager.fragments
        .filterIsInstance<QuoteDecisionDialogFragment>().singleOrNull()?.dialog
    private fun awaitPage(number: Int) = await("PDF page $number displayed") {
        check { it.findViewById<TextView>(R.id.pageCount).text.toString() == context.getString(R.string.quote_pdf_page, number, 2) &&
            it.findViewById<View>(R.id.pdfPage).visibility == View.VISIBLE }
    }
    private fun awaitMessage(res: Int) = await("PDF error $res") {
        check { it.findViewById<TextView>(R.id.pdfMessage).text.toString() == context.getString(res) }
    }
    private fun check(condition: (QuoteDetailsActivity) -> Boolean): Boolean {
        var result = false
        scenario!!.onActivity { result = condition(it) }
        return result
    }
    private fun checkText(id: Int): String {
        var text = ""
        scenario!!.onActivity { text = it.findViewById<TextView>(id).text.toString() }
        return text
    }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; Thread.sleep(50) }
        assertTrue(message, condition())
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            val folder = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.cacheDir
            folder.mkdirs()
            File(folder, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun quoteJson(): String {
        fun json(value: String?) = value?.let { "\"$it\"" } ?: "null"
        return """{"id":"quote-test","code":"EAQ-TEST","name":"Qualidade do ar","description":"Proposta fictícia de ensaios","companyName":"Cliente fictício","totalPrice":3450,"status":${json(status)},"stage":${json(stage)}}"""
    }
}
