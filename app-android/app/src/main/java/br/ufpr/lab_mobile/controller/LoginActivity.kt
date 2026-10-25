package br.ufpr.lab_mobile.controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ActivityLoginBinding
import br.ufpr.lab_mobile.service.LoginApiService
import br.ufpr.lab_mobile.service.LoginService
import br.ufpr.lab_mobile.service.RetrofitProvider
import br.ufpr.lab_mobile.service.SessionStore
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var model: LoginViewModel
    private var navigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val service = LoginService(RetrofitProvider.retrofit(getString(R.string.api_base_url)).create(LoginApiService::class.java))
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LoginViewModel(service, SessionStore.get(this@LoginActivity), savedInstanceState?.getLong("cooldown_until") ?: 0) as T
            }
        })[LoginViewModel::class.java]
        binding.loginButton.setOnClickListener { submit() }
        binding.restoreRetry.setOnClickListener { model.restore() }
        binding.forgotPassword.setOnClickListener { showMessage(getString(R.string.password_recovery_unavailable)) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.loginButton.isEnabled = !state.busy && state.cooldownUntil <= System.currentTimeMillis()
                    binding.email.isEnabled = !state.busy
                    binding.password.isEnabled = !state.busy
                    binding.keepConnected.isEnabled = !state.busy
                    binding.loginButton.setText(if (state.busy) R.string.logging_in else R.string.login_action)
                    binding.loginProgress.isVisible = state.busy
                    binding.restoreRetry.isVisible = state.retryRestore
                    binding.errorCard.isVisible = state.message != null
                    state.message?.let {
                        showMessage(if (it == R.string.login_cooldown) getString(it, state.secondsRemaining) else getString(it))
                    }
                    state.token?.let { if (!navigating) openQuotes(it) }
                }
            }
        }
    }

    private fun submit() {
        if (model.state.value.busy || model.state.value.cooldownUntil > System.currentTimeMillis()) return
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.errorCard.isVisible = false
        val email = binding.email.text?.toString().orEmpty()
        val password = binding.password.text?.toString().orEmpty()
        val error = validateLogin(email, password)
        if (error != null) {
            binding.emailLayout.error = when (error.emailError) {
                EmailError.REQUIRED -> getString(R.string.required_email)
                EmailError.INVALID -> getString(R.string.invalid_email)
                null -> null
            }
            binding.passwordLayout.error = if (error.passwordMissing) getString(R.string.required_password) else null
            (if (error.emailError != null) binding.email else binding.password).requestFocus()
            return
        }
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.password.windowToken, 0)
        model.submit(email, password, binding.keepConnected.isChecked)
    }

    private fun showMessage(message: String) {
        binding.errorMessage.text = message
        binding.errorCard.isVisible = true
    }

    private fun openQuotes(token: String) {
        navigating = true
        startActivity(Intent(this, QuotesActivity::class.java).putExtra(EXTRA_ACCESS_TOKEN, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("cooldown_until", model.state.value.cooldownUntil)
        super.onSaveInstanceState(outState)
    }

    companion object {
        fun clearSession(context: Context) = SessionStore.get(context).clear()
        const val SESSION_PREFERENCES = SessionStore.PREFERENCES
        const val ACCESS_TOKEN = SessionStore.ACCESS_TOKEN
        const val EXTRA_ACCESS_TOKEN = "access_token"
    }
}

internal data class LoginInputError(val emailError: EmailError?, val passwordMissing: Boolean)
internal enum class EmailError { REQUIRED, INVALID }

internal fun validateLogin(email: String, password: String): LoginInputError? {
    val normalizedEmail = email.trim()
    val emailError = when {
        normalizedEmail.isEmpty() -> EmailError.REQUIRED
        !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalizedEmail) -> EmailError.INVALID
        else -> null
    }
    return if (emailError != null || password.isBlank()) {
        LoginInputError(emailError, password.isBlank())
    } else null
}
