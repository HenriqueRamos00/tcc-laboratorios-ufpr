package br.ufpr.tcc.auth.account;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Identifica a restricao informada pelo Hibernate sem interpretar mensagens do driver. */
final class AccountConstraintViolation {

    private AccountConstraintViolation() {
    }

    static boolean matches(DataIntegrityViolationException failure, String expectedName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null) {
                    String unquoted = name.replace("\"", "");
                    String simpleName = unquoted.substring(unquoted.lastIndexOf('.') + 1);
                    if (expectedName.equalsIgnoreCase(simpleName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
