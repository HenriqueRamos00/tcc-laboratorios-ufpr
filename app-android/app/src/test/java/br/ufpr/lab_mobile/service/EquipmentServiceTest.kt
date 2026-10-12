package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Equipment
import br.ufpr.lab_mobile.model.filterEquipment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class EquipmentServiceTest {
    @Test
    fun `maps controller array nullable fields and ignores unused fields with Bearer header`() = runBlocking {
        val service = service("""[
            {"id":"opaque-1","serialNumber":"SN-17","tag":"TR-A","type":null,"location":null,
             "company":"Empresa","identification":"unused","reportCount":3,"mostRecentReportDate":"2026-10-12"},
            {"id":"opaque-2","serialNumber":null,"tag":"TR-B","type":"Transformador","location":"Local"},
            {"id":"opaque-3","tag":"TR-C"}
        ]""") { request ->
            assertEquals("Bearer session-token", request.header("Authorization"))
            assertEquals("/api/equipments", request.url.encodedPath)
            assertNull(request.url.query)
            assertEquals("GET", request.method)
        }
        val equipment = service.getEquipment("session-token")
        assertEquals(3, equipment.size)
        assertEquals(Equipment("opaque-1", "SN-17", "TR-A", null, null), equipment[0])
        assertEquals(Equipment("opaque-2", null, "TR-B", "Transformador", "Local"), equipment[1])
        assertEquals(Equipment("opaque-3", null, "TR-C", null, null), equipment[2])
    }

    @Test
    fun `empty array stays empty`() = runBlocking {
        assertTrue(service("[]").getEquipment("token").isEmpty())
    }

    @Test
    fun `HTTP errors remain errors instead of an empty list`() = runBlocking {
        listOf(401, 403, 404, 500, 502, 503).forEach { code ->
            try {
                service("{}", code = code).getEquipment("token")
                fail("Expected HTTP $code")
            } catch (error: HttpException) {
                assertEquals(code, error.code())
            }
        }
    }

    @Test
    fun `missing or malformed response is a loading failure`() = runBlocking {
        listOf("null", "{}", "not json").forEach { body ->
            try {
                service(body).getEquipment("token")
                fail("Expected invalid-body failure")
            } catch (error: Exception) {
                assertFalse(error is HttpException)
            }
        }
    }

    @Test
    fun `network failure and cancellation are preserved`() = runBlocking {
        listOf(IOException("Offline"), CancellationException("Cancelled")).forEach { failure ->
            val api = object : EquipmentApiService {
                override suspend fun getEquipment(authorization: String): Response<List<Equipment>> = throw failure
            }
            try {
                EquipmentService(api).getEquipment("token")
                fail("Expected original failure")
            } catch (error: Exception) {
                assertSame(failure, error)
            }
        }
    }

    @Test
    fun `trimmed serial and TAG substrings ignore case and preserve server order`() {
        val items = listOf(
            Equipment("1", "SN-123", "other", "matching", "matching"),
            Equipment("2", null, "tag-123", null, null),
            Equipment("3", "serial", null, null, null),
        )
        assertEquals(listOf(items[0], items[1]), filterEquipment(items, " 123 "))
        assertEquals(listOf(items[0]), filterEquipment(items, " sn- "))
        assertEquals(listOf(items[1]), filterEquipment(items, " TAG- "))
        assertTrue(filterEquipment(items, "matching").isEmpty())
        assertTrue(filterEquipment(items, "unknown").isEmpty())
        listOf("", "  ", "\t\n").forEach { assertSame(items, filterEquipment(items, it)) }
        assertTrue(filterEquipment(emptyList(), "123").isEmpty())
    }

    private fun service(body: String, code: Int = 200, check: (okhttp3.Request) -> Unit = {}): EquipmentService {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            check(chain.request())
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test response").body(body.toResponseBody()).build()
        }).build()
        val api = Retrofit.Builder().baseUrl("http://localhost/api/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(EquipmentApiService::class.java)
        return EquipmentService(api)
    }
}
