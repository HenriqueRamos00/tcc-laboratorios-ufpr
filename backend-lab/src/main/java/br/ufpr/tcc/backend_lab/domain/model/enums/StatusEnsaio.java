package br.ufpr.tcc.backend_lab.domain.model.enums;

import java.util.Arrays;

public enum StatusEnsaio {
    INCOMPLETE("incomplete"),
    COMPLETE("complete"),
    VALIDATED("validated"),
    FINALIZED("finalized"),
    DRAFTING_REPORT("drafting-report"),
    REPORT_PUBLISHED("report-published"),
    CANCELLED("cancelled");

    private final String codigo;

    StatusEnsaio(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static StatusEnsaio deCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(status -> status.codigo.equals(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Status técnico desconhecido: " + codigo));
    }
}
