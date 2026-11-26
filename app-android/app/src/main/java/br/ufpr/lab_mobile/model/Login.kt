package br.ufpr.lab_mobile.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String) {
    override fun toString() = "LoginRequest(password=[REDACTED])"
}

data class LoginResponse(
    @SerializedName("Token") val accessToken: String?,
    val tokenType: String?,
    val expiresIn: Long?,
    val user: AuthenticatedUser?,
) {
    override fun toString() = "LoginResponse(token=[REDACTED])"
}

data class AuthenticatedUser(
    val id: String?,
    val name: String?,
    val email: String?,
    val role: String?,
    val companies: List<ClientCompany>? = null,
    val serviceCode: String? = null,
    val authorizations: ClientAuthorizations? = null,
)

data class ClientCompany(val id: String?, val name: String?)
data class ClientAuthorizations(val companyCnpjs: List<String>?)
