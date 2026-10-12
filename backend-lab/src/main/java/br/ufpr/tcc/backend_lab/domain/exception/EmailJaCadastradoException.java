package br.ufpr.tcc.backend_lab.domain.exception;

public class EmailJaCadastradoException extends RuntimeException {
	public EmailJaCadastradoException() {
		super("Já existe um usuário com este e-mail");
	}
}
