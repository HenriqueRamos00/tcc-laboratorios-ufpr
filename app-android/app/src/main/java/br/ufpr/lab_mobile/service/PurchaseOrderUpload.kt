package br.ufpr.lab_mobile.service

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun purchaseOrderName(resolver: ContentResolver, uri: Uri): String =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }.orEmpty()

internal suspend fun uploadPurchaseOrder(
    service: ClientDecisionService,
    id: String,
    resolver: ContentResolver,
    uri: Uri,
    cacheDir: File,
) = withContext(Dispatchers.IO) {
    val fileName = purchaseOrderName(resolver, uri).trim()
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (fileName.isBlank() || fileName.length > 255 || extension !in setOf("pdf", "doc", "docx")) {
        throw InvalidPurchaseOrderException()
    }
    val file = File.createTempFile("purchase-order-", ".$extension", cacheDir)
    try {
        val input = resolver.openInputStream(uri) ?: throw InvalidPurchaseOrderException()
        input.use { source ->
            file.outputStream().use { target ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > ClientDecisionService.MAX_PURCHASE_ORDER_BYTES) throw PurchaseOrderTooLargeException()
                    target.write(buffer, 0, count)
                }
            }
        }
        if (file.length() == 0L) throw InvalidPurchaseOrderException()
        service.acceptWithPurchaseOrder(id, file, fileName)
    } finally {
        file.delete()
    }
}

internal class InvalidPurchaseOrderException : IOException()
internal class PurchaseOrderTooLargeException : IOException()
