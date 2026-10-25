package br.ufpr.lab_mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.CheckBox
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import br.ufpr.lab_mobile.controller.EquipmentActivity
import br.ufpr.lab_mobile.controller.EquipmentDetailsActivity
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.controller.QuotesActivity
import br.ufpr.lab_mobile.controller.ResultsActivity
import br.ufpr.lab_mobile.service.RetrofitProvider
import br.ufpr.lab_mobile.service.SessionStore
import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.LoginResponse
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
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
class PortalLoginAndDetailsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val retrofitField = RetrofitProvider::class.java.getDeclaredField("retrofit").apply { isAccessible = true }
    private val baseUrlField = RetrofitProvider::class.java.getDeclaredField("currentBaseUrl").apply { isAccessible = true }
    private var previousRetrofit: Any? = null
    private var previousBaseUrl: Any? = null
    private var scenario: ActivityScenario<out Activity>? = null
    private val requests = CopyOnWriteArrayList<Request>()
    @Volatile private var loginCode = 200
    @Volatile private var loginBody = LOGIN
    @Volatile private var meCode = 200
    @Volatile private var detailCode = 200
    @Volatile private var historyCode = 200
    @Volatile private var history = HISTORY
    @Volatile private var equipmentList = LIST
    @Volatile private var offlineMe = false
    @Volatile private var offlineLogin = false
    @Volatile private var loginGate: CountDownLatch? = null
    @Volatile private var detailGate: CountDownLatch? = null

    @Before fun installApi() {
        SessionStore.get(context).clear()
        previousRetrofit = retrofitField.get(null)
        previousBaseUrl = baseUrlField.get(null)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            val path = request.url.encodedPath
            if (path == "/auth/login") loginGate?.await(10, TimeUnit.SECONDS)
            if (path == "/api/equipments/opaque%2Fid%252F") detailGate?.await(10, TimeUnit.SECONDS)
            if (path == "/auth/me" && offlineMe) throw IOException("Offline test")
            if (path == "/auth/login" && offlineLogin) throw IOException("Offline test")
            val (code, body) = when (path) {
                "/auth/login" -> loginCode to loginBody
                "/auth/me" -> meCode to USER
                "/api/quotes" -> 200 to "[]"
                "/api/equipments" -> 200 to equipmentList
                "/api/equipments/opaque%2Fid%252F" -> detailCode to DETAIL
                "/api/equipments/opaque%2Fid%252F/reports" -> historyCode to history
                else -> 404 to "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test response")
                .header("Retry-After", "2").body(body.toResponseBody()).build()
        }.build()
        retrofitField.set(null, RetrofitProvider.retrofit(context.getString(R.string.api_base_url)).newBuilder().client(client).build())
    }

    @After fun cleanup() {
        loginGate?.countDown()
        detailGate?.countDown()
        instrumentation.runOnMainSync {
            Stage.values().filter { it != Stage.DESTROYED }.flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
            }.distinct().forEach { it.finish() }
        }
        scenario?.close()
        SessionStore.get(context).clear()
        retrofitField.set(null, previousRetrofit)
        baseUrlField.set(null, previousBaseUrl)
    }

    @Test fun loginValidatesThenSurvivesRotationWithoutDuplicateSubmission() {
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        onActive { it.findViewById<View>(R.id.loginButton).performClick() }
        assertTrue(check { it.findViewById<TextInputLayout>(R.id.emailLayout).error == context.getString(R.string.required_email) })
        assertTrue(check { it.findViewById<TextInputLayout>(R.id.passwordLayout).error == context.getString(R.string.required_password) })
        assertTrue(requests.isEmpty())
        loginGate = CountDownLatch(1)
        fillLogin()
        onActive {
            it.findViewById<View>(R.id.loginButton).performClick()
            it.findViewById<View>(R.id.loginButton).performClick()
        }
        await("single login submitted") { requests.count { it.url.encodedPath == "/auth/login" } == 1 }
        scenario!!.recreate()
        assertTrue(check { !it.findViewById<View>(R.id.loginButton).isEnabled })
        loginGate!!.countDown()
        await("quotes opened") { resumedActivity() is QuotesActivity }
        assertTrue(check { it.findViewById<TextView>(R.id.quotes_greeting).text.toString().contains("Maria") })
        assertEquals(1, requests.count { it.url.encodedPath == "/auth/login" })
        assertNotNull(SessionStore.get(context).current)
    }

    @Test fun rejectedResponsesMalformedSuccessAndCooldownStayOnLogin() {
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        fillLogin()
        listOf(400 to R.string.login_bad_request, 401 to R.string.invalid_credentials, 502 to R.string.login_service_unavailable).forEach { (code, message) ->
            loginCode = code
            submit()
            awaitText(R.id.errorMessage, message)
            assertNull(SessionStore.get(context).current)
        }
        offlineLogin = true
        submit()
        awaitText(R.id.errorMessage, R.string.login_loading_error)
        offlineLogin = false
        loginCode = 200
        loginBody = """{"Token":"token","tokenType":"Bearer"}"""
        submit()
        awaitText(R.id.errorMessage, R.string.login_invalid_response)
        loginCode = 429
        submit()
        await("cooldown blocks submission") { check { !it.findViewById<View>(R.id.loginButton).isEnabled && it.findViewById<View>(R.id.errorCard).isShown } }
        val count = requests.size
        onActive { it.findViewById<View>(R.id.loginButton).performClick() }
        scenario!!.recreate()
        assertEquals(count, requests.size)
        await("cooldown ends") { check { it.findViewById<View>(R.id.loginButton).isEnabled } }
        assertTrue(resumedActivity() is LoginActivity)
    }

    @Test fun uncheckedRememberKeepsSessionOnlyInMemoryAndMissingNameUsesCliente() {
        loginBody = LOGIN.replace("\"name\":\"Maria\",", "")
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        fillLogin()
        onActive { it.findViewById<CheckBox>(R.id.keepConnected).isChecked = false }
        submit()
        await("authenticated greeting with fallback") {
            resumedActivity() is QuotesActivity && check { it.findViewById<TextView>(R.id.quotes_greeting).text == context.getString(R.string.portal_greeting, "Cliente") }
        }
        assertNotNull(SessionStore.get(context).current)
        assertNull(SessionStore.get(context).rememberCandidate())
    }

    @Test fun expiredRememberedSessionNeverOpensProtectedContent() {
        seedSession(context, "portal-token")
        context.getSharedPreferences(SessionStore.PREFERENCES, 0).edit().putLong("expires_at", 1).commit()
        forgetVerifiedSession()
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        assertTrue(resumedActivity() is LoginActivity)
        assertTrue(requests.isEmpty())
        assertNull(SessionStore.get(context).rememberCandidate())
    }

    @Test fun foregroundExpiryReturnsToLoginAndRemovesProtectedHistory() {
        SessionStore.get(context).accept(LoginResponse("portal-token", "Bearer", 2,
            AuthenticatedUser("u1", "Maria", "cliente@empresa.com", "client")), true)
        scenario = ActivityScenario.launch(Intent(context, EquipmentDetailsActivity::class.java)
            .putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "portal-token").putExtra(EquipmentDetailsActivity.EXTRA_EQUIPMENT_ID, "opaque/id%2F"))
        awaitLogin()
        assertNull(SessionStore.get(context).current)
        assertNull(SessionStore.get(context).rememberCandidate())
        instrumentation.runOnMainSync {
            assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED).none { it is EquipmentDetailsActivity })
        }
    }

    @Test fun rememberedSessionRequiresMeAndNetworkRetryRetainsCredentials() {
        seedSession(context, "portal-token")
        forgetVerifiedSession()
        offlineMe = true
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        awaitText(R.id.errorMessage, R.string.session_restore_error)
        assertTrue(resumedActivity() is LoginActivity)
        assertNull(SessionStore.get(context).current)
        assertNotNull(SessionStore.get(context).rememberCandidate())
        assertTrue(requests.none { it.url.encodedPath.startsWith("/api/") })
        offlineMe = false
        onActive { it.findViewById<View>(R.id.restoreRetry).performClick() }
        await("verified session opens quotes") { resumedActivity() is QuotesActivity }
        assertEquals(2, requests.count { it.url.encodedPath == "/auth/me" })
        assertEquals("Bearer portal-token", requests.first().header("Authorization"))
    }

    @Test fun rejectedRememberedSessionIsClearedAndPlaceholderNeverOpensContent() {
        seedSession(context, "portal-token")
        forgetVerifiedSession()
        meCode = 401
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        awaitText(R.id.errorMessage, R.string.session_expired)
        assertNull(SessionStore.get(context).rememberCandidate())
        assertNull(SessionStore.get(context).current)
        scenario!!.close()
        context.getSharedPreferences(SessionStore.PREFERENCES, 0).edit().putString(SessionStore.ACCESS_TOKEN, "legacy-placeholder").commit()
        requests.clear()
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        assertTrue(resumedActivity() is LoginActivity)
        assertTrue(requests.isEmpty())
        assertNull(SessionStore.get(context).rememberCandidate())
    }

    @Test fun listToDetailsPaginationRotationDisabledDownloadBackAndDrawer() {
        equipmentList = (listOf(DETAIL) + (1..20).map {
            """{"id":"other-$it","serialNumber":"SN-$it","tag":"TR-A-$it"}"""
        }).joinToString(prefix = "[", postfix = "]")
        launchList(expectedCount = 21)
        onActive { it.findViewById<EditText>(R.id.search).setText(" TR-A ") }
        await("filtered scrollable list") { check {
            val list = it.findViewById<RecyclerView>(R.id.equipmentList)
            list.adapter!!.itemCount == 22 && list.canScrollVertically(1)
        } }
        onActive { (it.findViewById<RecyclerView>(R.id.equipmentList).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, -20) }
        await("list position before navigation") { check { it.findViewById<RecyclerView>(R.id.equipmentList).findViewHolderForAdapterPosition(0)?.itemView?.top == -20 + dp(14) } }
        var listTop = 0
        onActive { listTop = it.findViewById<RecyclerView>(R.id.equipmentList).findViewHolderForAdapterPosition(0)!!.itemView.top }
        openFirstEquipment()
        awaitDetails()
        assertEquals("opaque/id%2F", (resumedActivity() as EquipmentDetailsActivity).intent.getStringExtra(EquipmentDetailsActivity.EXTRA_EQUIPMENT_ID))
        assertTrue(check { it.findViewById<TextView>(R.id.quotes_greeting).text.toString().contains("Maria") })
        onActive { activity ->
            val fields = activity.findViewById<LinearLayout>(R.id.identificationFields)
            assertEquals(7, fields.childCount)
            assertEquals(context.getString(R.string.equipment_serial_number), fields.getChildAt(0).findViewById<TextView>(R.id.label).text)
            assertEquals(context.getString(R.string.equipment_not_informed), fields.getChildAt(2).findViewById<TextView>(R.id.value).text)
            val reports = activity.findViewById<LinearLayout>(R.id.reportsList)
            assertEquals(4, reports.childCount)
            assertEquals("09/10/2026", reports.getChildAt(0).findViewById<TextView>(R.id.reportDate).text)
            assertFalse(reports.getChildAt(0).findViewById<View>(R.id.downloadReport).isEnabled)
            assertEquals(context.getString(R.string.equipment_pdf_unavailable), reports.getChildAt(0).findViewById<View>(R.id.downloadReport).contentDescription)
            activity.findViewById<View>(R.id.nextPage).performClick()
            activity.findViewById<NestedScrollView>(R.id.detailsScroll).fullScroll(View.FOCUS_DOWN)
        }
        await("second page") { check { it.findViewById<TextView>(R.id.pageRange).text == context.getString(R.string.equipment_report_range, 5, 8, 9) } }
        await("history scrolled to bottom") { check {
            val view = it.findViewById<NestedScrollView>(R.id.detailsScroll)
            view.scrollY == (view.getChildAt(0).height - view.height).coerceAtLeast(0)
        } }
        var scroll = 0
        onActive { scroll = it.findViewById<NestedScrollView>(R.id.detailsScroll).scrollY; it.recreate() }
        awaitDetails()
        await("page and scroll restored") { check {
            it.findViewById<TextView>(R.id.pageRange).text == context.getString(R.string.equipment_report_range, 5, 8, 9) &&
                it.findViewById<NestedScrollView>(R.id.detailsScroll).scrollY == scroll
        } }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("back preserves list search") { resumedActivity() is EquipmentActivity && check { it.findViewById<EditText>(R.id.search).text.toString() == " TR-A " } }
        assertTrue(check { it.findViewById<RecyclerView>(R.id.equipmentList).findViewHolderForAdapterPosition(0)?.itemView?.top == listTop })
        openFirstEquipment()
        awaitDetails()
        onActive { it.findViewById<View>(R.id.menuButton).performClick() }
        await("drawer opens from details") { check { it.findViewById<DrawerLayout>(R.id.portalDrawer).isDrawerOpen(Gravity.RIGHT) } }
        onActive { it.findViewById<View>(R.id.navReports).performClick() }
        await("drawer opens reports") { resumedActivity() is ResultsActivity }
    }

    @Test fun historyFailureRetainsDetailsAndRetryDistinguishesEmptyHistory() {
        historyCode = 502
        launchDetails()
        awaitText(R.id.historyState, R.string.equipment_history_error)
        assertTrue(check { it.findViewById<View>(R.id.identificationFields).isShown })
        assertEquals(1, requests.count { it.url.encodedPath == "/api/equipments/opaque%2Fid%252F" })
        historyCode = 200
        history = "[]"
        onActive { it.findViewById<View>(R.id.historyRetry).performClick() }
        awaitText(R.id.historyState, R.string.equipment_history_empty)
        assertTrue(check { !it.findViewById<View>(R.id.historyRetry).isShown && it.findViewById<LinearLayout>(R.id.reportsList).childCount == 0 })
        assertEquals(1, requests.count { it.url.encodedPath == "/api/equipments/opaque%2Fid%252F" })
    }

    @Test fun detailNotFoundAndRetryAreDistinct() {
        detailCode = 502
        detailGate = CountDownLatch(1)
        launchDetails()
        await("detail loading state") { check { it.findViewById<View>(R.id.loading)?.isShown == true } }
        detailGate!!.countDown()
        awaitText(R.id.stateMessage, R.string.equipment_details_error)
        detailCode = 200
        onActive { it.findViewById<View>(R.id.retryButton).performClick() }
        awaitDetails()
        detailCode = 404
        onActive { it.recreate() }
        awaitText(R.id.stateMessage, R.string.equipment_not_found)
        assertTrue(check { !it.findViewById<View>(R.id.retryButton).isShown && !it.findViewById<View>(R.id.detailsContent).isShown })
    }

    @Test fun compactDetailsWithLargeFontsScrollAndExposeAccessibleTargets() {
        val original = shell("settings get system font_scale").trim()
        try {
            shell("settings put system font_scale 1.5")
            launchDetails()
            awaitDetails()
            onActive { it.findViewById<View>(R.id.equipmentDetailsRoot).layoutParams = it.findViewById<View>(R.id.equipmentDetailsRoot).layoutParams.apply { width = dp(320) } }
            await("compact width") { check { it.findViewById<View>(R.id.equipmentDetailsRoot).width == dp(320) } }
            capture("equipment-details-320-font-150-identification.png")
            onActive { it.findViewById<NestedScrollView>(R.id.detailsScroll).fullScroll(View.FOCUS_DOWN) }
            await("pagination reachable") { check {
                val target = it.findViewById<View>(R.id.nextPage)
                val visible = Rect()
                target.getLocalVisibleRect(visible) && visible.width() == target.width && visible.height() == target.height
            } }
            onActive { activity ->
                listOf(R.id.backButton, R.id.previousPage, R.id.nextPage).forEach { id ->
                    val view = activity.findViewById<View>(id)
                    assertTrue(view.width >= dp(48) && view.height >= dp(48))
                }
                val reports = activity.findViewById<LinearLayout>(R.id.reportsList)
                repeat(reports.childCount) { index ->
                    val card = reports.getChildAt(index)
                    listOf(R.id.analysisType, R.id.equipmentType, R.id.reportDate, R.id.status).forEach { id ->
                        val text = card.findViewById<TextView>(id)
                        assertTrue((0 until text.layout.lineCount).all { text.layout.getEllipsisCount(it) == 0 })
                    }
                }
            }
            capture("equipment-details-320-font-150-history.png")
        } finally {
            if (original == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $original")
        }
    }

    private fun openFirstEquipment() {
        await("first equipment bound after filtering") { check {
            val list = it.findViewById<RecyclerView>(R.id.equipmentList)
            !list.hasPendingAdapterUpdates() && !list.isAnimating &&
                list.findViewHolderForAdapterPosition(1)?.itemView?.findViewById<TextView>(R.id.serialNumber)?.text == "SN-17"
        } }
        onActive {
            it.findViewById<RecyclerView>(R.id.equipmentList).findViewHolderForAdapterPosition(1)!!
                .itemView.findViewById<View>(R.id.viewEquipment).performClick()
        }
    }

    private fun launchList(expectedCount: Int = 2) {
        seedSession(context, "portal-token")
        scenario = ActivityScenario.launch(Intent(context, EquipmentActivity::class.java).putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "portal-token"))
        await("equipment list loaded") { check { it.findViewById<RecyclerView>(R.id.equipmentList)?.adapter?.itemCount == expectedCount + 1 } }
    }
    private fun launchDetails() {
        seedSession(context, "portal-token")
        scenario = ActivityScenario.launch(Intent(context, EquipmentDetailsActivity::class.java)
            .putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, "portal-token").putExtra(EquipmentDetailsActivity.EXTRA_EQUIPMENT_ID, "opaque/id%2F"))
    }
    private fun awaitDetails() = await("equipment and history loaded") {
        resumedActivity() is EquipmentDetailsActivity && check {
            it.findViewById<View>(R.id.detailsContent)?.isShown == true && it.findViewById<View>(R.id.historyLoading)?.visibility == View.GONE
        }
    }
    private fun fillLogin() = onActive {
        it.findViewById<EditText>(R.id.email).setText(" CLIENTE@EMPRESA.COM ")
        it.findViewById<EditText>(R.id.password).setText(" password ")
    }
    private fun submit() = onActive { it.findViewById<View>(R.id.loginButton).performClick() }
    private fun onActive(action: (Activity) -> Unit) = instrumentation.runOnMainSync {
        action(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single())
    }
    private fun check(condition: (Activity) -> Boolean): Boolean {
        var result = false
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).singleOrNull()?.let { result = condition(it) }
        }
        return result
    }
    private fun awaitText(id: Int, message: Int) = await("message $message") { check { it.findViewById<TextView>(id)?.text == context.getString(message) } }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; Thread.sleep(50) }
        assertTrue(message, condition())
    }
    private fun forgetVerifiedSession() = SessionStore::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .use { it.readBytes().toString(Charsets.UTF_8) }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        onActive { activity ->
            val decor = activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                ?: File(activity.getExternalFilesDir(null), "portal-verification")
            assertTrue(output.exists() || output.mkdirs())
            File(output, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    companion object {
        const val USER = """{"id":"u1","name":"Maria","email":"cliente@empresa.com","role":"client"}"""
        const val LOGIN = """{"Token":"portal-token","tokenType":"Bearer","expiresIn":3600,"user":$USER}"""
        const val DETAIL = """{"id":"opaque/id%2F","serialNumber":"SN-17","tag":"TR-A","location":"Subestação","company":"Empresa","type":null,"power":null,"oilType":null,"voltage":null}"""
        const val LIST = """[$DETAIL,{"id":"other","serialNumber":"SN-18","tag":"TR-B"}]"""
        val HISTORY = (1..9).joinToString(prefix = "[", postfix = "]") { """{"id":"r$it","date":"2026-10-0$it","analysisType":"physical-chemical","status":"completed","reportUrl":"https://example.invalid/demo.pdf"}""" }
    }
}
