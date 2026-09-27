package br.ufpr.lab_mobile.model

data class Quote(
    val id: String?,
    val code: String?,
    val name: String?,
    val status: String?,
    val stage: String?,
    val description: String?,
    val companyName: String?,
    val externalContactName: String?,
    val externalContactEmail: String?,
    val totalPrice: Double?,
    val createdDate: String?,
)
