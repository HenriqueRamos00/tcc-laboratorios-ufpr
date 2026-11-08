package br.ufpr.tcc.backend_lab.domain.exception;

public class ConflitoRelatorioException extends RuntimeException {
    private final String code;

    public ConflitoRelatorioException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
