package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.portalApplication
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns session guards for every protected screen, including screens without an API request. */
abstract class PortalActivity : AppCompatActivity() {
    protected val sessions get() = portalApplication.sessions
    protected var hasSession = false
        private set
    private var redirecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = sessions.current
        if (initial == null) { returnToLogin(); return }
        hasSession = true
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sessions.session.collect { session ->
                        if (session?.id != initial.id) returnToLogin()
                        else findViewById<TextView>(R.id.quotes_greeting)?.text = getString(R.string.portal_greeting, session.user.name)
                    }
                }
                launch {
                    while (isActive && sessions.belongsTo(initial)) {
                        try { withContext(Dispatchers.IO) { sessions.validSession(initial) } }
                        catch (_: IOException) {
                            // Avoid repeatedly renewing while offline or the source is unavailable.
                            delay(30_000)
                            continue
                        }
                        val expiry = sessions.current?.expiresAt ?: break
                        delay((expiry - System.currentTimeMillis() - 30_000).coerceIn(1_000, 30_000))
                    }
                }
            }
        }
    }

    private fun returnToLogin() {
        if (redirecting) return
        redirecting = true
        startActivity(Intent(this, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, sessions.endedByExpiration)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
