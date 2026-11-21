package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.LoginRequest
import br.ufpr.lab_mobile.model.LoginResponse
import br.ufpr.lab_mobile.model.AuthenticatedUser
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.Locale

interface LoginApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/refresh")
    fun refresh(@Header("Authorization") authorization: String): Call<LoginResponse>

    @GET("auth/me")
    fun me(@Header("Authorization") authorization: String): Call<AuthenticatedUser>
}

class LoginService(private val api: LoginApiService) {
    suspend fun login(email: String, password: String) =
        api.login(LoginRequest(email.trim().lowercase(Locale.ROOT), password))
}
