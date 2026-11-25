package br.ufpr.lab_mobile.service

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** O servidor respondeu, mas o conteúdo não é um PDF aceitável (tipo, assinatura ou tamanho). */
class InvalidPdfException : IOException("Invalid PDF")

/** Nome usado pelo código de orçamentos, que já tratava este erro antes do download de relatórios. */
typealias InvalidQuoteDocumentException = InvalidPdfException

private const val MAX_PDF_BYTES = 32L * 1024 * 1024
private val PDF_SIGNATURE = "%PDF-".toByteArray()

/**
 * Faz a requisição e grava o PDF em [destination]. Usado pelo documento do orçamento e pelo laudo.
 *
 * Garante que só fica no disco um PDF de verdade: confere o Content-Type, o limite de 32 MiB e a
 * assinatura "%PDF-". Em qualquer falha, ou se a tela for fechada no meio, o arquivo é apagado.
 */
internal suspend fun downloadPdf(destination: File, request: suspend () -> Response<ResponseBody>) {
    var complete = false
    try {
        val response = request()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        val body = response.body() ?: throw InvalidPdfException()
        body.use {
            val mediaType = body.contentType()
            if (mediaType?.type != "application" || mediaType.subtype != "pdf") throw InvalidPdfException()
            // Closing the body unblocks a pending socket read when the screen is abandoned.
            coroutineScope {
                val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { body.close() }
                }
                try {
                    withContext(Dispatchers.IO) {
                        copyLimited(body, destination)
                        checkPdfSignature(destination)
                    }
                } finally { closer.cancel() }
            }
        }
        currentCoroutineContext().ensureActive()
        complete = true
    } finally {
        if (!complete) destination.delete()
    }
}

private suspend fun copyLimited(body: ResponseBody, destination: File) {
    body.byteStream().use { input ->
        destination.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_PDF_BYTES) throw InvalidPdfException()
                output.write(buffer, 0, count)
            }
        }
    }
}

private fun checkPdfSignature(file: File) {
    file.inputStream().use { input ->
        val header = ByteArray(PDF_SIGNATURE.size)
        if (input.read(header) != header.size || !header.contentEquals(PDF_SIGNATURE)) {
            throw InvalidPdfException()
        }
    }
}
