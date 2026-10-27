package br.ufpr.tcc.backend_lab.domain.model.enums;

import java.util.Arrays;
import java.util.Optional;

public enum StatusOrcamentoTecnico {
    INCOMPLETE("incomplete"),
    COMPLETE("complete"),
    VALIDATED("validated"),
    REPORT_PUBLISHED("report-published"),
    CANCELLED("cancelled");

    private final String codigo;

    StatusOrcamentoTecnico(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static Optional<StatusOrcamentoTecnico> fromCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(status -> status.codigo.equals(codigo))
                .findFirst();
    }
}
