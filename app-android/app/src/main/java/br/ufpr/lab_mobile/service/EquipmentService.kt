package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Equipment
import br.ufpr.lab_mobile.model.EquipmentDetail
import br.ufpr.lab_mobile.model.EquipmentReport
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface EquipmentApiService {
    @GET("equipments")
    suspend fun getEquipment(@Header("Authorization") authorization: String): Response<List<Equipment>>

    @GET("equipments/{id}")
    suspend fun getDetail(@Header("Authorization") authorization: String, @Path("id") id: String): Response<EquipmentDetail>

    @GET("equipments/{id}/reports")
    suspend fun getReports(@Header("Authorization") authorization: String, @Path("id") id: String): Response<List<EquipmentReport>>
}

class EquipmentService(private val api: EquipmentApiService) {
    suspend fun getEquipment(token: String): List<Equipment> {
        val response = api.getEquipment("Bearer $token")
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        return response.body() ?: throw IllegalStateException("Empty equipment response")
    }

    suspend fun getDetail(token: String, id: String): EquipmentDetail {
        require(id.isNotBlank())
        val response = api.getDetail("Bearer $token", id)
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        return response.body()?.takeIf { !it.id.isNullOrBlank() }
            ?: throw IllegalStateException("Empty equipment detail")
    }

    suspend fun getReports(token: String, id: String): List<EquipmentReport> {
        require(id.isNotBlank())
        val response = api.getReports("Bearer $token", id)
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        return response.body() ?: throw IllegalStateException("Empty equipment history")
    }
}
