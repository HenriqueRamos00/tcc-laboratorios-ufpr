package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.HealthIndicators
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface HealthApiService {
    @GET("api/equipments/{id}/health")
    suspend fun getIndicators(
        @Path("id") equipmentId: String,
    ): Response<HealthIndicators>
}

/** Consulta os indicadores de saúde de um equipamento. */
class HealthService(private val api: HealthApiService) {
    suspend fun getIndicators(equipmentId: String): HealthIndicators {
        require(equipmentId.isNotBlank()) { "Equipment ID must not be blank" }
        return successBody(api.getIndicators(equipmentId.trim())) ?: throw IllegalStateException("Empty health response")
    }
}
