package br.ufpr.lab_mobile.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginActivityTest {
    @Test
    fun `credencial local funciona somente em debug`() {
        assertEquals(true, isTestLogin(true, "lactec@lactec.com", "lactec"))
        assertEquals(false, isTestLogin(false, "lactec@lactec.com", "lactec"))
    }

    @Test
    fun `valida os campos localmente`() {
        assertEquals(
            LoginInputError(EmailError.INVALID, passwordMissing = true),
            validateLogin("email-invalido", ""),
        )
        assertNull(validateLogin("cliente@empresa.com", "senha123"))
    }

    @Test
    fun `traduz respostas da API`() {
        assertEquals(LoginOutcome.Success("token"), loginOutcome(200, "token"))
        assertEquals(LoginOutcome.Failure, loginOutcome(200, ""))
        assertEquals(LoginOutcome.InvalidCredentials, loginOutcome(401, null))
        assertEquals(LoginOutcome.ServiceUnavailable, loginOutcome(502, null))
        assertEquals(LoginOutcome.Failure, loginOutcome(500, null))
    }
}
