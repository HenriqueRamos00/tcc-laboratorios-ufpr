package br.ufpr.lab_mobile

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.Spinner
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.ComponentDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.controller.QuoteStatusDialogFragment
import br.ufpr.lab_mobile.controller.QuotesActivity
import br.ufpr.lab_mobile.controller.QuoteDecisionDialogFragment
import br.ufpr.lab_mobile.controller.QuoteDetailsActivity
import br.ufpr.lab_mobile.model.QuoteSimulations
import br.ufpr.lab_mobile.service.RetrofitProvider
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Real Android views/lifecycle, isolated HTTP responses confined to instrumentation. */
@RunWith(AndroidJUnit4::class)
class QuoteStatusDialogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private val baseUrlField = RetrofitProvider::class.java.getDeclaredField("currentBaseUrl").apply { isAccessible = true }
    private var previousRetrofit: Any? = null
    private var previousBaseUrl: Any? = null
    private var scenario: ActivityScenario<QuotesActivity>? = null
    private val detailRequests = CopyOnWriteArrayList<String>()
    private val detailCode = AtomicInteger(200)
    private val blockedRequests = AtomicInteger(0)
    private val cancelledRequests = AtomicInteger(0)
    @Volatile private var blockDetails = false
    @Volatile private var stage: String? = "Em negociação"
    @Volatile private var status = "Em Andamento"

    @Before
    fun installTestOnlyApi() {
        QuoteSimulations.clear()
        previousRetrofit = retrofitField.get(null)
        previousBaseUrl = baseUrlField.get(null)
        val baseUrl = context.getString(R.string.api_base_url)
        val testClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val isDetail = request.url.encodedPath == "/api/quotes/$QUOTE_ID"
            if (isDetail) {
                detailRequests.add("${request.header("Authorization")} ${request.url.encodedPath}")
                if (blockDetails) {
                    blockedRequests.incrementAndGet()
                    val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
                    while (blockDetails && !chain.call().isCanceled() && SystemClock.uptimeMillis() < deadline) {
                        Thread.sleep(20)
                    }
                    if (chain.call().isCanceled()) {
                        cancelledRequests.incrementAndGet()
                        throw IOException("Cancelled test request")
                    }
                }
            }
            val code = if (isDetail) detailCode.get() else 200
            val payload = if (isDetail) quoteJson() else "[${quoteJson()}]"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("Instrumented test response")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val testRetrofit = RetrofitProvider.retrofit(baseUrl).newBuilder().client(testClient).build()
        retrofitField.set(null, testRetrofit)
    }

    @After
    fun restoreApi() {
        blockDetails = false
        scenario?.close()
        retrofitField.set(null, previousRetrofit)
        baseUrlField.set(null, previousBaseUrl)
        QuoteSimulations.clear()
    }

    @Test
    fun pendingStatusAllowsSimulationWithoutChangingListOrDialogStatus() {
        status = "Pendente de Aceite"
        stage = "Qualificação"
        launchList()
        openStatus()
        awaitContent()
        assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.acceptQuote).visibility == View.VISIBLE })
        scenario!!.onActivity { dialog(it)!!.findViewById<View>(R.id.acceptQuote).performClick() }
        awaitUi("Simulation confirmation shown") { fragment(it)!!.childFragmentManager.fragments.any { child -> child is QuoteDecisionDialogFragment } }
        scenario!!.onActivity { activity ->
            val confirmation = fragment(activity)!!.childFragmentManager.fragments.filterIsInstance<QuoteDecisionDialogFragment>().single().dialog!!
            confirmation.findViewById<View>(R.id.confirmDecision).performClick()
        }
        awaitUi("Simulation preserves the card status") { activity ->
            dialog(activity)!!.findViewById<View>(R.id.acceptQuote).visibility == View.GONE &&
                activity.findViewById<RecyclerView>(R.id.quotesList).findViewHolderForAdapterPosition(0)!!.itemView
                    .findViewById<TextView>(R.id.status).text.toString() == context.getString(R.string.quote_stage_qualification)
        }
        assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.acceptQuote).visibility == View.GONE && dialog(it)!!.findViewById<View>(R.id.rejectQuote).visibility == View.GONE })
        assertTrue(checkUi { dialog(it)!!.findViewById<TextView>(R.id.currentStatus).text.toString().contains("Qualificação") })
        scenario!!.recreate()
        awaitContent()
        assertTrue(checkUi { dialog(it)!!.findViewById<TextView>(R.id.currentStatus).text.toString().contains("Qualificação") })
    }

    @Test
    fun statusPdfAccessOpensSameQuoteDetailsScreen() {
        launchList()
        openStatus()
        awaitContent()
        val monitor = instrumentation.addMonitor(QuoteDetailsActivity::class.java.name, null, false)
        try {
            scenario!!.onActivity { dialog(it)!!.findViewById<View>(R.id.viewPdf).performClick() }
            val details = instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT_MS)
            assertTrue("Quote details opened", details is QuoteDetailsActivity)
            assertEquals(QUOTE_ID, details.intent.getStringExtra(QuoteDetailsActivity.EXTRA_QUOTE_ID))
            instrumentation.runOnMainSync { details.finish() }
            awaitContent()
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test
    fun openingRepeatedTapsRecreationAndBackPreserveQuoteAndFilters() {
        launchList()
        scenario!!.onActivity { activity ->
            activity.findViewById<TextView>(R.id.search).text = "EAQ-DEVICE"
            activity.findViewById<Spinner>(R.id.statusFilter).setSelection(2)
        }
        awaitList()
        scenario!!.onActivity { activity ->
            val button = statusButton(activity)!!
            button.performClick()
            button.performClick()
        }
        awaitContent()
        assertEquals(1, detailRequests.size)
        assertTrue(checkUi { it.supportFragmentManager.fragments.count { fragment -> fragment is QuoteStatusDialogFragment } == 1 })
        assertEquals("Bearer device-test-token /api/quotes/$QUOTE_ID", detailRequests.single())
        scenario!!.recreate()
        awaitContent()
        assertEquals(2, detailRequests.size)
        assertTrue(checkUi { activity ->
            activity.findViewById<TextView>(R.id.search).text.toString() == "EAQ-DEVICE" &&
                activity.findViewById<Spinner>(R.id.statusFilter).selectedItemPosition == 2
        })
        scenario!!.onActivity { (dialog(it) as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        awaitUi("Dialog closes on Back") { fragment(it) == null }
        assertTrue(checkUi { it.findViewById<TextView>(R.id.search).text.toString() == "EAQ-DEVICE" })
        assertTrue(checkUi { it.findViewById<Spinner>(R.id.statusFilter).selectedItemPosition == 2 })
    }

    @Test
    fun retryClearsErrorAndShowsFreshContent() {
        detailCode.set(500)
        launchList()
        openStatus()
        awaitUi("Retryable server error") { dialog(it)?.findViewById<View>(R.id.retryButton)?.isShown == true }
        assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.statusContent).visibility == View.GONE })
        detailCode.set(200)
        stage = "Recebido"
        scenario!!.onActivity { dialog(it)!!.findViewById<View>(R.id.retryButton).performClick() }
        awaitContent()
        assertTrue(checkUi { dialog(it)!!.findViewById<TextView>(R.id.phaseTitle).text.toString() == context.getString(R.string.quote_status_phase_technical) })
        assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.stateMessage).visibility == View.GONE })
        assertEquals(2, detailRequests.size)
    }

    @Test
    fun notFoundAndExpiredSessionHideContentAndRetry() {
        launchList()
        listOf(404 to R.string.quote_status_not_found, 401 to R.string.session_expired).forEach { (code, message) ->
            detailCode.set(code)
            openStatus()
            awaitUi("HTTP $code state") { dialog(it)?.findViewById<TextView>(R.id.stateMessage)?.text.toString() == context.getString(message) }
            assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.statusContent).visibility == View.GONE })
            assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.retryButton).visibility == View.GONE })
            closeStatus()
        }
    }

    @Test
    fun dismissalDuringLoadingCancelsRequestAndDoesNotReopenDialog() {
        blockDetails = true
        launchList()
        openStatus()
        awaitCondition("Detail request started") { blockedRequests.get() == 1 }
        assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.loading).isShown })
        closeStatus()
        awaitCondition("Retrofit request cancelled") { cancelledRequests.get() == 1 }
        assertTrue(checkUi { fragment(it) == null })
        blockDetails = false
        openStatus()
        awaitContent()
        assertEquals(2, detailRequests.size)
    }

    @Test
    fun rotationDuringLoadingCancelsOldRequestAndReloadsSameQuote() {
        blockDetails = true
        launchList()
        openStatus()
        awaitCondition("First detail request started") { blockedRequests.get() == 1 }
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        awaitCondition("Recreation cancels and restarts request") { cancelledRequests.get() == 1 && blockedRequests.get() == 2 }
        assertTrue(checkUi { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE })
        blockDetails = false
        awaitContent()
        assertEquals(2, detailRequests.size)
        assertTrue(detailRequests.all { it.endsWith("/api/quotes/$QUOTE_ID") })
    }

    @Test
    fun boundariesTerminalAndUnavailableStagesRenderAccurately() {
        launchList()
        listOf("Recebido" to R.string.quote_status_phase_technical, "Completo" to R.string.quote_status_phase_laboratory).forEach { (value, title) ->
            stage = value
            openStatus()
            awaitContent()
            assertTrue(checkUi { dialog(it)!!.findViewById<TextView>(R.id.phaseTitle).text.toString() == context.getString(title) })
            closeStatus()
        }
        stage = "Aprovado pelo cliente"
        openStatus()
        awaitContent()
        assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.noticeGroup).visibility == View.GONE })
        closeStatus()
        status = "Refused"
        openStatus()
        awaitContent()
        assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.progressSteps).visibility == View.GONE && dialog(it)!!.findViewById<View>(R.id.terminalMessage).isShown })
        closeStatus()
        status = "Em Andamento"
        listOf("Etapa futura", null).forEach { value ->
            stage = value
            openStatus()
            awaitContent()
            val expected = if (value == null) context.getString(R.string.quote_status_unavailable) else context.getString(R.string.quote_status_unknown_value, value)
            assertTrue(checkUi { dialog(it)!!.findViewById<TextView>(R.id.currentStatus).text.toString() == context.getString(R.string.quote_status_current, expected) })
            assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.progressSteps).visibility == View.GONE })
            closeStatus()
        }
    }

    @Test
    fun backendBusinessStatusesDoNotOverrideStageLabelsAndCurrentStepIsCentered() {
        listOf(
            Triple("Pendente de Aceite", "Qualificação", R.string.quote_stage_qualification),
            Triple("Em Andamento", "Negociação", R.string.quote_stage_negotiation),
        ).forEach { (businessStatus, backendStage, labelRes) ->
            status = businessStatus
            stage = backendStage
            if (scenario == null) launchList() else {
                scenario!!.recreate()
                awaitList()
            }
            val expected = context.getString(labelRes)
            assertTrue(checkUi { activity ->
                activity.findViewById<RecyclerView>(R.id.quotesList)
                    .findViewHolderForAdapterPosition(0)!!.itemView
                    .findViewById<TextView>(R.id.status).text.toString() == expected
            })
            openStatus()
            awaitContent()
            assertTrue(checkUi { activity ->
                val dialog = dialog(activity)!!
                val center = dialog.findViewById<View>(R.id.stepTwo)
                val row = dialog.findViewById<View>(R.id.progressSteps)
                center.findViewById<TextView>(R.id.label).text.toString() == expected &&
                    kotlin.math.abs(center.left + center.width / 2f - row.width / 2f) <= 1f &&
                    dialog.findViewById<TextView>(R.id.currentStatus).text.toString() == context.getString(R.string.quote_status_current, expected)
            })
            listOf("Atual", "Pendente", "Concluída", "Enviar e-mail").forEach { removedCopy ->
                assertTrue(checkUi { activity ->
                    val matches = ArrayList<View>()
                    dialog(activity)!!.window!!.decorView.findViewsWithText(matches, removedCopy, View.FIND_VIEWS_WITH_TEXT)
                    matches.filterIsInstance<TextView>().none { it.isShown && it.text.toString() == removedCopy }
                })
            }
            if (backendStage == "Qualificação") {
                assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.stepOne).visibility == View.INVISIBLE })
            }
            closeStatus()
        }
    }

    @Test
    fun threeStepsWrapWithoutTruncationAndExposeAccessibleStates() {
        stage = "Em análise pela área"
        launchList()
        openStatus()
        awaitContent()
        awaitUi("Step labels laid out") { dialog(it)!!.findViewById<View>(R.id.stepThree).findViewById<TextView>(R.id.label).layout != null }
        assertTrue(checkUi { activity ->
            val dialog = dialog(activity)!!
            val row = dialog.findViewById<View>(R.id.progressSteps)
            listOf(R.id.stepOne, R.id.stepTwo, R.id.stepThree).all { id ->
                val group = dialog.findViewById<View>(id)
                val view = group.findViewById<TextView>(R.id.label)
                val layout = view.layout
                view.isShown && group.left >= 0 && group.right <= row.width &&
                    group.findViewById<ImageView>(R.id.icon).let { icon ->
                        icon.isShown && icon.drawable != null && icon.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    } &&
                    layout.height + view.compoundPaddingTop + view.compoundPaddingBottom <= view.height &&
                    (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 } &&
                    view.contentDescription.toString().let { it.contains("Concluída") || it.contains("Atual") || it.contains("Pendente") }
            }
        })
        assertTrue(checkUi { dialog(it)!!.findViewById<TextView>(R.id.currentStatus).accessibilityLiveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE })
        if (context.resources.configuration.fontScale >= 1.5f) {
            assertTrue(checkUi { dialog(it)!!.findViewById<View>(R.id.quoteStatusScroll).canScrollVertically(1) })
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.let(::File) ?: context.cacheDir
            outputDir.mkdirs()
            File(outputDir, "hu03-status.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun launchList() {
        scenario = ActivityScenario.launch(Intent(context, QuotesActivity::class.java).putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "device-test-token"))
        scenario!!.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        awaitList()
    }

    private fun awaitList() = awaitUi("Quote list loaded") { statusButton(it)?.isShown == true }

    private fun statusButton(activity: QuotesActivity): View? = activity.findViewById<RecyclerView>(R.id.quotesList)
        .findViewHolderForAdapterPosition(0)?.itemView?.findViewById(R.id.viewStatus)

    private fun openStatus() {
        awaitList()
        scenario!!.onActivity { statusButton(it)!!.performClick() }
    }

    private fun closeStatus() {
        scenario!!.onActivity { fragment(it)!!.dismiss() }
        awaitUi("Dialog dismissed") { fragment(it) == null }
    }

    private fun awaitContent() = awaitUi("Fresh status content") { dialog(it)?.findViewById<View>(R.id.statusContent)?.isShown == true }

    private fun fragment(activity: QuotesActivity) = activity.supportFragmentManager.findFragmentByTag(QuoteStatusDialogFragment.TAG) as? QuoteStatusDialogFragment
    private fun dialog(activity: QuotesActivity) = fragment(activity)?.dialog

    private fun checkUi(condition: (QuotesActivity) -> Boolean): Boolean {
        var result = false
        scenario!!.onActivity { result = condition(it) }
        return result
    }

    private fun awaitUi(message: String, condition: (QuotesActivity) -> Boolean) = awaitCondition(message) { checkUi(condition) }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(message, condition())
    }

    private fun quoteJson(): String {
        val stageJson = stage?.let { "\"$it\"" } ?: "null"
        return """{"id":"$QUOTE_ID","code":"EAQ-DEVICE","description":"Verificação instrumentada","status":"$status","stage":$stageJson,"externalContactName":"Contato de teste","externalContactEmail":"contato@example.invalid"}"""
    }

    private companion object {
        const val QUOTE_ID = "hu03-quote"
        const val TIMEOUT_MS = 10_000L
    }
}
