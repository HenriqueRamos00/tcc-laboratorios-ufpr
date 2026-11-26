package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.LabClassification
import br.ufpr.lab_mobile.model.MeasuredValue
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HealthServiceTest {
    @Test
    fun `reads the portal contract and ignores what the app does not show`() = runBlocking {
        lateinit var path: String
        val indicators = service { path = it.url.encodedPath; BODY }.getIndicators(" MQ ")

        assertEquals("/api/equipments/MQ/health", path)
        assertEquals("TR-A", indicators.tag)
        val gases = indicators.gases
        assertEquals("2026-11-23", gases.date)
        assertEquals(listOf("h2", "c2h2", "co"), gases.indicators.map { it.key })
        assertEquals(MeasuredValue.Quantity(BigDecimal("25.0")), gases.indicators[0].latest)
        assertEquals(MeasuredValue.NotDetected, gases.indicators[1].latest)
        assertEquals("null stays not performed", MeasuredValue.NotPerformed, gases.indicators[2].latest)
        assertEquals(LabClassification.NORMAL, gases.report?.classification)
        assertEquals("Sem anormalidades.", gases.report?.text)
        assertEquals("máx. 40", indicators.oil.indicators.single().limit)
        assertEquals(MeasuredValue.Text("<5"), indicators.oil.indicators.single().latest)
    }

    @Test
    fun `an equipment without analyses has empty panels`() = runBlocking {
        val indicators = service {
            """{"tag":"TR-A","physicalChemical":{"columns":[],"tests":[],"report":{"title":null,"paragraphs":[]}},
                "dissolvedGases":{"analysisDates":[],"gases":[],"report":{"title":null,"paragraphs":[]}}}"""
        }.getIndicators("MQ")
        assertTrue(indicators.gases.indicators.isEmpty())
        assertTrue(indicators.oil.indicators.isEmpty())
    }

    @Test
    fun `HTTP errors and empty bodies are failures`() = runBlocking {
        listOf(400, 401, 403, 404, 501, 502).forEach { code ->
            try {
                service(code) { "{}" }.getIndicators("MQ")
                fail("Expected HTTP $code")
            } catch (error: HttpException) {
                assertEquals(code, error.code())
            }
        }
        listOf("null", "not json").forEach { body ->
            try {
                service { body }.getIndicators("MQ")
                fail("Expected invalid-body failure")
            } catch (error: Exception) {
                assertFalse(error is HttpException)
            }
        }
    }

    @Test
    fun `the opaque id is encoded once and a blank one never reaches the API`() = runBlocking {
        lateinit var path: String
        service { path = it.url.encodedPath; BODY }.getIndicators("a/b %2F")
        assertEquals("/api/equipments/a%2Fb%20%252F/health", path)
        try {
            service { fail("No request expected"); BODY }.getIndicators(" ")
            fail("Expected rejected identifier")
        } catch (_: IllegalArgumentException) { }
    }

    private fun service(code: Int = 200, respond: (Request) -> String): HealthService {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val body = respond(chain.request())
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test response").body(body.toResponseBody()).build()
        }).build()
        return HealthService(Retrofit.Builder().baseUrl("http://localhost/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(HealthApiService::class.java))
    }

    private companion object {
        const val BODY = """{"equipmentId":"MQ","tag":"TR-A","selectedAnalysis":null,
            "physicalChemical":{"conformity":null,"verdict":null,"nominalVoltage":"13.800 kV",
              "columns":[{"date":"2026-11-23","isCurrentResult":true}],
              "analyses":[{"date":"2026-11-23","values":{"agua":null}}],
              "tests":[{"key":"agua","name":"Teor de Água (ppm m/m)","method":"NBR 10710","limit":"máx. 40",
                "valuesByAnalysis":["<5"],"classification":null}],
              "report":{"title":null,"paragraphs":[]}},
            "dissolvedGases":{"conformity":null,"verdict":null,"analysisDates":["2026-11-23"],
              "analyses":[{"date":"2026-11-23","values":{"h2":25.0,"c2h2":null}}],
              "gases":[{"key":"h2","name":"Hidrogênio","formula":"H₂","results":[25.0]},
                {"key":"c2h2","name":"Acetileno","formula":"C₂H₂","results":["ND"]},
                {"key":"co","name":"Monóxido de Carbono","formula":"CO","results":[null]}],
              "report":{"title":"normal","paragraphs":["Sem anormalidades."]}},
            "diagnosis":null,"unreadableResults":[],"totalizerChecks":[]}"""
    }
}
