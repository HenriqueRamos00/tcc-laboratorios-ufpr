package br.ufpr.tcc.backend_lab.domain.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum StatusComercialOrcamento {
    PENDING_ACCEPTANCE("PendingAcceptance"),
    IN_PROGRESS("InProgress"),
    FINISHED("Finished"),
    REFUSED("Refused"),
    CANCELLED("Cancelled");

    private final String codigo;

    StatusComercialOrcamento(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static List<String> codigos() {
        return Arrays.stream(values()).map(StatusComercialOrcamento::codigo).toList();
    }

    public static Optional<StatusComercialOrcamento> fromCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(status -> status.codigo.equals(codigo))
                .findFirst();
    }
}
