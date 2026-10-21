package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;

public record ServicoResponse(String id, String name, String code, boolean active) {
    public static ServicoResponse fromEntity(Servico servico) {
        return new ServicoResponse(servico.getId().toString(), servico.getNome(), servico.getCodigo(), servico.isAtivo());
    }
}
