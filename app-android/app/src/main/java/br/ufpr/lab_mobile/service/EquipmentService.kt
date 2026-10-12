package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Equipment
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface EquipmentApiService {
    @GET("equipments")
    suspend fun getEquipment(@Header("Authorization") authorization: String): Response<List<Equipment>>
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
}
