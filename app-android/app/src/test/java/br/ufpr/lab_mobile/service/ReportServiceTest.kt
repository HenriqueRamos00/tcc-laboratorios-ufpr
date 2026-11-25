package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.QuoteReports
import br.ufpr.lab_mobile.model.Report
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
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ReportServiceTest {
    private class FakeResponse(
        val body: String,
        val code: Int = 200,
        val total: Int? = null,
        val contentType: String = "application/json",
    )

    @Test
    fun `lists published reports through the portal route and ignores reports without id`() = runBlocking {
        val requests = mutableListOf<Request>()
        val service = service { request ->
            requests += request
            FakeResponse("""[
                {"id":"r-1","identification":"LACTEC-00307/2026","requestNumber":"00307/2026","quoteCode":"Q-1",
                 "sampleId":"AM-1","sampleName":"Óleo","status":"Publicado","publicationDate":"2026-03-07",
                 "hasSignedDocument":true,"laboratory":"Lab. de Óleos","client":"unused"},
                {"id":"r-2","identification":null,"hasSignedDocument":false,"pdfAvailable":true},
                {"id":"","identification":"sem id"}
            ]""", total = 3)
        }

        val reports = service.getReports()

        assertEquals("/api/reports", requests.single().url.encodedPath)
        assertEquals("1", requests.single().url.queryParameter("page"))
        assertEquals("200", requests.single().url.queryParameter("pageSize"))
        assertEquals(listOf("r-1", "r-2"), reports.map(Report::id))
        assertEquals("AM-1 · Óleo", reports[0].sample)
        assertEquals("2026-03-07", reports[0].publishedOn)
        assertEquals(true, reports[0].canDownload)
        assertEquals(true, reports[1].canDownload)
        assertEquals("relatorio-LACTEC-00307-2026.pdf", reports[0].fileName)
    }

    @Test
    fun `walks every page until the total informed by the API`() = runBlocking {
        val pages = mutableListOf<String?>()
        val service = service { request ->
            val page = request.url.queryParameter("page")
            pages += page
            val ids = if (page == "1") (1..200) else (201..250)
            FakeResponse(ids.joinToString(",", "[", "]") { """{"id":"r-$it"}""" }, total = 250)
        }

        val reports = service.getReports()

        assertEquals(listOf("1", "2"), pages)
        assertEquals(250, reports.size)
        assertEquals("r-250", reports.last().id)
    }

    @Test
    fun `without the total header only the first page is read`() = runBlocking {
        var calls = 0
        val service = service { calls++; FakeResponse("""[{"id":"r-1"}]""") }
        assertEquals(1, service.getReports().size)
        assertEquals(1, calls)
    }

    @Test
    fun `quote conclusion keeps test type summary and reports`() = runBlocking {
        lateinit var path: String
        val service = service { request ->
            path = request.url.encodedPath
            FakeResponse("""{"quoteId":"q-1","quoteCode":"Q-1","protocol":"00307/2026","testType":"Físico-químico",
                "summary":"Dentro dos limites","reports":[{"id":"r-1","identification":"LACTEC-1"}]}""", total = 1)
        }

        val conclusion = service.getQuoteReports(" q-1 ")

        assertEquals("/api/quotes/q-1/reports", path)
        assertEquals(
            QuoteReports("q-1", "Q-1", "00307/2026", "Físico-químico", "Dentro dos limites",
                listOf(Report("r-1", "LACTEC-1", null, null, null, null, null, null, null))),
            conclusion,
        )
    }

    @Test
    fun `204 means the quote has no published report yet`() = runBlocking {
        assertNull(service { FakeResponse("", code = 204) }.getQuoteReports("q-1"))
    }

    @Test
    fun `HTTP errors remain errors instead of an empty list`() = runBlocking {
        listOf(401, 403, 404, 502).forEach { code ->
            val service = service { FakeResponse("{}", code = code) }
            listOf<suspend () -> Unit>({ service.getReports() }, { service.getQuoteReports("q-1") }).forEach { call ->
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
    fun `downloads the report PDF from the file route`(): Unit = runBlocking {
        val bytes = "%PDF-1.7\nlaudo".toByteArray()
        lateinit var path: String
        val client = client { request ->
            path = request.url.encodedPath
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody("application/pdf".toMediaType())).build()
        }
        val destination = File.createTempFile("report-test", ".pdf")

        ReportService(api(client)).downloadReport(" r-1 ", destination)

        assertEquals("/api/reports/r-1/file", path)
        assertArrayEquals(bytes, destination.readBytes())
        destination.delete()
    }

    @Test
    fun `a response that is not a PDF is rejected and leaves no file`() = runBlocking {
        val service = service { FakeResponse("<html>erro</html>", contentType = "text/html") }
        val destination = File.createTempFile("report-test", ".pdf")
        try {
            service.downloadReport("r-1", destination)
            fail("Expected invalid PDF")
        } catch (_: InvalidPdfException) {
            assertFalse(destination.exists())
        }
    }

    private fun service(respond: (Request) -> FakeResponse) = ReportService(api(client { request ->
        val fake = respond(request)
        okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(fake.code).message("Test response")
            .apply { fake.total?.let { header("X-Total-Count", it.toString()) } }
            .body(fake.body.toResponseBody(fake.contentType.toMediaType())).build()
    }))

    private fun client(respond: (Request) -> okhttp3.Response) =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain -> respond(chain.request()) }).build()

    private fun api(client: OkHttpClient) = Retrofit.Builder().baseUrl("http://localhost/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(ReportApiService::class.java)
}
