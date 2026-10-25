package br.ufpr.lab_mobile

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import br.ufpr.lab_mobile.controller.LoginActivity
import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.LoginResponse
import br.ufpr.lab_mobile.service.SessionStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

internal fun seedSession(context: Context, token: String?, remember: Boolean = true) {
    SessionStore.get(context).clear()
    if (!token.isNullOrBlank()) assertNotNull(SessionStore.get(context).accept(
        LoginResponse(token, "Bearer", 3600, AuthenticatedUser("test-user", "Maria", "maria@example.com", "client")), remember,
    ))
}

internal fun resumedActivity(): Activity? {
    var activity: Activity? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).singleOrNull()
    }
    return activity
}

internal fun awaitLogin() {
    val end = SystemClock.uptimeMillis() + 15_000
    while (SystemClock.uptimeMillis() < end) {
        if (resumedActivity() is LoginActivity) return
        Thread.sleep(50)
    }
    assertTrue("Login shown after session rejection", resumedActivity() is LoginActivity)
}
