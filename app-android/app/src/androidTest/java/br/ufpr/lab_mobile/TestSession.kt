package br.ufpr.lab_mobile

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.ClientCompany
import br.ufpr.lab_mobile.model.LoginResponse
import br.ufpr.lab_mobile.session.SessionInterceptor
import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Test-only sessions replace the removed debug credential bypass. */
internal object TestSession {
    fun clear(context: Context) = context.portalApplication.sessions.logout()

    fun start(context: Context, marker: String? = "test-client") {
        if (marker.isNullOrBlank()) { clear(context); return }
        val expiry = System.currentTimeMillis() / 1000 + 3600
        val claims = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"exp":$expiry,"role":"CLIENTE","sub":"client@example.com"}""".toByteArray())
        context.portalApplication.sessions.signIn(LoginResponse("header.$claims.signature", "Bearer", 3600,
            AuthenticatedUser(marker, "Test Client", "client@example.com", "Client", listOf(ClientCompany("1", "Test Company")))), false)
    }

    fun interceptor(context: Context) = SessionInterceptor(context.getString(R.string.api_base_url).toHttpUrl(), context.portalApplication.sessions)

    fun loginVisible(): Boolean {
        var visible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            visible = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is LoginActivity }
        }
        return visible
    }
}
