package br.ufpr.lab_mobile.service

import android.content.Context
import android.content.SharedPreferences
import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.LoginResponse
import br.ufpr.lab_mobile.model.QuoteSimulations
import br.ufpr.lab_mobile.model.isValid
import com.google.gson.Gson

data class PortalSession(val token: String, val user: AuthenticatedUser, val expiresAt: Long)

class SessionStore(
    private val preferences: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var verified: PortalSession? = null
    val current: PortalSession?
        get() {
            if (verified?.expiresAt?.let { it <= now() } == true) clear()
            return verified
        }

    fun rememberCandidate(): PortalSession? {
        if (!preferences.contains(ACCESS_TOKEN)) return null
        val candidate = runCatching {
            check(preferences.getInt("version", 0) == 1)
            PortalSession(
                preferences.getString(ACCESS_TOKEN, null).orEmpty(),
                Gson().fromJson(preferences.getString("user", null), AuthenticatedUser::class.java),
                preferences.getLong("expires_at", 0),
            )
        }.getOrNull()
        if (candidate == null || !validToken(candidate.token) || !candidate.user.isValid() || candidate.expiresAt <= now()) {
            clear()
            return null
        }
        return candidate
    }

    fun accept(response: LoginResponse?, remember: Boolean): PortalSession? {
        val token = response?.accessToken ?: return null
        val user = response.user?.takeIf { it.isValid() } ?: return null
        val seconds = response.expiresIn ?: return null
        val time = now()
        if (!validToken(token) || !response.tokenType.equals("Bearer", true) ||
            seconds <= 0 || seconds > (Long.MAX_VALUE - time) / 1000) return null
        val session = PortalSession(token, user, time + seconds * 1000)
        clear()
        verified = session
        if (remember) persist(session)
        return session
    }

    fun restore(candidate: PortalSession, user: AuthenticatedUser?): PortalSession? {
        if (candidate.expiresAt <= now() || user?.isValid() != true) return null
        val session = candidate.copy(user = user)
        verified = session
        persist(session)
        return session
    }

    fun clear() {
        verified = null
        preferences.edit().clear().apply()
        QuoteSimulations.clear()
    }

    private fun persist(session: PortalSession) {
        preferences.edit().putInt("version", 1).putString(ACCESS_TOKEN, session.token)
            .putString("user", Gson().toJson(session.user)).putLong("expires_at", session.expiresAt).apply()
    }

    private fun validToken(token: String) = token.isNotBlank() && token.none { it.isWhitespace() || it.isISOControl() }

    companion object {
        const val PREFERENCES = "session"
        const val ACCESS_TOKEN = "access_token"
        @Volatile private var instance: SessionStore? = null

        fun get(context: Context): SessionStore = instance ?: synchronized(this) {
            instance ?: SessionStore(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))
                .also { instance = it }
        }
    }
}
