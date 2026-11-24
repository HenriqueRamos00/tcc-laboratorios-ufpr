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
    val protocol: String? = null,
    // Etapa informada pelo laboratório (ex.: "em-execucao", "relatorio-publicado"). O comercial
    // para de avançar depois do aceite, então só este campo mostra o andamento das análises.
    val laboratoryStage: String? = null,
    val clientDecision: ClientDecision? = null,
)

/** Última decisão registrada pelo cliente no portal (aceite, ordem de compra, envio de amostra ou recusa). */
data class ClientDecision(
    val id: String?,
    val kind: String?,
    val recordedAt: String?,
)
