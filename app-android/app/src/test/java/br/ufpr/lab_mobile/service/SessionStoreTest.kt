package br.ufpr.lab_mobile.service

import android.content.SharedPreferences
import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.LoginResponse
import br.ufpr.lab_mobile.model.QuoteSimulations
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class SessionStoreTest {
    private val values = mutableMapOf<String, Any?>()
    private var now = 1_000L
    private val preferences = memoryPreferences(values)
    private val sessions = SessionStore(preferences) { now }
    private val user = AuthenticatedUser("u1", "Maria", "cliente@empresa.com", "client")
    private val response = LoginResponse("portal-token", "Bearer", 60, user)

    @Test fun onlyRememberedSessionsSurviveANewStoreAndRequireVerification() {
        assertNotNull(sessions.accept(response, false))
        assertTrue(values.isEmpty())
        assertNull(SessionStore(preferences) { now }.rememberCandidate())
        val saved = sessions.accept(response, true)!!
        val restarted = SessionStore(preferences) { now }
        assertEquals(saved, restarted.rememberCandidate())
        assertNull(restarted.current)
        assertEquals(user.copy(name = "Atualizada"), restarted.restore(saved, user.copy(name = "Atualizada"))!!.user)
        assertEquals(saved.token, restarted.current!!.token)
    }

    @Test fun expiryClearsMemoryPersistenceAndSimulatedDecisions() {
        sessions.accept(response, true)
        QuoteSimulations.startSession(response.accessToken!!)
        now = 61_000
        assertNull(sessions.current)
        assertTrue(values.isEmpty())
        assertFalse(QuoteSimulations.isSessionActive(response.accessToken))
    }

    @Test fun logoutClearsAllSessionFields() {
        sessions.accept(response, true)
        sessions.clear()
        assertNull(sessions.current)
        assertNull(sessions.rememberCandidate())
        assertTrue(values.isEmpty())
    }

    @Test fun placeholderAndMalformedPreferencesAreRejected() {
        values[SessionStore.ACCESS_TOKEN] = "old-placeholder"
        assertNull(sessions.rememberCandidate())
        assertTrue(values.isEmpty())
        sessions.accept(response, true)
        values["user"] = "null"
        assertNull(sessions.rememberCandidate())
        assertTrue(values.isEmpty())
    }

    @Test fun malformedSuccessfulResponsesNeverCreateSessions() {
        val badResponses = listOf(null, response.copy(accessToken = ""), response.copy(accessToken = "bad\ntoken"),
            response.copy(tokenType = "Basic"), response.copy(expiresIn = null), response.copy(expiresIn = 0),
            response.copy(expiresIn = -1), response.copy(expiresIn = Long.MAX_VALUE), response.copy(user = null),
            response.copy(user = user.copy(id = null)), response.copy(user = user.copy(role = null)),
            Gson().fromJson("""{"accessToken":"legacy-token"}""", LoginResponse::class.java))
        badResponses.forEach {
            assertNull(sessions.accept(it, true))
            assertNull(sessions.current)
            assertTrue(values.isEmpty())
        }
        assertNotNull(sessions.accept(response.copy(user = user.copy(name = null)), false))
    }

    @Test fun rejectedOrExpiredRestorationNeverVerifiesTheCandidate() {
        val candidate = sessions.accept(response, true)!!
        val restarted = SessionStore(preferences) { now }
        assertNull(restarted.restore(candidate, null))
        assertNull(restarted.current)
        now = candidate.expiresAt
        assertNull(restarted.restore(candidate, user))
        assertNull(restarted.rememberCandidate())
    }
}

private fun memoryPreferences(values: MutableMap<String, Any?>): SharedPreferences {
    val pending = mutableMapOf<String, Any?>()
    var clearing = false
    lateinit var editor: SharedPreferences.Editor
    editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
        when (method.name) {
            "clear" -> { clearing = true; editor }
            "putInt", "putLong", "putString" -> { pending[args!![0] as String] = args[1]; editor }
            "apply", "commit" -> { if (clearing) values.clear(); values.putAll(pending); pending.clear(); clearing = false; if (method.name == "commit") true else null }
            else -> error("Unexpected editor call: ${method.name}")
        }
    } as SharedPreferences.Editor
    return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when (method.name) {
            "edit" -> editor
            "contains" -> values.containsKey(args!![0])
            "getInt", "getLong", "getString" -> values[args!![0]] ?: args[1]
            else -> error("Unexpected preferences call: ${method.name}")
        }
    } as SharedPreferences
}
