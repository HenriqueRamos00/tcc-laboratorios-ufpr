package br.ufpr.tcc.backend_lab.domain.exception;

public class TecnicoNaoEncontradoException extends RuntimeException {
	public TecnicoNaoEncontradoException(Long id) {
		super("Técnico não encontrado");
	}
}
