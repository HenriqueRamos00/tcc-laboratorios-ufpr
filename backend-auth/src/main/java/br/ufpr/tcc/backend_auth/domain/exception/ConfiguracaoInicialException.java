package br.ufpr.tcc.backend_auth.domain.exception;

public class ConfiguracaoInicialException extends RuntimeException {
    private final boolean concluida;

    public ConfiguracaoInicialException(boolean concluida) {
        super(concluida ? "O administrador inicial já foi cadastrado" : "Link de configuração inválido");
        this.concluida = concluida;
    }

    public boolean isConcluida() {
        return concluida;
    }
}
