package br.ufpr.lab_mobile.controller

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ActivityEquipmentDetailsBinding
import br.ufpr.lab_mobile.databinding.ItemEquipmentInformationBinding
import br.ufpr.lab_mobile.databinding.ItemEquipmentReportBinding
import br.ufpr.lab_mobile.model.EquipmentDetail
import br.ufpr.lab_mobile.model.EquipmentReport
import br.ufpr.lab_mobile.model.REPORTS_PER_PAGE
import br.ufpr.lab_mobile.model.analysisLabel
import br.ufpr.lab_mobile.model.formattedDate
import br.ufpr.lab_mobile.model.newestReportsFirst
import br.ufpr.lab_mobile.model.reportPage
import br.ufpr.lab_mobile.model.reportPageCount
import br.ufpr.lab_mobile.model.statusLabel
import br.ufpr.lab_mobile.service.EquipmentApiService
import br.ufpr.lab_mobile.service.EquipmentService
import br.ufpr.lab_mobile.service.RetrofitProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

class EquipmentDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEquipmentDetailsBinding
    private lateinit var service: EquipmentService
    private lateinit var token: String
    private lateinit var equipmentId: String
    private var portalDrawer: PortalDrawer? = null
    private var equipment: EquipmentDetail? = null
    private var reports = emptyList<EquipmentReport>()
    private var detailJob: Job? = null
    private var historyJob: Job? = null
    private var page = 0
    private var pendingScroll: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = requireSessionToken() ?: return
        equipmentId = intent.getStringExtra(EXTRA_EQUIPMENT_ID).orEmpty()
        page = savedInstanceState?.getInt("history_page") ?: 0
        pendingScroll = savedInstanceState?.getInt("details_scroll")
        enableEdgeToEdge()
        binding = ActivityEquipmentDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.equipmentDetailsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        portalDrawer = PortalDrawer(this, token, PortalDestination.EQUIPMENT)
        service = EquipmentService(RetrofitProvider.retrofit(getString(R.string.api_base_url)).create(EquipmentApiService::class.java))
        binding.backButton.setOnClickListener { finish() }
        binding.retryButton.setOnClickListener { loadDetail() }
        binding.historyRetry.setOnClickListener { loadHistory() }
        binding.previousPage.setOnClickListener { page--; renderReports() }
        binding.nextPage.setOnClickListener { page++; renderReports() }
        loadDetail()
    }

    private fun loadDetail() {
        detailJob?.cancel()
        historyJob?.cancel()
        equipment = null
        binding.detailsContent.isVisible = false
        binding.stateMessage.isVisible = false
        binding.retryButton.isVisible = false
        binding.loading.isVisible = true
        if (equipmentId.isBlank()) { detailError(R.string.equipment_not_found, false); return }
        detailJob = lifecycleScope.launch {
            try {
                equipment = service.getDetail(token, equipmentId)
                renderIdentification(equipment!!)
                binding.loading.isVisible = false
                binding.detailsContent.isVisible = true
                loadHistory()
            } catch (error: CancellationException) { throw error
            } catch (error: HttpException) {
                if (error.code() == 401) { returnToLogin(clear = true); return@launch }
                detailError(if (error.code() == 404) R.string.equipment_not_found else R.string.equipment_details_error, error.code() != 404)
            } catch (_: Exception) { detailError(R.string.equipment_details_error, true) }
        }
    }

    private fun detailError(message: Int, retry: Boolean) {
        binding.loading.isVisible = false
        binding.stateMessage.setText(message)
        binding.stateMessage.isVisible = true
        binding.retryButton.isVisible = retry
    }

    private fun renderIdentification(detail: EquipmentDetail) {
        binding.identificationFields.removeAllViews()
        listOf(
            Triple(R.string.equipment_serial_number, detail.serialNumber, R.drawable.ic_equipment_serial),
            Triple(R.string.equipment_location, detail.location, R.drawable.ic_equipment_location),
            Triple(R.string.equipment_power, detail.power, R.drawable.ic_equipment_power),
            Triple(R.string.equipment_oil_type, detail.oilType, R.drawable.ic_equipment_oil),
            Triple(R.string.equipment_voltage, detail.voltage, R.drawable.ic_equipment_power),
            Triple(R.string.equipment_tag, detail.tag, R.drawable.ic_equipment_tag),
            Triple(R.string.equipment_company, detail.company, R.drawable.ic_equipment_company),
        ).forEach { (label, value, icon) ->
            val field = ItemEquipmentInformationBinding.inflate(layoutInflater, binding.identificationFields, false)
            field.label.setText(label)
            field.value.text = valueOrFallback(value)
            field.icon.setImageResource(icon)
            binding.identificationFields.addView(field.root)
        }
    }

    private fun loadHistory() {
        historyJob?.cancel()
        reports = emptyList()
        binding.reportsList.removeAllViews()
        binding.historyLoading.isVisible = true
        binding.historyState.isVisible = false
        binding.historyRetry.isVisible = false
        binding.pagination.isVisible = false
        historyJob = lifecycleScope.launch {
            try {
                reports = newestReportsFirst(service.getReports(token, equipmentId))
                renderReports()
            } catch (error: CancellationException) { throw error
            } catch (error: HttpException) {
                if (error.code() == 401) { returnToLogin(clear = true); return@launch }
                historyError()
            } catch (_: Exception) { historyError() }
        }
    }

    private fun historyError() {
        binding.historyLoading.isVisible = false
        binding.historyState.setText(R.string.equipment_history_error)
        binding.historyState.isVisible = true
        binding.historyRetry.isVisible = true
        restoreScroll()
    }

    private fun renderReports() {
        page = page.coerceIn(0, reportPageCount(reports.size) - 1)
        binding.historyLoading.isVisible = false
        binding.historyState.setText(R.string.equipment_history_empty)
        binding.historyState.isVisible = reports.isEmpty()
        binding.pagination.isVisible = reports.isNotEmpty()
        binding.reportsList.removeAllViews()
        reportPage(reports, page).forEach { report ->
            val item = ItemEquipmentReportBinding.inflate(layoutInflater, binding.reportsList, false)
            item.reportDate.text = valueOrFallback(report.formattedDate())
            item.analysisType.setText(report.analysisLabel())
            item.status.setText(report.statusLabel())
            item.status.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, when (report.status) {
                "completed" -> R.color.quote_finished
                "in-progress" -> R.color.quote_progress
                else -> R.color.quote_action_neutral
            }))
            item.equipmentType.text = valueOrFallback(equipment?.type)
            item.downloadReport.contentDescription = getString(R.string.equipment_pdf_unavailable)
            binding.reportsList.addView(item.root)
        }
        binding.pageRange.text = getString(R.string.equipment_report_range,
            page * REPORTS_PER_PAGE + 1, minOf((page + 1) * REPORTS_PER_PAGE, reports.size), reports.size)
        binding.previousPage.isEnabled = page > 0
        binding.nextPage.isEnabled = page < reportPageCount(reports.size) - 1
        restoreScroll()
    }

    private fun valueOrFallback(value: String?) = value?.takeIf(String::isNotBlank) ?: getString(R.string.equipment_not_informed)

    private fun restoreScroll() {
        val position = pendingScroll ?: return
        binding.detailsContent.doOnLayout {
            binding.detailsScroll.post { binding.detailsScroll.scrollTo(0, position); pendingScroll = null }
        }
    }

    override fun onResume() {
        super.onResume()
        portalDrawer?.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) {
            outState.putInt("history_page", page)
            outState.putInt("details_scroll", pendingScroll ?: binding.detailsScroll.scrollY)
        }
        super.onSaveInstanceState(outState)
    }

    companion object { const val EXTRA_EQUIPMENT_ID = "equipment_id" }
}
