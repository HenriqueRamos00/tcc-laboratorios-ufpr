package br.ufpr.lab_mobile.service

import java.io.File
import java.util.Locale
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class BillingAddressRequest(
    val company: String,
    val cnpj: String,
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val invoiceEmails: List<String>,
)

data class AcceptanceApprovalRequest(
    val requesterName: String,
    val responsibleName: String,
    val jobTitle: String,
    val date: String,
    val allowsResultsByEmail: Boolean,
    val resultsEmail: String?,
)

data class AcceptanceTermsRequest(
    val billing: BillingAddressRequest,
    val approval: AcceptanceApprovalRequest,
)

data class RejectionRequest(val justification: String?)

interface ClientDecisionApiService {
    @POST("api/quotes/{id}/acceptance")
    suspend fun recordAcceptance(
        @Path("id") id: String,
        @Body request: AcceptanceTermsRequest,
    ): Response<Unit>

    @Multipart
    @POST("api/quotes/{id}/purchase-order")
    suspend fun recordPurchaseOrder(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
    ): Response<Unit>

    @POST("api/quotes/{id}/rejection")
    suspend fun recordRejection(
        @Path("id") id: String,
        @Body request: RejectionRequest,
    ): Response<Unit>
}

class ClientDecisionService(private val api: ClientDecisionApiService) {
    suspend fun accept(id: String, request: AcceptanceTermsRequest) {
        require(id.isNotBlank()) { "Quote ID must not be blank" }
        ensureSuccessful(api.recordAcceptance(id.trim(), request))
    }

    suspend fun acceptWithPurchaseOrder(id: String, file: File, fileName: String) {
        require(id.isNotBlank()) { "Quote ID must not be blank" }
        val mediaType = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }.toMediaType()
        val part = MultipartBody.Part.createFormData("file", fileName, file.asRequestBody(mediaType))
        ensureSuccessful(api.recordPurchaseOrder(id.trim(), part))
    }

    suspend fun reject(id: String, justification: String?) {
        require(id.isNotBlank()) { "Quote ID must not be blank" }
        val value = justification?.trim().orEmpty().takeIf(String::isNotEmpty)
        require(value == null || value.length <= MAX_JUSTIFICATION_LENGTH) {
            "Rejection justification is too long"
        }
        ensureSuccessful(api.recordRejection(id.trim(), RejectionRequest(value)))
    }

    private fun ensureSuccessful(response: Response<Unit>) {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
    }

    companion object {
        const val MAX_JUSTIFICATION_LENGTH = 1000
        const val MAX_PURCHASE_ORDER_BYTES = 50L * 1024 * 1024
    }
}
