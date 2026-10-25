package br.ufpr.lab_mobile.controller

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.ufpr.lab_mobile.service.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun Activity.requireSessionToken(): String? {
    val session = SessionStore.get(this).current
    val supplied = intent.getStringExtra(LoginActivity.EXTRA_ACCESS_TOKEN)
    if (session != null && (supplied == null || supplied == session.token)) return session.token
    returnToLogin()
    return null
}

internal fun Activity.returnToLogin(clear: Boolean = false) {
    if (clear) SessionStore.get(this).clear()
    if (isFinishing) return
    startActivity(Intent(this, LoginActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    finish()
}

internal fun AppCompatActivity.watchSessionExpiry() {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (requireSessionToken() == null) return@repeatOnLifecycle
            val expiry = SessionStore.get(this@watchSessionExpiry).current?.expiresAt ?: return@repeatOnLifecycle
            delay((expiry - System.currentTimeMillis()).coerceAtLeast(0))
            returnToLogin(clear = true)
        }
    }
}
