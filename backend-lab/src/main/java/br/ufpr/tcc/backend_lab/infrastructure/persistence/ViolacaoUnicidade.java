package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;

public final class ViolacaoUnicidade {

    private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";
    // Nome gerado pelo Hibernate para a restrição UNIQUE(email) sem nome explícito.
    private static final String CONSTRAINT_EMAIL_GERADA_HIBERNATE = "ukkfsp0s1tflm1cwlj8idhqsad0";
    private static final Set<String> CONSTRAINTS_EMAIL =
            Set.of(
                    "uk_usuarios_email",
                    "uk_usuarios_email_normalizado",
                    "usuarios_email_key",
                    CONSTRAINT_EMAIL_GERADA_HIBERNATE);
    private static final Set<String> CONSTRAINTS_CNPJ = Set.of("uk_empresas_cnpj");

    private ViolacaoUnicidade() {}

    public static boolean email(Throwable error) {
        return isViolation(error, CONSTRAINTS_EMAIL);
    }

    public static boolean cnpj(Throwable error) {
        return isViolation(error, CONSTRAINTS_CNPJ);
    }

    private static boolean isViolation(Throwable error, Set<String> knownConstraints) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && SQL_STATE_UNIQUE_VIOLATION.equals(violation.getSQLState())
                    && violation.getConstraintName() != null
                    && knownConstraints.contains(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
