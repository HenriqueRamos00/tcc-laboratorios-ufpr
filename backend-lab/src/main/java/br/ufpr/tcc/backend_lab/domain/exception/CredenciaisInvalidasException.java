package br.ufpr.tcc.backend_lab.domain.exception;

public class CredenciaisInvalidasException extends RuntimeException {

	public CredenciaisInvalidasException() {
		super("Credenciais inválidas");
	}
}
