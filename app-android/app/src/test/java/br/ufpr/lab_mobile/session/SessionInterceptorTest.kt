package br.ufpr.lab_mobile.session

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SessionInterceptorTest {
    private val api = FakeSessionApi()
    private val sessions = SessionManager(ROOT, MemoryStorage(), api, { NOW })
    private fun client(answer: (Request) -> Pair<Int, String>) = OkHttpClient.Builder()
        .addInterceptor(SessionInterceptor(ROOT.toHttpUrl(), sessions))
        .addInterceptor { chain ->
            val (code, body) = answer(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test").body(body.toResponseBody()).build()
        }.build()

    @Test fun `uses latest shared bearer only for protected portal paths`() {
        sessions.signIn(login(), false)
        val original = sessions.current!!
        val seen = mutableListOf<Request>()
        val client = client { seen += it; 200 to "[]" }
        listOf("${ROOT}api/quotes", "${ROOT}auth/login", "${ROOT}api-lookalike/quotes", "https://other-host/api/quotes").forEach { url ->
            client.newCall(Request.Builder().url(url).build()).execute().close()
        }
        assertEquals("Bearer ${original.token}", seen[0].header("Authorization"))
        seen.drop(1).forEach { assertNull(it.header("Authorization")) }
        sessions.renew(original)
        client.newCall(Request.Builder().url("${ROOT}api/quotes").build()).execute().close()
        assertEquals("Bearer ${sessions.current!!.token}", seen.last().header("Authorization"))
    }

    @Test fun `401 retries once with renewed token then ends rejected session`() {
        sessions.signIn(login(), false)
        val sent = mutableListOf<String?>()
        val client = client { sent += it.header("Authorization"); 401 to "{}" }
        client.newCall(Request.Builder().url("${ROOT}api/quotes").build()).execute().use {
            assertEquals(401, it.code)
        }
        assertEquals(2, sent.size)
        assertNotEquals(sent[0], sent[1])
        assertEquals(1, api.refreshes.get())
        assertNull(sessions.current)
    }

    @Test fun `permission tenant configuration and upstream errors do not end the session`() {
        sessions.signIn(login(), false)
        val initial = sessions.current
        listOf(403 to "{}", 501 to "{}", 502 to "{}", 401 to """{"code":"tenant-not-identified"}""").forEach { answer ->
            client { answer }.newCall(Request.Builder().url("${ROOT}api/equipments").build()).execute().use {
                assertEquals(answer.first, it.code)
            }
            assertEquals(initial, sessions.current)
        }
        assertEquals(0, api.refreshes.get())
    }

    @Test fun `no session never sends a protected request and late data cannot cross a login`() {
        var sent = 0
        var client = client { sent++; 200 to "[]" }
        try { client.newCall(Request.Builder().url("${ROOT}api/quotes").build()).execute(); fail() }
        catch (_: SessionExpiredException) { assertEquals(0, sent) }
        sessions.signIn(login(), false)
        client = client { sessions.signIn(login(id = "43"), false); 200 to "[]" }
        try { client.newCall(Request.Builder().url("${ROOT}api/quotes").build()).execute(); fail() }
        catch (_: SessionExpiredException) { assertEquals("43", sessions.current!!.user.id) }
    }

    @Test fun `temporary refresh failure does not erase session`() {
        sessions.signIn(login(), false)
        val initial = sessions.current
        api.onRefresh = { throw IOException("offline") }
        try { client { 401 to "{}" }.newCall(Request.Builder().url("${ROOT}api/quotes").build()).execute(); fail() }
        catch (_: IOException) { assertEquals(initial, sessions.current) }
    }
}
