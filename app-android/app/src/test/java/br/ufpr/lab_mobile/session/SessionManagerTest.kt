package br.ufpr.lab_mobile.session

import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.ClientAuthorizations
import br.ufpr.lab_mobile.model.ClientCompany
import br.ufpr.lab_mobile.model.LoginResponse
import com.google.gson.Gson
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

internal const val ROOT = "http://localhost:5125/"
internal const val NOW = 1_800_000_000_000L

internal fun login(now: Long = NOW, lactec: Boolean = true, id: String = "42", nonce: String = "first"): LoginResponse {
    val claims = if (lactec) """"authSource":"lactec","roles":"CLIENTE","serviceCode":"portal-web","sub":"$id""""
        else """"role":"CLIENTE","sub":"client@example.com""""
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":${now / 1000 + 900},$claims,"jti":"$nonce"}""".toByteArray())
    return LoginResponse("header.$encoded.signature", "Bearer", 900,
        AuthenticatedUser(id, "Marina", "client@example.com", if (lactec) "CLIENTE" else "Client",
            companies = if (lactec) null else listOf(ClientCompany("1", "Company")),
            serviceCode = if (lactec) "portal-web" else null,
            authorizations = if (lactec) ClientAuthorizations(listOf("04252011000110")) else null))
}

internal class MemoryStorage : SessionStorage {
    var saved: PortalSession? = null
    override fun read() = saved
    override fun write(session: PortalSession?) { saved = session }
}

internal class FakeSessionApi : SessionApi {
    val refreshes = AtomicInteger()
    val checks = AtomicInteger()
    var onRefresh: (String) -> Response<LoginResponse> = { Response.success(login(nonce = "renewed")) }
    var onMe: (String) -> Response<AuthenticatedUser> = { Response.success(login().user) }
    override fun refresh(token: String): Response<LoginResponse> { refreshes.incrementAndGet(); return onRefresh(token) }
    override fun me(token: String): Response<AuthenticatedUser> { checks.incrementAndGet(); return onMe(token) }
}

class SessionManagerTest {
    private var time = NOW
    private val storage = MemoryStorage()
    private val api = FakeSessionApi()
    private fun manager(root: String = ROOT) = SessionManager(root, storage, api, { time })

    @Test fun `maps exact Token contract and persists complete identity only when remembered`() {
        val response = Gson().fromJson(Gson().toJson(login()), LoginResponse::class.java)
        assertTrue(Gson().toJson(response).contains("\"Token\""))
        assertFalse(Gson().toJson(response).contains("accessToken"))
        val sessions = manager()
        sessions.signIn(response, true)
        assertEquals("Marina", storage.saved!!.user.name)
        assertEquals(sessions.current, manager().current)
        sessions.signIn(response, false)
        assertNull(storage.saved)
        assertNotNull(sessions.current)
        assertNull(manager().current)
        assertFalse(sessions.current.toString().contains(response.accessToken!!))
    }

    @Test fun `rejects malformed tokens expired sessions and nonclient identities`() {
        val valid = login()
        listOf(valid.copy(accessToken = "local-test-token"), valid.copy(tokenType = "Basic"),
            valid.copy(expiresIn = 0), valid.copy(user = valid.user!!.copy(role = "ADMIN")),
            valid.copy(user = valid.user.copy(serviceCode = "other-service")),
            valid.copy(user = valid.user.copy(authorizations = null)), login(now = NOW - 1_000_000),
            Gson().fromJson("""{"accessToken":"wrong-field"}""", LoginResponse::class.java)
        ).forEach { response ->
            val sessions = manager()
            try { sessions.signIn(response, true); fail("Expected invalid session") }
            catch (_: InvalidSessionException) { assertNull(sessions.current); assertNull(storage.saved) }
        }
    }

    @Test fun `backend lab restores locally and expires without calling unsupported me or refresh`() {
        val sessions = manager()
        sessions.signIn(login(lactec = false), true)
        assertTrue(manager().restore())
        val initial = sessions.current!!
        time += 901_000
        assertNull(sessions.validSession(initial))
        assertNull(storage.saved)
        assertEquals(0, api.refreshes.get())
        assertEquals(0, api.checks.get())
    }

    @Test fun `remembered lactec session verifies identity and can renew after token expiration`() {
        manager().signIn(login(), true)
        val sessions = manager()
        assertTrue(sessions.restore())
        assertEquals(1, api.checks.get())
        val original = sessions.current!!
        time += 901_000
        api.onRefresh = { Response.success(login(now = time, nonce = "second")) }
        val renewed = sessions.validSession(original)!!
        assertNotEquals(original.token, renewed.token)
        assertEquals(original.id, renewed.id)
        assertEquals(renewed, storage.saved)
    }

    @Test fun `temporary renewal and identity failures preserve session and permanent rejection clears it`() {
        val sessions = manager()
        sessions.signIn(login(), true)
        val original = sessions.current!!
        api.onRefresh = { Response.error(502, "{}".toResponseBody()) }
        try { sessions.renew(original); fail() } catch (_: IOException) { assertEquals(original, sessions.current) }
        api.onMe = { throw IOException("offline") }
        try { sessions.restore(); fail() } catch (_: IOException) { assertEquals(original, sessions.current) }
        api.onRefresh = { Response.error(401, "{}".toResponseBody()) }
        assertNull(sessions.renew(original))
        assertNull(sessions.current)
        assertNull(storage.saved)
        assertTrue(sessions.endedByExpiration)
        sessions.logout()
        assertFalse(sessions.endedByExpiration)
    }

    @Test fun `renewal and me cannot change identity`() {
        val sessions = manager()
        sessions.signIn(login(), false)
        api.onRefresh = { Response.success(login(id = "another-user")) }
        try { sessions.renew(sessions.current!!); fail() } catch (_: InvalidSessionException) { assertNull(sessions.current) }
        sessions.signIn(login(), false)
        api.onMe = { Response.success(login(id = "another-user").user) }
        try { sessions.restore(); fail() } catch (_: InvalidSessionException) { assertNull(sessions.current) }
    }

    @Test fun `restoration retries identity once after renewal and malformed renewal ends session`() {
        val sessions = manager()
        sessions.signIn(login(), false)
        api.onMe = { token ->
            if (token == login().accessToken) Response.error(401, "{}".toResponseBody())
            else Response.success(login().user)
        }
        assertTrue(sessions.restore())
        assertEquals(2, api.checks.get())
        assertEquals(1, api.refreshes.get())
        api.onRefresh = { throw com.google.gson.JsonSyntaxException("Malformed response") }
        try { sessions.renew(sessions.current!!); fail() }
        catch (_: InvalidSessionException) { assertNull(sessions.current) }
    }

    @Test fun `parallel requests share one renewal and late renewal cannot resurrect logout`() {
        val sessions = manager()
        sessions.signIn(login(), true)
        val original = sessions.current!!
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        api.onRefresh = { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); Response.success(login(nonce = "second")) }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<PortalSession?> { sessions.renew(original) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = pool.submit<PortalSession?> { sessions.renew(original) }
            release.countDown()
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))
            assertEquals(1, api.refreshes.get())
            api.onRefresh = { sessions.logout(); Response.success(login(nonce = "third")) }
            assertNull(sessions.renew(sessions.current!!))
            assertNull(sessions.current)
            assertNull(storage.saved)
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun `late failures cannot clear a different login and saved sessions are bound to API root`() {
        val sessions = manager()
        sessions.signIn(login(), true)
        val old = sessions.current!!
        sessions.signIn(login(id = "43"), true)
        sessions.invalidate(old)
        assertEquals("43", sessions.current!!.user.id)
        assertNull(sessions.validSession(old))
        assertNull(manager("https://other-server/").current)
        assertNull(storage.saved)
    }
}
