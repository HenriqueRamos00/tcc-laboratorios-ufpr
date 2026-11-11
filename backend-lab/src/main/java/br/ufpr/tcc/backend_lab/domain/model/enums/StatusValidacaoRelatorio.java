package br.ufpr.tcc.backend_lab.domain.model.enums;

public enum StatusValidacaoRelatorio {
    PENDING_VALIDATION("pending-validation"),
    VALIDATED("validated");

    private final String codigo;

    StatusValidacaoRelatorio(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
