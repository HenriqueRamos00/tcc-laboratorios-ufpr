package br.ufpr.lab_mobile.controller

import android.os.Bundle
import android.os.Parcelable
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
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
import br.ufpr.lab_mobile.adapter.FixedViewAdapter
import br.ufpr.lab_mobile.adapter.ListFooterAdapter
import br.ufpr.lab_mobile.databinding.ActivityEquipmentBinding
import br.ufpr.lab_mobile.databinding.EquipmentListHeaderBinding
import br.ufpr.lab_mobile.model.Equipment
import br.ufpr.lab_mobile.model.filterEquipment
import br.ufpr.lab_mobile.service.EquipmentApiService
import br.ufpr.lab_mobile.service.EquipmentService
import br.ufpr.lab_mobile.service.PagedList
import br.ufpr.lab_mobile.service.RetrofitProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EquipmentActivity : PortalActivity() {
    private lateinit var binding: ActivityEquipmentBinding
    private lateinit var header: EquipmentListHeaderBinding
    private var portalDrawer: PortalDrawer? = null
    private lateinit var equipment: PagedList<Equipment>
    private var status: LoadStatus = LoadStatus.Loading
    private var requestJob: Job? = null
    private var savedListPosition: Parcelable? = null
    private val equipmentAdapter = EquipmentAdapter { EquipmentDetailsActivity.open(this, it.id) }
    private val footerAdapter = ListFooterAdapter { loadEquipment() }

    override val greeting: TextView? get() = if (::header.isInitialized) header.quotesGreeting else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasSession) return
        enableEdgeToEdge()
        binding = ActivityEquipmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.equipmentRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        portalDrawer = PortalDrawer(this, PortalDestination.EQUIPMENT)
        equipment = EquipmentService(
            RetrofitProvider.retrofit(getString(R.string.api_base_url)).create(EquipmentApiService::class.java),
        ).equipment()
        binding.equipmentList.layoutManager = LinearLayoutManager(this)
        header = EquipmentListHeaderBinding.inflate(layoutInflater, binding.equipmentList, false)
        // The header scrolls with the cards so enlarged text and the keyboard leave every field reachable.
        binding.equipmentList.adapter = ConcatAdapter(FixedViewAdapter(header.root), equipmentAdapter, footerAdapter)
        binding.equipmentList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // Quem rolou a lista não quer ser levado de volta quando a próxima página chegar.
                if (newState != RecyclerView.SCROLL_STATE_IDLE) savedListPosition = null
            }
        })
        savedListPosition = savedInstanceState?.getParcelable(STATE_LIST_POSITION)
        header.search.setText(savedInstanceState?.getString(STATE_SEARCH).orEmpty())
        header.search.doAfterTextChanged {
            savedListPosition = null
            if (equipment.items.isNotEmpty()) {
                render()
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

    /** Lê as páginas que faltam; depois de uma falha, continua de onde parou. */
    private fun loadEquipment() {
        requestJob?.cancel()
        status = LoadStatus.Loading
        render()
        requestJob = lifecycleScope.launch {
            status = try {
                equipment.loadRemaining(::render)
                LoadStatus.Complete
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loadFailure(error, R.string.equipment_loading_error, hasSession = sessions.current != null)
            }
            render()
        }
    }

    /** Redesenha a tela a partir do que já chegou, da situação da leitura e do termo de busca. */
    private fun render() {
        val loaded = equipment.items
        val visible = filterEquipment(loaded, header.search.text?.toString().orEmpty())
        equipmentAdapter.show(visible)
        footerAdapter.show(listFooter(equipment, status))
        restoreListPosition()

        val status = status
        val failure = (status as? LoadStatus.Failed)?.takeIf { loaded.isEmpty() }
        val message = when {
            failure != null -> failure.message
            status != LoadStatus.Complete || visible.isNotEmpty() -> null
            loaded.isEmpty() -> R.string.equipment_empty
            else -> R.string.equipment_no_matches
        }
        header.loading.isVisible = status == LoadStatus.Loading && loaded.isEmpty()
        header.equipmentState.text = message?.let(::getString)
        header.equipmentState.isVisible = message != null
        header.retry.isVisible = failure?.retryable == true
    }

    /** A posição salva pode estar numa página que ainda não chegou, então é tentada a cada uma. */
    private fun restoreListPosition() {
        val position = savedListPosition ?: return
        binding.equipmentList.layoutManager?.onRestoreInstanceState(position)
        if (status == LoadStatus.Complete) savedListPosition = null
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
