package br.ufpr.lab_mobile.controller

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.model.Report
import br.ufpr.lab_mobile.portalApplication
import br.ufpr.lab_mobile.service.InvalidPdfException
import br.ufpr.lab_mobile.service.ReportService
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Baixa o PDF de um laudo e salva no local que o cliente escolher.
 *
 * 1. O cliente toca em "Baixar relatório" e o seletor do Android pergunta onde salvar.
 * 2. O PDF é baixado para um arquivo temporário e validado ([ReportService.downloadReport]).
 * 3. Só então o conteúdo é copiado para o destino; assim um PDF inválido nunca chega à pasta do cliente.
 *
 * Precisa ser criado no onCreate da tela, porque registra o seletor de arquivos
 * (registerForActivityResult só pode ser chamado antes de a tela ficar visível).
 */
internal class ReportDownloader(
    private val activity: AppCompatActivity,
    private val service: ReportService,
    private val onDownloadingChanged: (reportId: String?) -> Unit,
) {
    /** Laudo escolhido enquanto o seletor está aberto. */
    private var pendingReportId: String? = null
    /** Download em andamento: id do laudo e destino escolhido. Sobrevivem à rotação da tela. */
    private var downloadingId: String? = null
    private var destination: Uri? = null

    private val chooseDestination = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val reportId = pendingReportId
        pendingReportId = null
        if (uri != null && reportId != null) download(reportId, uri)
    }

    fun start(report: Report) {
        val reportId = report.id?.takeIf(String::isNotBlank) ?: return
        if (downloadingId != null || !report.canDownload) return
        pendingReportId = reportId
        chooseDestination.launch(report.fileName)
    }

    fun saveState(outState: Bundle) {
        outState.putString(STATE_PENDING_ID, pendingReportId)
        outState.putString(STATE_DOWNLOADING_ID, downloadingId)
        outState.putString(STATE_DESTINATION, destination?.toString())
    }

    /** Depois de girar a tela, retoma o download que foi interrompido. */
    fun restoreState(state: Bundle?) {
        if (state == null) return
        pendingReportId = state.getString(STATE_PENDING_ID)
        val reportId = state.getString(STATE_DOWNLOADING_ID)
        val uri = state.getString(STATE_DESTINATION)?.let(Uri::parse)
        if (reportId != null && uri != null) download(reportId, uri)
    }

    private fun download(reportId: String, target: Uri) {
        setDownloading(reportId, target)
        activity.lifecycleScope.launch {
            var temporary: File? = null
            try {
                val file = File.createTempFile("report-", ".pdf", activity.cacheDir)
                temporary = file
                service.downloadReport(reportId, file)
                withContext(Dispatchers.IO) { copy(file, target) }
                Toast.makeText(activity, R.string.report_download_success, Toast.LENGTH_SHORT).show()
                setDownloading(null, null)
            } catch (error: CancellationException) {
                // Girar a tela cancela o download, que recomeça em restoreState. Se o cliente saiu
                // da tela, o arquivo criado pelo seletor ficaria vazio: ele é removido.
                if (!activity.isChangingConfigurations) discard(target)
                throw error
            } catch (error: Exception) {
                discard(target)
                setDownloading(null, null)
                Toast.makeText(activity, messageFor(error), Toast.LENGTH_LONG).show()
            } finally {
                temporary?.delete()
            }
        }
    }

    private fun setDownloading(reportId: String?, target: Uri?) {
        downloadingId = reportId
        destination = target
        onDownloadingChanged(reportId)
    }

    private fun copy(source: File, target: Uri) {
        val output = activity.contentResolver.openOutputStream(target, "wt")
            ?: throw IOException("Missing document destination")
        output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
    }

    private fun discard(target: Uri) {
        try { DocumentsContract.deleteDocument(activity.contentResolver, target) } catch (_: Exception) { }
    }

    private fun messageFor(error: Exception): Int = when {
        error is HttpException && error.code() == 401 && activity.portalApplication.sessions.current == null ->
            R.string.session_expired
        error is HttpException && error.code() == 404 -> R.string.report_download_missing
        error is InvalidPdfException -> R.string.report_download_invalid
        else -> R.string.report_download_error
    }

    private companion object {
        const val STATE_PENDING_ID = "report_download_pending_id"
        const val STATE_DOWNLOADING_ID = "report_download_id"
        const val STATE_DESTINATION = "report_download_destination"
    }
}
