package br.ufpr.lab_mobile.controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.EquipmentReportListView
import br.ufpr.lab_mobile.adapter.identificationFields
import br.ufpr.lab_mobile.adapter.showFields
import br.ufpr.lab_mobile.databinding.ActivityEquipmentDetailsBinding
import br.ufpr.lab_mobile.model.EquipmentDetail
import br.ufpr.lab_mobile.model.EquipmentReport
import br.ufpr.lab_mobile.service.EquipmentApiService
import br.ufpr.lab_mobile.service.EquipmentService
import br.ufpr.lab_mobile.service.RetrofitProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Detalhe de um equipamento, como a página /cliente/equipamentos/:id do portal web.
 *
 * Faz duas consultas em paralelo: a identificação (GET /api/equipments/{id}) e o histórico de
 * relatórios (GET /api/equipments/{id}/reports). Se só o histórico falhar, a identificação
 * continua na tela e o histórico pode ser consultado de novo sozinho.
 */
class EquipmentDetailsActivity : PortalActivity() {
    private lateinit var binding: ActivityEquipmentDetailsBinding
    private lateinit var service: EquipmentService
    private lateinit var reportList: EquipmentReportListView
    private lateinit var downloader: ReportDownloader
    private lateinit var equipmentId: String
    private var detailJob: Job? = null
    private var historyJob: Job? = null
    /** Rolagem a restaurar depois que as duas consultas terminarem. */
    private var pendingScroll: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasSession) return
        enableEdgeToEdge()
        binding = ActivityEquipmentDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.equipmentDetailsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.backButton.setOnClickListener { finish() }
        equipmentId = intent.getStringExtra(EXTRA_EQUIPMENT_ID)?.trim().orEmpty()
        pendingScroll = savedInstanceState?.getInt(STATE_SCROLL)

        service = EquipmentService(
            RetrofitProvider.retrofit(getString(R.string.api_base_url)).create(EquipmentApiService::class.java),
        )
        reportList = EquipmentReportListView(binding.reportsList) { report -> downloader.start(report) }
        downloader = ReportDownloader(
            this,
            downloadReport = { reportId, destination -> service.downloadReport(equipmentId, reportId, destination) },
        ) { reportId -> reportList.setDownloading(reportId) }
        downloader.restoreState(savedInstanceState)

        binding.retryButton.setOnClickListener { load() }
        binding.historyRetry.setOnClickListener { loadHistory() }
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) {
            outState.putInt(STATE_SCROLL, pendingScroll ?: binding.equipmentDetailsScroll.scrollY)
            downloader.saveState(outState)
        }
        super.onSaveInstanceState(outState)
    }

    private fun load() {
        detailJob?.cancel()
        historyJob?.cancel()
        binding.content.isVisible = false
        binding.stateMessage.isVisible = false
        binding.retryButton.isVisible = false
        if (equipmentId.isBlank()) { showDetailError(R.string.equipment_details_not_found, retryable = false); return }
        binding.loading.isVisible = true
        detailJob = lifecycleScope.launch {
            try {
                renderDetail(service.getDetail(equipmentId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                historyJob?.cancel()
                when ((error as? HttpException)?.code()) {
                    401 -> showDetailError(sessionMessageOr(R.string.equipment_details_error), retryable = false)
                    // Sem acesso e inexistente recebem a mesma resposta, e repetir a consulta não muda nenhuma.
                    400, 403, 404 -> showDetailError(R.string.equipment_details_not_found, retryable = false)
                    501 -> showDetailError(R.string.equipment_details_unavailable, retryable = false)
                    else -> showDetailError(R.string.equipment_details_error, retryable = true)
                }
            }
        }
        loadHistory()
    }

    private fun renderDetail(equipment: EquipmentDetail) {
        binding.loading.isVisible = false
        binding.content.isVisible = true
        val subtitle = listOfNotNull(equipment.displayTag, equipment.type?.takeIf(String::isNotBlank)).joinToString(" · ")
        binding.equipmentName.text = subtitle
        binding.equipmentName.isVisible = subtitle.isNotEmpty()
        binding.viewHealth.setOnClickListener { HealthIndicatorsActivity.open(this, equipmentId, equipment.displayTag) }
        binding.nameplateNotice.isVisible = equipment.nameplateKnown == false
        showFields(binding.identificationFields, identificationFields(equipment))
        restoreScroll()
    }

    private fun showDetailError(message: Int, retryable: Boolean) {
        binding.loading.isVisible = false
        binding.content.isVisible = false
        binding.stateMessage.setText(message)
        binding.stateMessage.isVisible = true
        binding.retryButton.isVisible = retryable
    }

    private fun loadHistory() {
        historyJob?.cancel()
        reportList.show(emptyList())
        binding.historyTitle.setText(R.string.equipment_history_title)
        binding.historyLoading.isVisible = true
        binding.historyState.isVisible = false
        binding.historyRetry.isVisible = false
        historyJob = lifecycleScope.launch {
            try {
                renderHistory(service.getReports(equipmentId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val unauthorized = (error as? HttpException)?.code() == 401
                showHistoryState(
                    if (unauthorized) sessionMessageOr(R.string.equipment_history_error) else R.string.equipment_history_error,
                    retryable = !unauthorized,
                )
            }
            restoreScroll()
        }
    }

    private fun renderHistory(reports: List<EquipmentReport>) {
        if (reports.isEmpty()) { showHistoryState(R.string.equipment_history_empty, retryable = false); return }
        binding.historyLoading.isVisible = false
        binding.historyTitle.text = getString(R.string.equipment_history_title_count, reports.size)
        reportList.show(reports)
    }

    private fun showHistoryState(message: Int, retryable: Boolean) {
        binding.historyLoading.isVisible = false
        binding.historyState.setText(message)
        binding.historyState.isVisible = true
        binding.historyRetry.isVisible = retryable
    }

    /** Um 401 com a sessão ainda ativa é recusa da fonte; sem sessão, a tela já está voltando ao login. */
    private fun sessionMessageOr(message: Int) = if (sessions.current == null) R.string.session_expired else message

    private fun restoreScroll() {
        val position = pendingScroll ?: return
        if (!binding.content.isVisible || binding.historyLoading.isVisible) return
        pendingScroll = null
        binding.equipmentDetailsScroll.doOnLayout { it.scrollTo(0, position) }
        binding.equipmentDetailsScroll.requestLayout()
    }

    override fun onDestroy() {
        detailJob?.cancel()
        historyJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_EQUIPMENT_ID = "equipment_id"
        private const val STATE_SCROLL = "equipment_details_scroll"

        fun open(context: Context, id: String?) {
            if (id.isNullOrBlank()) {
                Toast.makeText(context, R.string.equipment_details_not_found, Toast.LENGTH_SHORT).show()
                return
            }
            // SINGLE_TOP: um segundo toque antes de a tela abrir não empilha outra cópia.
            context.startActivity(Intent(context, EquipmentDetailsActivity::class.java)
                .putExtra(EXTRA_EQUIPMENT_ID, id.trim())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
}
