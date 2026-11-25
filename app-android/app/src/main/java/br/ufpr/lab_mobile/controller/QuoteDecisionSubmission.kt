package br.ufpr.lab_mobile.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.service.InvalidPurchaseOrderException
import br.ufpr.lab_mobile.service.PurchaseOrderTooLargeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class QuoteDecisionState(
    val sending: Boolean = false,
    val completed: Boolean = false,
    val error: Throwable? = null,
) {
    val conflict: Boolean get() = (error as? HttpException)?.code() == 409
}

/** Keeps an explicit submission alive across rotation without replaying the POST. */
class QuoteDecisionSubmission : ViewModel() {
    private val mutableState = MutableStateFlow(QuoteDecisionState())
    val state = mutableState.asStateFlow()
    private var completionDelivered = false

    fun submit(operation: suspend () -> Unit) {
        if (state.value.sending || state.value.completed || state.value.conflict) return
        mutableState.value = QuoteDecisionState(sending = true)
        viewModelScope.launch {
            try {
                operation()
                mutableState.value = QuoteDecisionState(completed = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = QuoteDecisionState(error = error)
            }
        }
    }

    fun consumeCompletion(): Boolean {
        if (!state.value.completed || completionDelivered) return false
        completionDelivered = true
        return true
    }
}

internal fun Throwable.decisionMessage(): Int = when {
    this is InvalidPurchaseOrderException -> R.string.quote_purchase_order_invalid
    this is PurchaseOrderTooLargeException -> R.string.quote_purchase_order_too_large
    else -> when ((this as? HttpException)?.code()) {
        400 -> R.string.quote_decision_invalid
        401 -> R.string.session_expired
        404 -> R.string.quote_status_not_found
        409 -> R.string.quote_decision_conflict
        else -> R.string.quote_decision_error
    }
}
