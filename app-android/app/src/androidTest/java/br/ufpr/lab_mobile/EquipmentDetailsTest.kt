package br.ufpr.lab_mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufpr.lab_mobile.controller.EquipmentDetailsActivity
import br.ufpr.lab_mobile.service.RetrofitProvider
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EquipmentDetailsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private val baseUrlField = RetrofitProvider::class.java.getDeclaredField("currentBaseUrl").apply { isAccessible = true }
    private var oldRetrofit: Any? = null
    private var oldBaseUrl: Any? = null
    private var scenario: ActivityScenario<EquipmentDetailsActivity>? = null
    private val requests = ConcurrentLinkedQueue<Request>()
    @Volatile private var detail = DETAIL
    @Volatile private var detailStatus = 200
    @Volatile private var history = HISTORY
    @Volatile private var historyStatus = 200
    @Volatile private var documentGate: CountDownLatch? = null

    @Before
    fun installResponses() {
        TestSession.clear(context)
        oldRetrofit = retrofitField.get(null)
        oldBaseUrl = baseUrlField.get(null)
        val client = OkHttpClient.Builder().addInterceptor(TestSession.interceptor(context)).addInterceptor { chain ->
            requests.add(chain.request())
            val path = chain.request().url.encodedPath
            val response = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Instrumented response")
            when {
                path.endsWith("/document") -> {
                    documentGate?.await(10, TimeUnit.SECONDS)
                    response.code(200).body(PDF.toResponseBody("application/pdf".toMediaType()))
                }
                path.endsWith("/reports") -> response.code(historyStatus).body(history.toResponseBody())
                else -> response.code(detailStatus).body(detail.toResponseBody())
            }.build()
        }.build()
        retrofitField.set(null, RetrofitProvider.retrofit(context.getString(R.string.api_base_url)).newBuilder().client(client).build())
    }

    @After
    fun restore() {
        documentGate?.countDown()
        scenario?.close()
        retrofitField.set(null, oldRetrofit)
        baseUrlField.set(null, oldBaseUrl)
        TestSession.clear(context)
    }

    @Test
    fun identificationAndHistoryComeFromTheEquipmentRoutes() {
        launch()
        awaitReports(3)
        assertEquals(setOf("/api/equipments/RVE", "/api/equipments/RVE/reports"), requests.map { it.url.encodedPath }.toSet())
        onActivity { activity ->
            assertEquals("TR-A · Transformador", text(activity, R.id.equipmentName))
            assertEquals(
                listOf(
                    "Número de Série" to "SN-17", "TAG" to "TR-A", "Tipo de Equipamento" to "Transformador",
                    "Tipo do Óleo" to "Mineral", "Empresa" to "Companhia de Energia", "Tensão" to "138 kV",
                    "Potência" to "20 MVA", "Fabricante" to "Não informado", "Ano de Fabricação" to "1998",
                    "Volume de Óleo" to "4500 L", "Localização" to "SE Norte",
                ),
                fields(activity),
            )
            assertEquals(View.GONE, activity.findViewById<View>(R.id.nameplateNotice).visibility)
            assertEquals("Histórico de Relatórios (3)", text(activity, R.id.historyTitle))

            val first = reports(activity).getChildAt(0)
            assertEquals("07/03/2026", text(first, R.id.reportDate))
            assertEquals("Cromatografia", text(first, R.id.analysisType))
            assertEquals("Concluído", text(first, R.id.status))
            assertTrue(first.findViewById<View>(R.id.downloadReport).isEnabled)
            val second = reports(activity).getChildAt(1)
            assertEquals("Análise Físico-Química", text(second, R.id.analysisType))
            assertEquals("Em andamento", text(second, R.id.status))
            assertEquals(context.getString(R.string.report_pdf_unavailable), text(second, R.id.downloadReport))
            assertFalse(second.findViewById<View>(R.id.downloadReport).isEnabled)
            assertTrue("a disabled button looks disabled", second.findViewById<View>(R.id.downloadReport).alpha <
                first.findViewById<View>(R.id.downloadReport).alpha)
            val third = reports(activity).getChildAt(2)
            assertEquals("the minimum date is not shown", "Não informado", text(third, R.id.reportDate))
            assertEquals("Outros ensaios", text(third, R.id.analysisType))
        }
    }

    @Test
    fun unknownNameplateIsExplainedOnceInsteadOfRepeatingThePlaceholder() {
        detail = """{"id":"RVE","serialNumber":null,"tag":"01439/2026-004","company":"Empresa","nameplateKnown":false}"""
        launch()
        awaitReports(3)
        onActivity { activity ->
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nameplateNotice).visibility)
            assertEquals(
                listOf("Número de Série" to "Não informado", "TAG" to "01439/2026-004", "Empresa" to "Empresa"),
                fields(activity),
            )
        }
    }

    @Test
    fun historyFailureKeepsTheIdentificationAndRetriesAlone() {
        historyStatus = 502
        launch()
        awaitText(R.id.historyState, R.string.equipment_history_error)
        await("identification stays visible") { check { it.findViewById<View>(R.id.content).visibility == View.VISIBLE } }
        historyStatus = 200
        val before = requests.size
        onActivity { it.findViewById<View>(R.id.historyRetry).performClick() }
        awaitReports(3)
        assertEquals("only the history is requested again", listOf("/api/equipments/RVE/reports"),
            requests.drop(before).map { it.url.encodedPath })
        onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.historyRetry).visibility) }
    }

    @Test
    fun emptyHistoryIsNotAnError() {
        history = "[]"
        launch()
        awaitText(R.id.historyState, R.string.equipment_history_empty)
        onActivity {
            assertEquals(View.GONE, it.findViewById<View>(R.id.historyRetry).visibility)
            assertEquals("Histórico de Relatórios", text(it, R.id.historyTitle))
        }
    }

    @Test
    fun detailFailuresOfferRetryOnlyWhenItCanHelp() {
        listOf(
            Triple(404, R.string.equipment_details_not_found, false),
            Triple(403, R.string.equipment_details_not_found, false),
            Triple(501, R.string.equipment_details_unavailable, false),
            Triple(502, R.string.equipment_details_error, true),
        ).forEach { (status, message, retryable) ->
            detailStatus = status
            launch()
            awaitText(R.id.stateMessage, message)
            onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.content).visibility)
                assertEquals(if (retryable) View.VISIBLE else View.GONE, it.findViewById<View>(R.id.retryButton).visibility)
            }
        }
        detailStatus = 200
        onActivity { it.findViewById<View>(R.id.retryButton).performClick() }
        awaitReports(3)
        onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.stateMessage).visibility) }
    }

    @Test
    fun downloadUsesTheEquipmentRouteAndBlocksTheOtherButtons() {
        history = """[{"id":"r-1","date":"2026-03-07","analysisType":"chromatography","status":"completed","pdfAvailable":true},
            {"id":"r-2","date":"2025-03-07","analysisType":"chromatography","status":"completed","pdfAvailable":true}]"""
        documentGate = CountDownLatch(1)
        val target = File.createTempFile("equipment-report", ".pdf", context.cacheDir)
        val picker = PickerAnswer(Uri.fromFile(target))
        instrumentation.addMonitor(picker)
        launch()
        awaitReports(2)
        onActivity { reports(it).getChildAt(0).findViewById<View>(R.id.downloadReport).performClick() }
        await("download in progress") { check {
            text(reports(it).getChildAt(0), R.id.downloadReport) == context.getString(R.string.report_downloading)
        } }
        assertEquals("relatorio-chromatography-2026-03-07.pdf", picker.suggestedName)
        onActivity { activity ->
            (0..1).forEach { assertFalse(reports(activity).getChildAt(it).findViewById<View>(R.id.downloadReport).isEnabled) }
        }
        documentGate!!.countDown()
        await("buttons released after the download") { check { activity ->
            (0..1).all { reports(activity).getChildAt(it).findViewById<View>(R.id.downloadReport).isEnabled }
        } }
        instrumentation.removeMonitor(picker)
        assertTrue(requests.any { it.url.encodedPath == "/api/equipments/RVE/reports/r-1/document" })
        assertArrayEquals(PDF.toByteArray(), target.readBytes())
        target.delete()
    }

    @Test
    fun recreationRestoresTheScrollAfterBothRequests() {
        history = (1..20).joinToString(",", "[", "]") {
            """{"id":"r-$it","date":"2026-03-07","analysisType":"chromatography","status":"completed","pdfAvailable":true}"""
        }
        launch()
        awaitReports(20)
        onActivity { it.findViewById<NestedScrollView>(R.id.equipmentDetailsScroll).scrollTo(0, 600) }
        scenario!!.recreate()
        awaitReports(20)
        await("scroll restored") { check { it.findViewById<View>(R.id.equipmentDetailsScroll).scrollY == 600 } }
    }

    @Test
    fun backLeavesTheScreen() {
        launch()
        awaitReports(3)
        onActivity {
            it.findViewById<View>(R.id.backButton).performClick()
            assertTrue(it.isFinishing)
        }
    }

    @Test
    fun unauthorizedClearsSessionAndReturnsToLogin() {
        detailStatus = 401
        historyStatus = 401
        launch()
        await("login shown after unauthorized response") { TestSession.loginVisible() }
        assertNull(context.portalApplication.sessions.current)
    }

    @Test
    fun compactLargeTextIsNeverClipped() {
        val originalFontScale = shell("settings get system font_scale").trim()
        try {
            shell("settings put system font_scale 1.5")
            launch()
            awaitReports(3)
            await("enlarged text applied") { check { it.resources.configuration.fontScale == 1.5f } }
            onActivity { activity ->
                val root = activity.findViewById<View>(R.id.equipmentDetailsRoot)
                root.layoutParams = root.layoutParams.apply { width = dp(320) }
            }
            await("320dp viewport") { check { it.findViewById<View>(R.id.equipmentDetailsRoot).width == dp(320) } }
            onActivity { activity ->
                val rows = activity.findViewById<ViewGroup>(R.id.identificationFields)
                (0 until rows.childCount).forEach { assertEquals("one column", 1, (rows.getChildAt(it) as ViewGroup).childCount) }
                texts(activity.findViewById(R.id.content)).filter { it.isShown }.forEach { view ->
                    val layout = view.layout
                    assertTrue("\"${view.text}\" has no ellipsis", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
                    assertTrue("\"${view.text}\" is not cut", view.height >= layout.height)
                }
                (0..2).forEach { row ->
                    assertEquals("date or placeholder never splits", 1, reports(activity).getChildAt(row).findViewById<TextView>(R.id.reportDate).lineCount)
                }
                val button = reports(activity).getChildAt(0).findViewById<View>(R.id.downloadReport)
                assertTrue(button.width >= dp(48) && button.height >= dp(48))
                val scroll = activity.findViewById<NestedScrollView>(R.id.equipmentDetailsScroll)
                scroll.scrollTo(0, scroll.getChildAt(0).height)
            }
            await("last report reachable") { check { activity ->
                val button = reports(activity).getChildAt(2).findViewById<View>(R.id.downloadReport)
                val visible = Rect()
                button.getLocalVisibleRect(visible) && visible.height() == button.height
            } }
        } finally {
            if (originalFontScale == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $originalFontScale")
        }
    }

    private fun launch() {
        scenario?.close()
        TestSession.start(context, "equipment-session")
        scenario = ActivityScenario.launch(Intent(context, EquipmentDetailsActivity::class.java)
            .putExtra(EquipmentDetailsActivity.EXTRA_EQUIPMENT_ID, "RVE"))
    }

    private fun reports(activity: Activity) = activity.findViewById<ViewGroup>(R.id.reportsList)
    private fun text(activity: Activity, id: Int) = activity.findViewById<TextView>(id).text.toString()
    private fun text(view: View, id: Int) = view.findViewById<TextView>(id).text.toString()
    private fun fields(activity: Activity): List<Pair<String, String>> {
        val rows = activity.findViewById<ViewGroup>(R.id.identificationFields)
        return (0 until rows.childCount).map { rows.getChildAt(it) as ViewGroup }
            .flatMap { row -> (0 until row.childCount).map(row::getChildAt) }
            .filterIsInstance<ViewGroup>()
            .map { text(it, R.id.label) to text(it, R.id.value) }
    }
    private fun texts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun onActivity(action: (EquipmentDetailsActivity) -> Unit) = scenario!!.onActivity(action)
    private fun check(condition: (EquipmentDetailsActivity) -> Boolean): Boolean {
        var result = false
        onActivity { result = condition(it) }
        return result
    }
    private fun awaitReports(count: Int) = await("$count reports and the identification") { check {
        reports(it).childCount == count && it.findViewById<View>(R.id.content).visibility == View.VISIBLE
    } }
    private fun awaitText(id: Int, message: Int) = await("message ${context.getString(message)}") { check {
        it.findViewById<View>(id).visibility == View.VISIBLE && text(it, id) == context.getString(message)
    } }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(message, condition())
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Answers the system file picker with a file of the test, recording the suggested name. */
    private class PickerAnswer(private val target: Uri) : Instrumentation.ActivityMonitor() {
        @Volatile var suggestedName: String? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_CREATE_DOCUMENT) return null
            suggestedName = intent.getStringExtra(Intent.EXTRA_TITLE)
            return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(target))
        }
    }

    private companion object {
        const val PDF = "%PDF-1.7\nlaudo"
        const val DETAIL = """{"id":"RVE","serialNumber":"SN-17","tag":"SN-17","equipmentTag":"TR-A","type":"Transformador",
            "location":"SE Norte","company":"Companhia de Energia","oilType":"Mineral","voltage":"138 kV","power":"20 MVA",
            "manufacturer":null,"manufacturingYear":"1998","oilVolume":"4500 L","nameplateKnown":true}"""
        const val HISTORY = """[
            {"id":"r-1","date":"2026-03-07","analysisType":"chromatography","status":"completed","pdfAvailable":true},
            {"id":"r-2","date":"2025-03-07","analysisType":"physical-chemical","status":"in-progress","pdfAvailable":false},
            {"id":"r-3","date":"0001-01-01","analysisType":"other","status":"completed","hasSignedDocument":true}
        ]"""
    }
}
