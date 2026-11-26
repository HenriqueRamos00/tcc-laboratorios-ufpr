package br.ufpr.lab_mobile.session

import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.LoginResponse
import com.google.gson.JsonParser
import java.io.IOException
import java.util.Base64
import java.util.UUID

data class PortalSession(
    val token: String,
    val user: AuthenticatedUser,
    val expiresAt: Long,
    val renewable: Boolean,
    val remembered: Boolean,
    val apiRoot: String,
    // Stable across token renewal; changes on every login, including the same account.
    val id: String = UUID.randomUUID().toString(),
) {
    override fun toString() = "PortalSession(id=$id, token=[REDACTED])"
}

class InvalidSessionException : IOException("Invalid portal session")
class SessionExpiredException : IOException("Portal session expired")

internal object SessionValidation {
    fun fromLogin(response: LoginResponse?, remembered: Boolean, apiRoot: String, now: Long): PortalSession {
        val token = response?.accessToken ?: throw InvalidSessionException()
        val user = response.user ?: throw InvalidSessionException()
        val duration = response.expiresIn ?: throw InvalidSessionException()
        if (!response.tokenType.equals("Bearer", ignoreCase = true) || duration !in 1..Int.MAX_VALUE.toLong())
            throw InvalidSessionException()
        val (expiry, renewable) = identity(token, user)
        val expiresAt = minOf(expiry, now + duration * 1000)
        if (expiresAt <= now) throw InvalidSessionException()
        return PortalSession(token, user, expiresAt, renewable, remembered, apiRoot)
    }

    fun validStored(session: PortalSession, apiRoot: String): Boolean = try {
        val (expiry, renewable) = identity(session.token, session.user)
        session.apiRoot == apiRoot && session.id.isNotBlank() && session.remembered &&
            session.expiresAt > 0 && session.expiresAt <= expiry && session.renewable == renewable
    } catch (_: Exception) { false }

    fun validUser(session: PortalSession, user: AuthenticatedUser): Boolean = try {
        identity(session.token, user)
        user.id == session.user.id && user.email.equals(session.user.email, ignoreCase = true)
    } catch (_: Exception) { false }

    // This is response validation, not signature verification. Authorization stays on the server.
    private fun identity(token: String, user: AuthenticatedUser): Pair<Long, Boolean> {
        try {
            val parts = token.split('.')
            if (parts.size != 3 || parts.any(String::isBlank)) throw InvalidSessionException()
            val claims = JsonParser.parseString(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).asJsonObject
            fun claim(name: String): String? = claims[name]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val expiration = claims["exp"]?.asBigDecimal?.longValueExact() ?: throw InvalidSessionException()
            if (expiration !in 1..Long.MAX_VALUE / 1000) throw InvalidSessionException()
            if (listOf(user.id, user.name, user.email).any { it.isNullOrBlank() } ||
                user.role !in setOf("CLIENTE", "Client")) throw InvalidSessionException()
            val source = claim("authSource")
            if (source != null && source != "lactec") throw InvalidSessionException()
            val renewable = source == "lactec"
            if (claim(if (renewable) "roles" else "role") != "CLIENTE") throw InvalidSessionException()
            if (renewable) {
                if (user.role != "CLIENTE" || user.id != claim("sub") ||
                    user.serviceCode.isNullOrBlank() || user.serviceCode != claim("serviceCode") ||
                    user.authorizations?.companyCnpjs == null) throw InvalidSessionException()
            } else if (user.companies == null || user.companies.any { it.id.isNullOrBlank() || it.name.isNullOrBlank() }) {
                throw InvalidSessionException()
            }
            return expiration * 1000 to renewable
        } catch (failure: InvalidSessionException) { throw failure
        } catch (_: Exception) { throw InvalidSessionException() }
    }
}
