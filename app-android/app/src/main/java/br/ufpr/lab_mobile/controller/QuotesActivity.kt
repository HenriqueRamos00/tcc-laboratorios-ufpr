package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.QuoteAdapter
import br.ufpr.lab_mobile.databinding.ActivityQuotesBinding
import br.ufpr.lab_mobile.service.QuoteApiService
import br.ufpr.lab_mobile.service.QuoteService
import br.ufpr.lab_mobile.service.RetrofitProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class QuotesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuotesBinding
    private lateinit var quoteService: QuoteService
    private lateinit var token: String
    private val quoteAdapter = QuoteAdapter(
        onViewQuote = { showFutureScreenMessage() },
        onViewStatus = { quote -> showQuoteStatus(quote.id) },
    )
    private var debounceJob: Job? = null
    private var requestJob: Job? = null
    private var skipInitialStatusSelection = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQuotesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.quotesRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        token = intent.getStringExtra(LoginActivity.EXTRA_ACCESS_TOKEN)
            ?: getSharedPreferences(LoginActivity.SESSION_PREFERENCES, MODE_PRIVATE)
                .getString(LoginActivity.ACCESS_TOKEN, null)
            ?: run {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        quoteService = QuoteService(
            RetrofitProvider.retrofit(getString(R.string.api_base_url))
                .create(QuoteApiService::class.java),
        )

        binding.quotesList.apply {
            layoutManager = LinearLayoutManager(this@QuotesActivity)
            adapter = quoteAdapter
        }
        binding.statusFilter.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.quote_status_labels,
            android.R.layout.simple_spinner_dropdown_item,
        )
        binding.statusFilter.setSelection(savedInstanceState?.getInt(STATE_STATUS_FILTER) ?: 0)
        binding.search.setText(savedInstanceState?.getString(STATE_SEARCH).orEmpty())
        binding.statusFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (skipInitialStatusSelection) skipInitialStatusSelection = false
                else scheduleLoad(immediate = true)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.search.doAfterTextChanged { scheduleLoad() }
        loadQuotes()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SEARCH, binding.search.text?.toString())
        outState.putInt(STATE_STATUS_FILTER, binding.statusFilter.selectedItemPosition)
        super.onSaveInstanceState(outState)
    }

    private fun scheduleLoad(immediate: Boolean = false) {
        debounceJob?.cancel()
        debounceJob = lifecycleScope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            loadQuotes()
        }
    }

    private fun loadQuotes() {
        requestJob?.cancel()
        val search = binding.search.text?.toString()?.trim().takeUnless(String?::isNullOrEmpty)
        val status = resources.getStringArray(R.array.quote_status_values)
            .getOrNull(binding.statusFilter.selectedItemPosition)
            ?.takeUnless(String::isEmpty)

        setLoading(true)
        requestJob = lifecycleScope.launch {
            try {
                val quotes = withContext(Dispatchers.IO) {
                    quoteService.getQuotes(token, search, status)
                }
                quoteAdapter.updateQuotes(quotes)
                binding.quotesList.scrollToPosition(0)
                showState(if (quotes.isEmpty()) getString(R.string.quotes_empty) else null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                quoteAdapter.updateQuotes(emptyList())
                showState(
                    getString(
                        if (error.code() == 401) R.string.session_expired
                        else R.string.quotes_loading_error,
                    ),
                )
            } catch (_: Exception) {
                quoteAdapter.updateQuotes(emptyList())
                showState(getString(R.string.quotes_loading_error))
            } finally {
                if (requestJob == coroutineContext[Job]) setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        if (loading) quoteAdapter.updateQuotes(emptyList())
        binding.loading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.quotesList.visibility = if (loading) View.GONE else View.VISIBLE
        if (loading) binding.quotesState.visibility = View.GONE
    }

    private fun showState(message: String?) {
        binding.quotesState.text = message
        binding.quotesState.visibility = if (message == null) View.GONE else View.VISIBLE
        binding.quotesList.visibility = if (message == null) View.VISIBLE else View.GONE
    }

    private fun showFutureScreenMessage() {
        Toast.makeText(this, R.string.quote_details_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun showQuoteStatus(quoteId: String?) {
        if (quoteId.isNullOrBlank()) {
            Toast.makeText(this, R.string.quote_status_invalid_id, Toast.LENGTH_SHORT).show()
            return
        }
        if (supportFragmentManager.isStateSaved ||
            supportFragmentManager.findFragmentByTag(QuoteStatusDialogFragment.TAG) != null
        ) return
        // Commit synchronously so a second tap also finds the first dialog.
        QuoteStatusDialogFragment.newInstance(quoteId.trim())
            .showNow(supportFragmentManager, QuoteStatusDialogFragment.TAG)
    }

    private companion object {
        const val STATE_SEARCH = "quotes_search"
        const val STATE_STATUS_FILTER = "quotes_status_filter"
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
