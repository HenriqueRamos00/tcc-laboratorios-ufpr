package br.ufpr.tcc.backend_lab.domain.model.enums;

public enum StatusExecucaoEnsaio {
    INCOMPLETE("incomplete"),
    COMPLETE("complete"),
    VALIDATED("validated"),
    FINALIZED("finalized"),
    DRAFTING_REPORT("drafting-report"),
    REPORT_PUBLISHED("report-published"),
    CANCELLED("cancelled");

    private final String codigo;

    StatusExecucaoEnsaio(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
