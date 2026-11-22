package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Equipment
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET

interface EquipmentApiService {
    @GET("api/equipments")
    suspend fun getEquipment(): Response<List<Equipment>>
}

class EquipmentService(private val api: EquipmentApiService) {
    suspend fun getEquipment(): List<Equipment> {
        val response = api.getEquipment()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        return response.body() ?: throw IllegalStateException("Empty equipment response")
    }
}
