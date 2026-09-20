package br.ufpr.lab_mobile.api

import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

object ApiClient {
    fun endpoint(baseUrl: String): AuthEndpoint = Retrofit.Builder()
        .baseUrl("${baseUrl.trimEnd('/')}/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthEndpoint::class.java)
}

interface AuthEndpoint {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val accessToken: String?)
