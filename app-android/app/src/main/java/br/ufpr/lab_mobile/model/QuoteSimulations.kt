package br.ufpr.lab_mobile.model

import br.ufpr.lab_mobile.R

enum class SimulatedDecision(val labelRes: Int) {
    ACCEPTED(R.string.quote_simulated_accept),
    REJECTED(R.string.quote_simulated_reject),
}

data class QuoteSimulation(val decision: SimulatedDecision, val reason: String?)

/** In-memory session overlay. Never changes the server's quote or survives process restart. */
object QuoteSimulations {
    private val results = mutableMapOf<String, QuoteSimulation>()
    private var sessionToken: String? = null

    @Synchronized
    fun startSession(token: String) {
        if (sessionToken != token) clear()
        sessionToken = token
    }

    @Synchronized
    fun get(id: String?): QuoteSimulation? = results[id?.trim()]

    @Synchronized
    fun isSessionActive(token: String?): Boolean = !token.isNullOrBlank() && sessionToken == token

    @Synchronized
    fun confirm(id: String, status: String?, decision: SimulatedDecision, reason: String?): Boolean {
        val key = id.trim()
        if (sessionToken == null || key.isEmpty() || !QuoteStatusRules.canDecide(null, status) || results.containsKey(key)) return false
        results[key] = QuoteSimulation(decision, reason?.trim()?.takeIf { it.isNotEmpty() })
        return true
    }

    @Synchronized
    fun clear() {
        results.clear()
        sessionToken = null
    }
}
