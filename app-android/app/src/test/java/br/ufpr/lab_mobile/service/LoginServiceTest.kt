package br.ufpr.lab_mobile.service

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginServiceTest {
    @Test fun `login uses root auth path exact Token contract normalized email and unchanged password`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/portal/auth/login", request.url.encodedPath)
            assertNull(request.header("Authorization"))
            val bytes = okio.Buffer().also { request.body!!.writeTo(it) }
            val body = JsonParser.parseString(bytes.readUtf8()).asJsonObject
            assertEquals("client@example.com", body["email"].asString)
            assertEquals("  PassWord  ", body["password"].asString)
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"Token":"portal-token","tokenType":"Bearer","expiresIn":900,"user":{"id":"42","name":"Marina","role":"CLIENTE"}}""".toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("http://localhost/portal/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(LoginApiService::class.java)
        val response = LoginService(api).login(" CLIENT@EXAMPLE.COM ", "  PassWord  ")
        assertEquals("portal-token", response.body()!!.accessToken)
        assertEquals("Marina", response.body()!!.user!!.name)
    }

    @Test fun `public client refuses redirects`() {
        val client = RetrofitProvider.publicRetrofit("http://localhost/").callFactory() as OkHttpClient
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test fun `renewal and identity use root auth routes and explicit bearer without credentials`() {
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            assertEquals("Bearer existing-token", request.header("Authorization"))
            val payload = if (request.method == "POST") {
                val body = okio.Buffer().also { request.body?.writeTo(it) }.readUtf8()
                assertFalse(body.contains("password"))
                """{"Token":"renewed-token","tokenType":"Bearer","expiresIn":900}"""
            } else """{"id":"42","name":"Marina"}"""
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("http://localhost/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(LoginApiService::class.java)
        assertEquals("renewed-token", api.refresh("Bearer existing-token").execute().body()!!.accessToken)
        assertEquals("Marina", api.me("Bearer existing-token").execute().body()!!.name)
        assertEquals(listOf("/auth/refresh", "/auth/me"), paths)
    }
}
