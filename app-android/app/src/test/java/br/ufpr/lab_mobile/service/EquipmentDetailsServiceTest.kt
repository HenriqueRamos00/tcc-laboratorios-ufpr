package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EquipmentDetailsServiceTest {
    @Test fun detailsAndReportsEncodeOpaqueIdsOnceAndSendBearer() = runBlocking {
        val id = "opaque/id %2F+?#é"
        val paths = mutableListOf<String>()
        val service = service { request ->
            assertEquals("Bearer session-token", request.header("Authorization"))
            paths.add(request.url.encodedPath)
            assertNull(request.url.query)
            if (request.url.encodedPath.endsWith("/reports")) HISTORY else DETAIL
        }
        val detail = service.getDetail("session-token", id)
        assertEquals("opaque", detail.id)
        assertNull(detail.serialNumber)
        assertNull(detail.power)
        assertNull(detail.voltage)
        assertNull(detail.location)
        assertNull(detail.oilType)
        assertEquals("Empresa", detail.company)
        val reports = service.getReports("session-token", id)
        assertEquals(2, reports.size)
        assertNull(reports[0].reportUrl)
        val encoded = "/api/equipments/opaque%2Fid%20%252F+%3F%23%C3%A9"
        assertEquals(listOf(encoded, "$encoded/reports"), paths)
    }

    @Test fun historyEmptyArrayIsDistinctFromHttpOrMalformedResponse() = runBlocking {
        assertTrue(service { "[]" }.getReports("token", "id").isEmpty())
        listOf(401, 403, 404, 500, 502).forEach { code ->
            try { service(code) { "{}" }.getDetail("token", "id"); fail("Expected HTTP error")
            } catch (error: HttpException) { assertEquals(code, error.code()) }
            try { service(code) { "{}" }.getReports("token", "id"); fail("Expected HTTP error")
            } catch (error: HttpException) { assertEquals(code, error.code()) }
        }
        listOf("null", "{}").forEach { body ->
            try { service { body }.getDetail("token", "id"); fail("Invalid detail should fail")
            } catch (_: IllegalStateException) { }
        }
        try { service { "null" }.getReports("token", "id"); fail("Null history should fail")
        } catch (_: IllegalStateException) { }
    }

    @Test fun reportLabelsDatesOrderAndFourItemPages() {
        val reports = (1..9).map { EquipmentReport("$it", "2026-10-0$it", "physical-chemical", "completed", null) }
        val ordered = newestReportsFirst(reports + EquipmentReport("unknown", null, null, null, null))
        assertEquals(listOf("9", "8", "7", "6"), reportPage(ordered, 0).map { it.id })
        assertEquals(listOf("5", "4", "3", "2"), reportPage(ordered, 1).map { it.id })
        assertEquals(listOf("1", "unknown"), reportPage(ordered, 2).map { it.id })
        assertEquals(3, reportPageCount(ordered.size))
        assertEquals(1, reportPageCount(0))
        assertEquals(1, reportPageCount(4))
        assertEquals(2, reportPageCount(5))
        assertTrue(reportPage(emptyList(), 10).isEmpty())
        assertEquals("09/10/2026", ordered.first().formattedDate())
        assertNull(ordered.last().formattedDate())
        assertEquals(br.ufpr.lab_mobile.R.string.equipment_analysis_physical_chemical, ordered[0].analysisLabel())
        assertEquals(br.ufpr.lab_mobile.R.string.equipment_report_completed, ordered[0].statusLabel())
        assertEquals(br.ufpr.lab_mobile.R.string.equipment_analysis_chromatography, ordered[0].copy(analysisType = "chromatography").analysisLabel())
        assertEquals(br.ufpr.lab_mobile.R.string.equipment_report_in_progress, ordered[0].copy(status = "in-progress").statusLabel())
        assertEquals(br.ufpr.lab_mobile.R.string.equipment_not_informed, ordered.last().statusLabel())
        assertEquals(br.ufpr.lab_mobile.R.string.equipment_not_informed, ordered.last().analysisLabel())
    }

    private fun service(code: Int = 200, respond: (okhttp3.Request) -> String): EquipmentService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
                .message("Test").body(respond(chain.request()).toResponseBody()).build()
        }.build()
        return EquipmentService(Retrofit.Builder().baseUrl("http://localhost/api/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(EquipmentApiService::class.java))
    }

    companion object {
        const val DETAIL = """{"id":"opaque","tag":"TR-A","serialNumber":null,"company":"Empresa","identification":"TR-A","reportCount":2,"mostRecentReportDate":"2026-10-25"}"""
        const val HISTORY = """[{"id":"r1","date":"2026-10-25","analysisType":"physical-chemical","status":"completed","reportUrl":null},{"id":"r2","date":"2026-10-20","analysisType":"chromatography","status":"in-progress"}]"""
    }
}
