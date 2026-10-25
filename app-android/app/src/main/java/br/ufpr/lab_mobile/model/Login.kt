package br.ufpr.lab_mobile.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    @SerializedName("Token") val accessToken: String?,
    val tokenType: String?,
    val expiresIn: Long?,
    val user: AuthenticatedUser?,
)

data class AuthenticatedUser(
    val id: String?,
    val name: String?,
    val email: String?,
    val role: String?,
)

internal fun AuthenticatedUser.isValid() =
    !id.isNullOrBlank() && !email.isNullOrBlank() && !role.isNullOrBlank()
