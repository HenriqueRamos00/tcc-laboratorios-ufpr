package br.ufpr.lab_mobile.session

import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.LoginResponse
import com.google.gson.JsonParseException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response

interface SessionStorage {
    fun read(): PortalSession?
    fun write(session: PortalSession?)
}

interface SessionApi {
    fun refresh(token: String): Response<LoginResponse>
    fun me(token: String): Response<AuthenticatedUser>
}

/** Network operations are blocking: call from IO or the OkHttp worker, never the UI thread. */
class SessionManager(
    private val apiRoot: String,
    private val storage: SessionStorage,
    private val api: SessionApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val onSessionEnded: () -> Unit = {},
) {
    private val stateLock = Any()
    private val renewalLock = Any()
    private val mutableSession = MutableStateFlow<PortalSession?>(load())
    val session = mutableSession.asStateFlow()
    val current: PortalSession? get() = session.value
    @Volatile var endedByExpiration = false
        private set

    private fun load(): PortalSession? {
        val saved = try { storage.read() } catch (_: Exception) { null }
        if (saved != null && SessionValidation.validStored(saved, apiRoot)) return saved
        storage.write(null)
        return null
    }

    fun signIn(response: LoginResponse?, remembered: Boolean) {
        val next = SessionValidation.fromLogin(response, remembered, apiRoot, now())
        synchronized(stateLock) {
            storage.write(next.takeIf { remembered })
            onSessionEnded()
            endedByExpiration = false
            mutableSession.value = next
        }
    }

    fun logout() = synchronized(stateLock) { clear(expired = false) }

    fun invalidate(expected: PortalSession) = synchronized(stateLock) {
        if (current?.id == expected.id && current?.token == expected.token) clear(expired = true)
    }

    private fun clear(expired: Boolean) {
        endedByExpiration = expired
        mutableSession.value = null
        onSessionEnded()
        storage.write(null)
    }

    fun belongsTo(expected: PortalSession): Boolean = current?.id == expected.id

    /** Returns the latest token for this login only, without crossing an account switch. */
    fun validSession(expected: PortalSession): PortalSession? {
        val latest = current?.takeIf { it.id == expected.id } ?: return null
        if (latest.expiresAt - now() > 30_000) return latest
        if (latest.renewable) return renew(latest)
        if (latest.expiresAt > now()) return latest
        invalidate(latest)
        return null
    }

    fun renew(expected: PortalSession): PortalSession? = synchronized(renewalLock) {
        val latest = current?.takeIf { it.id == expected.id } ?: return null
        // A simultaneous request already renewed this token.
        if (latest.token != expected.token) return latest
        if (!latest.renewable) { invalidate(latest); return null }
        val response = try { api.refresh(latest.token) }
        catch (_: JsonParseException) { invalidate(latest); throw InvalidSessionException() }
        if (response.code() in listOf(401, 403)) {
            response.errorBody()?.close()
            invalidate(latest)
            return null
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw IOException("Session renewal unavailable (HTTP ${response.code()})")
        }
        val next = try {
            SessionValidation.fromLogin(response.body(), latest.remembered, apiRoot, now()).also {
                if (it.user.id != latest.user.id || !it.renewable) throw InvalidSessionException()
            }.copy(id = latest.id)
        } catch (failure: InvalidSessionException) {
            invalidate(latest)
            throw failure
        }
        replace(latest, next)
    }

    fun restore(): Boolean {
        val expected = current ?: return false
        val usable = validSession(expected) ?: return false
        if (!usable.renewable) return belongsTo(usable) // /auth/me is 501 in backend-lab.
        var checked = usable
        var response = checkIdentity(checked)
        if (response.code() == 401) {
            response.errorBody()?.close()
            checked = renew(checked) ?: return false
            response = checkIdentity(checked)
        }
        if (response.code() in listOf(401, 403)) {
            response.errorBody()?.close()
            invalidate(checked)
            return false
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw IOException("Session verification unavailable (HTTP ${response.code()})")
        }
        val user = response.body()
        if (user == null || !SessionValidation.validUser(checked, user)) {
            invalidate(checked)
            throw InvalidSessionException()
        }
        return replace(checked, checked.copy(user = user)) != null
    }

    private fun replace(expected: PortalSession, next: PortalSession): PortalSession? = synchronized(stateLock) {
        if (!belongsTo(expected)) return null
        if (current != expected) return current
        storage.write(next.takeIf { it.remembered })
        mutableSession.value = next
        next
    }

    private fun checkIdentity(session: PortalSession): Response<AuthenticatedUser> = try { api.me(session.token) }
    catch (_: JsonParseException) { invalidate(session); throw InvalidSessionException() }
}
