package br.ufpr.lab_mobile.controller

import br.ufpr.lab_mobile.R
import org.junit.Assert.*
import org.junit.Test

class LoginActivityTest {
    @Test fun requiredFieldsAndEmailAreValidated() {
        assertEquals(LoginInputError(EmailError.REQUIRED, true), validateLogin("  ", ""))
        assertEquals(LoginInputError(EmailError.INVALID, true), validateLogin("invalid", ""))
        assertNull(validateLogin(" cliente@empresa.com ", " password "))
    }

    @Test fun rejectedRequestsHaveSpecificMessages() {
        assertEquals(R.string.login_bad_request, loginError(400))
        assertEquals(R.string.invalid_credentials, loginError(401))
        assertEquals(R.string.login_service_unavailable, loginError(502))
        assertEquals(R.string.login_loading_error, loginError(500))
    }
}
