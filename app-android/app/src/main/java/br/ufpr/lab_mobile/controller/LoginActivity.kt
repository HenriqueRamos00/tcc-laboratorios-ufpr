package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import br.ufpr.lab_mobile.BuildConfig
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.api.ApiClient
import br.ufpr.lab_mobile.api.AuthEndpoint
import br.ufpr.lab_mobile.api.LoginRequest
import br.ufpr.lab_mobile.databinding.ActivityLoginBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var endpoint: AuthEndpoint

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

        endpoint = ApiClient.endpoint(getString(R.string.api_base_url))
        if (getSharedPreferences(SESSION_PREFERENCES, MODE_PRIVATE)
                .getString(ACCESS_TOKEN, null) != null
        ) {
            openQuotes()
            return
        }
        binding.loginButton.setOnClickListener { submit() }
        binding.forgotPassword.setOnClickListener {
            showMessage(getString(R.string.password_recovery_unavailable))
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
        if (isTestLogin(BuildConfig.DEBUG, email, password)) {
            saveSessionAndOpenQuotes(TEST_TOKEN)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    endpoint.login(LoginRequest(email, password))
                }

                when (val outcome = loginOutcome(response.code(), response.body()?.accessToken)) {
                    is LoginOutcome.Success -> saveSessionAndOpenQuotes(outcome.accessToken)
                    LoginOutcome.InvalidCredentials -> showMessage(getString(R.string.invalid_credentials))
                    LoginOutcome.ServiceUnavailable -> showMessage(getString(R.string.login_service_unavailable))
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
        binding.loginButton.text = getString(if (loading) R.string.logging_in else R.string.login_action)
        binding.loginProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun saveSessionAndOpenQuotes(accessToken: String) {
        val preferences = getSharedPreferences(SESSION_PREFERENCES, MODE_PRIVATE).edit()
        if (binding.keepConnected.isChecked) preferences.putString(ACCESS_TOKEN, accessToken)
        else preferences.remove(ACCESS_TOKEN)
        preferences.apply()
        openQuotes()
    }

    private fun openQuotes() {
        startActivity(Intent(this, QuotesActivity::class.java))
        finish()
    }

    private companion object {
        const val TEST_TOKEN = "local-test-token"
        const val SESSION_PREFERENCES = "session"
        const val ACCESS_TOKEN = "access_token"
    }
}

private const val TEST_EMAIL = "lactec@lactec.com"
private const val TEST_PASSWORD = "lactec"

internal fun isTestLogin(debug: Boolean, email: String, password: String) =
    debug && email == TEST_EMAIL && password == TEST_PASSWORD

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
    data object Failure : LoginOutcome
}

internal fun loginOutcome(statusCode: Int, accessToken: String?): LoginOutcome =
    when (statusCode) {
        200 -> accessToken?.takeIf(String::isNotBlank)
            ?.let(LoginOutcome::Success)
            ?: LoginOutcome.Failure
        401 -> LoginOutcome.InvalidCredentials
        502 -> LoginOutcome.ServiceUnavailable
        else -> LoginOutcome.Failure
    }
