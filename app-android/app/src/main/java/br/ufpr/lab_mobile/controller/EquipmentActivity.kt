package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.EquipmentAdapter
import br.ufpr.lab_mobile.databinding.ActivityEquipmentBinding
import br.ufpr.lab_mobile.databinding.EquipmentListHeaderBinding
import br.ufpr.lab_mobile.model.Equipment
import br.ufpr.lab_mobile.model.filterEquipment
import br.ufpr.lab_mobile.service.EquipmentApiService
import br.ufpr.lab_mobile.service.EquipmentService
import br.ufpr.lab_mobile.service.RetrofitProvider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

class EquipmentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEquipmentBinding
    private lateinit var header: EquipmentListHeaderBinding
    private lateinit var service: EquipmentService
    private lateinit var token: String
    private var portalDrawer: PortalDrawer? = null
    private var loadedEquipment: List<Equipment>? = null
    private var requestJob: Job? = null
    private var savedListPosition: Parcelable? = null
    private val equipmentAdapter = EquipmentAdapter {
        Snackbar.make(binding.equipmentRoot, R.string.equipment_details_soon, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionToken = intent.getStringExtra(LoginActivity.EXTRA_ACCESS_TOKEN)
            ?: getSharedPreferences(LoginActivity.SESSION_PREFERENCES, MODE_PRIVATE)
                .getString(LoginActivity.ACCESS_TOKEN, null)
        if (sessionToken.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        token = sessionToken
        enableEdgeToEdge()
        binding = ActivityEquipmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.equipmentRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        portalDrawer = PortalDrawer(this, token, PortalDestination.EQUIPMENT)
        service = EquipmentService(
            RetrofitProvider.retrofit(getString(R.string.api_base_url)).create(EquipmentApiService::class.java),
        )
        binding.equipmentList.layoutManager = LinearLayoutManager(this)
        header = EquipmentListHeaderBinding.inflate(layoutInflater, binding.equipmentList, false)
        // The header scrolls with the cards so enlarged text and the keyboard leave every field reachable.
        val headerAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 1
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(header.root) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
        binding.equipmentList.adapter = ConcatAdapter(headerAdapter, equipmentAdapter)
        savedListPosition = savedInstanceState?.getParcelable(STATE_LIST_POSITION)
        header.search.setText(savedInstanceState?.getString(STATE_SEARCH).orEmpty())
        header.search.doAfterTextChanged {
            savedListPosition = null
            if (loadedEquipment != null) {
                filterLoadedEquipment()
                (binding.equipmentList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
            }
        }
        header.search.setOnEditorActionListener { view, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) false
            else {
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            }
        }
        header.retry.setOnClickListener { loadEquipment() }
        loadEquipment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::header.isInitialized) {
            outState.putString(STATE_SEARCH, header.search.text?.toString())
            outState.putParcelable(STATE_LIST_POSITION, savedListPosition ?: binding.equipmentList.layoutManager?.onSaveInstanceState())
        }
        super.onSaveInstanceState(outState)
    }

    private fun loadEquipment() {
        requestJob?.cancel()
        loadedEquipment = null
        equipmentAdapter.updateEquipment(emptyList())
        header.loading.isVisible = true
        header.equipmentState.isVisible = false
        header.retry.isVisible = false
        requestJob = lifecycleScope.launch {
            try {
                loadedEquipment = service.getEquipment(token)
                filterLoadedEquipment()
                savedListPosition?.let { binding.equipmentList.layoutManager?.onRestoreInstanceState(it) }
                savedListPosition = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                if (error.code() == 401) LoginActivity.clearSession(this@EquipmentActivity)
                showState(if (error.code() == 401) R.string.session_expired else R.string.equipment_loading_error, error.code() != 401)
            } catch (_: Exception) {
                showState(R.string.equipment_loading_error, retry = true)
            }
        }
    }

    private fun filterLoadedEquipment() {
        val equipment = loadedEquipment ?: return
        val filtered = filterEquipment(equipment, header.search.text?.toString().orEmpty())
        equipmentAdapter.updateEquipment(filtered)
        showState(when {
            equipment.isEmpty() -> R.string.equipment_empty
            filtered.isEmpty() -> R.string.equipment_no_matches
            else -> null
        })
    }

    private fun showState(message: Int?, retry: Boolean = false) {
        header.loading.isVisible = false
        header.equipmentState.text = message?.let(::getString)
        header.equipmentState.isVisible = message != null
        header.retry.isVisible = retry
    }

    override fun onResume() {
        super.onResume()
        portalDrawer?.onResume()
    }

    private companion object {
        const val STATE_SEARCH = "equipment_search"
        const val STATE_LIST_POSITION = "equipment_list_position"
    }
}
