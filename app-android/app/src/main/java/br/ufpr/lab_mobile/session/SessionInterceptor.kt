package br.ufpr.lab_mobile.session

import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class SessionInterceptor(private val root: HttpUrl, private val sessions: SessionManager) : Interceptor {
    private val protectedPath = root.resolve("api/")!!.encodedPath

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isProtected(request.url)) return chain.proceed(request)
        val started = sessions.current ?: throw SessionExpiredException()
        val usable = sessions.validSession(started) ?: throw SessionExpiredException()
        var response = chain.proceed(request.withToken(usable))
        if (!sessions.belongsTo(usable)) { response.close(); throw SessionExpiredException() }
        if (response.code != 401 || isTenantConfigurationError(response)) return response

        // Only replay reads, once. Login and renewal use a separate client without this interceptor.
        if (!usable.renewable || request.method !in setOf("GET", "HEAD")) {
            sessions.invalidate(usable)
            return response
        }
        response.close()
        val renewed = sessions.renew(usable) ?: throw SessionExpiredException()
        response = chain.proceed(request.withToken(renewed))
        if (!sessions.belongsTo(renewed)) { response.close(); throw SessionExpiredException() }
        if (response.code == 401 && !isTenantConfigurationError(response)) sessions.invalidate(renewed)
        return response
    }

    private fun isProtected(url: HttpUrl) = url.scheme == root.scheme && url.host == root.host &&
        url.port == root.port && url.encodedPath.startsWith(protectedPath)

    private fun Request.withToken(session: PortalSession) = newBuilder()
        .header("Authorization", "Bearer ${session.token}").build()

    private fun isTenantConfigurationError(response: Response): Boolean = try {
        JsonParser.parseString(response.peekBody(8192).string()).asJsonObject["code"]?.asString == "tenant-not-identified"
    } catch (_: Exception) { false }
}
