package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ViolacaoUnicidadeTests {

    @Test
    void reconheceEmailDuplicadoNaExcecaoDePersistencia() {
        var erro = new DataIntegrityViolationException(
                "Falha ao persistir", erroDeBanco("uk_usuarios_email_normalizado", "23505"));

        assertTrue(ViolacaoUnicidade.email(erro));
        assertFalse(ViolacaoUnicidade.cnpj(erro));
    }

    @Test
    void reconheceCnpjDuplicado() {
        var erro = erroDeBanco("uk_empresas_cnpj", "23505");

        assertTrue(ViolacaoUnicidade.cnpj(erro));
        assertFalse(ViolacaoUnicidade.email(erro));
    }

    @Test
    void ignoraConstraintDesconhecida() {
        var erro = erroDeBanco("outra_constraint", "23505");

        assertFalse(ViolacaoUnicidade.email(erro));
        assertFalse(ViolacaoUnicidade.cnpj(erro));
    }

    @Test
    void naoConfundeChaveEstrangeiraComEmailDuplicado() {
        var erro = erroDeBanco("uk_usuarios_email", "23503");

        assertFalse(ViolacaoUnicidade.email(erro));
    }

    private ConstraintViolationException erroDeBanco(String nomeConstraint, String estadoSql) {
        return new ConstraintViolationException(
                "Falha de integridade", new SQLException("Falha de integridade", estadoSql), nomeConstraint);
    }
}
