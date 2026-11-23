package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.Equipment
import br.ufpr.lab_mobile.model.EquipmentDetail
import br.ufpr.lab_mobile.model.EquipmentReport
import java.io.File
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface EquipmentApiService {
    @GET("api/equipments")
    suspend fun getEquipment(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<List<Equipment>>

    @GET("api/equipments/{id}")
    suspend fun getEquipmentDetail(
        @Path("id") id: String,
    ): Response<EquipmentDetail>

    @GET("api/equipments/{id}/reports")
    suspend fun getEquipmentReports(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<List<EquipmentReport>>

    @Streaming
    @GET("api/equipments/{id}/reports/{reportId}/document")
    suspend fun getReportDocument(
        @Path("id") id: String,
        @Path("reportId") reportId: String,
    ): Response<ResponseBody>
}

/**
 * Consulta os equipamentos do cliente no portal. Os identificadores são opacos e voltam para a
 * API como vieram; o de um relatório do histórico só vale nas rotas do próprio equipamento.
 */
class EquipmentService(private val api: EquipmentApiService) {
    /** Todos os equipamentos do cliente, em todas as páginas, para a busca local da lista. */
    suspend fun getEquipment(): List<Equipment> = collectPages(EQUIPMENT_PAGE_SIZE) { page ->
        val response = api.getEquipment(page, EQUIPMENT_PAGE_SIZE)
        val equipment = successBody(response) ?: throw IllegalStateException("Empty equipment response")
        PageResult(equipment, totalCount(response))
    }

    suspend fun getDetail(id: String): EquipmentDetail {
        require(id.isNotBlank()) { "Equipment ID must not be blank" }
        return successBody(api.getEquipmentDetail(id.trim()))?.takeIf { !it.id.isNullOrBlank() }
            ?: throw IllegalStateException("Empty equipment detail response")
    }

    /** Histórico completo, na ordem da API: do relatório mais recente para o mais antigo. */
    suspend fun getReports(id: String): List<EquipmentReport> {
        require(id.isNotBlank()) { "Equipment ID must not be blank" }
        return collectPages(REPORT_PAGE_SIZE) { page ->
            val response = api.getEquipmentReports(id.trim(), page, REPORT_PAGE_SIZE)
            val reports = successBody(response) ?: throw IllegalStateException("Empty equipment history response")
            PageResult(reports, totalCount(response))
        }
    }

    /** Grava o PDF de um relatório do histórico, com as mesmas validações dos demais PDFs. */
    suspend fun downloadReport(id: String, reportId: String, destination: File) {
        require(id.isNotBlank()) { "Equipment ID must not be blank" }
        require(reportId.isNotBlank()) { "Report ID must not be blank" }
        downloadPdf(destination) { api.getReportDocument(id.trim(), reportId.trim()) }
    }

    private companion object {
        /** Na fonte AutoLAB cada página lê todos os ensaios dos seus equipamentos, com limite de linhas. */
        const val EQUIPMENT_PAGE_SIZE = 20

        /** Maior página que as rotas de equipamento aceitam na fonte Spring; acima disso respondem 400. */
        const val REPORT_PAGE_SIZE = 100
    }
}
