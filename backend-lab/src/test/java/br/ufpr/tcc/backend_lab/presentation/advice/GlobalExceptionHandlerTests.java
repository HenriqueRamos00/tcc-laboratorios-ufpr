package br.ufpr.tcc.backend_lab.presentation.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void emailDuplicadoRetornaConflitoComMensagemClara() {
        var resposta = handler.handleViolacaoIntegridade(erroDeUnicidade("uk_usuarios_email"));

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("E-mail já cadastrado", resposta.getBody().message());
    }

    @Test
    void cnpjDuplicadoRetornaConflitoComMensagemClara() {
        var resposta = handler.handleViolacaoIntegridade(erroDeUnicidade("uk_empresas_cnpj"));

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("CNPJ já cadastrado", resposta.getBody().message());
    }

    @Test
    void outraConstraintRetornaErroInternoSemExporDetalhesDoBanco() {
        var resposta = handler.handleViolacaoIntegridade(erroDeUnicidade("outra_constraint"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
        assertEquals("Falha ao persistir cadastro", resposta.getBody().message());
    }

    private DataIntegrityViolationException erroDeUnicidade(String nomeConstraint) {
        return new DataIntegrityViolationException(
                "Falha ao persistir cadastro",
                new ConstraintViolationException(
                        "Valor duplicado", new SQLException("Valor duplicado", "23505"), nomeConstraint));
    }
}
