package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ActivityLoginBinding
import br.ufpr.lab_mobile.service.LoginService
import br.ufpr.lab_mobile.portalApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var loginService: LoginService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        loginService = LoginService(portalApplication.loginApi)
        binding.loginButton.setOnClickListener { submit() }
        binding.forgotPassword.setOnClickListener {
            showMessage(getString(R.string.password_recovery_unavailable))
        }
        if (intent.getBooleanExtra(EXTRA_SESSION_EXPIRED, false)) showMessage(getString(R.string.session_expired))
        restoreSession()
    }

    private fun restoreSession() {
        if (portalApplication.sessions.current == null) return
        setLoading(true)
        lifecycleScope.launch {
            try {
                if (withContext(Dispatchers.IO) { portalApplication.sessions.restore() }) openQuotes()
                else showMessage(getString(R.string.session_expired))
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { showMessage(getString(R.string.session_restore_error))
            } finally { setLoading(false) }
        }
    }

    private fun submit() {
        clearErrors()
        val email = binding.email.text?.toString().orEmpty().trim()
        val password = binding.password.text?.toString().orEmpty()
        val inputError = validateLogin(email, password)
        if (inputError != null) {
            showValidation(inputError)
            return
        }

        getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.password.windowToken, 0)
        val remember = binding.keepConnected.isChecked
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    loginService.login(email, password)
                }
                response.errorBody()?.close()

                when (loginOutcome(response.code(), response.body()?.accessToken)) {
                    is LoginOutcome.Success -> {
                        withContext(Dispatchers.IO) { portalApplication.sessions.signIn(response.body(), remember) }
                        openQuotes()
                    }
                    LoginOutcome.InvalidCredentials -> showMessage(getString(R.string.invalid_credentials))
                    LoginOutcome.ServiceUnavailable -> showMessage(getString(R.string.login_service_unavailable))
                    LoginOutcome.Cooldown -> showMessage(getString(R.string.login_cooldown, response.headers()["Retry-After"]?.toIntOrNull()?.coerceAtLeast(1) ?: 60))
                    LoginOutcome.Failure -> showMessage(getString(R.string.login_loading_error))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(getString(R.string.login_loading_error))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showValidation(error: LoginInputError) {
        binding.emailLayout.error = when (error.emailError) {
            EmailError.REQUIRED -> getString(R.string.required_email)
            EmailError.INVALID -> getString(R.string.invalid_email)
            null -> null
        }
        binding.passwordLayout.error =
            if (error.passwordMissing) getString(R.string.required_password) else null
        (if (error.emailError != null) binding.email else binding.password).requestFocus()
    }

    private fun clearErrors() {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.errorCard.visibility = View.GONE
    }

    private fun showMessage(message: String) {
        binding.errorMessage.text = message
        binding.errorCard.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.loginButton.isEnabled = !loading
        binding.email.isEnabled = !loading
        binding.password.isEnabled = !loading
        binding.keepConnected.isEnabled = !loading
        binding.loginButton.text = getString(if (loading) R.string.logging_in else R.string.login_action)
        binding.loginProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openQuotes() {
        startActivity(
            Intent(this, QuotesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    companion object {
        fun clearSession(context: Context) {
            context.portalApplication.sessions.logout()
        }
        const val EXTRA_SESSION_EXPIRED = "session_expired"
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

internal sealed interface LoginOutcome {
    data class Success(val accessToken: String) : LoginOutcome
    data object InvalidCredentials : LoginOutcome
    data object ServiceUnavailable : LoginOutcome
    data object Cooldown : LoginOutcome
    data object Failure : LoginOutcome
}

internal fun loginOutcome(statusCode: Int, accessToken: String?): LoginOutcome =
    when (statusCode) {
        200 -> accessToken?.takeIf(String::isNotBlank)
            ?.let(LoginOutcome::Success)
            ?: LoginOutcome.Failure
        401 -> LoginOutcome.InvalidCredentials
        502 -> LoginOutcome.ServiceUnavailable
        429 -> LoginOutcome.Cooldown
        else -> LoginOutcome.Failure
    }
