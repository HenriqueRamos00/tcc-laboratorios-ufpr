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
import br.ufpr.lab_mobile.adapter.formatDisplayDate
import br.ufpr.lab_mobile.adapter.showClassification
import br.ufpr.lab_mobile.adapter.showHighlights
import br.ufpr.lab_mobile.adapter.showResults
import br.ufpr.lab_mobile.adapter.stacksFields
import br.ufpr.lab_mobile.databinding.ActivityHealthIndicatorsBinding
import br.ufpr.lab_mobile.model.HealthIndicators
import br.ufpr.lab_mobile.model.HealthPanel
import br.ufpr.lab_mobile.model.TestReport
import br.ufpr.lab_mobile.service.HealthApiService
import br.ufpr.lab_mobile.service.HealthService
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Indicadores de saúde de um equipamento, versão resumida da página do portal web: o parecer do
 * laboratório, os resultados em destaque e todos os resultados da análise mais recente.
 *
 * O app não classifica nem diagnostica. Os métodos de diagnóstico, como os triângulos de Duval,
 * ficam só no portal web, onde há espaço para o contexto que eles exigem.
 */
class HealthIndicatorsActivity : PortalActivity() {
    private enum class Tab { GASES, OIL }

    private lateinit var binding: ActivityHealthIndicatorsBinding
    private lateinit var service: HealthService
    private lateinit var equipmentId: String
    private var indicators: HealthIndicators? = null
    /** Aba escolhida pelo cliente; `null` enquanto vale a primeira aba com resultados. */
    private var chosenTab: Tab? = null
    private var reportExpanded = false
    private var loadJob: Job? = null

    private val tabListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(selected: TabLayout.Tab) {
            chosenTab = Tab.entries[selected.position]
            reportExpanded = false
            renderTab()
            binding.healthScroll.scrollTo(0, 0)
        }
        override fun onTabUnselected(unselected: TabLayout.Tab) = Unit
        override fun onTabReselected(reselected: TabLayout.Tab) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasSession) return
        enableEdgeToEdge()
        binding = ActivityHealthIndicatorsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.healthRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.backButton.setOnClickListener { finish() }
        // Com fonte ampliada, abas de largura igual partiriam "Físico-químico" ao meio.
        if (resources.configuration.stacksFields) binding.healthTabs.tabMode = TabLayout.MODE_AUTO
        equipmentId = intent.getStringExtra(EXTRA_EQUIPMENT_ID)?.trim().orEmpty()
        chosenTab = savedInstanceState?.getString(STATE_TAB)?.let(Tab::valueOf)
        reportExpanded = savedInstanceState?.getBoolean(STATE_REPORT_EXPANDED) ?: false

        service = HealthService(
            RetrofitProvider.retrofit(getString(R.string.api_base_url)).create(HealthApiService::class.java),
        )
        binding.reportToggle.setOnClickListener {
            reportExpanded = !reportExpanded
            applyReportExpansion()
        }
        binding.retryButton.setOnClickListener { load() }
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, chosenTab?.name)
        outState.putBoolean(STATE_REPORT_EXPANDED, reportExpanded)
        super.onSaveInstanceState(outState)
    }

    private fun load() {
        loadJob?.cancel()
        indicators = null
        binding.content.isVisible = false
        binding.stateMessage.isVisible = false
        binding.retryButton.isVisible = false
        if (equipmentId.isBlank()) { showState(R.string.equipment_details_not_found); return }
        binding.loading.isVisible = true
        loadJob = lifecycleScope.launch {
            try {
                render(service.getIndicators(equipmentId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                when ((error as? HttpException)?.code()) {
                    401 -> showState(if (sessions.current == null) R.string.session_expired else R.string.health_error)
                    // A tela é aberta a partir do detalhe, então o equipamento existe: falta a análise.
                    404 -> showState(R.string.health_empty)
                    400, 403 -> showState(R.string.equipment_details_not_found)
                    501 -> showState(R.string.equipment_details_unavailable)
                    else -> showState(R.string.health_error, retryable = true)
                }
            }
        }
    }

    private fun render(result: HealthIndicators) {
        if (result.gases.indicators.isEmpty() && result.oil.indicators.isEmpty()) { showState(R.string.health_empty); return }
        indicators = result
        binding.loading.isVisible = false
        binding.content.isVisible = true
        val name = intent.getStringExtra(EXTRA_EQUIPMENT_NAME)?.takeIf(String::isNotBlank) ?: result.tag.orEmpty()
        binding.equipmentName.text = name
        binding.equipmentName.isVisible = name.isNotBlank()

        val firstWithResults = if (result.gases.indicators.isNotEmpty()) Tab.GASES else Tab.OIL
        // Selecionar por código não é escolha do cliente, então o ouvinte só entra depois.
        binding.healthTabs.removeOnTabSelectedListener(tabListener)
        binding.healthTabs.getTabAt((chosenTab ?: firstWithResults).ordinal)?.select()
        binding.healthTabs.addOnTabSelectedListener(tabListener)
        renderTab()
    }

    private fun renderTab() {
        val result = indicators ?: return
        val tab = Tab.entries[binding.healthTabs.selectedTabPosition]
        val panel = if (tab == Tab.GASES) result.gases else result.oil
        val empty = panel.indicators.isEmpty()
        binding.tabContent.isVisible = !empty
        binding.tabState.isVisible = empty
        if (empty) {
            binding.tabState.setText(if (tab == Tab.GASES) R.string.health_no_gases else R.string.health_no_oil)
            return
        }
        renderReport(panel)
        binding.highlightsTitle.isVisible = panel.highlights.isNotEmpty()
        showHighlights(binding.highlights, panel.highlights)
        showResults(binding.results, panel.indicators)
    }

    private fun renderReport(panel: HealthPanel) {
        val report: TestReport? = panel.report
        binding.analysisDate.text = getString(
            R.string.health_analysis_date, formatDisplayDate(panel.date) ?: getString(R.string.equipment_not_informed),
        )
        binding.classification.showClassification(report?.classification)
        binding.reportHeading.text = report?.heading
        binding.reportHeading.isVisible = report?.heading != null
        binding.reportText.text = report?.text?.ifEmpty { null } ?: getString(R.string.health_report_missing)
        applyReportExpansion()
    }

    /** Um parecer pode ter milhares de caracteres; recolhido, ele não esconde os resultados. */
    private fun applyReportExpansion() {
        binding.reportText.maxLines = if (reportExpanded) Int.MAX_VALUE else REPORT_COLLAPSED_LINES
        binding.reportToggle.setText(if (reportExpanded) R.string.health_report_collapse else R.string.health_report_expand)
        binding.reportToggle.isVisible = reportExpanded
        if (reportExpanded) return
        // Só depois do layout se sabe se o texto foi cortado.
        binding.reportText.requestLayout()
        binding.reportText.doOnLayout {
            val layout = binding.reportText.layout
            val cut = layout != null && layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
            // Mudar a visibilidade durante o layout seria ignorado.
            binding.reportToggle.post { if (!reportExpanded) binding.reportToggle.isVisible = cut }
        }
    }

    private fun showState(message: Int, retryable: Boolean = false) {
        binding.loading.isVisible = false
        binding.content.isVisible = false
        binding.stateMessage.setText(message)
        binding.stateMessage.isVisible = true
        binding.retryButton.isVisible = retryable
    }

    override fun onDestroy() {
        loadJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_EQUIPMENT_ID = "equipment_id"
        const val EXTRA_EQUIPMENT_NAME = "equipment_name"
        private const val STATE_TAB = "health_tab"
        private const val STATE_REPORT_EXPANDED = "health_report_expanded"
        private const val REPORT_COLLAPSED_LINES = 6

        /** [name] é como o detalhe chama o equipamento, para as duas telas mostrarem o mesmo nome. */
        fun open(context: Context, id: String?, name: String?) {
            if (id.isNullOrBlank()) {
                Toast.makeText(context, R.string.equipment_details_not_found, Toast.LENGTH_SHORT).show()
                return
            }
            context.startActivity(Intent(context, HealthIndicatorsActivity::class.java)
                .putExtra(EXTRA_EQUIPMENT_ID, id.trim())
                .putExtra(EXTRA_EQUIPMENT_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
}
