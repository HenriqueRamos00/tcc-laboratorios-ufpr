package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.EquipmentReport
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EquipmentDetailsServiceTest {
    private class FakeResponse(
        val body: String,
        val code: Int = 200,
        val total: Int? = null,
        val contentType: String = "application/json",
    )

    @Test
    fun `detail reads every field of the portal contract`() = runBlocking {
        lateinit var request: Request
        val detail = service { request = it; FakeResponse(DETAIL) }.getDetail(" RVE ")

        assertEquals("/api/equipments/RVE", request.url.encodedPath)
        assertNull(request.url.query)
        assertEquals("SN-17", detail.serialNumber)
        assertEquals("TR-PLACA", detail.displayTag)
        assertEquals("Transformador", detail.type)
        assertEquals("SE Norte", detail.location)
        assertEquals("Empresa", detail.company)
        assertEquals("Mineral", detail.oilType)
        assertEquals("138 kV", detail.voltage)
        assertEquals("20 MVA", detail.power)
        assertEquals("WEG", detail.manufacturer)
        assertEquals("1998", detail.manufacturingYear)
        assertEquals("4500 L", detail.oilVolume)
        assertEquals(true, detail.nameplateKnown)
    }

    @Test
    fun `nullable fields stay null and an unknown nameplate stays false`() = runBlocking {
        val detail = service {
            FakeResponse("""{"id":"RVE","serialNumber":null,"tag":"01439/2026-004","equipmentTag":null,
                "company":"Empresa","nameplateKnown":false}""")
        }.getDetail("RVE")
        assertNull(detail.serialNumber)
        assertEquals("01439/2026-004", detail.displayTag)
        assertNull(detail.voltage)
        assertNull(detail.location)
        assertEquals(false, detail.nameplateKnown)
    }

    @Test
    fun `opaque identifiers are encoded once on every equipment route`(): Unit = runBlocking {
        val id = "opaque/id %2F+?#é"
        val encoded = "/api/equipments/opaque%2Fid%20%252F+%3F%23%C3%A9"
        val paths = mutableListOf<String>()
        val service = service { request ->
            paths += request.url.encodedPath
            when {
                request.url.encodedPath.endsWith("/document") -> FakeResponse(PDF, contentType = "application/pdf")
                request.url.encodedPath.endsWith("/reports") -> FakeResponse("[]", total = 0)
                else -> FakeResponse(DETAIL)
            }
        }
        val destination = File.createTempFile("equipment-test", ".pdf")
        service.getDetail(id)
        service.getReports(id)
        service.downloadReport(id, "r/1", destination)
        assertEquals(listOf(encoded, "$encoded/reports", "$encoded/reports/r%2F1/document"), paths)
        destination.delete()
    }

    @Test
    fun `history keeps the API order and walks pages of at most one hundred`() = runBlocking {
        val queries = mutableListOf<String?>()
        val service = service { request ->
            queries += request.url.query
            val page = request.url.queryParameter("page")!!.toInt()
            val ids = if (page == 1) 1..100 else 101..130
            FakeResponse(ids.joinToString(",", "[", "]") { """{"id":"r-$it"}""" }, total = 130)
        }
        val reports = service.getReports("RVE")
        assertEquals((1..130).map { "r-$it" }, reports.map(EquipmentReport::id))
        assertEquals(listOf("page=1&pageSize=100", "page=2&pageSize=100"), queries)
    }

    @Test
    fun `history reads date type status and PDF availability`() = runBlocking {
        val reports = service {
            FakeResponse("""[
                {"id":"r-1","date":"2026-03-07","analysisType":"chromatography","analysisTypeCode":"chromatography",
                 "status":"completed","hasSignedDocument":false,"pdfAvailable":true},
                {"id":"r-2","date":"2025-03-07","analysisType":"other","status":"in-progress","hasSignedDocument":true,
                 "pdfAvailable":false}
            ]""", total = 2)
        }.getReports("RVE")
        assertEquals(
            listOf(
                EquipmentReport("r-1", "2026-03-07", "chromatography", "completed", false, true),
                EquipmentReport("r-2", "2025-03-07", "other", "in-progress", true, false),
            ),
            reports,
        )
        assertTrue(reports[0].canDownload)
        assertFalse(reports[1].canDownload)
    }

    @Test
    fun `an empty history is different from a failed one`() = runBlocking {
        assertTrue(service { FakeResponse("[]", total = 0) }.getReports("RVE").isEmpty())
        listOf(401, 403, 404, 501, 502).forEach { code ->
            val service = service { FakeResponse("{}", code = code) }
            listOf<suspend () -> Unit>({ service.getDetail("RVE") }, { service.getReports("RVE") }).forEach { call ->
                try {
                    call()
                    fail("Expected HTTP $code")
                } catch (error: HttpException) {
                    assertEquals(code, error.code())
                }
            }
        }
    }

    @Test
    fun `missing or incomplete bodies are loading failures`() = runBlocking {
        val calls = listOf<suspend () -> Unit>(
            { service { FakeResponse("null") }.getDetail("RVE") },
            { service { FakeResponse("{}") }.getDetail("RVE") },
            { service { FakeResponse("null") }.getReports("RVE") },
        )
        calls.forEach { call ->
            try {
                call()
                fail("Expected invalid-body failure")
            } catch (_: IllegalStateException) { }
        }
    }

    @Test
    fun `downloads the PDF from the equipment route`() = runBlocking {
        lateinit var path: String
        val destination = File.createTempFile("equipment-test", ".pdf")
        service { request -> path = request.url.encodedPath; FakeResponse(PDF, contentType = "application/pdf") }
            .downloadReport(" RVE ", " UjE ", destination)
        assertEquals("/api/equipments/RVE/reports/UjE/document", path)
        assertArrayEquals(PDF.toByteArray(), destination.readBytes())
        destination.delete()
        Unit
    }

    @Test
    fun `a response that is not a PDF is rejected and leaves no file`() = runBlocking {
        val destination = File.createTempFile("equipment-test", ".pdf")
        try {
            service { FakeResponse("<html>erro</html>", contentType = "text/html") }.downloadReport("RVE", "UjE", destination)
            fail("Expected invalid PDF")
        } catch (_: InvalidPdfException) {
            assertFalse(destination.exists())
        }
    }

    @Test
    fun `blank identifiers never reach the API`() = runBlocking {
        val service = service { fail("No request expected"); FakeResponse("[]") }
        val calls = listOf<suspend () -> Unit>(
            { service.getDetail(" ") },
            { service.getReports("") },
            { service.downloadReport("", "r", File("unused")) },
            { service.downloadReport("e", " ", File("unused")) },
        )
        calls.forEach { call ->
            try {
                call()
                fail("Expected rejected identifier")
            } catch (_: IllegalArgumentException) { }
        }
    }

    private fun service(respond: (Request) -> FakeResponse): EquipmentService {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val fake = respond(chain.request())
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(fake.code).message("Test response")
                .apply { fake.total?.let { header("X-Total-Count", it.toString()) } }
                .body(fake.body.toResponseBody(fake.contentType.toMediaType())).build()
        }).build()
        return EquipmentService(Retrofit.Builder().baseUrl("http://localhost/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(EquipmentApiService::class.java))
    }

    private companion object {
        const val PDF = "%PDF-1.7\nlaudo"
        const val DETAIL = """{"id":"RVE","serialNumber":"SN-17","tag":"SN-17","equipmentTag":"TR-PLACA",
            "type":"Transformador","location":"SE Norte","company":"Empresa","identification":"L-1","reportCount":2,
            "mostRecentReportDate":"2026-03-07","oilType":"Mineral","voltage":"138 kV","power":"20 MVA",
            "manufacturer":"WEG","manufacturingYear":"1998","oilVolume":"4500 L","nameplateKnown":true}"""
    }
}
