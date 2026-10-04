package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Quote
import com.google.gson.JsonElement
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

class QuoteDocumentServiceTest {
    @Test
    fun `downloads raw PDF bytes with bearer token and trimmed quote id`(): Unit = runBlocking {
        val bytes = "%PDF-1.4\nfictional quote".toByteArray()
        val body = TrackedBody(bytes, "application/pdf".toMediaType())
        val api = FakeQuoteApi(Response.success(body))
        val destination = tempFile()

        QuoteService(api).downloadDocument("secret", " q-17 ", destination)

        assertEquals("Bearer secret", api.authorization)
        assertEquals("q-17", api.id)
        assertArrayEquals(bytes, destination.readBytes())
        assertTrue(body.closed)
        destination.delete()
    }

    @Test
    fun `rejects missing media type wrong signature and empty PDF and deletes output`() = runBlocking {
        val responses = listOf(
            Response.success(TrackedBody("%PDF-1.4".toByteArray(), null)),
            Response.success(TrackedBody("<html>".toByteArray(), "application/pdf".toMediaType())),
            Response.success(TrackedBody(byteArrayOf(), "application/pdf".toMediaType())),
            Response.success(null as ResponseBody?),
        )
        responses.forEach { response ->
            val destination = tempFile()
            try {
                QuoteService(FakeQuoteApi(response)).downloadDocument("token", "q", destination)
                fail("Expected invalid PDF")
            } catch (_: InvalidQuoteDocumentException) {
                assertFalse(destination.exists())
            }
        }
    }

    @Test
    fun `preserves HTTP errors and removes partial destination`() = runBlocking {
        listOf(401, 404, 502).forEach { code ->
            val destination = tempFile()
            try {
                QuoteService(FakeQuoteApi(Response.error<ResponseBody>(code, "error".toResponseBody()))).downloadDocument("token", "q", destination)
                fail("Expected HTTP $code")
            } catch (error: HttpException) {
                assertEquals(code, error.code())
                assertFalse(destination.exists())
            }
        }
    }

    @Test
    fun `network failure deletes partial destination and a later retry succeeds`(): Unit = runBlocking {
        val bytes = "%PDF-retried".toByteArray()
        val api = FakeQuoteApi(
            IOException("offline"),
            Response.success(TrackedBody(bytes, "application/pdf".toMediaType())),
        )
        val service = QuoteService(api)
        val destination = tempFile()

        try {
            service.downloadDocument("token", "q", destination)
            fail("Expected network failure")
        } catch (_: IOException) {
            assertFalse(destination.exists())
        }
        service.downloadDocument("token", "q", destination)
        assertArrayEquals(bytes, destination.readBytes())
        assertEquals(2, api.calls)
        destination.delete()
    }

    @Test
    fun `cancellation closes response stream and removes destination`() = runBlocking {
        val body = BlockingBody()
        val destination = tempFile()
        val job = async(Dispatchers.Default) {
            QuoteService(FakeQuoteApi(Response.success(body))).downloadDocument("token", "q", destination)
        }

        assertTrue("body read did not start", body.reading.await(3, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(body.closed.await(3, TimeUnit.SECONDS))
        assertFalse(destination.exists())
    }

    @Test
    fun `Retrofit contract uses document endpoint authorization and quote id`() {
        val method = QuoteApiService::class.java.methods.single { it.name == "getDocument" }
        assertEquals("quotes/{id}/document", requireNotNull(method.getAnnotation(GET::class.java)).value)
        assertEquals("Authorization", method.parameterAnnotations[0].filterIsInstance<Header>().single().value)
        assertEquals("id", method.parameterAnnotations[1].filterIsInstance<Path>().single().value)
    }

    private fun tempFile() = File.createTempFile("quote-document-", ".pdf")

    private class FakeQuoteApi(vararg results: Any) : QuoteApiService {
        private val results = results.toMutableList()
        var authorization: String? = null
        var id: String? = null
        var calls = 0

        @Suppress("UNCHECKED_CAST")
        override suspend fun getDocument(authorization: String, id: String): Response<ResponseBody> {
            this.authorization = authorization
            this.id = id
            calls++
            val result = results.removeAt(0)
            if (result is Throwable) throw result
            return result as Response<ResponseBody>
        }

        override suspend fun getQuote(authorization: String, id: String): Response<Quote> =
            error("Unexpected quote request")

        override suspend fun getQuotes(
            authorization: String,
            search: String?,
            status: String?,
        ): Response<JsonElement> = error("Unexpected list request")
    }

    private class TrackedBody(bytes: ByteArray, private val mediaType: okhttp3.MediaType?) : ResponseBody() {
        var closed = false
        private val trackedSource = object : ForwardingSource(Buffer().write(bytes)) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

        override fun contentType() = mediaType
        override fun contentLength() = -1L
        override fun source(): BufferedSource = trackedSource
    }

    private class BlockingBody : ResponseBody() {
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        private val lock = Object()
        private var sentHeader = false
        private var sourceClosed = false
        private val blockingSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!sentHeader) {
                    sentHeader = true
                    sink.writeUtf8("%PDF-")
                    return 5
                }
                reading.countDown()
                synchronized(lock) {
                    while (!sourceClosed) lock.wait()
                }
                return -1
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {
                synchronized(lock) {
                    sourceClosed = true
                    lock.notifyAll()
                }
                closed.countDown()
            }
        }.buffer()

        override fun contentType() = "application/pdf".toMediaType()
        override fun contentLength() = -1L
        override fun source(): BufferedSource = blockingSource
    }
}
