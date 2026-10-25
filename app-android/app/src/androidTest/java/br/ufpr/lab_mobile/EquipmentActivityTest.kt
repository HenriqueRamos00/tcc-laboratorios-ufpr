package br.ufpr.lab_mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import br.ufpr.lab_mobile.controller.EquipmentActivity
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.service.RetrofitProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EquipmentActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private val baseUrlField = RetrofitProvider::class.java.getDeclaredField("currentBaseUrl").apply { isAccessible = true }
    private var oldRetrofit: Any? = null
    private var oldBaseUrl: Any? = null
    private var scenario: ActivityScenario<EquipmentActivity>? = null
    private val requests = ConcurrentLinkedQueue<Request>()
    @Volatile private var body = FIXTURE
    @Volatile private var status = 200
    @Volatile private var offline = false
    @Volatile private var responseGate: CountDownLatch? = null

    @Before
    fun installResponses() {
        LoginActivity.clearSession(context)
        oldRetrofit = retrofitField.get(null)
        oldBaseUrl = baseUrlField.get(null)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            responseGate?.await(10, TimeUnit.SECONDS)
            if (offline) throw IOException("Offline test")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("Instrumented response").body(body.toResponseBody()).build()
        }.build()
        retrofitField.set(null, RetrofitProvider.retrofit(context.getString(R.string.api_base_url)).newBuilder().client(client).build())
    }

    @After
    fun restore() {
        responseGate?.countDown()
        scenario?.close()
        retrofitField.set(null, oldRetrofit)
        baseUrlField.set(null, oldBaseUrl)
        LoginActivity.clearSession(context)
    }

    @Test
    fun loadingContentPlaceholdersAndLocalSearch() {
        responseGate = CountDownLatch(1)
        launch()
        await("loading visible") { check { it.findViewById<View>(R.id.loading)?.visibility == View.VISIBLE } }
        responseGate!!.countDown()
        awaitItems(3)
        assertEquals("Bearer equipment-session", requests.single().header("Authorization"))
        assertEquals("/api/equipments", requests.single().url.encodedPath)
        onActivity { activity ->
            val card = list(activity).findViewHolderForAdapterPosition(1)!!.itemView
            assertEquals("SN-17", card.findViewById<TextView>(R.id.serialNumber).text.toString())
            assertEquals("TR-A", card.findViewById<TextView>(R.id.tag).text.toString())
            assertEquals(context.getString(R.string.equipment_not_informed), card.findViewById<TextView>(R.id.equipmentType).text.toString())
            assertEquals(context.getString(R.string.equipment_not_informed), card.findViewById<TextView>(R.id.location).text.toString())
        }
        setSearch("  sn-17 ")
        awaitItems(1)
        setSearch(" tr-b ")
        awaitItems(1)
        onActivity { activity ->
            val card = list(activity).findViewHolderForAdapterPosition(1)!!.itemView
            assertEquals("TR-B", card.findViewById<TextView>(R.id.tag).text.toString())
            listOf(R.id.serialNumber, R.id.equipmentType, R.id.location).forEach {
                assertEquals(context.getString(R.string.equipment_not_informed), card.findViewById<TextView>(it).text.toString())
            }
        }
        setSearch("no matching equipment")
        awaitState(R.string.equipment_no_matches)
        awaitItems(0)
        setSearch("   ")
        awaitItems(3)
        assertEquals("search never fetches again", 1, requests.size)
    }

    @Test
    fun missingOrBlankCardValuesAllUseThePlaceholder() {
        body = """[{"id":"blank","serialNumber":" ","tag":"","type":null}]"""
        launch()
        awaitItems(1)
        onActivity { activity ->
            val card = list(activity).findViewHolderForAdapterPosition(1)!!.itemView
            listOf(R.id.serialNumber, R.id.tag, R.id.equipmentType, R.id.location).forEach {
                assertEquals(context.getString(R.string.equipment_not_informed), card.findViewById<TextView>(it).text.toString())
            }
        }
    }

    @Test
    fun emptyServerResponseHasDistinctEmptyState() {
        body = "[]"
        launch()
        awaitState(R.string.equipment_empty)
        awaitItems(0)
        setSearch("anything")
        awaitState(R.string.equipment_empty)
        onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.retry).visibility) }
    }

    @Test
    fun serverAndNetworkFailuresAllowRetryWithoutShowingFailedData() {
        status = 503
        launch()
        awaitState(R.string.equipment_loading_error)
        awaitItems(0)
        status = 200
        responseGate = CountDownLatch(1)
        onActivity { it.findViewById<View>(R.id.retry).performClick() }
        await("retry is loading with no equipment cards") { check {
            it.findViewById<View>(R.id.loading)?.visibility == View.VISIBLE && list(it).adapter!!.itemCount == 1
        } }
        responseGate!!.countDown()
        awaitItems(3)
        offline = true
        scenario!!.recreate()
        awaitState(R.string.equipment_loading_error)
        awaitItems(0)
        offline = false
        onActivity { it.findViewById<View>(R.id.retry).performClick() }
        awaitItems(3)
        assertEquals(4, requests.size)
    }

    @Test
    fun unauthorizedClearsSessionAndProtectedNavigation() {
        context.getSharedPreferences(LoginActivity.SESSION_PREFERENCES, 0).edit()
            .putString(LoginActivity.ACCESS_TOKEN, "equipment-session").commit()
        status = 401
        launch()
        awaitLogin()
        assertNull(context.getSharedPreferences(LoginActivity.SESSION_PREFERENCES, 0).getString(LoginActivity.ACCESS_TOKEN, null))
    }

    @Test
    fun missingAndBlankSessionsReturnToLoginWithoutRequestingEquipment() {
        listOf(null, "  ").forEach { token ->
            launch(token)
            await("Login shown") {
                var login = false
                instrumentation.runOnMainSync {
                    login = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is LoginActivity }
                }
                login
            }
            scenario?.close()
            scenario = null
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun recreationReloadsAndRestoresFilteredListPositionAndOpenDrawer() {
        body = (1..30).joinToString(prefix = "[", postfix = "]") {
            """{"id":"$it","serialNumber":"SN-$it","tag":"TR-$it"}"""
        }
        launch()
        awaitItems(30)
        setSearch("  tr- ")
        onActivity { (list(it).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(12, -20) }
        await("scrolled to equipment 12") { check { (list(it).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() == 12 } }
        var offset = 0
        onActivity {
            offset = list(it).findViewHolderForAdapterPosition(12)!!.itemView.top
            it.findViewById<View>(R.id.menuButton).performClick()
        }
        await("drawer open") { check { it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerOpen(Gravity.RIGHT) } }
        scenario!!.recreate()
        awaitItems(30)
        await("filtered list position restored after reload") { check {
            val manager = list(it).layoutManager as LinearLayoutManager
            manager.findFirstVisibleItemPosition() == 12 && manager.findViewByPosition(12)?.top == offset
        } }
        onActivity {
            assertTrue(it.findViewById<View>(R.id.navEquipment).isSelected)
            assertTrue(it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerOpen(Gravity.RIGHT))
            it.findViewById<DrawerLayout>(R.id.portalDrawer).closeDrawer(Gravity.RIGHT)
            (list(it).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
        }
        await("search restored") { check { it.findViewById<EditText>(R.id.search)?.text?.toString() == "  tr- " } }
        assertEquals(2, requests.size)
    }

    @Test
    fun compactLargeTextCardsSearchKeyboardAndDrawerStayReachable() {
        val originalFontScale = shell("settings get system font_scale").trim()
        try {
            shell("settings put system font_scale 1.5")
            launch()
            awaitItems(3)
            await("enlarged text applied") { check { it.resources.configuration.fontScale == 1.5f } }
            onActivity { activity ->
                val root = activity.findViewById<View>(R.id.equipmentRoot)
                root.layoutParams = root.layoutParams.apply { width = dp(320) }
            }
            await("320dp viewport") { check { it.findViewById<View>(R.id.equipmentRoot).width == dp(320) } }
            capture("equipment-compact-320-font-150.png")
            onActivity { activity ->
                val search = activity.findViewById<EditText>(R.id.search)
                assertEquals(context.getString(R.string.equipment_search_hint), search.hint)
                search.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
            await("keyboard appears without hiding the portal menu") { check {
                ViewCompat.getRootWindowInsets(it.findViewById(R.id.equipmentRoot))?.isVisible(WindowInsetsCompat.Type.ime()) == true
            } }
            onActivity { activity ->
                val search = activity.findViewById<EditText>(R.id.search)
                activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
                search.clearFocus()
                (list(activity).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(3, 0)
            }
            await("last card button fully reachable") { check {
                val button = list(it).findViewHolderForAdapterPosition(3)?.itemView?.findViewById<View>(R.id.viewEquipment)
                val visible = Rect()
                button != null && button.getLocalVisibleRect(visible) && visible.width() == button.width && visible.height() == button.height
            } }
            onActivity { activity ->
                val card = list(activity).findViewHolderForAdapterPosition(3)!!.itemView
                listOf(R.id.serialNumber, R.id.tag, R.id.equipmentType, R.id.location).forEach {
                    val text = card.findViewById<TextView>(it)
                    assertTrue("field text has no ellipsis", (0 until text.layout.lineCount).all { line -> text.layout.getEllipsisCount(line) == 0 })
                }
                val button = card.findViewById<View>(R.id.viewEquipment)
                assertTrue(button.width >= dp(48) && button.height >= dp(48))
            }
            capture("equipment-compact-320-font-150-card.png")
            onActivity { it.findViewById<View>(R.id.menuButton).performClick() }
            await("equipment drawer open at compact width") { check { it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerOpen(Gravity.RIGHT) } }
            onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.navEquipment).isSelected)
                listOf(R.id.navQuotes, R.id.navReports, R.id.navEquipment, R.id.logoutButton).forEach {
                    val target = activity.findViewById<View>(it)
                    assertTrue(target.width >= dp(48) && target.height >= dp(48))
                }
            }
            capture("equipment-compact-320-font-150-drawer.png")
            } finally {
            if (originalFontScale == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $originalFontScale")
        }
    }

    private fun launch(token: String? = "equipment-session") {
        seedSession(context, token)
        val intent = Intent(context, EquipmentActivity::class.java)
        token?.let { intent.putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, it) }
        scenario = ActivityScenario.launch(intent)
    }

    private fun list(activity: EquipmentActivity) = activity.findViewById<RecyclerView>(R.id.equipmentList)
    private fun onActivity(action: (EquipmentActivity) -> Unit) = scenario!!.onActivity(action)
    private fun check(condition: (EquipmentActivity) -> Boolean): Boolean {
        var result = false
        onActivity { result = condition(it) }
        return result
    }
    private fun setSearch(text: String) = onActivity { it.findViewById<EditText>(R.id.search).setText(text) }
    private fun awaitItems(count: Int) = await("$count equipment cards") { check {
        list(it).adapter!!.itemCount == count + 1 && !list(it).isComputingLayout && !list(it).hasPendingAdapterUpdates() &&
            it.findViewById<View>(R.id.loading)?.visibility != View.VISIBLE
    } }
    private fun awaitState(message: Int) = await("equipment state $message") { check {
        it.findViewById<TextView>(R.id.equipmentState)?.text == context.getString(message)
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
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        onActivity { activity ->
            val decor = activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.let(::File) ?: File(activity.getExternalFilesDir(null), "portal-verification")
            assertTrue(output.exists() || output.mkdirs())
            File(output, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private companion object {
        const val FIXTURE = """[
            {"id":"a","serialNumber":"SN-17","tag":"TR-A","type":null,"location":null},
            {"id":"b","serialNumber":null,"tag":"TR-B","type":"","location":" "},
            {"id":"c","serialNumber":"long serial number that must wrap without clipping","tag":"TR-C"}
        ]"""
    }
}
