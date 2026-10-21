package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.AtualizarServicoRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.ServicoResponse;
import br.ufpr.tcc.backend_auth.application.service.ServicoService;
import br.ufpr.tcc.backend_auth.domain.repository.ServicoRepository;
import br.ufpr.tcc.backend_auth.domain.repository.TokenServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtualizarServicoUseCase {
    private final ServicoRepository servicos;
    private final TokenServicoRepository tokens;
    private final ServicoService cadastro;

    @Transactional
    public ServicoResponse execute(Long id, AtualizarServicoRequest request) {
        cadastro.validar(request);
        var servico = cadastro.bloquear(id);
        cadastro.verificarCodigo(request.code(), id);
        servico.setNome(request.name());
        servico.setCodigo(request.code());
        servico.setAtivo(request.active());
        if (!request.active()) tokens.excluirPorServico(id);
        return ServicoResponse.fromEntity(servicos.saveAndFlush(servico));
    }
}
