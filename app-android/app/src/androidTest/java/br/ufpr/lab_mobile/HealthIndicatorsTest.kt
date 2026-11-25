package br.ufpr.lab_mobile

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufpr.lab_mobile.controller.HealthIndicatorsActivity
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.tabs.TabLayout
import java.util.concurrent.ConcurrentLinkedQueue
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthIndicatorsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private var oldRetrofit: Any? = null
    private var scenario: ActivityScenario<HealthIndicatorsActivity>? = null
    private val requests = ConcurrentLinkedQueue<Request>()
    @Volatile private var body = body()
    @Volatile private var status = 200

    @Before
    fun installResponses() {
        TestSession.clear(context)
        oldRetrofit = retrofitField.get(null)
        val client = OkHttpClient.Builder().addInterceptor(TestSession.interceptor(context)).addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("Instrumented response").body(body.toResponseBody()).build()
        }.build()
        retrofitField.set(null, RetrofitProvider.retrofit(context.getString(R.string.api_base_url)).newBuilder().client(client).build())
    }

    @After
    fun restore() {
        scenario?.close()
        retrofitField.set(null, oldRetrofit)
        TestSession.clear(context)
    }

    @Test
    fun gasesTabShowsTheReportHighlightsAndEveryResult() {
        launch()
        awaitContent()
        assertEquals(listOf("/api/equipments/MQ/health"), requests.map { it.url.encodedPath })
        onActivity { activity ->
            assertEquals("the detail name wins over the API tag", "TR-PLACA", text(activity, R.id.equipmentName))
            assertEquals("Normal", text(activity, R.id.classification))
            assertEquals("Análise mais recente: 23/11/2026", text(activity, R.id.analysisDate))
            assertEquals("Sem anormalidades.", text(activity, R.id.reportText))
            assertEquals(View.GONE, activity.findViewById<View>(R.id.reportHeading).visibility)
            assertEquals(
                listOf(
                    Triple("Total de Gases Combustíveis", "361 ppm", "+41 desde 10/11/2025"),
                    Triple("Hidrogênio", "25 ppm", "Sem mudança desde 10/11/2025"),
                    Triple("Acetileno", "Não detectado", "Antes: 2 ppm (10/11/2025)"),
                ),
                cards(activity, R.id.highlights),
            )
            assertEquals(
                listOf(
                    Triple("Hidrogênio H₂", "25 ppm", "Sem mudança desde 10/11/2025"),
                    Triple("Monóxido de Carbono CO", "Não realizado", ""),
                    Triple("Acetileno C₂H₂", "Não detectado", "Antes: 2 ppm (10/11/2025)"),
                    Triple("Total de Gases Combustíveis", "361 ppm", "+41 desde 10/11/2025"),
                ),
                cards(activity, R.id.results),
            )
        }
    }

    @Test
    fun oilTabShowsLimitsAndTheReportHeadingWithoutInventingAClassification() {
        launch()
        awaitContent()
        selectTab(1)
        onActivity { activity ->
            assertEquals(View.GONE, activity.findViewById<View>(R.id.classification).visibility)
            assertEquals("ENSAIOS EM ÓLEO ISOLANTE", text(activity, R.id.reportHeading))
            assertEquals(context.getString(R.string.health_report_missing), text(activity, R.id.reportText))
            assertEquals(listOf(Triple("Teor de Água (ppm m/m)", "18,4", "")), cards(activity, R.id.highlights))
            val row = activity.findViewById<ViewGroup>(R.id.results).getChildAt(0)
            assertEquals("Limite: máx. 40 · Método: NBR 10710", text(row, R.id.reference))
            val density = activity.findViewById<ViewGroup>(R.id.results).getChildAt(1)
            assertEquals("nothing registered", View.GONE, density.findViewById<View>(R.id.reference).visibility)
        }
    }

    @Test
    fun aTypeWithoutAnalysesSaysSoAndTheOtherOpensFirst() {
        body = body(gases = """{"analysisDates":[],"gases":[],"report":{"title":null,"paragraphs":[]}}""")
        launch()
        awaitContent()
        onActivity { assertEquals("opens the tab with results", 1, it.findViewById<TabLayout>(R.id.healthTabs).selectedTabPosition) }
        selectTab(0)
        onActivity { activity ->
            assertEquals(context.getString(R.string.health_no_gases), text(activity, R.id.tabState))
            assertEquals(View.GONE, activity.findViewById<View>(R.id.tabContent).visibility)
        }
    }

    @Test
    fun longReportsCollapseAndTheChoiceSurvivesRecreation() {
        body = body(paragraph = "Parecer extenso do laboratório. ".repeat(60))
        launch()
        awaitContent()
        await("toggle offered for a cut report") { check { it.findViewById<View>(R.id.reportToggle).visibility == View.VISIBLE } }
        onActivity { activity ->
            val report = activity.findViewById<TextView>(R.id.reportText)
            assertEquals(6, report.layout.lineCount)
            activity.findViewById<View>(R.id.reportToggle).performClick()
        }
        await("report expanded") { check { it.findViewById<TextView>(R.id.reportText).lineCount > 6 } }
        scenario!!.recreate()
        awaitContent()
        await("still expanded") { check { it.findViewById<TextView>(R.id.reportText).lineCount > 6 } }
        onActivity { assertEquals(context.getString(R.string.health_report_collapse), text(it, R.id.reportToggle)) }
    }

    @Test
    fun shortReportsHaveNoToggleAndTheTabSurvivesRecreation() {
        launch()
        awaitContent()
        selectTab(1)
        scenario!!.recreate()
        awaitContent()
        onActivity {
            assertEquals(1, it.findViewById<TabLayout>(R.id.healthTabs).selectedTabPosition)
            assertEquals(View.GONE, it.findViewById<View>(R.id.reportToggle).visibility)
        }
    }

    @Test
    fun noAnalysesAndFailuresHaveTheirOwnStates() {
        body = """{"tag":"TR-A","physicalChemical":{"columns":[],"tests":[]},"dissolvedGases":{"analysisDates":[],"gases":[]}}"""
        launch()
        awaitState(R.string.health_empty, retryable = false)
        body = body()
        listOf(
            Triple(404, R.string.health_empty, false),
            Triple(403, R.string.equipment_details_not_found, false),
            Triple(501, R.string.equipment_details_unavailable, false),
            Triple(502, R.string.health_error, true),
        ).forEach { (code, message, retryable) ->
            status = code
            launch()
            awaitState(message, retryable)
        }
        status = 200
        onActivity { it.findViewById<View>(R.id.retryButton).performClick() }
        awaitContent()
        onActivity {
            assertEquals(View.GONE, it.findViewById<View>(R.id.stateMessage).visibility)
            it.findViewById<View>(R.id.backButton).performClick()
            assertTrue(it.isFinishing)
        }
    }

    private fun launch() {
        scenario?.close()
        TestSession.start(context, "health-session")
        scenario = ActivityScenario.launch(Intent(context, HealthIndicatorsActivity::class.java)
            .putExtra(HealthIndicatorsActivity.EXTRA_EQUIPMENT_ID, "MQ")
            .putExtra(HealthIndicatorsActivity.EXTRA_EQUIPMENT_NAME, "TR-PLACA"))
    }

    /** Name, value and variation of each card or row, in screen order. */
    private fun cards(activity: Activity, container: Int): List<Triple<String, String, String>> =
        items(activity.findViewById(container)).map { item ->
            val variation = item.findViewById<TextView>(R.id.variation)
            Triple(text(item, R.id.name), text(item, R.id.value), if (variation.visibility == View.VISIBLE) variation.text.toString() else "")
        }

    private fun items(view: ViewGroup): List<View> = (0 until view.childCount).map(view::getChildAt).flatMap { child ->
        if (child.isScreenReaderFocusable) listOf(child) else (child as? ViewGroup)?.let(::items).orEmpty()
    }

    private fun selectTab(position: Int) = onActivity { it.findViewById<TabLayout>(R.id.healthTabs).getTabAt(position)!!.select() }
    private fun text(activity: Activity, id: Int) = activity.findViewById<TextView>(id).text.toString()
    /** Without the invisible joiner that keeps units in one line. */
    private fun text(view: View, id: Int) = view.findViewById<TextView>(id).text.toString().replace("\u2060", "")
    private fun onActivity(action: (HealthIndicatorsActivity) -> Unit) = scenario!!.onActivity(action)
    private fun check(condition: (HealthIndicatorsActivity) -> Boolean): Boolean {
        var result = false
        onActivity { result = condition(it) }
        return result
    }
    private fun awaitContent() = await("indicators visible") { check { it.findViewById<View>(R.id.content).visibility == View.VISIBLE } }
    private fun awaitState(message: Int, retryable: Boolean) {
        await("state ${context.getString(message)}") { check {
            it.findViewById<View>(R.id.stateMessage).visibility == View.VISIBLE && text(it, R.id.stateMessage) == context.getString(message)
        } }
        onActivity { assertEquals(if (retryable) View.VISIBLE else View.GONE, it.findViewById<View>(R.id.retryButton).visibility) }
    }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(message, condition())
    }

    private companion object {
        fun body(
            paragraph: String = "Sem anormalidades.",
            gases: String = """{"analysisDates":["2025-11-10","2026-11-23"],"gases":[
                {"key":"h2","name":"Hidrogênio","formula":"H₂","results":[25.0,25]},
                {"key":"co","name":"Monóxido de Carbono","formula":"CO","results":[300]},
                {"key":"c2h2","name":"Acetileno","formula":"C₂H₂","results":[2,"ND"]},
                {"key":"combustiveis","name":"Total de Gases Combustíveis","formula":"","results":[320,361]}],
                "report":{"title":"normal","paragraphs":["$paragraph"]}}""",
        ) = """{"tag":"SN-17","dissolvedGases":$gases,
            "physicalChemical":{"columns":[{"date":"2026-11-23","isCurrentResult":true}],"tests":[
                {"key":"agua","name":"Teor de Água (ppm m/m)","method":"NBR 10710","limit":"máx. 40","valuesByAnalysis":[18.4]},
                {"key":"densidade","name":"Densidade","method":"-","limit":"-","valuesByAnalysis":[0.87]}],
                "report":{"title":"ENSAIOS EM ÓLEO ISOLANTE","paragraphs":[]}}}"""
    }
}
