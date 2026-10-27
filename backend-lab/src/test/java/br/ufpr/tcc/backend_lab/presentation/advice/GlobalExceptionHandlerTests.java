package br.ufpr.tcc.backend_lab.presentation.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PersistenciaController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

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
        return new DataIntegrityViolationException("Falha ao persistir cadastro",
                new ConstraintViolationException("Valor duplicado",
                        new SQLException("Valor duplicado", "23505"), nomeConstraint));
    }

    @Test
    void traduzConflitoJpaDiretoPara409SemExigirNovoLogin() throws Exception {
        mockMvc.perform(get("/falhas/otimista-jpa"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O ensaio foi alterado por outra sessão"));
    }

    @Test
    void traduzConflitoOtimistaDoRepositorioPara409() throws Exception {
        mockMvc.perform(get("/falhas/otimista-spring"))
                .andExpect(status().isConflict());
    }

    @Test
    void informaFalhaDePersistenciaSemExporDetalhesDoBanco() throws Exception {
        mockMvc.perform(get("/falhas/persistencia"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Falha ao persistir os dados"));
    }

    @RestController
    static class PersistenciaController {
        @GetMapping("/falhas/otimista-jpa")
        void conflitoJpa() {
            throw new OptimisticLockException("Detalhe interno da persistência");
        }

        @GetMapping("/falhas/otimista-spring")
        void conflitoSpring() {
            throw new ObjectOptimisticLockingFailureException("ExecucaoEnsaio", 1L);
        }

        @GetMapping("/falhas/persistencia")
        void persistencia() {
            throw new JpaSystemException(new IllegalStateException("Detalhe interno do banco"));
        }
    }
}
