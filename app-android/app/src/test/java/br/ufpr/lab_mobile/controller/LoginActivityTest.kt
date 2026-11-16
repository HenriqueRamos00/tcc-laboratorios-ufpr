package br.ufpr.lab_mobile.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginActivityTest {
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
        assertEquals(LoginOutcome.Cooldown, loginOutcome(429, null))
        assertEquals(LoginOutcome.Failure, loginOutcome(500, null))
    }
}
