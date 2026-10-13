package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoResponse;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOrcamentoClienteUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final AcessoClienteAtual acessoClienteAtual;

    @Transactional(readOnly = true)
    public OrcamentoResponse execute(Long usuarioId, Long orcamentoId) {
        var cliente = acessoClienteAtual.execute(usuarioId);
        var orcamento =
                orcamentoRepository
                        .findById(orcamentoId)
                        .orElseThrow(
                                () ->
                                        RegraNegocioException.naoEncontrado(
                                                "Orçamento não encontrado"));
        acessoClienteAtual.autorizar(cliente, orcamento);

        return OrcamentoResponse.from(orcamento);
    }
}
