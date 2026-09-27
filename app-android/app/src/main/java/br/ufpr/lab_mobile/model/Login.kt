package br.ufpr.lab_mobile.model

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val accessToken: String?,
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
