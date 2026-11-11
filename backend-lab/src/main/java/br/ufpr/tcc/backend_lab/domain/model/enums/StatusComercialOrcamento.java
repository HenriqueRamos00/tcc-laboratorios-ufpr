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

    public boolean aceitaNovosEnsaios() {
        return this == IN_PROGRESS || this == FINISHED;
    }

    public static List<StatusComercialOrcamento> elegiveisParaNovosEnsaios() {
        return List.of(IN_PROGRESS, FINISHED);
    }

    public static List<String> codigosElegiveisParaNovosEnsaios() {
        return elegiveisParaNovosEnsaios().stream().map(StatusComercialOrcamento::codigo).toList();
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
