package br.ufpr.lab_mobile

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.controller.QuotesActivity
import br.ufpr.lab_mobile.controller.ResultsActivity
import br.ufpr.lab_mobile.service.RetrofitProvider
import java.io.File
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
class PortalResponsiveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private val baseUrlField = RetrofitProvider::class.java.getDeclaredField("currentBaseUrl").apply { isAccessible = true }
    private var oldRetrofit: Any? = null
    private var oldBaseUrl: Any? = null
    private var scenario: ActivityScenario<*>? = null

    @Before
    fun useOfflineQuotes() {
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
        LoginActivity.clearSession(context)
    }

    @Test
    fun portalLayoutsTargetsScrollingAndPhysicalRotation() {
        val originalFontScale = shell("settings get system font_scale").trim()
        assertTrue("Device returned a numeric font scale", originalFontScale.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
        var fontScaleChanged = false
        var originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        try {
            launchQuotes()
            assertPortalChrome(R.id.quotesRoot, R.id.navQuotes)
            openDrawer(R.id.navQuotes)
            assertDrawer(R.id.navQuotes)
            capture("quotes-drawer.png")
            closeDrawer()
            scenario?.close()
            scenario = null

            launchResults()
            onActive { activity ->
                originalRequestedOrientation = activity.requestedOrientation
            }
            assertPortalChrome(R.id.resultsRoot, R.id.navReports)
            onActive { activity ->
                assertTrue(activity.findViewById<View>(R.id.resultsScroll) is androidx.core.widget.NestedScrollView)
                assertMinimumTarget(activity, R.id.menuButton)
                val first = activity.findViewById<ViewGroup>(R.id.reportsList).getChildAt(0)
                val second = activity.findViewById<ViewGroup>(R.id.reportsList).getChildAt(1)
                assertEquals(context.getString(R.string.demo_report_download, "LACTEC-00307/2026"),
                    first.findViewById<View>(R.id.downloadReport).contentDescription)
                assertEquals(context.getString(R.string.demo_report_download, "LACTEC-00308/2026"),
                    second.findViewById<View>(R.id.downloadReport).contentDescription)
                assertMinimumTarget(first, R.id.downloadReport)
                assertMinimumTarget(second, R.id.downloadReport)
            }
            openDrawer(R.id.navReports)
            assertDrawer(R.id.navReports)
            capture("results-drawer.png")
            closeDrawer()

            onActive { it.findViewById<NestedScrollView>(R.id.resultsScroll).scrollTo(0, 0) }
            capture("results-top.png")
            onActive { it.findViewById<NestedScrollView>(R.id.resultsScroll).fullScroll(View.FOCUS_DOWN) }
            await("second report and download fully visible at scroll end") { checkResultsScrollEnd() }
            capture("results-bottom.png")

            setResultsRootWidth(320)
            fontScaleChanged = true
            shell("settings put system font_scale 1.5")
            await("font scale applied") { checkActiveOrNull { it.resources.configuration.fontScale == 1.5f } }
            scenario!!.recreate()
            await("results recreated at larger font scale") { checkActiveOrNull { it is ResultsActivity && it.resources.configuration.fontScale == 1.5f } }
            setResultsRootWidth(320)
            await("results root constrained to 320dp") { checkActiveOrNull { it.findViewById<View>(R.id.resultsRoot).width == dp(320) } }
            onActive { it.findViewById<NestedScrollView>(R.id.resultsScroll).fullScroll(View.FOCUS_DOWN) }
            await("last report remains fully visible at compact width") { checkResultsScrollEnd() }
            assertCompactResults()
            capture("compact-320-font-150-results.png")
            openDrawer(R.id.navReports)
            assertDrawer(R.id.navReports)
            capture("compact-320-font-150-drawer.png")
            closeDrawer()

            restoreFontScale(originalFontScale)
            fontScaleChanged = false
            scenario!!.recreate()
            await("default font scale restored") { checkActiveOrNull { it.resources.configuration.fontScale == originalFontScale.toFloat() } }
            setResultsRootWidth(null)

            onActive { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            await("device rotated to landscape") {
                checkActiveOrNull {
                    it is ResultsActivity && it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                        it.window.decorView.width > it.window.decorView.height
                }
            }
            assertPortalChrome(R.id.resultsRoot, R.id.navReports)
            onActive { it.findViewById<NestedScrollView>(R.id.resultsScroll).fullScroll(View.FOCUS_DOWN) }
            await("landscape report two fully visible") { checkResultsScrollEnd() }
            capture("landscape-results.png")
            onActive { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            await("device rotated back to portrait") {
                checkActiveOrNull {
                    it is ResultsActivity && it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
                        it.window.decorView.height > it.window.decorView.width
                }
            }
        } finally {
            if (fontScaleChanged) restoreFontScale(originalFontScale)
            activeActivityOrNull()?.let { activity ->
                instrumentation.runOnMainSync { activity.requestedOrientation = originalRequestedOrientation }
            }
        }
    }

    private fun launchQuotes() {
        seedSession(context, "responsive-test-session")
        scenario = ActivityScenario.launch<QuotesActivity>(
            Intent(context, QuotesActivity::class.java).putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "responsive-test-session"),
        )
    }

    private fun launchResults() {
        seedSession(context, "responsive-test-session")
        scenario = ActivityScenario.launch<ResultsActivity>(
            Intent(context, ResultsActivity::class.java).putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "responsive-test-session"),
        )
    }

    private fun assertPortalChrome(rootId: Int, selectedId: Int) = onActive { activity ->
        val root = activity.findViewById<View>(rootId)
        val headerButton = activity.findViewById<View>(R.id.menuButton)
        val drawer = activity.findViewById<DrawerLayout>(R.id.portalDrawer)
        val rootRect = rect(root)
        val headerRect = rect(headerButton)
        val drawerRect = rect(drawer)
        val insets = ViewCompat.getRootWindowInsets(root)
        assertTrue("drawer starts below the portal header", drawerRect.top >= headerRect.bottom)
        assertNotNull("root receives window insets", insets)
        val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        assertTrue("content root applies the left safe inset", root.paddingLeft >= maxOf(systemBars.left, cutout.left))
        assertTrue("content root applies the top safe inset", root.paddingTop >= maxOf(systemBars.top, cutout.top))
        assertTrue("content root applies the right safe inset", root.paddingRight >= maxOf(systemBars.right, cutout.right))
        assertTrue("content root applies the bottom safe inset", root.paddingBottom >= maxOf(systemBars.bottom, cutout.bottom))
        assertTrue(activity.findViewById<View>(selectedId).isSelected)
        listOf(R.id.navQuotes, R.id.navReports, R.id.navEquipment).forEach {
            assertEquals("only the current destination is selected", it == selectedId, activity.findViewById<View>(it).isSelected)
        }
        assertEquals(context.getString(R.string.menu_open), headerButton.contentDescription)
        assertMinimumTarget(activity, R.id.menuButton)
        assertTrue(rootRect.width() >= dp(320))
    }

    private fun assertDrawer(selectedId: Int) = onActive { activity ->
        val drawer = activity.findViewById<DrawerLayout>(R.id.portalDrawer)
        val panel = activity.findViewById<View>(R.id.drawerPanel)
        val drawerRect = rect(drawer)
        val panelRect = rect(panel)
        assertTrue("drawer panel is aligned to the right edge", kotlin.math.abs(drawerRect.right - panelRect.right) <= 1)
        assertTrue("drawer panel opens on the right", panelRect.left > drawerRect.left)
        assertEquals(context.getString(R.string.menu_close), activity.findViewById<View>(R.id.menuButton).contentDescription)
        assertTrue(activity.findViewById<View>(selectedId).isSelected)
        listOf(R.id.navQuotes, R.id.navReports, R.id.navEquipment, R.id.logoutButton)
            .forEach { assertMinimumTarget(activity, it) }
        assertTrue(activity.findViewById<View>(selectedId).contentDescription?.isNotBlank() == true ||
            (activity.findViewById<View>(selectedId) as? TextView)?.text?.isNotBlank() == true)
    }

    private fun assertCompactResults() = onActive { activity ->
        val root = activity.findViewById<View>(R.id.resultsRoot)
        assertEquals("root constrained to a 320dp viewport", dp(320), root.width)
        val scroll = activity.findViewById<NestedScrollView>(R.id.resultsScroll)
        scroll.fullScroll(View.FOCUS_DOWN)
        val rows = activity.findViewById<ViewGroup>(R.id.reportsList)
        assertEquals(2, rows.childCount)
        val last = rows.getChildAt(1)
        val date = last.findViewById<TextView>(R.id.reportDate)
        assertTrue("publication date keeps a visible text line", date.lineCount >= 1 && date.height >= date.lineHeight)
        assertTrue("download target stays at least 48dp", targetSize(last.findViewById(R.id.downloadReport), 48))
    }

    private fun checkResultsScrollEnd(): Boolean {
        var visible = false
        onActive { activity ->
            val scroll = activity.findViewById<NestedScrollView>(R.id.resultsScroll)
            val last = activity.findViewById<ViewGroup>(R.id.reportsList).getChildAt(1)
            val download = last.findViewById<View>(R.id.downloadReport)
            val viewport = Rect()
            val visibleTarget = Rect()
            val target = rect(download)
            scroll.getGlobalVisibleRect(viewport)
            visible = download.getGlobalVisibleRect(visibleTarget) && visibleTarget == target && viewport.contains(target)
        }
        return visible
    }

    private fun setResultsRootWidth(widthDp: Int?) = onActive { activity ->
        val root = activity.findViewById<View>(R.id.resultsRoot)
        root.layoutParams = root.layoutParams.apply { width = widthDp?.let(::dp) ?: ViewGroup.LayoutParams.MATCH_PARENT }
        root.requestLayout()
    }

    private fun openDrawer(selectedId: Int) {
        onActive {
            assertFalse("drawer must be closed before the menu click", it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerVisible(Gravity.RIGHT))
            it.findViewById<View>(R.id.menuButton).performClick()
        }
        await("drawer fully open") { checkActive { it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerOpen(Gravity.RIGHT) } }
        assertDrawer(selectedId)
    }

    private fun closeDrawer() {
        onActive { it.findViewById<View>(R.id.menuButton).performClick() }
        await("drawer fully closed") { checkActive { !it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerOpen(Gravity.RIGHT) } }
    }

    private fun assertMinimumTarget(activity: android.app.Activity, viewId: Int) =
        assertTrue("view $viewId has a 48dp touch target", targetSize(activity.findViewById(viewId), 48))

    private fun assertMinimumTarget(parent: View, viewId: Int) =
        assertTrue("view $viewId has a 48dp touch target", targetSize(parent.findViewById(viewId), 48))

    private fun targetSize(view: View, minDp: Int): Boolean {
        val minPx = minDp * context.resources.displayMetrics.density
        return view.width >= minPx - 1 && view.height >= minPx - 1
    }

    private fun rect(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        onActive { activity ->
            val decor = activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val testOutputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            val folder = testOutputDir?.let(::File) ?: File(activity.getExternalFilesDir(null), "portal-verification")
            assertTrue("verification screenshot directory is writable", folder.exists() || folder.mkdirs())
            assertTrue("verification screenshot is encoded", File(folder, "portal-$name").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            })
            bitmap.recycle()
        }
    }

    private fun restoreFontScale(value: String) {
        if (value == "null") shell("settings delete system font_scale")
        else shell("settings put system font_scale $value")
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun activeActivity(): androidx.appcompat.app.AppCompatActivity {
        var found: androidx.appcompat.app.AppCompatActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull() as? androidx.appcompat.app.AppCompatActivity
        }
        assertNotNull("Expected a resumed portal Activity", found)
        return found!!
    }

    private fun activeActivityOrNull(): androidx.appcompat.app.AppCompatActivity? {
        var found: androidx.appcompat.app.AppCompatActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull() as? androidx.appcompat.app.AppCompatActivity
        }
        return found
    }

    private fun onActive(action: (androidx.appcompat.app.AppCompatActivity) -> Unit) {
        val activity = activeActivity()
        instrumentation.runOnMainSync { action(activity) }
    }

    private fun checkActive(condition: (androidx.appcompat.app.AppCompatActivity) -> Boolean): Boolean {
        var result = false
        onActive { result = condition(it) }
        return result
    }

    private fun checkActiveOrNull(condition: (androidx.appcompat.app.AppCompatActivity) -> Boolean): Boolean {
        var result = false
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull() as? androidx.appcompat.app.AppCompatActivity
            result = activity?.let(condition) ?: false
        }
        return result
    }

    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(message, condition())
    }
}
