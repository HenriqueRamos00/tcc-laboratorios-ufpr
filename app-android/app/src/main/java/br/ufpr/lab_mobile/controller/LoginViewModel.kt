package br.ufpr.lab_mobile.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.service.LoginService
import br.ufpr.lab_mobile.service.SessionStore
import br.ufpr.lab_mobile.service.retryAfterMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class LoginState(
    val busy: Boolean = false,
    val message: Int? = null,
    val retryRestore: Boolean = false,
    val cooldownUntil: Long = 0,
    val secondsRemaining: Long = 0,
    val token: String? = null,
)

internal class LoginViewModel(
    private val service: LoginService,
    private val sessions: SessionStore,
    initialCooldown: Long = 0,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state = mutableState.asStateFlow()

    init {
        if (initialCooldown > System.currentTimeMillis()) cooldown(initialCooldown) else restore()
    }

    fun restore() {
        if (state.value.busy) return
        sessions.current?.let { mutableState.value = LoginState(token = it.token); return }
        val candidate = sessions.rememberCandidate() ?: run {
            mutableState.value = LoginState()
            return
        }
        mutableState.value = LoginState(busy = true)
        viewModelScope.launch {
            try {
                val response = service.me(candidate.token)
                if (response.isSuccessful) {
                    val restored = sessions.restore(candidate, response.body())
                    if (restored != null) mutableState.value = LoginState(token = restored.token)
                    else rejectRestoration()
                } else {
                    response.errorBody()?.close()
                    if (response.code() in listOf(400, 401, 403, 404)) rejectRestoration()
                    else mutableState.value = LoginState(message = R.string.session_restore_error, retryRestore = true)
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = LoginState(message = R.string.session_restore_error, retryRestore = true)
            }
        }
    }

    private fun rejectRestoration() {
        sessions.clear()
        mutableState.value = LoginState(message = R.string.session_expired)
    }

    fun submit(email: String, password: String, remember: Boolean) {
        if (state.value.busy || state.value.cooldownUntil > System.currentTimeMillis()) return
        mutableState.value = LoginState(busy = true)
        viewModelScope.launch {
            try {
                val response = service.login(email, password)
                if (response.isSuccessful) {
                    val session = sessions.accept(response.body(), remember)
                    mutableState.value = if (session == null) LoginState(message = R.string.login_invalid_response)
                        else LoginState(token = session.token)
                } else {
                    response.errorBody()?.close()
                    if (response.code() == 429) {
                        val now = System.currentTimeMillis()
                        cooldown(now + retryAfterMillis(response.headers()["Retry-After"], now).coerceAtLeast(1000))
                    } else mutableState.value = LoginState(message = loginError(response.code()))
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { mutableState.value = LoginState(message = R.string.login_loading_error) }
        }
    }

    fun cooldown(until: Long) {
        if (until <= System.currentTimeMillis() || state.value.cooldownUntil == until) return
        mutableState.value = LoginState(message = R.string.login_cooldown, cooldownUntil = until)
        viewModelScope.launch {
            while (System.currentTimeMillis() < until) {
                val remaining = until - System.currentTimeMillis()
                mutableState.value = mutableState.value.copy(secondsRemaining = remaining / 1000 + if (remaining % 1000 > 0) 1 else 0)
                delay(1000)
            }
            if (state.value.cooldownUntil == until) mutableState.value = LoginState()
        }
    }
}

internal fun loginError(code: Int) = when (code) {
    400 -> R.string.login_bad_request
    401, 403 -> R.string.invalid_credentials
    502 -> R.string.login_service_unavailable
    else -> R.string.login_loading_error
}
