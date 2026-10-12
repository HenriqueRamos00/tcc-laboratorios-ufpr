package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoResponse;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListOrcamentosClienteUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final AcessoClienteAtual acessoClienteAtual;

    @Transactional(readOnly = true)
    public List<OrcamentoResponse> execute(Long usuarioId) {
        acessoClienteAtual.execute(usuarioId);
        return orcamentoRepository.listarAcessiveisPorUsuario(usuarioId).stream()
                .map(OrcamentoResponse::from)
                .toList();
    }
}
