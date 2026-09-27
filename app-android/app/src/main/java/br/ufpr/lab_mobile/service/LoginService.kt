package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.LoginRequest
import br.ufpr.lab_mobile.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LoginApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}

class LoginService(private val api: LoginApiService) {
    suspend fun login(email: String, password: String) = api.login(LoginRequest(email, password))
}
