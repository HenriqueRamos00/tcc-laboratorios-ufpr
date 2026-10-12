package br.ufpr.lab_mobile

import android.content.Intent
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.controller.EquipmentActivity
import br.ufpr.lab_mobile.controller.QuotesActivity
import br.ufpr.lab_mobile.controller.ResultsActivity
import br.ufpr.lab_mobile.model.QuoteSimulations
import br.ufpr.lab_mobile.service.RetrofitProvider
import okhttp3.Interceptor
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
class SidebarResultsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private val baseUrlField = RetrofitProvider::class.java.getDeclaredField("currentBaseUrl").apply { isAccessible = true }
    private var oldRetrofit: Any? = null
    private var oldBaseUrl: Any? = null
    private var scenario: ActivityScenario<*>? = null

    @Before
    fun installOfflineQuotesResponse() {
        QuoteSimulations.clear()
        oldRetrofit = retrofitField.get(null)
        oldBaseUrl = baseUrlField.get(null)
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("Instrumented response").body("[]".toResponseBody()).build()
        }).build()
        retrofitField.set(null, RetrofitProvider.retrofit(context.getString(R.string.api_base_url)).newBuilder().client(client).build())
    }

    @After
    fun restore() {
        scenario?.close()
        retrofitField.set(null, oldRetrofit)
        baseUrlField.set(null, oldBaseUrl)
        QuoteSimulations.clear()
        context.getSharedPreferences(LoginActivity.SESSION_PREFERENCES, 0).edit().clear().commit()
    }

    @Test
    fun resultsShowBothFixtureRecordsDownloadNoticeAndEmptyState() {
        launchResults()
        await("both report cards shown") { checkActive { it.findViewById<ViewGroup>(R.id.reportsList).childCount == 2 } }
        onActive { activity ->
            val rows = activity.findViewById<ViewGroup>(R.id.reportsList)
            val first = rows.getChildAt(0)
            assertEquals("Relatório: LACTEC-00307/2026", first.findViewById<TextView>(R.id.reportNumber).text.toString())
            assertEquals("Protocolo: 00307/2026", first.findViewById<TextView>(R.id.protocol).text.toString())
            assertEquals("Amostra: Areia", first.findViewById<TextView>(R.id.sample).text.toString())
            assertEquals("Laboratório: Lab. de Óleos", first.findViewById<TextView>(R.id.laboratory).text.toString())
            assertEquals("Publicação: 07/03/2026", first.findViewById<TextView>(R.id.reportDate).text.toString())
            val second = rows.getChildAt(1)
            assertEquals("Relatório: LACTEC-00308/2026", second.findViewById<TextView>(R.id.reportNumber).text.toString())
            assertEquals("Protocolo: 00308/2026", second.findViewById<TextView>(R.id.protocol).text.toString())
            assertEquals("Amostra: Água", second.findViewById<TextView>(R.id.sample).text.toString())
            assertEquals("Laboratório: Lab. de Águas", second.findViewById<TextView>(R.id.laboratory).text.toString())
            assertEquals("Publicação: 08/03/2026", second.findViewById<TextView>(R.id.reportDate).text.toString())
            first.findViewById<View>(R.id.downloadReport).performClick()
        }
        await("download notice shown") {
            checkActive { it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text == context.getString(R.string.demo_download_unavailable) }
        }
        await("download notice dismissed") {
            checkActive { it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text) == null }
        }
        onActive { activity ->
            (activity as ResultsActivity).renderReports(emptyList())
            assertEquals(0, activity.findViewById<ViewGroup>(R.id.reportsList).childCount)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.reportsEmpty).visibility)
        }
    }

    @Test
    fun drawerClosesWithMenuOutsideTapAndBackOnBothScreens() {
        launchResults()
        assertDrawerState(open = false, selected = R.id.navReports)
        openDrawer(R.id.navReports)
        closeWithMenu(R.id.navReports)
        openDrawer(R.id.navReports)
        tapOutsideDrawer()
        await("results drawer closed by outside tap") { !drawerOpen() }
        assertDrawerState(open = false, selected = R.id.navReports)
        openDrawer(R.id.navReports)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("results drawer closed by back") { !drawerOpen() }
        assertTrue(activeActivity() is ResultsActivity)

        launchQuotes()
        assertDrawerState(open = false, selected = R.id.navQuotes)
        openDrawer(R.id.navQuotes)
        closeWithMenu(R.id.navQuotes)
        openDrawer(R.id.navQuotes)
        tapOutsideDrawer()
        await("quotes drawer closed by outside tap") { !drawerOpen() }
        assertDrawerState(open = false, selected = R.id.navQuotes)
        openDrawer(R.id.navQuotes)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("quotes drawer closed by back") { !drawerOpen() }
        assertTrue(activeActivity() is QuotesActivity)
    }

    @Test
    fun resultsRecreationKeepsBackClosingDrawerBeforeLeavingScreen() {
        launchResults()
        openDrawer(R.id.navReports)
        scenario!!.recreate()
        await("open results drawer restored after recreation") { activeActivity() is ResultsActivity && drawerOpen() }
        onActive { assertTrue(it.findViewById<View>(R.id.resultsScroll) != null) }
        assertDrawerState(open = true, selected = R.id.navReports)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("drawer closed by back after recreation") { !drawerOpen() }
        assertTrue("Back closes the drawer without finishing ResultsActivity", activeActivity() is ResultsActivity)
    }

    @Test
    fun repeatedNavigationPreservesOriginalQuotesSearchAndFilter() {
        launchQuotes()
        var original: QuotesActivity? = null
        onActive { activity ->
            original = activity as QuotesActivity
            activity.findViewById<Spinner>(R.id.statusFilter).setSelection(2)
            activity.findViewById<EditText>(R.id.search).setText("preserve this search")
        }

        repeat(2) {
            navigate(R.id.navQuotes, QuotesActivity::class.java)
            assertSame("Quotes on Quotes keeps the current instance", original, activeActivity())
            navigate(R.id.navReports, ResultsActivity::class.java)
            navigate(R.id.navReports, ResultsActivity::class.java)
            val reports = activeActivity()
            navigate(R.id.navEquipment, EquipmentActivity::class.java)
            assertTrue("Switching secondary destinations finishes Reports", reports!!.isFinishing || reports.isDestroyed)
            val equipment = activeActivity()
            navigate(R.id.navEquipment, EquipmentActivity::class.java)
            assertSame("Current equipment destination keeps the activity", equipment, activeActivity())
            navigate(R.id.navQuotes, QuotesActivity::class.java)
            assertTrue("Returning to Quotes finishes Equipment", equipment!!.isFinishing || equipment.isDestroyed)
            assertSame("Returning from reports reuses the original quotes activity", original, activeActivity())
            onActive { activity ->
                assertEquals("preserve this search", activity.findViewById<EditText>(R.id.search).text.toString())
                assertEquals(2, activity.findViewById<Spinner>(R.id.statusFilter).selectedItemPosition)
            }
        }
    }

    @Test
    fun equipmentBackReturnsToQuotesAndLogoutClearsSession() {
        launchQuotes()
        val quotes = activeActivity()
        navigate(R.id.navReports, ResultsActivity::class.java)
        navigate(R.id.navEquipment, EquipmentActivity::class.java)
        openDrawer(R.id.navEquipment)
        tapOutsideDrawer()
        await("equipment drawer closed by outside tap") { !drawerOpen() }
        openDrawer(R.id.navEquipment)
        closeWithMenu(R.id.navEquipment)
        openDrawer(R.id.navEquipment)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back closes equipment drawer") { !drawerOpen() }
        assertTrue(activeActivity() is EquipmentActivity)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back returns to original Quotes") { activeActivity() === quotes }
        navigate(R.id.navEquipment, EquipmentActivity::class.java)
        context.getSharedPreferences(LoginActivity.SESSION_PREFERENCES, 0).edit()
            .putString(LoginActivity.ACCESS_TOKEN, "test-session").commit()
        openDrawer(R.id.navEquipment)
        onActive { it.findViewById<View>(R.id.logoutButton).performClick() }
        await("equipment logout returns to Login") { activeActivity() is LoginActivity }
        assertNull(context.getSharedPreferences(LoginActivity.SESSION_PREFERENCES, 0).getString(LoginActivity.ACCESS_TOKEN, null))
    }

    @Test
    fun logoutClearsSavedAndSimulationSessionsAndMissingSessionOpensLogin() {
        context.getSharedPreferences(LoginActivity.SESSION_PREFERENCES, 0).edit()
            .putString(LoginActivity.ACCESS_TOKEN, "test-session").commit()
        QuoteSimulations.startSession("test-session")
        assertTrue(QuoteSimulations.isSessionActive("test-session"))
        launchResults()
        openDrawer(R.id.navReports)
        onActive { it.findViewById<View>(R.id.logoutButton).performClick() }
        await("login shown after logout") { activeActivity() is LoginActivity }
        assertNull(context.getSharedPreferences(LoginActivity.SESSION_PREFERENCES, 0).getString(LoginActivity.ACCESS_TOKEN, null))
        assertFalse(QuoteSimulations.isSessionActive("test-session"))
        scenario?.close()
        scenario = null
        launchResults(token = null)
        await("missing session redirects to login") { activeActivity() is LoginActivity }
    }

    private fun launchResults(token: String? = "test-session") {
        val intent = Intent(context, ResultsActivity::class.java)
        token?.let { intent.putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, it) }
        scenario = ActivityScenario.launch<ResultsActivity>(intent)
    }

    private fun launchQuotes() {
        scenario?.close()
        scenario = ActivityScenario.launch<QuotesActivity>(
            Intent(context, QuotesActivity::class.java).putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "test-session"),
        )
    }

    private fun navigate(itemId: Int, destination: Class<*>) {
        openDrawer(when (activeActivity()) {
            is ResultsActivity -> R.id.navReports
            is EquipmentActivity -> R.id.navEquipment
            else -> R.id.navQuotes
        })
        onActive {
            it.findViewById<View>(itemId).performClick()
            it.findViewById<View>(itemId).performClick()
        }
        await("navigation completed") { activeActivity()?.javaClass == destination && !drawerOpen() }
    }

    private fun openDrawer(selected: Int) {
        onActive { it.findViewById<View>(R.id.menuButton).performClick() }
        await("drawer fully opened") { drawerOpen() }
        assertDrawerState(open = true, selected = selected)
    }

    private fun closeWithMenu(selected: Int) {
        onActive { it.findViewById<View>(R.id.menuButton).performClick() }
        await("drawer fully closed by menu") { !drawerOpen() }
        assertDrawerState(open = false, selected = selected)
    }

    private fun tapOutsideDrawer() {
        var point = floatArrayOf(0f, 0f)
        onActive { activity ->
            val drawer = activity.findViewById<DrawerLayout>(R.id.portalDrawer)
            val location = IntArray(2)
            drawer.getLocationOnScreen(location)
            point = floatArrayOf(location[0] + drawer.width / 4f, location[1] + drawer.height / 2f)
        }
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, point[0], point[1], 0)
        val up = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, point[0], point[1], 0)
        instrumentation.sendPointerSync(down)
        instrumentation.sendPointerSync(up)
        down.recycle()
        up.recycle()
    }

    private fun drawerOpen(): Boolean {
        var open = false
        onActive { open = it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerOpen(Gravity.RIGHT) }
        return open
    }

    private fun assertDrawerState(open: Boolean, selected: Int) {
        onActive { activity ->
            val menu = activity.findViewById<View>(R.id.menuButton)
            assertEquals(context.getString(if (open) R.string.menu_close else R.string.menu_open), menu.contentDescription)
            assertTrue(activity.findViewById<View>(selected).isSelected)
            listOf(R.id.navQuotes, R.id.navReports, R.id.navEquipment).forEach {
                assertEquals("only the current destination is selected", it == selected, activity.findViewById<View>(it).isSelected)
            }
        }
    }

    private fun activeActivity(): androidx.appcompat.app.AppCompatActivity? {
        var found: androidx.appcompat.app.AppCompatActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull() as? androidx.appcompat.app.AppCompatActivity
        }
        return found
    }

    private fun onActive(action: (androidx.appcompat.app.AppCompatActivity) -> Unit) {
        val activity = activeActivity()
        assertNotNull("Expected a resumed portal Activity", activity)
        instrumentation.runOnMainSync { action(activity!!) }
    }

    private fun checkActive(condition: (androidx.appcompat.app.AppCompatActivity) -> Boolean): Boolean {
        var result = false
        onActive { result = condition(it) }
        return result
    }

    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(message, condition())
    }
}
