package br.ufpr.lab_mobile.service

import com.google.gson.Gson
import br.ufpr.lab_mobile.model.LoginResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.Instant

class LoginServiceTest {
    @Test fun exactPortalPathsTokenMappingAndUnchangedPassword() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val service = service { request ->
            requests.add(request)
            if (request.method == "POST") {
                val buffer = okio.Buffer()
                request.body!!.writeTo(buffer)
                val body = Gson().fromJson(buffer.readUtf8(), Map::class.java)
                assertEquals("cliente@empresa.com", body["email"])
                assertEquals(" Password with Spaces \t", body["password"])
                SUCCESS
            } else USER
        }
        val response = service.login("  CLIENTE@EMPRESA.COM ", " Password with Spaces \t").body()!!
        assertEquals("portal-token", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(3600L, response.expiresIn)
        assertEquals("Maria", response.user!!.name)
        assertEquals("Maria", service.me(response.accessToken!!).body()!!.name)
        assertEquals(listOf("/auth/login", "/auth/me"), requests.map { it.url.encodedPath })
        assertNull(requests[0].header("Authorization"))
        assertEquals("Bearer portal-token", requests[1].header("Authorization"))
        assertNull(Gson().fromJson(SUCCESS.replace("Token", "accessToken"), LoginResponse::class.java).accessToken)
    }

    @Test fun preservesRejectedResponsesAndNetworkFailures() = runBlocking {
        listOf(400, 401, 429, 502).forEach { code ->
            assertEquals(code, service(code) { "{}" }.login("a@b.com", "secret").code())
            assertEquals(code, service(code) { "{}" }.me("token").code())
        }
        try {
            service { throw IOException("Offline") }.login("a@b.com", "secret")
            fail("Network failure must be preserved")
        } catch (_: IOException) { }
    }

    @Test fun cooldownUsesSecondsOrHttpDateAndHandlesMissingHeader() {
        val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
        assertEquals(30_000, retryAfterMillis("30", now))
        assertEquals(30_000, retryAfterMillis("Sun, 25 Oct 2026 12:00:30 GMT", now))
        assertEquals(0, retryAfterMillis("Sun, 25 Oct 2026 11:59:00 GMT", now))
        listOf(null, "invalid", "-1").forEach { assertEquals(60_000, retryAfterMillis(it, now)) }
    }

    private fun service(code: Int = 200, respond: (okhttp3.Request) -> String): LoginService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test").body(respond(chain.request()).toResponseBody()).build()
        }.build()
        return LoginService(Retrofit.Builder().baseUrl("http://localhost/api/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(LoginApiService::class.java))
    }

    companion object {
        const val USER = """{"id":"u1","name":"Maria","email":"cliente@empresa.com","role":"client"}"""
        const val SUCCESS = """{"Token":"portal-token","tokenType":"Bearer","expiresIn":3600,"user":$USER}"""
    }
}
