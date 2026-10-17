package br.ufpr.tcc.backend_lab.domain.exception;

public class EquipamentoNaoEncontradoException extends RuntimeException {
	public EquipamentoNaoEncontradoException(Long id) {
		super("Equipamento não encontrado: " + id);
	}
}
