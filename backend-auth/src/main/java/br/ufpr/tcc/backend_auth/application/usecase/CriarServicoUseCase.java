package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarServicoRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.ServicoResponse;
import br.ufpr.tcc.backend_auth.application.service.ServicoService;
import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import br.ufpr.tcc.backend_auth.domain.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CriarServicoUseCase {
    private final ServicoRepository servicos;
    private final ServicoService cadastro;

    @Transactional
    public ServicoResponse execute(CriarServicoRequest request) {
        cadastro.validar(request);
        cadastro.verificarCodigo(request.code(), null);
        Servico servico = new Servico();
        servico.setNome(request.name());
        servico.setCodigo(request.code());
        return ServicoResponse.fromEntity(servicos.saveAndFlush(servico));
    }
}
