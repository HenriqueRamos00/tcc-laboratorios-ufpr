package br.ufpr.lab_mobile

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufpr.lab_mobile.service.AcceptanceTermsRequest
import br.ufpr.lab_mobile.service.ClientDecisionApiService
import br.ufpr.lab_mobile.service.ClientDecisionService
import br.ufpr.lab_mobile.service.InvalidPurchaseOrderException
import br.ufpr.lab_mobile.service.RejectionRequest
import br.ufpr.lab_mobile.service.uploadPurchaseOrder
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class PurchaseOrderUploadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun enviaArquivoDoSeletorELimpaCopiaTemporaria(): Unit = runBlocking {
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "pedido-teste.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        }))
        val bytes = "%PDF-1.4\npedido de compra".toByteArray()
        var sent = false
        val api = object : ClientDecisionApiService {
            override suspend fun recordAcceptance(id: String, request: AcceptanceTermsRequest): Response<Unit> = error("Termo inesperado")
            override suspend fun recordRejection(id: String, request: RejectionRequest): Response<Unit> = error("Recusa inesperada")
            override suspend fun recordPurchaseOrder(id: String, file: MultipartBody.Part): Response<Unit> {
                assertEquals("quote-1", id)
                assertTrue(file.headers.toString().contains("name=\"file\""))
                assertArrayEquals(bytes, Buffer().also { file.body.writeTo(it) }.readByteArray())
                sent = true
                return Response.success(Unit)
            }
        }
        try {
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            uploadPurchaseOrder(ClientDecisionService(api), "quote-1", resolver, uri, context.cacheDir)
            assertTrue(sent)
            assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("purchase-order-") })
            resolver.openOutputStream(uri, "wt")!!.close()
            sent = false
            try {
                uploadPurchaseOrder(ClientDecisionService(api), "quote-1", resolver, uri, context.cacheDir)
                fail("Arquivo vazio não deve ser enviado")
            } catch (_: InvalidPurchaseOrderException) {
                assertFalse(sent)
                assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("purchase-order-") })
            }
        } finally { resolver.delete(uri, null, null) }
    }
}
