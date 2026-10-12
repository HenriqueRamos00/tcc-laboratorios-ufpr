package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ActivityResultsBinding
import br.ufpr.lab_mobile.databinding.ItemDemoReportBinding
import br.ufpr.lab_mobile.model.DemoLabReport
import br.ufpr.lab_mobile.model.DemoLabReports
import com.google.android.material.snackbar.Snackbar

class ResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultsBinding
    private var portalDrawer: PortalDrawer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(LoginActivity.EXTRA_ACCESS_TOKEN)
            ?: getSharedPreferences(LoginActivity.SESSION_PREFERENCES, MODE_PRIVATE)
                .getString(LoginActivity.ACCESS_TOKEN, null)
        if (token.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.resultsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        portalDrawer = PortalDrawer(this, token, PortalDestination.REPORTS)
        // shortcut: standalone demonstration, replace this source when HU 4 API integration lands.
        renderReports(DemoLabReports.reports)
    }

    fun renderReports(reports: List<DemoLabReport>) {
        binding.reportsList.removeAllViews()
        binding.reportsEmpty.isVisible = reports.isEmpty()
        reports.forEach { report ->
            val item = ItemDemoReportBinding.inflate(layoutInflater, binding.reportsList, false)
            item.reportNumber.text = getString(R.string.demo_report_number, report.reportNumber)
            item.protocol.text = getString(R.string.demo_report_protocol, report.protocol)
            item.sample.text = getString(R.string.demo_report_sample, report.sample)
            item.laboratory.text = getString(R.string.demo_report_laboratory, report.laboratory)
            item.reportDate.text = getString(R.string.demo_report_date, report.date)
            item.downloadReport.contentDescription = getString(R.string.demo_report_download, report.reportNumber)
            item.downloadReport.setOnClickListener {
                Snackbar.make(binding.resultsRoot, R.string.demo_download_unavailable, Snackbar.LENGTH_LONG).show()
            }
            binding.reportsList.addView(item.root)
        }
    }

    override fun onResume() {
        super.onResume()
        portalDrawer?.onResume()
    }
}
