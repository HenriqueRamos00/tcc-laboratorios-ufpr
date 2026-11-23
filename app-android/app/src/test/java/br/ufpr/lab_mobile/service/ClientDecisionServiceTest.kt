package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.model.AuthenticatedUser
import br.ufpr.lab_mobile.model.ClientAuthorizations
import br.ufpr.lab_mobile.model.ClientCompany
import br.ufpr.lab_mobile.model.LoginResponse
import br.ufpr.lab_mobile.session.PortalSession
import br.ufpr.lab_mobile.session.SessionApi
import br.ufpr.lab_mobile.session.SessionInterceptor
import br.ufpr.lab_mobile.session.SessionManager
import br.ufpr.lab_mobile.session.SessionStorage
import com.google.gson.JsonParser
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ClientDecisionServiceTest {
    @Test
    fun `ambas as origens enviam o mesmo contrato de termo com o bearer da sessao`(): Unit = runBlocking {
        listOf(false, true).forEach { lactec ->
            val fixture = Fixture(lactec)
            fixture.service.accept(" quote-1 ", terms())
            val sent = fixture.requests.single()
            assertEquals("POST", sent.method)
            assertEquals("/api/quotes/quote-1/acceptance", sent.url.encodedPath)
            assertEquals("Bearer ${fixture.sessions.current!!.token}", sent.header("Authorization"))
            val payload = JsonParser.parseString(fixture.bodies.single()).asJsonObject
            assertEquals(setOf("billing", "approval"), payload.keySet())
            assertEquals(2, payload["billing"].asJsonObject["invoiceEmails"].asJsonArray.size())
            assertEquals("2026-11-22", payload["approval"].asJsonObject["date"].asString)
            assertFalse(payload.has("dataSource"))
        }
    }

    @Test
    fun `ordem de compra usa multipart file e preserva nome tipo e conteudo`(): Unit = runBlocking {
        val fixture = Fixture()
        val file = File.createTempFile("purchase-order-", ".pdf")
        try {
            file.writeText("%PDF-1.4\nconteudo do pedido")
            fixture.service.acceptWithPurchaseOrder("q", file, "pedido.pdf")
            val sent = fixture.requests.single()
            assertEquals("/api/quotes/q/purchase-order", sent.url.encodedPath)
            assertTrue(sent.body!!.contentType().toString().startsWith("multipart/form-data;"))
            val body = fixture.bodies.single()
            assertTrue(body.contains("name=\"file\"; filename=\"pedido.pdf\""))
            assertTrue(body.contains("Content-Type: application/pdf"))
            assertTrue(body.contains(file.readText()))
        } finally { file.delete() }
    }

    @Test
    fun `recusa aceita justificativa ausente e normaliza a informada`(): Unit = runBlocking {
        val fixture = Fixture()
        fixture.service.reject("q", "  Motivo informado  ")
        fixture.service.reject("q", " \n ")
        assertEquals("Motivo informado", JsonParser.parseString(fixture.bodies[0]).asJsonObject["justification"].asString)
        assertEquals("{}", fixture.bodies[1])
        assertTrue(fixture.requests.all { it.url.encodedPath == "/api/quotes/q/rejection" })
        try {
            fixture.service.reject("q", "a".repeat(1001))
            fail("Deveria impedir justificativa acima do limite")
        } catch (_: IllegalArgumentException) {
            assertEquals(2, fixture.requests.size)
        }
    }

    @Test
    fun `erros nao viram sucesso e POST com 401 nao e repetido`(): Unit = runBlocking {
        listOf(400, 401, 404, 409, 502).forEach { code ->
            val fixture = Fixture(lactec = true, code = code)
            try {
                fixture.service.reject("q", null)
                fail("Deveria propagar HTTP $code")
            } catch (error: HttpException) {
                assertEquals(code, error.code())
                assertEquals(1, fixture.requests.size)
                assertEquals(code == 401, fixture.sessions.current == null)
            }
        }
    }

    private fun terms() = AcceptanceTermsRequest(
        BillingAddressRequest("Empresa", "12345678000190", "Rua 1", "Curitiba", "PR", "80000000",
            listOf("financeiro@exemplo.com", "fiscal@exemplo.com")),
        AcceptanceApprovalRequest("Solicitante", "Responsável", "Gerente", "2026-11-22", false, null),
    )

    private class Fixture(lactec: Boolean = false, code: Int = 201) {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        private val root = "https://portal.exemplo.test/"
        val sessions = SessionManager(root, object : SessionStorage {
            override fun read(): PortalSession? = null
            override fun write(session: PortalSession?) = Unit
        }, object : SessionApi {
            override fun refresh(token: String): retrofit2.Response<LoginResponse> = error("POST não deve ser repetido")
            override fun me(token: String): retrofit2.Response<AuthenticatedUser> = error("Consulta inesperada")
        })
        val service: ClientDecisionService

        init {
            val expiry = System.currentTimeMillis() / 1000 + 3600
            val sourceClaims = if (lactec) "\"authSource\":\"lactec\",\"roles\":\"CLIENTE\",\"serviceCode\":\"portal-web\""
                else "\"role\":\"CLIENTE\""
            val claims = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"exp":$expiry,"sub":"client",$sourceClaims}""".toByteArray())
            sessions.signIn(LoginResponse("header.$claims.signature", "Bearer", 3600,
                AuthenticatedUser("client", "Cliente", "cliente@exemplo.com", if (lactec) "CLIENTE" else "Client",
                    companies = listOf(ClientCompany("1", "Empresa")), serviceCode = "portal-web",
                    authorizations = ClientAuthorizations(listOf("12345678000190")))), false)
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionInterceptor(root.toHttpUrl(), sessions))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    bodies += Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Teste")
                        .body("""{"id":"receipt","kind":0,"recordedAt":"2026-11-22T12:00:00Z"}"""
                            .toResponseBody("application/json".toMediaType())).build()
                }.build()
            service = ClientDecisionService(Retrofit.Builder().baseUrl(root).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(ClientDecisionApiService::class.java))
        }
    }
}
