package br.ufpr.tcc.backend_lab.domain.exception;

import java.util.Objects;

public class RegraNegocioException extends RuntimeException {

    private final Motivo motivo;

    public RegraNegocioException(Motivo motivo, String message) {
        super(message);
        this.motivo = Objects.requireNonNull(motivo, "motivo");
    }

    public Motivo getMotivo() {
        return motivo;
    }

    public static RegraNegocioException dadosInvalidos(String message) {
        return new RegraNegocioException(Motivo.DADOS_INVALIDOS, message);
    }

    public static RegraNegocioException conflito(String message) {
        return new RegraNegocioException(Motivo.CONFLITO, message);
    }

    public static RegraNegocioException naoAutenticado(String message) {
        return new RegraNegocioException(Motivo.NAO_AUTENTICADO, message);
    }

    public static RegraNegocioException acessoNegado(String message) {
        return new RegraNegocioException(Motivo.ACESSO_NEGADO, message);
    }

    public static RegraNegocioException naoEncontrado(String message) {
        return new RegraNegocioException(Motivo.NAO_ENCONTRADO, message);
    }

    public enum Motivo {
        DADOS_INVALIDOS,
        CONFLITO,
        NAO_AUTENTICADO,
        ACESSO_NEGADO,
        NAO_ENCONTRADO
    }
}
