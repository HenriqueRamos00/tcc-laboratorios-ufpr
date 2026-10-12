package br.ufpr.tcc.backend_lab.application.dto.response;

public enum CadastroStatus {
    ACTIVE,
    INACTIVE;

    public boolean toAtivo() {
        return this == ACTIVE;
    }

    public static CadastroStatus from(boolean ativo) {
        return ativo ? ACTIVE : INACTIVE;
    }
}
