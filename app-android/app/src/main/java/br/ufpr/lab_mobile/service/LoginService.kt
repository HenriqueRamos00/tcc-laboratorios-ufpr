package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.LoginRequest
import br.ufpr.lab_mobile.model.LoginResponse
import br.ufpr.lab_mobile.model.AuthenticatedUser
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.Locale
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

interface LoginApiService {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("/auth/me")
    suspend fun me(@Header("Authorization") authorization: String): Response<AuthenticatedUser>
}

class LoginService(private val api: LoginApiService) {
    suspend fun login(email: String, password: String) =
        api.login(LoginRequest(email.trim().lowercase(Locale.ROOT), password))

    suspend fun me(token: String) = api.me("Bearer $token")
}

internal fun retryAfterMillis(value: String?, now: Long): Long {
    val seconds = value?.trim()?.toLongOrNull()
    if (seconds != null && seconds >= 0) return seconds.coerceAtMost((Long.MAX_VALUE - now) / 1000) * 1000
    val date = runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
    return date?.let { (it - now).coerceAtLeast(0) } ?: 60_000L
}
